# Changes

## 1.3.0

- Added automatic pinned-model setup with HTTPS download, SHA/size verification, pause/resume, network constraints, safe candidate activation and automatic image-input testing.
- Added a source-cited local study coach: Q&A, simpler explanations, Socratic-hint prompts, formative feedback, bilingual terminology, source comparison and practice-card generation.
- Added multilingual search-term rewriting, bounded source retrieval, policy checks and citation matching.
- Added reviewed/cloze cards, integration with the existing recall queue, Anki TSV export, timed mock exams, saved responses, separate answer keys and student self-marking.
- Added daily plans based on due reviews and past lapses; expanded project backups for coach/deck/mock state.
- Added model-download networking permissions and disabled known bundled-OCR DataTransport components. Printed OCR and native failure isolation are tested separately.
- Automatic setup still needs several GB of model data. Real-device model compatibility/handwriting quality is not guaranteed, and no exam-grade promises are made.

## 1.2.0

- Added actual local visual-model execution through LiteRT-LM, streamed model import, image-input compatibility testing and isolated native AI service process.
- Added per-project Documents Only / Enriched Learning policies, explicit reference-source roles, German/Turkish/English output and optional exam context/date.
- Added whole-page plus overlapping-region visual analysis, source-evidence checks, uncertainty/rejection records, checkpoint recovery and a coverage ledger.
- Added exam notes, reviewed hidden-answer recall, student corrections, topic interleaving, spaced-review heuristics and an error notebook.
- Added PDF/Markdown study exports with separate answer keys and an original-source visual appendix; expanded backups to retain exam readings and review history.
- Upgraded Kotlin/Compose compiler to 2.3.20 and KSP to 2.3.9 for the pinned runtime. Direct download targets ARM64; emulator builds retain x86_64.
- Local visual interpretation remains an experimental AI draft. Physical MagicPad 2 handwriting/graph/math accuracy has not been certified; Office/OneNote still need PDF export.

## 1.1.0

- Added per-source, per-original-page manual pagination with bounded cut editing, overlap, automatic coverage checks, adjacent-page preview and source/output comparison.
- Added a visual completeness dashboard, explicit missing/review distinctions and page mapping navigation.
- Added streamed project backup/restore with original/replacement bytes, settings, ordering, custom cuts and removed-source history; validates paths, hashes and declared sizes before publishing a new project.
- Added viewport-based PDF/image zoom, thumbnails, direct page entry, section navigation and offline original/OCR search.
- Added SHA-verified per-source conversion checkpoints, settings invalidation, corrupt-cache recovery and manual cache cleanup.
- Added multi-selection, bulk section names, natural sorting, duplicate warnings and reversible removal groups.
- Added page-bounded Markdown export, source-position overlap de-duplication, a searchable page index and an editable/copyable German study prompt.
- Added guided Office/OneNote PDF preparation and explicit unique-basename batch replacement confirmation.
- Added Room schema 1→2 migration, on-demand WorkManager setup, opt-in external production signing, and versionCode 2 with the original downloadable APK's development signing certificate retained.
- Fixed GitHub SDK setup and added Android runtime emulator checks.

## 1.0.0

Initial native PDF/image/text compiler, long-page coverage, explicit external-format fallbacks, OCR transcripts, export/report UI, sharing and validation fixtures.
