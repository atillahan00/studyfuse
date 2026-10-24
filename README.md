# StudyFuse 1.1 — Android study notes compiler

Compile PDFs, handwritten-note screenshots and UTF-8 text into one organized study document. Native Kotlin, Jetpack Compose and Material 3, with a Turkish tablet interface. Android 10+; targets Android 16.

[Download APK](https://github.com/atillahan00/studyfuse/raw/refs/heads/main/downloads/StudyFuse-debug.apk) · [Android Studio project ZIP](https://github.com/atillahan00/studyfuse/raw/refs/heads/main/downloads/StudyFuse-Android-Studio.zip) · [Download checksums](downloads/SHA256SUMS.txt) · [Builds and Android tests](https://github.com/atillahan00/studyfuse/actions/workflows/android.yml)

**Office and OneNote files require source-app PDF export/replacement.** They are not advertised as native conversions. Unsupported sources receive explicit warning pages. OCR recognizes printed Latin text; it is not handwriting or equation recognition. Essential PDF/image/text processing, backup, search and prompt generation work offline, without accounts, ads, subscriptions or an Internet permission.

![StudyFuse tablet project](examples/tablet-project.png)

## New in 1.1

| Feature | Implemented behavior |
|---|---|
| Interactive pagination | Edit cut positions per original PDF/image page, move a selected cut with a slider, regenerate a preview, and compare it with the matching source region. Coverage is checked; long intervals are further tiled when needed. |
| Visual completeness dashboard | Source-by-source status, original/generated page counts, warnings and output ranges. Compare corresponding source/output segments side by side. Missing sources and sources requiring visual review have distinct statuses. |
| Portable backup/restore | A ZIP containing original bytes, replacement PDFs, section names, order, manual pagination, export settings and removed-source undo state. SHA-256, entry names, declared sizes and available storage are validated before an atomic restore to a new project. |
| Search and navigation | Offline original-text/unverified-OCR search, page-number entry, section navigation and thumbnail grids. Viewport rendering makes zooming into long notes memory-conscious. |
| Incremental exports | Verified source conversion checkpoints survive export cancellation. Unchanged sources are reused; layout/content changes invalidate the relevant checkpoint. Corrupt cache is rebuilt. |
| Batch organization | Multi-select, bulk section naming, natural filename sorting, exact-duplicate warnings and reversible removals. Duplicates are never automatically deleted. |
| AI companion files | A Markdown document filtered to visible page bounds, a page search index, and an editable German study prompt. Original visuals remain authoritative; OCR remains unverified. |
| Office preparation | Conversion checklist, individual PDF replacement and batch matching by unique filename stem, with an explicit confirmation before replacement. |
| Upgrade foundation | Database migration from version 1 preserves projects. The published update uses the same development signing key as the original downloadable APK. Optional production signing reads private credentials only from environment variables. |

## Install and update

Install `StudyFuse-debug.apk` on an Android 10+ device. This is an evaluation/debug APK, not a Play Store production release. Android may request permission to install from the application opening it. The directly downloadable 1.1 APK is intended to update the original 1.0 download without uninstalling it; back up important projects first.

CI-generated debug APKs may have a different signing key. Do not uninstall an existing installation to switch keys until its projects have been backed up. Physical-tablet/OEM behavior and arbitrary Office export fidelity are not universally certified.

## Everyday workflow

1. **Yeni proje** or **Dosya içe aktar** starts a project. **Dosyalar** accepts multiple documents; **Klasör** recursively imports a selected folder in natural filename order. Sources are copied into private storage and hashed. A cloud-backed Android document provider may need connectivity to obtain a file before StudyFuse can copy it locally.
2. Long press to reorder, or use the source menu's arrows. **Doğal sırala** puts `2` before `10`. **Toplu seç** enables bulk section naming and removal. **Kaldırmayı geri al** restores the latest removal group. Removed bytes remain private until project deletion and are included in project backups.
3. For Word, PowerPoint, spreadsheets or OneNote, open **Office / OneNote hazırlığı**. Export all relevant content to PDF in the source application. **PDF'leri toplu eşleştir** matches unique basenames, shows the proposed mapping, and waits for your confirmation. Unmatched/ambiguous PDFs are reported for individual selection; nothing is silently skipped.
4. Select **AI Çalışma** for a contents page, section headings, bookmarks and page numbers. **Arşiv kalitesi** omits contents/divider pages. Lossless image encoding is the default. Existing PDF graphics remain vector content. Margins, overlap, output orientation, compression and volume size are configurable.
5. In a PDF/image source menu, choose **Sayfa bölmelerini düzenle**. Cut positions use PDF points from the top of an original page. Edit the list or a slider, press **Bölmeleri önizle**, inspect adjacent generated pages and **Kaynakla karşılaştır**, then save. Original-size mode deliberately does not paginate: choose A4 before editing cuts.
6. **PDF oluştur** runs in the background with progress and cancellation. Verified completed source conversions are retained for a retry. Individual failures get warning pages and an incomplete report; other sources continue safely. Very large or unsupported raster resources retain their encoded PDF data and receive a review warning.
7. Open **Kaynak / çıktı karşılaştırması** to inspect every source's status and jump to the matching output region. The dashboard distinguishes missing content from content included but not fully raster-verified. Comparisons use independent zoom controls; unusual CropBox/MediaBox differences require checking the full source as well.
8. The viewer supports thumbnails, section navigation, direct page entry and **Belgede ara**. OCR matches are labelled **Doğrulanmamış OCR**. Image-only handwriting has no guaranteed searchable text.
9. Save/share the PDF and optionally **Markdown kaydet** or **Almanca istek hazırla**. The German prompt is an offline editable template; it does not contact an AI service. Upload the PDF with its companion text to your chosen assistant and request page-specific analysis.
10. **Proje yedeği kaydet** exports a restorable ZIP. On the home screen, **Yedek geri yükle** creates a separate restored project. Generated PDFs/checkpoints are rebuilt rather than included in the backup. **Ayarlar → Dönüştürme önbelleğini temizle** removes checkpoints without deleting originals.

Documents stay on device unless you explicitly save them through an Android provider or share them with another application. The app does not request broad storage access. Deleting a project deletes its private copies, including removed-source history; exported files elsewhere remain yours.

## Build and test

Use JDK **21**, Android SDK platform **36**, Build Tools **35.0.0** (AGP default) or **36.0.0**, and Android Studio supporting AGP 8.10.1. Accept SDK licenses. Set `ANDROID_HOME` or create `local.properties` with `sdk.dir=/absolute/path/to/android-sdk`. No API keys are required.

```sh
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
# Installable APK: app/build/outputs/apk/debug/app-debug.apk
# Connected Android device/emulator:
./gradlew :app:connectedDebugAndroidTest
# Independent MuPDF verification of generated test PDFs:
python3 -m pip install pymupdf
python3 scripts/verify_pdf_artifacts.py
```

Gradle 8.13 is pinned with a verified distribution checksum. Build dependencies need network on the first build; document processing does not. MuPDF is a development-only verifier, not linked into the Android app. Test reports are under `core/build/reports/tests/` and `app/build/reports/tests/testDebugUnitTest/`; lint is under `app/build/reports/`.

The GitHub workflow installs supported SDK packages explicitly, runs tests/lint, builds APKs and retains artifacts. A separate API 35 emulator job exercises actual Android PDF rendering, long-image decoding and bundled OCR. Inspect the workflow run for its observed result; configuring a test job is not evidence that it passed.

For an independently managed production signing key, set `STUDYFUSE_SIGNING_STORE`, `STUDYFUSE_STORE_PASSWORD`, `STUDYFUSE_KEY_ALIAS`, and `STUDYFUSE_KEY_PASSWORD`, then build `:app:assembleRelease`. Keep the same key for updates and keep it private. No signing keys or passwords are committed. CI validation artifacts are not a substitute for a persistent release signing setup.

## Architecture and limitations

- `core/`: format detection, natural sorting, export settings, manual/automatic pagination and geometric coverage checks.
- `app/.../data/`: Room schema/migration, SAF import, transaction-safe backup/restore and source metadata.
- `app/.../engine/`: conversion adapters, source checkpoints, vector assembly, coverage reports and bounded companion-text extraction.
- `app/.../ui/`: Turkish Compose screens, pagination editor, comparison dashboard, viewport viewer and prompt editor.
- `app/src/test/`: conversion, archive, migration, cache, text, database and Compose tests.
- `app/src/androidTest/`: tests that need an actual Android runtime.

Read [format support](docs/FORMATS.md), [OneNote feasibility](docs/ONENOTE.md), [known limitations](docs/KNOWN_LIMITATIONS.md), [architecture](docs/ARCHITECTURE.md), [validation evidence](docs/VALIDATION.md), and [third-party licenses](THIRD_PARTY_NOTICES.md). [Example output](examples/StudyFuse-example.pdf) uses a synthetic long handwritten-style fixture, not a real teacher's notebook. An openable PDF, source hash, OCR confidence score or geometric coverage check alone is never treated as proof that every original mark was preserved.
