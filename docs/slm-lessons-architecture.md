# Grounded Visitor Assistant & Lessons Loop

**Status:** Proposed design · **Scope:** Visitor Q&A + self-improvement loop · **Owner:** Curator (single, human)

---

## 1. Problem & Positioning

Sellipi deliberately does **not** recognise glyphs; it identifies the inscription and displays peer-reviewed transcriptions. Two gaps remain:

1. **Visitor questions beyond the record** — e.g. *"Who is Devanampiya Tissa?"* The current DB (sites, inscriptions, transcriptions, letterforms) contains no people, places, or concepts.
2. **No memory of failure** — alignment misses, identification fallbacks (geo map / inscription ID), and unanswered questions are never recorded, so nothing improves between releases.

### Non-goals (hard rules)

| Rule | Reason |
|---|---|
| The SLM never reads, guesses, or completes glyphs | Gemma is not trained on Brahmi; it would destroy the accuracy claim |
| The SLM never fills an unattested letterform gap | Gaps are rendered honestly by design |
| Visitor input never directly changes scholarly content | Prevents data poisoning; only curator-verified lessons ship |
| No on-device weight updates are claimed | Not feasible; not defensible in a thesis |

### Claims

| Audience | Claim |
|---|---|
| **Thesis** | A human-verified, closed-loop feedback pipeline measurably increases grounded answer coverage and alignment success across releases without increasing hallucination rate. |
| **Marketing** | "Gets smarter with every visit." (Backed by per-release metrics in §7.) |

---

## 2. System Overview

```
                 ┌────────────────────── Device (offline runtime) ──────────────────────┐
 Visitor ──Q──▶  │ Normalise ─▶ Alias lookup ─▶ FTS5 over Knowledge Cards               │
                 │                                   │                                  │
                 │                  strong hit ◀─────┴─────▶ weak / no hit              │
                 │                      │                         │                     │
                 │           Gemma (EN, grounded,          "Not in our records yet"    │
                 │            must cite card IDs)          + KnowledgeGap lesson       │
                 │                      │                                               │
                 │            Citation post-check ──fail──▶ abstain + lesson           │
                 │                      │                                               │
                 │   EN: generated answer │ SI/TA: curated card text verbatim           │
                 │                                                                      │
                 │  Alignment / ID flows ──▶ Lessons DB (separate Room DB)             │
                 └──────────────────────────────┬───────────────────────────────────────┘
                                                │ opt-in, anonymised, deferred sync
                                                ▼
                        Supabase (insert-only lessons, pack registry, storage)
                                                │
                                                ▼
                  Curator review ─▶ verified ─▶ Knowledge Pack vN+1 ─▶ device download
```

---

## 3. Answer Cascade Contract

| Tier | Component | Input → Output | Pass condition |
|---|---|---|---|
| 0 | Deterministic context | Current inscription/site → pinned cards | Always runs; cards linked to the visible inscription are boosted |
| 1 | Alias normaliser | Raw query → canonical entity IDs | Case/diacritic/whitespace folding; si/ta/en + romanisation variants |
| 2 | FTS5 retrieval | Query + entity IDs → top-k cards with BM25 score | Score ≥ `T_strong` → Tier 3; else Tier 4 |
| 3 | Gemma (grounded rephrase) | Top-k cards → English answer with `[card:ID]` citations | Post-check: ≥1 citation, all cited IDs ∈ retrieved set |
| 4 | Abstain | — | Show "Not in our records yet" + log `KnowledgeGap` |

**Language strategy:** Gemma generates **English only**. Sinhala/Tamil UI shows the retrieved cards' curated `body_si` / `body_ta` verbatim (extractive). No runtime Sinhala/Tamil generation.

**Prompt rules (system prompt, fixed):** answer only from provided cards; cite every sentence; if cards don't answer, output the literal token `ABSTAIN`; never discuss letter shapes not present in cards; max ~120 words.

**Thresholds** `T_strong`, `k` are tuned on the evaluation set (§7), not hand-picked.

---

## 4. Model & Runtime

| Decision | Choice | Rationale |
|---|---|---|
| Model | **Gemma 3 1B IT, int4** (~0.5 GB) | Task is grounded rephrasing, not reasoning; text-only is sufficient |
| Upgrade path | Gemma 3n E2B | Only if eval shows 1B quality insufficient |
| Runtime | MediaPipe LLM Inference / LiteRT-LM | Official on-device path for Gemma on Android |
| Delivery | Play Asset Delivery (on-demand) or pre-visit Wi-Fi download with site pack | APK size limits; runtime stays offline |
| Capability gate | ≥ 6 GB RAM, API 29+, model file present & checksum-valid | Overlay Mode floor (API 26, all devices) untouched |
| Fallback when gated off | Extractive mode: show top card(s) directly, no generation | Q&A still works on 100% of devices |
| Thermal | Skip generation if `PowerManager.currentThermalStatus ≥ SEVERE`; use extractive mode | Outdoor sun, README Phase 5 concern |

