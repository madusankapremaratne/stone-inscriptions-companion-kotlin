# Sellipi (සෙල්ලිපි) — AR Site Companion

[![Platform](https://img.shields.io/badge/Platform-Android%20(API%2026%2B)-3DDC84.svg?style=flat&logo=android)](https://www.android.com/)
[![Language](https://img.shields.io/badge/Language-Kotlin%202.0-7F52FF.svg?style=flat&logo=kotlin)](https://kotlinlang.org/)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg?style=flat&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![AI / SLM](https://img.shields.io/badge/Edge%20AI-On--Device%20SLM%20%2B%20Multi--Agent-FF6F00.svg?style=flat&logo=google)](https://github.com/madusankapremaratne/stone-inscriptions-companion-kotlin)
[![AR](https://img.shields.io/badge/AR-ARCore%20%2F%20SceneView-4285F4.svg?style=flat&logo=google)](https://developers.google.com/ar)
[![Database](https://img.shields.io/badge/Database-Room%20(SQLite)-003B57.svg?style=flat&logo=sqlite)](https://developer.android.com/training/data-storage/room)
[![License](https://img.shields.io/badge/License-Research%20%2F%20Academic-lightgrey.svg)](#licensing--ethics)

> **Offline-first Android application, on-device Small Language Model (SLM) multi-agent workflow, and field research instrument bridging ancient Sri Lankan epigraphy, Augmented Reality, and diachronic palaeographic letterform evolution.**

---

## 🏛️ Overview

**Sellipi** is an offline Android companion designed for visitors and researchers standing before ancient Sri Lankan stone inscriptions (dating from the 3rd century BCE Early Brahmi to the 10th century CE Classical Sinhala, such as those in Anuradhapura, Mihintale, and Polonnaruwa). 

The application blends deterministic epigraphical registration with an **On-Device Small Language Model (SLM) Multi-Agent Workflow** to assist when stone surfaces are weathered, eroded, or unindexed:

1. **Identification**: Pre-registered inscriptions are recognized via ARCore Augmented Images (the stone surface as its own target), fixed-offset markers, or geofenced selection.
2. **Alignment & Rendering**: Scholarly peer-reviewed transcriptions (*Epigraphia Zeylanica*, *Inscriptions of Ceylon*) and vector glyph assets are projected directly onto the physical stone surface via CameraX or ARCore.
3. **Palaeographic Evolution**: Visitors can tap any individual letter to observe its historical transformation across 18 historical periods, animated via dynamic 2D vector path morphing with honest representation of archaeological record gaps.
4. **Agentic SLM Workflow (Identify, Critic, & Learning Agents)**: When letters are eroded or ambiguous, a collaborative multi-agent pipeline reasons over stroke morphology, validates historical context, and persists newly confirmed glyph variations directly to the local Room database for continuous on-device learning.
5. **Researcher Field Capture Pipeline**: A credential-gated field capture tool records calibrated, multi-angle imagery with ARCore 6-DoF camera pose, depth-derived millimetre-per-pixel scale, and GPS metadata.

---

## 🤖 On-Device SLM & Multi-Agent Epigraphical Workflow

When stone carvings are partially degraded by centuries of weathering, optical pattern matching alone is insufficient. Sellipi introduces an **Edge SLM Multi-Agent System** that simulates a team of expert epigraphers collaborating in real time on device:

```mermaid
sequenceDiagram
    autonumber
    actor User as Field Researcher / Visitor
    participant Camera as CameraOverlayScreen
    participant Orch as EpigraphicAgentOrchestrator
    participant SLM as LocalEpigraphicSlmEngine
    participant IdAgent as EpigraphicIdentifyAgent
    participant Critic as EpigraphicCriticAgent
    participant LearnAgent as EpigraphicLearningAgent
    participant DB as SellipiDatabase (Room)

    User->>Camera: Tap Unidentified / Eroded Glyph ("Agentic Scan")
    Camera->>Orch: runAgenticIdentification(strokeDescription, era, lineContext)
    Note over Orch,IdAgent: Stage 1: Feature Extraction & SLM CoT
    Orch->>IdAgent: identifyGlyph(strokes, era, site)
    IdAgent->>SLM: generateEpigraphicReasoning(IDENTIFY_PROMPT)
    SLM-->>IdAgent: JSON Top Candidate + Alternatives + Morphological CoT
    IdAgent-->>Orch: Ranked Candidates + Morphological Justifications

    Note over Orch,Critic: Stage 2: Historical & Grammatical Critique
    Orch->>Critic: validateCandidate(candidate, lineContext, era)
    Critic->>SLM: generateEpigraphicReasoning(CRITIC_PROMPT)
    SLM-->>Critic: Contextual Fit Validation + Confidence Calibration
    Critic-->>Orch: Validated Consensus Output

    Note over Orch,User: Stage 3: Human Verification & Consensus UI
    Orch-->>Camera: Emit CONSENSUS_READY (Interactive Bottom Sheet)
    User->>Camera: Tap "Approve & Teach Learning Agent"

    Note over Orch,DB: Stage 4: Local Database Memory Sync
    Camera->>Orch: approveAndLearnCandidate(candidate, inscriptionId)
    Orch->>LearnAgent: learnAndPersistGlyph(candidate, inscriptionId)
    LearnAgent->>DB: INSERT into learned_glyphs & letter_forms (is_reconstructed=1)
    DB-->>LearnAgent: OK
    LearnAgent-->>Orch: Few-Shot SLM Cache Updated
    Orch-->>Camera: Emit isLearnedAndSaved = true
```

### Specialized Agents in the System:

| Agent | Responsibility | Core Reasoning Method |
|---|---|---|
| **🔍 Identify Agent** (`EpigraphicIdentifyAgent`) | Analyzes visual stroke topology (vertical spines, loops, crossbars, aspect ratios) and ranks candidate letters. | On-Device SLM Chain-of-Thought querying the 38-letter $\times$ 18-period Aksharamalawa matrix. |
| **⚖️ Critic Agent** (`EpigraphicCriticAgent`) | Evaluates candidate compatibility against historical era, regnal dating, and surrounding syntactic formula (e.g. `දෙවනපිය මහරඣහ...`). | Historical grammar & phonological plausibility scoring; adjusts confidence. |
| **💾 Learning Agent** (`EpigraphicLearningAgent`) | Commits verified identifications into the local Room database (`learned_glyphs` & `letter_forms`). | Persists vector paths, confidence, and primes the local on-device few-shot SLM cache for future sessions. |
| **🎼 Orchestrator** (`EpigraphicAgentOrchestrator`) | Coordinates the pipeline state machine and streams live thought steps to Jetpack Compose UI. | StateFlow reactive dispatching across UI bottom sheets and camera overlays. |

---

## ⚡ Core Philosophy & Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│                          Physical Inscription                          │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                  ┌─────────────────┴─────────────────┐
                  ▼                                   ▼
        [ARCore Tracked Surface]            [Manual 4-Point Homography]
        (Augmented Images / Plaque)         (CameraX Universal Fallback)
                  │                                   │
                  └─────────────────┬─────────────────┘
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                           Sellipi App Core                             │
│                                                                        │
│   ┌─────────────────────┐   ┌──────────────────────────────────────┐   │
│   │ Offline Room DB     │   │ Vector Glyph Engine                  │   │
│   │ • Sites & Inscriptions│ │ • 38 Letters × 18 Attested Periods   │   │
│   │ • Transcriptions    │   │ • Dynamic SVG Path Interpolation     │   │
│   │ • Learned Glyphs    │   │ • Honest Gap Representation          │   │
│   └─────────────────────┘   └──────────────────────────────────────┘   │
│                                   │                                    │
│   ┌────────────────────────────────────────────────────────────┐       │
│   │ On-Device SLM Multi-Agent System                           │       │
│   │ • Identify Agent (Stroke Topology & Morphological CoT)     │       │
│   │ • Critic Agent (Syntactic & Historical Era Validator)      │       │
│   │ • Learning Agent (Local Database & Few-Shot Memory Sync)   │       │
│   └────────────────────────────────────────────────────────────┘       │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                  ┌─────────────────┴─────────────────┐
                  ▼                                   ▼
      [Visitor Exploration Mode]           [Researcher Capture Mode]
      • Interactive Inscription HUD        • Multi-angle Coverage Guidance
      • Diachronic Letter Evolution        • 6-DoF Pose & mm/px Scale Matrix
      • Agentic Scan & Verification        • Local Cryptographic Export
```

### 1. Dual Viewing Pipeline

| Capability | **Overlay Mode (Universal Floor)** | **AR Mode (Enhanced Experience)** |
|---|---|---|
| **Underlying Tech** | Android CameraX + Custom Canvas | ARCore Augmented Images + SceneView / Filament |
| **Device Support** | 100% of Android devices (API 26+) | ARCore-compatible devices |
| **Alignment Method** | Freeze-frame + 4-point draggable quad warp | Real-time 6-DoF feature tracking & surface anchoring |
| **Target Audience** | Low/mid-tier phones, high-glare conditions | Modern ARCore hardware in shaded/structured conditions |

### 2. Absolute Offline-First Design
Archaeological reserves (Mihintale, Ritigala, Polonnaruwa) often lack cellular data. Sellipi functions completely with airplane mode enabled:
- Pre-seeded Room SQLite database bundled in APK / downloadable site pack.
- Zero network dependencies during visitor runtime.
- On-device SLM inference and vector morphing executed entirely local to the CPU/NPU.

---

## 📐 Data Model Schema

The local relational schema enforces historical accuracy, epigraphical provenance, and continuous learning:

```mermaid
erDiagram
    SITE ||--o{ INSCRIPTION : contains
    INSCRIPTION ||--o{ TRANSCRIPTION_LINE : contains
    TRANSCRIPTION_LINE ||--o{ GLYPH_OCCURRENCE : has
    LETTER ||--o{ GLYPH_OCCURRENCE : references
    LETTER ||--o{ LETTER_FORM : manifests
    PERIOD ||--o{ LETTER_FORM : defines
    LETTER ||--o{ LEARNED_GLYPH : registers
    PERIOD ||--o{ LEARNED_GLYPH : categorizes
    INSCRIPTION ||--o{ CAPTURE_SESSION : produces

    SITE {
        string id PK
        string name_si
        string name_ta
        string name_en
        double latitude
        double longitude
        float geofence_radius_m
        string permit_reference
    }

    INSCRIPTION {
        string id PK
        string site_id FK
        string name_si
        string name_ta
        string name_en
        int date_range_start
        int date_range_end
        string dating_basis
        string primary_period_id
        string reference_photo
        int arcore_target_score
        string alignment_strategy
        string source_citation
    }

    TRANSCRIPTION_LINE {
        string id PK
        string inscription_id FK
        int line_number
        string text_original
        string text_modern_sinhala
        string translation_si
        string translation_ta
        string translation_en
    }

    GLYPH_OCCURRENCE {
        string id PK
        string transcription_line_id FK
        int position
        string letter_id FK
        float bbox_x
        float bbox_y
        float bbox_w
        float bbox_h
    }

    LETTER {
        string id PK
        int row_index
        string modern_sinhala_codepoint
        string romanisation
        string letter_name
        string glyph_image_path
    }

    PERIOD {
        string id PK
        int period_number
        string label_si
        string label_ta
        string label_en
        int year_start
        int year_end
        string chart_column_refs
    }

    LETTER_FORM {
        string id PK
        string letter_id FK
        string period_id FK
        int grid_row
        int grid_col
        string vector_path
        string image_asset_path
        int is_attested
        int is_reconstructed
        string source_inscription_ref
        string provenance
    }

    LEARNED_GLYPH {
        string id PK
        string letter_id FK
        string period_id FK
        string inscription_id
        string image_crop_path
        string vector_path
        float confidence
        string researcher_notes
        long timestamp
    }

    CAPTURE_SESSION {
        string id PK
        string inscription_id FK
        string device_model
        long timestamp
        double gps_lat
        double gps_lon
        float gps_accuracy
        string pose_matrix
        float scale_mm_per_px
        string image_paths
        string permit_reference
    }
```

---

## 🛠️ Technology Stack

- **Core & Architecture**: Kotlin 2.0+, Clean Architecture + MVI / MVVM, Android Jetpack.
- **UI Framework**: Jetpack Compose, Material Design 3 (High-Contrast Theme for outdoor sunlight readability).
- **On-Device SLM & Agents**:
  - **Edge SLM Interface** (Quantized local inference with structured JSON output).
  - **Multi-Agent Orchestration** (`IdentifyAgent`, `CriticAgent`, `LearningAgent`).
- **Camera & Vision**:
  - **CameraX** (Preview, ImageCapture, Matrix Analysis for Overlay Mode).
  - **ARCore SDK** (Augmented Images, Trackable Pose, Depth API).
- **3D & Vector Graphics**:
  - **SceneView / Filament** (Lightweight Android AR anchoring).
  - **Compose Graphics / Android GraphicVector** (Bézier path morphing & interpolation).
- **Local Persistence**: Room Database, SQLite, EncryptedSharedPreferences.
- **Location**: Google Play Services Location (FusedLocationProviderClient, Geofencing).
- **Asynchronous Flow**: Kotlin Coroutines, StateFlow, SharedFlow.

---

## 🗂️ Project Structure

```
stone-inscriptions-companion-kotlin/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── assets/                 # Pre-seeded SQLite DB & Site Packs
│   │   │   │   ├── databases/sellipi.db
│   │   │   │   └── glyph_images/       # 395 extracted transparent PNG glyphs
│   │   │   ├── java/org/sellipi/companion/
│   │   │   │   ├── agent/              # Multi-Agent Workflow Engine
│   │   │   │   │   ├── model/          # AgentThoughtStep, GlyphCandidate, AgentStage
│   │   │   │   │   ├── EpigraphicIdentifyAgent.kt
│   │   │   │   │   ├── EpigraphicCriticAgent.kt
│   │   │   │   │   ├── EpigraphicLearningAgent.kt
│   │   │   │   │   └── EpigraphicAgentOrchestrator.kt
│   │   │   │   ├── core/               # Design system, theme, base classes
│   │   │   │   │   ├── common/
│   │   │   │   │   └── theme/          # High-contrast outdoor palettes
│   │   │   │   ├── data/               # Room entities, DAOs, repositories
│   │   │   │   │   ├── local/
│   │   │   │   │   └── repository/
│   │   │   │   ├── domain/             # Use cases & business logic
│   │   │   │   │   ├── model/
│   │   │   │   │   └── usecase/
│   │   │   │   ├── engine/             # Specialized engines
│   │   │   │   │   ├── ar/             # ARCore Augmented Images session manager
│   │   │   │   │   ├── homography/     # 4-point perspective warp calculations
│   │   │   │   │   ├── morph/          # SVG vector path morphing & interpolation
│   │   │   │   │   ├── sensor/         # FusedLocation & Depth scale calculator
│   │   │   │   │   └── slm/            # On-Device Small Language Model & Prompts
│   │   │   │   ├── ui/                 # Jetpack Compose Screens & ViewModels
│   │   │   │   │   ├── agent/          # Agentic Workflow Bottom Sheet & CoT HUD
│   │   │   │   │   ├── home/           # Site & Inscription selector / Geofence
│   │   │   │   │   ├── overlay/        # CameraX 4-point alignment viewer
│   │   │   │   │   ├── ar/             # ARCore 3D spatial overlay screen
│   │   │   │   │   ├── evolution/      # Diachronic letter evolution viewer
│   │   │   │   │   └── researcher/     # Calibrated field capture & export
│   │   │   │   ├── MainActivity.kt
│   │   │   │   └── SellipiApplication.kt
│   │   │   └── res/
│   │   └── test/                       # Unit Tests (SLM, Agents, Homography, Morphing)
│   └── build.gradle.kts
├── docs/                               # Epigraphical documentation & spreadsheets
├── gradle/
├── scripts/
│   └── build_database.py               # Automated SQLite database builder
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## 🔬 Researcher Field Capture Mode

Site photography permits are scarce and strictly regulated. The application embeds a gated **Researcher Capture Mode**:
- **Multi-Angle Coverage Overlay**: Real-time visual feedback indicating which regions of the inscription face have been photographed across 8 azimuth/elevation sectors.
- **Calibrated Metadata**: Each capture records:
  - 6-DoF ARCore camera pose matrix.
  - Scale factor in **mm/pixel** derived from optical sensors.
  - GPS coordinates + accuracy radius.
  - Device sensor profiles, timestamp, and Department of Archaeology permit ID.
- **Strict Data Sovereignty**: All imagery and telemetry stay strictly on-device in local sandbox storage, exportable as an encrypted package.

---

## 🗺️ Implementation Roadmap

```
[Phase 0: Clearance & Foundation] ➔ [Phase 1: Data Model & Glyphs] ➔ [Phase 2: Overlay Mode (MVP)]
                                                                               │
[Phase 5: Field Hardening] 🠤 [Phase 4: Researcher Capture] 🠤 [Phase 3: AR & Agentic SLM Flow]
```

- **Phase 0 — Clearance & Foundation**: Pilot site selection (Mihintale, Anuradhapura), reference photo capture & `arcoreimg` quality scoring.
- **Phase 1 — Data Model & Vector Library**: Room SQLite database schema, pre-population scripts, 395 extracted glyph assets across 38 letters $\times$ 18 periods.
- **Phase 2 — Overlay Mode [First Shippable MVP]**: CameraX preview, 4-point perspective alignment, interactive letter bounding boxes, trilingual content browser, path morphing animation.
- **Phase 3 — AR & Agentic SLM Flow**: ARCore Augmented Images session, on-device SLM inference, multi-agent reasoning pipeline (`Identify` $\rightarrow$ `Critic` $\rightarrow$ `Learning`), and database learning sync.
- **Phase 4 — Researcher Capture Mode**: Guided multi-angle image capture, 6-DoF pose and scale metadata logging, local encrypted export bundle.
- **Phase 5 — Field Trial & Outdoor Hardening**: Direct sunlight high-contrast validation, thermal throttling mitigation, battery power profiling, visitor comprehension tests.

---

## 📜 Licensing & Ethics

- **Epigraphical Data**: Transcriptions derived from public-domain scholarly publications (*Epigraphia Zeylanica*, *Inscriptions of Ceylon*), cited per inscription.
- **Vector Glyph Forms**: Re-traced vector originals from public-domain stone inscriptions and estampages. Reconstructed or non-attested glyphs are explicitly tagged and never presented as historical artifacts.
- **Site Regulations**: All field photography conducted in accordance with regulations set forth by the Department of Archaeology, Sri Lanka.

---

## 📚 References & Citations

1. Department of Archaeology, Sri Lanka — *Epigraphia Zeylanica (Vols. I–VI)*.
2. Paranavitana, S. — *Inscriptions of Ceylon (Early Brahmi Inscriptions)*.
3. Google ARCore Documentation — *Augmented Images Developer Guide*.
