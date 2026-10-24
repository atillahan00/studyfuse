# Validation record — 24 October 2026

These are observed results from this delivery, not proposed tests.

## Successful builds

Executed on Linux x86_64 with JDK 21.0.2, Gradle 8.13, Android SDK 36, AGP 8.10.1, Kotlin 2.1.20. App bytecode targets JVM 17. The wrapper distribution SHA-256 was compared with Gradle's published checksum and pinned.

```
:core:test
:app:testDebugUnitTest
:app:lintDebug
:app:assembleDebug
:app:assembleDebugAndroidTest
```

The combined validation completed with `BUILD SUCCESSFUL`. After the final backup-policy manifest adjustment, lint, debug APK and connected-test APK builds were successfully repeated. Packaging was regenerated to remove obsolete incremental ZIP entries. The delivered debug APK's v2 signature and 16 KiB ZIP alignment were checked successfully. The actual APK declares minimum API 29 and target API 36, contains the bundled OCR models, and has no INTERNET or broad storage permission.

## 34 passing automated tests

| Suite | Tests | Failures / errors / skipped |
|---|---:|---:|
| Pure Kotlin pagination/format tests | 12 | 0 / 0 / 0 |
| Android conversion integration tests (Robolectric API 34, native graphics) | 15 | 0 / 0 / 0 |
| Room / URI import / generation publication tests | 5 | 0 / 0 / 0 |
| Compose tablet interaction tests | 2 | 0 / 0 / 0 |

Coverage includes:

- PDF merging, mixed image/PDF output, order and printed page numbers.
- Complete 1000 × 18000-point handwritten-style source coverage, sampled at **all 200 known marker bands**, including the very bottom. Exported PDF still uses vector form XObjects, and final source text is searchable.
- PDF rotations 0/90/180/270 and nonzero MediaBox origins compared against rendered source samples.
- German `ä ö ü ß`, Turkish `ğ ü ş ı ö ç`, and mathematical `∑ ∫ √ ∞ α β` text extraction.
- Corrupt PDFs, invalid UTF-8, changed source hashes, explicit Office fallbacks, and continuing after a bad source.
- **105-file batch**, deterministic source ranges, cancellation with original preservation and scratch cleanup.
- AI-mode table of contents/ranges, measured-size volume splitting, ink annotation flattening.
- JPX encoded-image retention, explicit REVIEW status for its unsupported local decoder, and byte identity of the retained image stream.
- Durable URI import, SHA-256, reordering, PDF replacement preserving originals, rejection of empty files, rollback/cleanup when a project update fails mid-import, and prevention of stale cancelled workers overwriting a newer generation.
- Actual Turkish home/settings/theme interaction, new project creation, project rename, and disabled export for an empty project. Tablet screenshots come from the tested Compose hierarchy, not a mock-up.
- Early rejection of pathological canvas sizes before unbounded allocation; randomized two-axis coverage across 1000 input geometries, missing-strip rejection, overlap, original size and minimum-text-size auto-landscape behavior.

## Independent renderer checks

`python3 scripts/verify_pdf_artifacts.py` passed with **PyMuPDF 1.26.6**:

- The generated mixed PDF has the expected red image and blue source-PDF samples.
- All 200 markers survive in the generated long-page output.
- The **29 output pages** agree with the conversion report, and the last original text remains searchable.
- A JPX image remains visible through an independent renderer even though the app correctly flags its own raster-verification limitation.

Robolectric native Skia and Android use differing buffer byte order in the PDFBox raw-image rendering path; mixed-image tests therefore inspect encoded RGB samples, while MuPDF independently verifies their rendered colours. Robolectric's platform `PdfRenderer` implementation is not used as proof of actual Android preview behavior.

## Lint and native compatibility

Lint has **0 errors**. Remaining warnings are dependency-internal Bouncy Castle trust-manager helpers (the app has no networking and does not use those helpers), Kotlin extension-style suggestions, and an Android backup-policy advisory. The manifest explicitly disables legacy backup and excludes all cloud/device-transfer domains; these policies are supplied in source and APK. The final lint text is supplied as evidence rather than hidden by a baseline.

Both packaged 64-bit libraries (`libandroidx.graphics.path.so`, `libmlkit_google_ocr_pipeline.so`) have ELF LOAD alignment **0x4000** for arm64-v8a and x86_64. `zipalign -c -P 16 -v 4` passes. The APK also contains 32-bit variants; the older 32-bit OCR variants use 4 KiB ELF alignment. These checks establish relevant binary packaging properties, not physical Android 15/16 certification.

## Not executed / not certified

No physical Android tablet or emulator was attached, so `connectedDebugAndroidTest` was **not executed**. Its APK compiled and contains actual platform PDF rendering, long-image region decoding and bundled printed-text OCR tests. The ML Kit native OCR engine itself was not run in Robolectric. There is no claim of handwritten OCR testing or support.

No teacher's real OneNote notebook, authenticated Microsoft Graph tenant, real Microsoft Office export, OEM background policy, share recipient, or Android 15/16 device was tested. The exact raw-Office acceptance scenario still needs the documented source-app PDF conversion/replacement step. A successful parse or an openable PDF is never represented as a guarantee that every original educational mark was preserved.
