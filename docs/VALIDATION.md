# StudyFuse 1.3 validation

## Scope and local build

The project uses JDK 21.0.2, Gradle 8.13, Android SDK 36, AGP 8.10.1, Kotlin/Compose compiler 2.3.20, KSP 2.3.9 and LiteRT-LM 0.16.1. Validation runs core tests, Android JVM/Robolectric tests, lint and both application/instrumentation APK builds. The final ARM64 build passed **97 JVM/Robolectric tests**: 34 core tests and 63 Android JVM tests, with zero failures, errors or skips. Lint reported **0 errors and 8 warnings**. Both application and instrumentation APKs built successfully. XML/log evidence is packaged under `validation-evidence/`.

Application source is `acf5480a056714eea04af703ffa8e89372b1a231`; validation source is `cf07ca8a8241dba441fe7756cc90b1fb394ba825` (subsequent commits change test synchronization/diagnostics and CI artifact collection, not application behavior). Hosted runs exposed a timing-sensitive Robolectric mock-exam UI check. The check now controls countdown frames, exercises accessible button actions, and waits for enabled assessment controls after asynchronous submission. It still verifies hidden answers, persisted typed responses and exactly one saved self-assessment. These are deterministic fixture tests, not a physical-touch or neural-accuracy benchmark.

New tests cover interrupted/ranged model transfer, ignored Range, corrupt SHA, bad ranges, cancellation, cached-file verification without networking, cleartext rejection, catalog pinning, Unicode retrieval/context bounds, source diversity, daily planning, local keyword rewriting, source-policy/citation rejection, generated-card approval boundaries, timed-mock recovery/expiry, answer-key protection, one-time self-assessment, cloze provenance, TSV escaping, state backup and actual workshop/hub UI interactions. The prepared mock-exam UI fixture is deterministic test data, not output of a neural handwriting benchmark.

Independent PyMuPDF verification checks mixed source colours, all 200 bands of the tall-page fixture including its final band, 29 output pages against the report and retained JPX content. Source-vector retention in exam exports is checked separately by actual PDF text/graphics operators.

## Hosted checks

Run [41199749074](https://github.com/atillahan00/studyfuse/actions/runs/41199749074), source `cf07ca8a8241dba441fe7756cc90b1fb394ba825`: build/test/lint/APK job **passed**; pinned public-model check **passed**. The connected Android job is still running at this intermediate publication; no result is inferred from an uncompleted job. See `github-build.txt` and `model-catalog.txt` for observed evidence.

The Android workflow runs build/tests/lint, an Android 15 / API 35 x86_64 tablet emulator suite, and a public model-catalog check. The catalog script verifies pinned publisher pointer SHA/size and a 64-byte HTTPS ranged response, without downloading multi-GB weights. This execution environment blocks direct model-host access; that is not represented as a successful full download.

The connected suite exercises native PDF rendering, long-image decoding, bundled printed OCR, region-level exam cancellation/recovery with a deterministic inference adapter, native AI failure isolation and absence of the known removed OCR diagnostics transport services. It does not establish real-model handwriting/math accuracy or MagicPad 2 inference performance.

## APK

The direct download is an ARM64 debug/evaluation APK for `org.studyfuse`, **1.3.0 / versionCode 4**, minimum API 29, target API 36. It retains the direct download's certificate SHA-256:

```
1bc145b386fae06945b5b41c38ec2ce29a4c26f97001f29b69308fc8f661c24b
```

The delivered APK is **85,983,769 bytes** (about 82 MiB). Its original development signature, 16 KiB ZIP alignment and every packaged ARM64 ELF LOAD alignment were verified. Manifest evidence confirms version 1.3.0 / 4, ARM64 only, min API 29 and target API 36. Verification files are under `validation-evidence/`. Internet/network-state permission is now intentional for model downloads; broad storage permissions are absent. Known OCR DataTransport components are removed; this is not a universal vendor/OS packet audit. CI debug keys may differ. No private signing key or weight file is committed.

## Limits

No physical MagicPad 2 or complete vision model is loaded in the local build environment. Public metadata/range verification and deterministic engine adapters must not be described as a full model download, on-device model compatibility test or educational accuracy evaluation. Setup uses the real native engine to test the model on the student's own device before activation. OCR/transcriptions, answers and solutions require source review. The app cannot guarantee grades or know undisclosed teacher exam scope.

See [automatic study workflow](AUTOMATIC_STUDY.md), [known limitations](KNOWN_LIMITATIONS.md), and the historical [1.2 record](VALIDATION-1.2.md).