> Note: model names/runtimes move fast — verify current Gemma variants and MediaPipe/LiteRT-LM APIs at implementation time.

---

## 5. Data Model

### 5.1 Knowledge Pack (content DB — ships with site pack, replaced wholesale)

Lives in the existing asset-seeded `sellipi.db`, bumped schema version.

```
KNOWLEDGE_CARD
  id PK                  -- e.g. "person.devanampiya_tissa"
  kind                   -- person | place | term | period | event
  title_en / title_si / title_ta
  body_en / body_si / body_ta      -- curated, ≤ 150 words each
  sources                -- citation string(s), mandatory
  confidence_note        -- e.g. "Dates per Mahavamsa; contested"
  pack_version

ENTITY_ALIAS
  alias_norm PK          -- normalised surface form
  card_id FK
  lang                   -- en | si | ta | roman
  origin                 -- curated | promoted_from_lesson

CARD_LINK
  card_id FK, inscription_id FK | site_id FK   -- powers Tier 0 boosting

KNOWLEDGE_CARD_FTS       -- FTS5 virtual table over titles + bodies + aliases
```

### 5.2 Lessons DB (separate Room DB — `sellipi_lessons.db`)

**Must be a separate database.** `SellipiDatabase` uses `createFromAsset()` + `fallbackToDestructiveMigration()`, so any content update would wipe lessons stored there.

```
LESSON
  id PK (UUID)
  type                   -- see 5.3
  inscription_id?        -- nullable
  pack_version           -- content version active when observed
  app_version
  payload_json           -- type-specific, schema-versioned
  trust                  -- observed | proposed | verified
  created_at
  synced_at?             -- null until uploaded

LOCAL_ADAPTATION         -- per-device learned state
  key PK                 -- e.g. "default_mode:INS_012", "alias:dewanam piyathissa"
  value_json
  evidence_count
  updated_at
```

### 5.3 Lesson Types

| Type | Captured when | Payload (key fields) | Local effect | Fleet effect |
|---|---|---|---|---|
| `KnowledgeGap` | Tier 4 abstain | query_norm, lang, top_score | none | Curator writes/extends a card |
| `AliasMiss` | Query misses, rephrase within 60 s hits | miss_query, hit_card_id | Add local alias | Promote to `ENTITY_ALIAS` |
| `AnswerFeedback` | 👍/👎 on answer | query, cited_ids, vote | Demote card for that query | Curator review queue |
| `AlignmentOutcome` | AR/Overlay session ends | mode, success, time_to_align_ms, lux, hour | Pick default mode per inscription | Re-score `arcoreTargetScore` |
| `AlignmentCorrection` | User drags quad / bbox | inscription_id, corner deltas (normalised) | Seed next session's initial quad | Refine stored bboxes / reference photo |
| `IdentificationRecovery` | User falls back to geo map / ID picker | attempted_method, chosen_inscription_id | none | Flag weak targets, geofence issues |

### 5.4 Trust Tiers

```
observed ──(automatic telemetry)──▶ proposed ──(curator approves)──▶ verified ──▶ ships in pack vN+1
```

Only `verified` lessons alter content seen by other visitors. Local adaptations (§5.2) affect only the device that observed them and never alter scholarly text.

---

## 6. Sync & Curation (Supabase)

### 6.1 Flow

1. Opt-in consent on first launch (default **off**); plain-language privacy note.
2. `WorkManager` job, constraint `NetworkType.UNMETERED`, uploads unsynced lessons in batches.
3. Curator reviews in Supabase Studio (Phase 1) → marks `verified` → exports approved changes.
4. `scripts/build_database.py` extended to merge verified cards/aliases/bbox refinements → new pack.
5. Pack uploaded to Supabase Storage; `pack_registry` row published; app checks on Wi-Fi.

### 6.2 Supabase Tables

```
lessons_inbox   (insert-only for anon role; no select/update/delete)
pack_registry   (read-only for anon: version, url, sha256, min_app_version)
curation_log    (curator-only: lesson_id, decision, pack_version, note)
```

### 6.3 Risks & Mitigations

| Risk | Mitigation |
|---|---|
| Anon key is extractable from APK | RLS: anon = `INSERT` on `lessons_inbox` only; payload size check constraint; per-device rate limit via edge function if abused |
| Free tier pauses projects after inactivity | App treats sync as best-effort; lessons queue locally indefinitely; a scheduled keep-alive or manual unpause before releases |
| Free tier storage cap | Lessons are small JSON; packs versioned, prune old ones |
| PII in free-text queries | Store normalised query only; strip digits/emails client-side; no device IDs, no GPS finer than inscription ID |
| Single curator bottleneck | Lessons clustered/deduplicated before review (group `KnowledgeGap` by normalised query); review sorted by frequency |

---

## 7. Evaluation Protocol (Thesis)

### 7.1 Datasets

| Set | Composition | Use |
|---|---|---|
| **Q-dev** | ~100 questions, en/si/ta | Tune `T_strong`, `k`, prompt |
| **Q-test** (held out, frozen) | ~200 questions: ~60% answerable from pack v1, ~40% not | Per-release scoring |
| **Field log** | Real anonymised visitor queries from sync | Source of new gaps; never used to score (avoid leakage) |

