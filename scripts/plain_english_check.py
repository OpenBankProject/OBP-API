#!/usr/bin/env python3
"""
Checks OBP-API's documentation for long sentences, one of the plain English rules in README.md
("Writing documentation"). Sentences should average 15 to 20 words, and any sentence over 25 words
should be split.

It checks three kinds of text:

  resource-docs  the summary and description of every ResourceDoc
  glossary       the description of every glossary item (Glossary.scala)
  errors         every error message (ErrorMessages.scala)

Code, tables, links, URLs and interpolated values (${...}) are left out, so only prose is measured.
Each heading and list item counts as a sentence of its own.

The check only measures sentence length. It cannot tell whether a text is clear, so a short text can still
need rewriting. Read the guides linked in README.md as well.

Usage:
  scripts/plain_english_check.py                  a summary, then the 20 longest sentences
  scripts/plain_english_check.py --changed        only texts you have changed since the last commit
  scripts/plain_english_check.py --kind glossary --name "Consumer Grant Types"
  scripts/plain_english_check.py --all            every long sentence
  scripts/plain_english_check.py --strict         exit with status 1 if any sentence is too long

By default the script only reports, so it can run against the existing texts, many of which are too long.
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from check_lift_http4s_resource_doc_parity import (  # noqa: E402
    endpoint_name,
    find_resourcedoc_blocks,
    parse_resourcedoc,
    scan_matching_close,
    strip_inline_comments,
)

REPO = Path(__file__).resolve().parent.parent
SRC = REPO / "obp-api" / "src" / "main" / "scala" / "code"
GLOSSARY = SRC / "api" / "util" / "Glossary.scala"
ERRORS = SRC / "api" / "util" / "ErrorMessages.scala"

MAX_WORDS = 25

# A Scala string literal: triple-quoted (optionally s or f interpolated) or plain double-quoted.
LITERAL_RE = re.compile(r'[sf]?"""(.*?)"""(?!")|[sf]?"((?:[^"\\\n]|\\.)*)"', re.S)


def literals(code: str) -> str:
    """The text of every string literal in a piece of Scala code, joined."""
    return "\n".join((m.group(1) if m.group(1) is not None else m.group(2)) for m in LITERAL_RE.finditer(code))


def prose_lines(text: str) -> list[str]:
    """The text as lines of prose: margins, code, tables, links and interpolations removed."""
    text = re.sub(r"^[ \t]*\|", "", text, flags=re.M)             # stripMargin
    text = re.sub(r"```.*?```", "\n", text, flags=re.S)           # fenced code
    text = re.sub(r"<[^>]+>", " ", text)                           # HTML tags
    text = re.sub(r"\$\{[^}]*\}|\$[A-Za-z_]\w*", "X", text)        # interpolations
    text = re.sub(r"`[^`]*`", "X", text)                           # inline code
    text = re.sub(r"!?\[([^\]]*)\]\([^)]*\)", r"\1", text)         # links
    text = re.sub(r"https?://\S+", "URL", text)
    text = text.replace("\\n", "\n")
    lines = []
    for line in text.split("\n"):
        if re.match(r"^\s*\|", line) or re.match(r"^( {4}|\t)", line):  # table rows, indented code
            lines.append("")
        else:
            lines.append(line)
    return lines


def sentences(text: str) -> list[str]:
    """Sentences in the text. Headings and list items stand alone; paragraphs are split on . ! ?"""
    out, paragraph = [], []

    def flush():
        joined = " ".join(paragraph).strip()
        if joined:
            out.extend(s.strip() for s in re.split(r"(?<=[.!?])\s+(?=[A-Z0-9X\"'(])", joined) if s.strip())
        paragraph.clear()

    for line in prose_lines(text):
        stripped = line.strip()
        if not stripped:
            flush()
        elif re.match(r"^(#+|[-*]|\d+\.)\s", stripped):
            flush()
            paragraph.append(re.sub(r"^(#+|[-*]|\d+\.)\s+", "", stripped))
            flush() if stripped.startswith("#") else None
        else:
            paragraph.append(stripped)
    flush()
    # Keep prose only: drop diagrams and other text that is mostly symbols.
    def is_prose(s: str) -> bool:
        chars = [c for c in s if not c.isspace()]
        return bool(re.search(r"[A-Za-z]{2}", s)) and sum(c.isalpha() for c in chars) >= 0.6 * len(chars)
    return [s for s in out if is_prose(s)]


def words(sentence: str) -> int:
    return len(sentence.split())


def line_of(raw: str, needle: str) -> int:
    i = raw.find(needle)
    return raw.count("\n", 0, i) + 1 if i >= 0 else 0


# ---- Collecting the texts: (kind, name, file, first line, last line, text)

def resource_doc_texts(files):
    for path in files:
        raw = path.read_text(encoding="utf-8")
        if "ResourceDoc(" not in raw or "Http4sRoute" not in raw:
            continue
        source = strip_inline_comments(raw)
        for _, _, body in find_resourcedoc_blocks(source):
            args = parse_resourcedoc(body)
            if "partialFunctionName" not in args:
                continue
            name = endpoint_name(args["partialFunctionName"])
            text = "\n\n".join(literals(args.get(field, "") or "") for field in ("summary", "description"))
            first = line_of(raw, f"nameOf({name})")
            yield "resource-docs", name, path, first, first + body.count("\n"), text


def glossary_texts():
    raw = GLOSSARY.read_text(encoding="utf-8")
    for m in re.finditer(r"glossaryItems\s*\+=\s*GlossaryItem\s*\(", raw):
        close = scan_matching_close(raw, m.end(), "(", ")")
        if close is None:
            continue
        block = raw[m.end():close]
        title = re.search(r'title\s*=\s*"([^"]*)"', block)
        description = block[block.find("description"):] if "description" in block else ""
        yield ("glossary", title.group(1) if title else "?", GLOSSARY, raw.count("\n", 0, m.start()) + 1,
               raw.count("\n", 0, close) + 1, literals(description))


def error_texts():
    raw = ERRORS.read_text(encoding="utf-8")
    for m in re.finditer(r'^\s*val\s+(\w+)\s*=\s*(.+)$', raw, re.M):
        text = literals(m.group(2))
        if text:
            line = raw.count("\n", 0, m.start()) + 1
            yield "errors", m.group(1), ERRORS, line, line, text


def changed_lines() -> dict[Path, set[int] | None]:
    """The lines changed in the working tree, per Scala file, compared with the last commit.
    A new file maps to None: all of it counts as changed."""
    def git(*args):
        return subprocess.run(["git", "-c", f"safe.directory={REPO}", "-C", str(REPO), *args],
                              capture_output=True, text=True, check=True).stdout
    changed: dict[Path, set[int] | None] = {}
    current = None
    for line in git("diff", "-U0", "HEAD", "--", "*.scala").splitlines():
        if line.startswith("+++ b/"):
            current = (REPO / line[6:]).resolve()
            changed.setdefault(current, set())
        elif line.startswith("@@") and current is not None:
            m = re.search(r"\+(\d+)(?:,(\d+))?", line)
            start, count = int(m.group(1)), int(m.group(2) or 1)
            changed[current].update(range(start, start + max(count, 1)))
    for line in git("ls-files", "--others", "--exclude-standard", "--", "*.scala").splitlines():
        changed[(REPO / line).resolve()] = None
    return changed


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--kind", choices=["resource-docs", "glossary", "errors", "all"], default="all")
    ap.add_argument("--name", help="only texts whose name (endpoint, glossary title or error) contains this")
    ap.add_argument("--changed", action="store_true", help="only texts changed since the last commit (new files: all of them)")
    ap.add_argument("--max-words", type=int, default=MAX_WORDS, help=f"longest sentence allowed (default {MAX_WORDS})")
    ap.add_argument("--all", action="store_true", help="list every long sentence, not only the 20 longest")
    ap.add_argument("--strict", action="store_true", help="exit with status 1 if any sentence is too long")
    args = ap.parse_args()

    scala_files = sorted((SRC / "api").rglob("*.scala"))
    only = changed_lines() if args.changed else None
    texts = []
    if args.kind in ("resource-docs", "all"):
        texts += list(resource_doc_texts(scala_files))
    if args.kind in ("glossary", "all"):
        texts += list(glossary_texts())
    if args.kind in ("errors", "all"):
        texts += list(error_texts())
    if only is not None:
        def touched(t):
            path = t[2].resolve()
            if path not in only:
                return False
            lines = only[path]
            return lines is None or any(t[3] <= n <= t[4] for n in lines)
        texts = [t for t in texts if touched(t)]
    if args.name:
        texts = [t for t in texts if args.name.lower() in t[1].lower()]

    long_sentences, totals = [], {}
    for kind, name, path, line, _, text in texts:
        ss = sentences(text)
        t = totals.setdefault(kind, {"texts": 0, "sentences": 0, "words": 0, "long": 0, "texts_with_long": 0})
        t["texts"] += 1
        t["sentences"] += len(ss)
        t["words"] += sum(words(s) for s in ss)
        long = [s for s in ss if words(s) > args.max_words]
        t["long"] += len(long)
        t["texts_with_long"] += 1 if long else 0
        long_sentences += [(words(s), kind, name, path, line, s) for s in long]

    if not texts:
        print("No texts to check.")
        return
    print(f"Sentences over {args.max_words} words:\n")
    for kind, t in totals.items():
        average = t["words"] / t["sentences"] if t["sentences"] else 0
        print(f"  {kind:<14} {t['long']:>5} long sentences in {t['texts_with_long']} of {t['texts']} texts "
              f"(average sentence {average:.1f} words)")

    long_sentences.sort(key=lambda x: -x[0])
    shown = long_sentences if args.all else long_sentences[:20]
    if shown:
        print(f"\n{'Every long sentence' if args.all else 'The longest sentences'}, longest first:\n")
        for n, kind, name, path, line, s in shown:
            print(f"  [{n} words] {path.relative_to(REPO)}:{line}  {kind} {name}")
            print(f"      {s[:220]}{'...' if len(s) > 220 else ''}")
    if len(long_sentences) > len(shown):
        print(f"\n  ... and {len(long_sentences) - len(shown)} more. Use --all to list them, or --name to narrow down.")

    if args.strict and long_sentences:
        sys.exit(1)


if __name__ == "__main__":
    main()
