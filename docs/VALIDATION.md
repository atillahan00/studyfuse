# StudyFuse 1.2 validation — 24 October 2026

## Local results

The project builds with JDK 21.0.2, Gradle 8.13, Android SDK 36, AGP 8.10.1, Kotlin/Compose compiler 2.3.20 and KSP 2.3.9. The local full validation includes `:core:test`, `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug` and `:app:assembleDebugAndroidTest`.

There are **67 JVM/Robolectric tests**, covering the existing PDF/image/text compiler and its 200-band long-page fixture, backup/restore, migrations and tablet UI, plus new learning scheduling/interleaving, source-policy/evidence rejection, unverified visual transcription, model-output parse failure, optional exam details, signature isolation, atomic review persistence, study export, exam-state backup and the actual workshop UI. These are engineering checks, not a handwriting or exam-solution accuracy benchmark. Detailed observed results are retained under `validation-evidence/` when packaged.

PyMuPDF independently checks the existing mixed export colours, all 200 long-page markers including the final band, 29 output pages against the report, and independently visible JPX content. The JPX source remains marked REVIEW by the app's conservative verifier.

## Connected Android checks

The workflow includes an Android 15 / API 35 x86_64 Pixel Tablet emulator job. It exercises bundled printed-text OCR, Android PDF rendering, long-image region decoding, the five-region exam pipeline with cancellation/cache recovery, and native AI failure reporting through the separate process. The exam-pipeline test uses an explicitly deterministic inference adapter: it verifies pipeline behavior and **does not test neural handwriting accuracy**. The missing-model test must report a failure and must not produce a substitute answer.

Observed CI results are recorded here and in `validation-evidence/android-runtime.txt` after the run completes. A configured job, compiled instrumentation APK, or successful deterministic test is not represented as a successful real-model benchmark. The [1.1 validation run](https://github.com/atillahan00/studyfuse/actions/runs/40111818167) is historical evidence for its own commit only.

## APK and update identity

The direct download is an **ARM64 debug APK**, package `org.studyfuse`, version **1.2.0 / versionCode 3**, minimum API 29, target API 36. CI also compiles x86_64 support for emulator checks. The delivered APK must retain the previous direct download's certificate SHA-256:

```
1bc145b386fae06945b5b41c38ec2ce29a4c26f97001f29b69308fc8f661c24b
```

Signature verification, APK 16 KiB alignment and 64-bit ELF LOAD alignment are checked from the final packaged APK and recorded in the evidence. The app's merged manifest is inspected for absence of Internet/broad storage permissions. No keystore or private key is included in the source ZIP. CI debug keys can differ from the persistent direct-download key.

## Material limits

No physical HONOR MagicPad 2 or compatible multi-GB vision model is available in this build environment. Actual model loading/image-code testing, handwriting/graph/formula interpretation, German/Turkish quality, device speed, memory pressure and thermal/OEM behavior need the on-device acceptance exercise. The application implements real local inference, but the build/test results do not establish its accuracy on a teacher's notebook. Generated items begin unapproved. Evidence matching and self-reported uncertainty cannot guarantee correctness or complete understanding.

Office/OneNote still require source-app PDF export/replacement. Enriched Learning supports imported reference documents and labelled local-model knowledge, without live web retrieval. See [local AI design/limits](LOCAL_AI.md) and [known limitations](KNOWN_LIMITATIONS.md). The [1.1 validation record](VALIDATION-1.1.md) is retained as history.
