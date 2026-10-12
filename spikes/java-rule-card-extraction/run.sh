#!/usr/bin/env bash
# PROTOTYPE, ticket #31. Builds once, then runs the pipeline from this folder.
#   ./run.sh --check-gold
#   ./run.sh --dry-run
#   ./run.sh --pages=3,5,16,123,124,134,138,139,146 --label=gold-pages     # Claude plan (claude -p)
#   ./run.sh --label=auto                                                # automatic page selection
#   SPRING_PROFILES_ACTIVE=spring-ai ./run.sh --label=api                # needs ANTHROPIC_API_KEY
#   ./run.sh --score[=runs/<file>.json]
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME_25:-$(/usr/libexec/java_home -v 25 2>/dev/null || echo /opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home)}"
jar=target/java-rule-card-extraction-0.0.1-PROTOTYPE.jar
if [[ ! -f $jar || -n $(find src pom.xml -newer "$jar" -print -quit) ]]; then mvn -q -B package -DskipTests; fi
exec "$JAVA_HOME/bin/java" -jar "$jar" "$@"
