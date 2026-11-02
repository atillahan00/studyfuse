# StudyFuse 1.3.3 validation

Processing source: `fab357083262e06a11aad0e3904eb3d4c900b639`. Final app source: `c043dab9d1274ffb3be9b1f4961129611b52cc89` changes only the completed-duration display to use the measured request duration rather than time since completion. The final source passed the complete local suite, lint and APK builds. Its UI-only commit skips a duplicate hosted workflow; the processing/native code validated below is unchanged.

## Local checks

**124 JVM/Robolectric tests passed** (38 core, 86 Android JVM), zero failures/errors/skips. Lint: **0 errors, 8 warnings**. Both ARM64 app and instrumentation APKs built successfully with JDK 21, Gradle 8.13, SDK 36 and Build Tools 35.0.0. Raw XML/build logs are under `validation-evidence/`.

New checks cover interval parsing/counting without large range expansion, invalid/unsupported selections, workload counts, legacy JSON settings, new option round trips, exact native-text preservation with visual additions, legacy detailed-overview reuse with stable approved-card IDs, telemetry privacy/completion state, observer failure isolation and actual fast-profile controls/persistence. The SDK completion regression remains in place. No whole-model accuracy/speed is inferred from controlled callbacks or fake inference adapters.

Independent PyMuPDF checks passed for all 200 tall-page markers including the last, mixed source colours, 29 report/output pages and retained JPX content.

## Hosted checks

[Run 41415165062](https://github.com/atillahan00/studyfuse/actions/runs/41415165062) on the processing source above passed build/tests/lint/APKs, public-model metadata/range verification and **eight native Android 15 / API 35 tablet-emulator tests**. Logs show Starting 8 tests, Finished 8 tests and BUILD SUCCESSFUL.

The added connected test uses actual PDF creation, conversion, native rendering and an explicit deterministic inference adapter. Three source pages take three overview calls; adding close-ups to one page takes four new calls; extending to all close-ups takes eight additional calls; switching GPU/CPU then takes zero new calls with 15 cached units. Original PDF bytes are verified unchanged, existing overview keys are reused and options reach the pipeline adapter. This demonstrates reduced work/reuse and numeric timing storage, **not** a measured MagicPad model speedup or successful neural execution of every visual-budget setting.

Existing connected checks still cover native image/PDF fidelity, printed OCR, cancellation/recovery, failure isolation/child metadata, removed known OCR telemetry services and the actual SDK completion ABI workaround. See `android-runtime.txt`, `github-build.txt` and `model-catalog.txt` in the evidence directory.

## Delivered APK

**1.3.3 / versionCode 7**, `org.studyfuse`, ARM64, min API 29, target API 36. Size: **87,483,666 bytes** (about 83.4 MiB). Signature, 16 KiB ZIP alignment and the SHA/ELF alignment of the unchanged native libraries passed. Certificate SHA-256 remains:

```
1bc145b386fae06945b5b41c38ec2ce29a4c26f97001f29b69308fc8f661c24b
```

Update in place to retain projects and the existing E2B weights. No SDK/model download change, private signing key or additional Android permission is introduced. The public APK/source ZIP are verified after publication and checksummed in downloads/SHA256SUMS.txt.

## Performance limits

The user reports roughly a minute per page even with one region and GPU language processing. This is not treated as a CPU-only problem. The patch reduces redundant text output, optionally defers exercises, offers explicit Gemma 4 visual budgets and improves reuse/parser/UI overhead. Actual neural speed on the physical HONOR tablet is still unmeasured here. Lower budgets may miss fine handwriting/diagram labels; reading-first does not produce a full practice pack. Original source PDF fidelity and generated-reading completeness are different questions. Numeric telemetry provides evidence for the next real-device comparison; no multiplier, seconds-per-page target or handwriting accuracy is claimed.

See [performance workflow](AI_PERFORMANCE.md), [known limitations](KNOWN_LIMITATIONS.md) and the historical [1.3.2 record](VALIDATION-1.3.2.md).