### 7.2 Metrics (reported per pack version)

| Metric | Definition | Target direction |
|---|---|---|
| Grounded answer rate | Answered with valid citations / answerable questions | ↑ |
| Abstention precision | Correct abstains / all abstains | ↑ |
| Hallucination rate | Answers with unsupported claims or invalid citations / all answers (human-judged) | ↓ or flat |
| Alias recall | Correct card retrieved for variant spellings / variant queries | ↑ |
| Alignment success rate | Successful alignments / sessions (from `AlignmentOutcome`) | ↑ |
| Median time-to-align | From `AlignmentOutcome` | ↓ |
| Identification fallback rate | `IdentificationRecovery` / sessions | ↓ |

### 7.3 Claim Validity

- The improvement claim is the **delta between pack vN and vN+1 on the frozen Q-test**, attributable to verified lessons (logged in `curation_log`).
- Ablation: vN+1 with vs. without lesson-derived changes isolates the loop's contribution from ad-hoc curator edits.
- Framing: *human-in-the-loop* self-improvement. An examiner will ask; say it upfront.

---

## 8. Phased Rollout

| Phase | Deliverable | Exit criteria |
|---|---|---|
| **P0 — Instrument** | Lessons DB, `AlignmentOutcome`, `AlignmentCorrection`, `IdentificationRecovery` capture; local export | Lessons recorded in a field test; no UI regression |
| **P1 — Knowledge pack** | `KNOWLEDGE_CARD`, `ENTITY_ALIAS`, `CARD_LINK`, FTS5; ~50 seed cards; extractive Q&A (no SLM) | Q&A works on all devices; Q-dev built |
| **P2 — Grounded SLM** | Gemma 3 1B on-demand delivery, capability gate, cascade, citation post-check | Hallucination rate on Q-dev within target; thermal fallback verified |
| **P3 — Sync & curation** | Supabase tables + RLS, WorkManager sync, consent UI, pack registry, `build_database.py` merge | End-to-end: lesson → verified → pack vN+1 on device |
| **P4 — Local adaptation** | Default-mode selection, seeded quads, local aliases | Measurable time-to-align improvement on repeat sessions |
| **P5 — Evaluation** | Frozen Q-test, per-release reports, ablation | Thesis chapter data |

**Why this order:** P0 and P1 deliver value and thesis data without the SLM; the SLM (P2) is the riskiest and least essential component, so it lands on top of a working extractive baseline.

### 8.1 Phase 0 — Implementation Notes

| Component | Location |
|---|---|
| Payloads, codec, session tracker (pure Kotlin, unit tested) | `domain/lessons/` |
| Lessons Room DB (`sellipi_lessons.db`), repository, export sink | `data/lessons/` |
| Overlay session capture (taps, quad drags, viewport) | `CameraOverlayViewModel`, `CameraOverlayScreen` |
| AR session capture (tracking acquired, fallback to Overlay) | `ArViewModel`, `SellipiNavHost` |
| Manual identification capture | `HomeViewModel`, `SellipiNavHost` |
| Export button + lesson count | `ResearcherCaptureScreen` → `Android/data/org.sellipi.companion/files/exports/` |

**Signal definitions and known limits**

- `AlignmentOutcome.success` = alignment *confirmed* (≥1 glyph tap hit, or ARCore acquired the surface). A visitor who aligns but never taps reads as unconfirmed.
- `glyphTapMisses` counts every tap that hits no bounding box, including taps used to dismiss the glyph sheet. Treat it as a noisy proxy for "letters the visitor could not reach".
- AR success is currently always `false`: `ArCoreSessionManager.onArFrameUpdated` is not yet driven by a frame loop. The lessons will record that honestly until AR tracking is wired.
- Every Home pick is logged as `IdentificationRecovery`, because no automatic identification exists yet. Once AR/geofence auto-ID ships, add an `attemptedMethod` field (payload schema v2).
- `rankInList` is position in a proximity-ordered list (nearest site first, chronological within a site) when `locationAvailable = true`, and in the chronological list otherwise. Always split analyses on `locationAvailable`.
- Lessons are stored with `trust = OBSERVED`, `synced_at = null`; sync arrives in P3.
- Room exports the lessons schema to `app/schemas/`. Commit it after the first build; it is the baseline for future migration tests.

---

## 9. Open Decisions

| # | Decision | Default if unresolved |
|---|---|---|
| D1 | Field study at a pilot site to collect real visitor questions? | Q-test is expert-written + synthetic variants; weaker external validity |
| D2 | Source hierarchy for cards (Paranavitana, *Epigraphia Zeylanica*, Department of Archaeology, *Mahavamsa*) | Inscriptional evidence ranks above chronicle; chronicle-only claims carry `confidence_note` |
| D3 | Seed card list (~50) | Entities named in pilot-site transcriptions first |
| D4 | `T_strong` / `k` values | Tuned on Q-dev in P2 |
