# Knowledge Pack: Curator Guide

Cards in `cards/` are the **only** source the Ask screen may answer from. If no card matches, the app says "Not in our records yet" and logs the question as a `KnowledgeGap` lesson, which becomes your to-do list.

## Workflow

```
edit/add cards/*.json
python3 scripts/build_knowledge_pack.py --check    # validate
./gradlew testDebugUnitTest --tests '*KnowledgeEvalTest*'   # retrieval report on q_dev
python3 scripts/build_knowledge_pack.py            # write into the app's content DB
```

Export lessons from the Researcher screen to find what visitors asked that the pack could not answer (`KNOWLEDGE_GAP`), and wording that should become an alias (`ALIAS_MISS`).

## Card Format

One card per file, named `<id>.json`.

| Field | Rule |
|---|---|
| `id` | `kind.snake_case_name`, e.g. `person.devanampiya_tissa` |
| `kind` | `person` · `place` · `term` · `period` · `event` |
| `status` | `draft` (debug builds only, shown with a DRAFT badge) or `verified` (ships to visitors) |
| `title.en` / `body.en` | Required. Body ≤ 150 words. |
| `title.si/ta`, `body.si/ta` | Optional. Shown **verbatim**; nothing is machine-translated. If a body is missing, the app shows English with a "translation pending" note. |
| `aliases` | Every way a visitor might name this card. Each title you fill in must also appear here. |
| `aliases[].ambiguous` | `true` for names shared by several people/places (e.g. "Tissa", "Mahinda"). An ambiguous alias alone never produces an answer. |
| `links.sites` / `links.inscriptions` | IDs from the content DB. Linked cards rank slightly higher when asked from that site/inscription. |
| `sources` | Required, non-empty. Cite edition and chapter/volume. |
| `confidence_note` | Required in spirit whenever dates or attributions are contested. Shown to visitors. |

## Before Marking a Card `verified`

- [ ] Every factual claim in `body.en` is supported by a listed source
- [ ] Contested dates or identifications are stated in `confidence_note`, not hidden
- [ ] Chronicle-only claims are labelled as such (source hierarchy: inscriptional evidence > modern epigraphic scholarship > chronicles)
- [ ] Sinhala/Tamil text, if present, was written or reviewed by a fluent reader
- [ ] Aliases cover common romanisations (w/v, th/t, doubled letters are folded automatically, but word splits and spellings like "Devanapiya" are not)

## Current Seed Cards

All five seed cards are **drafts written as format examples**. They have not been checked against their sources, and page-level citations are missing. Treat them as scaffolding to verify or rewrite, not as content.

## Evaluation Set

`eval/q_dev.json` is for tuning. Add real visitor questions from `KNOWLEDGE_GAP` lessons here. Keep a separate frozen `q_test.json` for reporting (docs/slm-lessons-architecture.md §7) and never tune against it.
