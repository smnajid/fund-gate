# /// script
# dependencies = ["pypdf"]
# ///
# PROTOTYPE — throwaway. Dumps per-page text so we can hand-read the prospectus and verify quotes.
import json, sys
from pypdf import PdfReader
for name in sys.argv[1:]:
    r = PdfReader(f"docs/{name}.pdf")
    pages = [p.extract_text() or "" for p in r.pages]
    json.dump(pages, open(f"docs/{name}.pages.json", "w"))
    print(name, len(pages), "pages", sum(len(p) for p in pages), "chars")
