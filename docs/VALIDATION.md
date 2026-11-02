# StudyFuse 1.3.5 validation

Processing source: `0cda70e819d879293a35ba8cfe733d1ecf64f0e6`. Subsequent documentation/evidence/download commits do not alter this application source.

## Local checks

**148 JVM/Robolectric tests passed** (39 core, 109 Android JVM), zero failures/errors/skips. Lint: **0 errors, 8 warnings**. ARM64 application and instrumentation APKs built with JDK 21, Gradle 8.13, SDK 36 and Build Tools 35.0.0.

New coach checks cover app-owned literal Unicode quotes/short evidence labels, conventional-ID compatibility, explicit task-specific JSON examples, partial valid/invalid card responses, unchanged rejection of invented references/legacy changed-negation quotes, honest insufficient-evidence answers, native-text provenance/metadata exclusion, 100-page batch plans, one bounded repair, no repair when valid cards already exist, cancellation/resume, missing-passage targeting, changed-source cache invalidation, corrupt checkpoint references and idempotent recovery after interrupted deck writes. Existing archive tests now retain the coverage record. The actual Compose tablet capture verifies full-source mode and optional focus controls; mocked model weights do not establish real neural quality.

Independent MuPDF checks passed for all 200 tall-page markers, 29 report/output pages, mixed source colours and retained JPX imagery. The PDF/image conversion pipeline is unchanged by the coach update.

## Hosted checks

[Run 41449132106](https://github.com/atillahan00/studyfuse/actions/runs/41449132106) passed the hosted build/tests/lint/APKs and public-model metadata/range checks. Its downloaded validation artifact confirms 148 JVM/Robolectric tests with zero failures/errors/skips; **10 native Android 15 / API 35 tablet-emulator tests also passed**, with zero failures/errors/skips confirmed in the downloaded runtime XML artifact. Raw logs and artifact digest/count summaries are in `validation-evidence/`. The added connected test exercises the source-label protocol with actual Android JSON/storage/coroutines: a valid Turkish quote is attached literally, invented source references are rejected independently, valid cards are stored without automatic approval and completed work is reused. It uses a deterministic inference adapter, not model weights.

## Delivered update and limits

Version **1.3.5 / code 9**, size **87,659,218 bytes** (about 83.6 MiB), SHA-256 `1231736becba8d2e17cd306843240c737de4f5ce8c9a204ac21131d8a3fc533c`, `org.studyfuse`, ARM64, min API 29, target API 36. The development signing certificate remains `1bc145b386fae06945b5b41c38ec2ce29a4c26f97001f29b69308fc8f661c24b`. Signature, 16 KiB ZIP alignment and unchanged native-library SHA/ELF load-segment alignment checks pass. No SDK/model/network-permission change is introduced. Update in place and retain the existing model, source PDFs, readings and reviewed cards; a source re-read is not required merely for this update.

The reported screenshot demonstrates rejection by the old literal-quote validator. No actual failing model response was provided, so its exact formatting/translation mismatch is unknown. The new protocol removes the need to retype quotations and long source IDs, preserves valid cards and exposes rejected items. Citation existence is not semantic verification: a valid excerpt can still be irrelevant to a wrong generated answer. No physical MagicPad model-speed, handwriting or pedagogical-accuracy benchmark is claimed.

Full-source mode visits available reading fragments in bounded batches. Coverage records distinguish passages with a card from those still without cards and do not claim every fact is represented. Cancellation/cache reuse are tested, not a guarantee against every Android/OEM interruption. See [source-link/card workflow](COACH_RELIABILITY.md), [limitations](KNOWN_LIMITATIONS.md) and [1.3.4 validation](VALIDATION-1.3.4.md).
