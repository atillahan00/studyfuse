# Changes

## 1.3.3

- Added explicit reading-first, balanced and full-detail AI profiles, configurable Gemma 4 visual budgets and selected-page close-ups. New settings default to one view; stored detailed settings are retained.
- Retain extracted PDF text directly instead of asking the model to repeat it. Reading-first leaves exercise generation to the existing local coach.
- Share equivalent overview/detail checkpoints across view plans and CPU/GPU backends, preserving existing reviewed item IDs. Quality/mode/source changes remain separated.
- Reuse one PDFBox/native parser per output volume; cached regions avoid parsing/rendering. Progress updates avoid repeatedly loading the full study corpus.
- Added numeric load/first-chunk/generation timing, live output-character progress and per-region extraction/render/inference timings. No response text is added to diagnostics.
- Existing working device profiles survive failed/cancelled re-tests. Real MagicPad speed remains unbenchmarked; low visual budgets can reduce fine-detail recognition.

## 1.3.2

- Reproduced the pinned SDK Flow adapter's NoSuchMethodError in its completion callback; switched production generation to the callback overload with app-compiled completion, bounded accumulation and cancellation.
- Added actual published-SDK ABI regression checks, normal/error/threaded/late/oversize/cancel bridge tests and sanitized uncaught child-error capture. A model-weight or SDK upgrade is not required.
- Prior startup/failure tests did not exercise successful SDK completion; physical tablet success still needs a retry with the preserved model.

## 1.3.1

- Added CPU-language/GPU-vision compatibility setup, optional bounded GPU→CPU recovery and cached model selection/retesting without deleting downloaded weights.
- Added private local startup-stage/RAM metadata, Android 11+ process-exit diagnostics, Android 10 fallback and explicit diagnostic copying.
- Apply the tested backend when opening study tools; separate compiled runtime caches by model/backend. Retain native-process isolation and elevate bound-service importance.
- Keep the original signing key and existing private model/project storage. The native runtime remains 0.16.1; this does not claim a confirmed fix for an unknown physical-device crash.

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
