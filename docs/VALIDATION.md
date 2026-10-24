# StudyFuse 1.1 validation — 24 October 2026

## Observed local results

The complete project built successfully using JDK 21.0.2, Gradle 8.13, Android SDK 36, AGP 8.10.1 and Kotlin 2.1.20. These tasks passed:

```
:core:test :app:testDebugUnitTest :app:lintDebug
:app:assembleDebug :app:assembleDebugAndroidTest
```

| Suite | Passing tests | Failures / errors / skipped |
|---|---:|---:|
| Pagination, manual cuts, natural ordering and formats | 14 | 0 / 0 / 0 |
| PDF/image/text, checkpoints and companion extraction | 18 | 0 / 0 / 0 |
| Repository/import transactions | 5 | 0 / 0 / 0 |
| Backup/restore, archive rejection and removal recovery | 6 | 0 / 0 / 0 |
| Existing database migration 1→2 | 1 | 0 / 0 / 0 |
| Actual Compose tablet interactions | 3 | 0 / 0 / 0 |
| **Total** | **47** | **0 / 0 / 0** |

Notable checks include all 200 known marker bands in the long-page fixture; uniform PDF transformations for rotations and nonzero origins; German/Turkish/math Unicode; corrupt inputs; 105 sources; cancellation; source byte identity; PDF volume coverage; actual ink annotations; retention/review of JPX images; SHA-verified cache reuse/rebuild; manual-cut coverage and invalid-boundary rejection; missing/off-page text filtering and overlap de-duplication; ZIP traversal/truncation rejection; original/replacement backup round-trip; undo-state preservation; migration of existing project/source records; and multi-select removal followed by UI undo.

Local Android integration tests use Robolectric API 34 with native graphics. Native Android PdfRenderer is not assumed to work in Robolectric; rendering tests requiring it are supplied as connected-device tests. Source/companion coordinate tests use actual generated PDFs, not mocked conversion success.

## Independent PDF checks

PyMuPDF 1.26.6 independently verified:

- Expected red image and blue source-PDF colours in a mixed export.
- All 200 known source markers, including the last band, in the generated long-note PDF.
- The final source text remains searchable and the **29 output pages** match the completeness report.
- JPX image data retained in the PDF is independently visible even though the local PDFBox raster verifier flags REVIEW.

Run `python3 scripts/verify_pdf_artifacts.py` after successful JVM tests to repeat these checks. PDF opening or geometric coverage alone is not a proof of complete visual fidelity.

## APK update and packaging

The delivered APK declares `org.studyfuse`, **version 1.1.0 / versionCode 2**, minimum API 29 and target API 36. Its signing certificate SHA-256 matches the original version 1.0 downloadable APK:

```
1bc145b386fae06945b5b41c38ec2ce29a4c26f97001f29b69308fc8f661c24b
```

That permits an in-place update of the original delivery installation, subject to Android's normal package checks. It remains a development/debug certificate, not a production release certification. Future CI debug keys may differ. Production signing hooks accept an external private keystore and credentials without committing them.

APK signature verification and `zipalign -c -P 16 4` passed. Packaged 64-bit native libraries have 0x4000 ELF LOAD alignment. The app has no Internet or broad storage permission. Backup/cloud transfer exclusions remain explicit. Lint reports **0 errors**; dependency/internal trust-manager and style/backup advisories are retained in the supplied report rather than hidden.

## GitHub Android runtime tests

The workflow fixes the obsolete `tools` SDK request by selecting `platform-tools` explicitly. It includes an API 35 emulator job using actual Android rendering, large-image region decoding/viewport checks and bundled printed-text OCR. Its observed run result is published in the repository/Actions and associated evidence when available; merely defining the job is not counted as a passing test.

No physical tablet was connected locally. There is no claim of hardware/OEM certification, reliable handwriting/mathematical OCR, authenticated OneNote/Graph integration or universal Office fidelity. Office/OneNote still require source-app PDF conversion. An incomplete source checkpoint restarts that source; completed sources are reused. Arbitrary huge/malformed documents and every diagram boundary still require care and visual review.
