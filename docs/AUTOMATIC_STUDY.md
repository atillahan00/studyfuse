# Automatic offline AI and study tools — 1.3

## What students can do inside the app

Start automatic model setup from home-screen Settings or the project exam workshop. Then import class material, compile it, generate the visual reading, inspect warnings, and use the learning hub. Additional exam details are optional. The hub offers cited question answering, simpler explanations, graduated-hint prompts, formative answer feedback, German–Turkish terminology, multi-source comparison and source-backed practice-card generation. Source-only/enriched policies are retained per project and visible in the hub.

New cards start unapproved. Students can check their evidence, create exact-source cloze questions, use expanding-interval recall in the original workshop, or export approved cards as UTF-8/HTML-escaped Anki TSV. Anki is optional; all essential practice works in StudyFuse. Mock exams choose reviewed cards across topics, prioritize previous lapses within topics, save answers/deadline, reveal answers on submission/expiry, and accept 0/1/2 student self-assessments. Scores are not official grades or readiness predictions. Daily plans use due questions, lapses and a 5–240-minute budget; they are planning heuristics, not learned memory forecasts.

## Model distribution

`ModelCatalog` pins publisher repository commits, exact sizes and SHA-256 digests. The app does not fetch an arbitrary changing latest model. Public Apache-2.0 mobile vision artifacts:

| Model | Bytes | SHA-256 |
|---|---:|---|
| Gemma 4 E2B | 2,588,147,712 | `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c` |
| Gemma 4 E4B | 3,659,530,240 | `0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0` |

Primary provenance: Google's [Gallery allowlist](https://github.com/google-ai-edge/gallery/blob/main/model_allowlists/1_0_22.json), publisher [E2B file](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/blob/6e5c4f1e395deb959c494953478fa5cec4b8008f/gemma-4-E2B-it.litertlm) and [E4B file](https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/blob/28299f30ee4d43294517a4ac93abd6163412f07f/gemma-4-E4B-it.litertlm). Publisher hashes and public download links were inspected; full weights and physical-device accuracy are separate validation tasks. `scripts/verify_model_catalog.py` checks remote pointer metadata and a small HTTPS ranged response without downloading several GB in CI.

The network client performs HTTPS GETs, follows a bounded number of HTTPS redirects, sends no authentication cookies/documents/prompts, and requires the expected size/hash. It preserves partial bytes on interruption, validates Content-Range before append, restarts if a server ignores Range, rejects changed ranges and discards digest-mismatched data. Files are streamed in 1 MiB buffers. A process-level mutex prevents overlapping pause/resume workers from writing the same partial file. Disk guards reserve working space; no second full model copy is made for installation.

WorkManager manages background transfer, default unmetered-network constraints, foreground notification, progress, cancellation and bounded retry/backoff. A completed candidate can be tested with no network constraint. Model testing/activation shares the existing inference mutex; downloading can coexist with the current model's study session. The previous selected model is not replaced until the new candidate passes a randomized image-code probe. That probe establishes basic image-input compatibility, not handwriting/math accuracy. Private models/images are not backed up. Manual SAF import remains a fallback; it follows the existing manual selection/test workflow.

A small APK does not mean a small model. Students still need the stated download/storage, compatible GPU drivers and sufficient free memory. Model-host availability, Android foreground-service limits, metered-network settings and power restrictions can interrupt setup; resume is provided. This execution environment blocks direct model-host access. Hosted CI checks public metadata/transport separately; no successful full-model or MagicPad 2 accuracy benchmark is inferred from a unit test or a download URL.

## Coach retrieval and evidence

The coach works from current-policy visual reading records, not unrestricted web search. Long readings are split into bounded, Unicode-safe, word-aware excerpts without removing original text. A BM25-style lexical index selects relevant excerpts within the model's context budget. If direct retrieval finds nothing, the same local model may rewrite search terms across German/Turkish/English; those terms are search aids, not study claims. Cross-source comparison requires relevant excerpts from two sources and diversifies retrieval across them. Very different terminology, ambiguous handwriting or inaccurate model rewrites can still miss relevant content.

Each final response must separate source-backed text from model-added information, return source IDs and exact supporting quotations, and preserve uncertainties. Documents Only rejects nonempty additions and unknown/unmatched citations. Generated cards require an existing passage, nonempty question/answer and matching quote. Enriched model knowledge appears in a separately labelled field. The student sees citations that open the corresponding original PDF page. New coach responses are independent source-grounded requests, not an unlimited conversational memory.

Quote checks prove string correspondence only. They cannot prove that an answer follows from the excerpt or that the model read handwriting correctly. The coach currently retrieves visual transcriptions/observations; correcting an individual card does not automatically repair every underlying reading record. Check original diagrams, numeric signs, units and mathematical solutions. Generated answers remain AI drafts; feedback is formative and has no fabricated numeric grade.

## Persistence and limitations

Coach history and generated cards are signature-scoped to source/model/config/policy. Cards have stable content IDs and separate student approval/review state. Timed mocks freeze question/answer snapshots at creation; later card edits should be checked separately against the original source. Answers are serialized to atomic files and a dialog cannot close while its pending save queue is active. Abrupt OS/process/power termination can still interrupt the most recent uncompleted save; there is no claim of synchronous durable persistence at every keystroke. Timing uses the device clock and is intended for private practice, not secure examination invigilation.

Mock self-marks update recall scheduling once per question. New coach cards participate in the existing recall queue. The TSV writer escapes tabs/newlines/HTML rather than accidentally creating extra fields. Backups now include `coach.json`, `cards.json`, `mock.json` and the existing readings/review state; generated exports and weights remain separate. Unsupported Office/OneNote formats still require source-app PDF export. No content can be recovered if the source app omitted it.

## Network permission and bundled OCR

Version 1.3 needs Internet and network-state permissions for user-initiated automatic setup. All document inference remains local; StudyFuse's network code accepts only catalog-selected model downloads, not upload requests. The bundled printed-text ML Kit SDK has its own [data-disclosure terms](https://developers.google.com/ml-kit/android-data-disclosure). Its known DataTransport backend discovery, job service and alarm receiver are removed from the merged manifest, and Firebase default collection is disabled. Connected Android checks verify the absent transport services and that bundled printed OCR still runs. This is a concrete configuration measure, not a universal packet-level audit of vendor SDKs, the operating system or external Android document providers. Sharing/exporting documents remains an explicit student action.

## Learning and accuracy

Retrieval practice and distributed practice are the foundation; self-explanation, worked examples, feedback and interleaving are used with their task-dependent limits. The app cannot guarantee an exam grade, know a teacher's undisclosed exam weighting, verify all source facts, or replace checking a model's interpretation. A future classroom benchmark needs representative real documents and human-marked answers on the target devices. The current engineering test suite validates retrieval/format/policy/persistence/timing/transport behavior, not exam achievement or neural accuracy.
