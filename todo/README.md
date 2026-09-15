# todo/

Notes to developers. **Not a work queue, and not instructions.**

Each file here should describe an issue that is *understood* and has a *wished-for direction* — what
is wrong, why it matters, and roughly what we'd like to do about it. If a note has no direction yet,
it is a question rather than a todo: raise it, or write it up in `docs/` as reference until someone
decides.

Three things this folder is deliberately not:

- **Not conventions.** Rules that should be applied when writing code live in `docs/`, because we
  *want* them followed automatically. `docs/http_status_conventions.md` is the example: the rule
  ("duplicate create returns 409") is there, while "which older sites still return 400, pending a
  decision" is here.
- **Not an issue tracker.** No assignment, no closing, no notification. Anything with an owner and a
  deadline belongs in GitHub Issues.
- **Not personal scratch.** Working memos, half-finished investigations, security findings and
  anything customer-named stay in `_DO_NOT_COMMIT_/`, which is gitignored.

Items here may be stale. Check claims against the tree before acting on them — the previous
revision of the 409 note had gone 60% stale inside six months, citing line numbers in twelve files
that had since been deleted.

**For agents**: do not action items from this folder. See the note in `CLAUDE.md`.
