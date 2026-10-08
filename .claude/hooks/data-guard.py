#!/usr/bin/env python3
"""PreToolUse hook: keep coding agents out of the bank's raw client data.

The bank's hour-0 export goes in data/bank/ (git-ignored). Anything a coding
agent reads is sent to an LLM, and the organisers forbid client-identifying
data reaching one (ADR 0004). This hook blocks any tool call that targets
that folder. It guards against accidents, not against a determined bypass:
work from the canonical schemas and synthetic fixtures instead.

Exit 2 blocks the call and shows stderr to the agent.
"""
import json
import os
import re
import sys

GUARDED = os.path.join("data", "bank")
PATH_KEYS = ("file_path", "path", "notebook_path", "glob")
# data/bank, data//bank, ./data/bank, as a whole path segment
BASH_PATTERN = re.compile(r"(^|[\s'\"=/.])data/+bank(/|\b|$)")


def block(reason):
    print(
        f"Blocked by data-guard: {reason}. data/bank/ holds the bank's client "
        "export and must never reach an LLM (ADR 0004). Use the canonical "
        "schemas and synthetic fixtures instead; a human converts the export.",
        file=sys.stderr,
    )
    sys.exit(2)


def under(path, root):
    path = os.path.realpath(path)
    return path == root or path.startswith(root + os.sep)


def main():
    try:
        event = json.load(sys.stdin)
    except ValueError:
        return
    project = os.environ.get("CLAUDE_PROJECT_DIR") or event.get("cwd") or os.getcwd()
    root = os.path.realpath(os.path.join(project, GUARDED))
    cwd = event.get("cwd") or project
    tool = event.get("tool_name", "")
    args = event.get("tool_input") or {}

    if under(cwd, root):
        block("the session's working directory is inside data/bank/")

    if tool == "Bash":
        command = args.get("command", "")
        if BASH_PATTERN.search(command) or root in command:
            block("the command references data/bank/")
        return

    keys = PATH_KEYS + ("pattern",) if tool == "Glob" else PATH_KEYS
    for key in keys:
        value = args.get(key)
        if not isinstance(value, str) or not value:
            continue
        candidate = value if os.path.isabs(value) else os.path.join(cwd, value)
        if under(candidate, root) or BASH_PATTERN.search(value):
            block(f"{tool} targets data/bank/")

    # MCP and other tools: look for the absolute path anywhere in the input.
    if tool not in ("Read", "Edit", "Write", "MultiEdit", "NotebookEdit", "Grep", "Glob"):
        if root in json.dumps(args):
            block(f"{tool} input references data/bank/")


if __name__ == "__main__":
    main()
