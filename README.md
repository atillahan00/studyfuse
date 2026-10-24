# StudyFuse — Android study notes compiler

A native Kotlin / Jetpack Compose application with a Turkish tablet interface. Android 10+ (API 29), compile/target API 36. Documents remain in private on-device storage. No account, server, advertisements, subscription, storage-wide access, or application Internet permission.

**This delivery is a working PDF/image/text compiler, with explicit PDF replacement workflows for Office and OneNote formats. It is not a universal Office renderer.** Selecting DOCX/PPTX/ONE files alone will produce an **incomplete** PDF containing warning pages. Export those documents to PDF in their source application and use **PDF ile değiştir** before compiling a complete batch. See [format support](docs/FORMATS.md) and [OneNote feasibility](docs/ONENOTE.md).

## Install

- [Download the debug APK](https://github.com/atillahan00/studyfuse/raw/refs/heads/main/downloads/StudyFuse-debug.apk)
- [Download the Android Studio source ZIP](https://github.com/atillahan00/studyfuse/raw/refs/heads/main/downloads/StudyFuse-Android-Studio.zip)
- [Checksums and device-test APK](downloads/README.md)
- [Example output PDF](examples/StudyFuse-example.pdf) · [Tablet interface](examples/tablet-project.png)

Install the supplied `StudyFuse-debug.apk` on an Android 10+ device. Android may ask you to allow installation from the app used to open the APK. The APK is a debug build for evaluation, not a signed Play Store release. It includes the OCR model for offline use. Keep the original notes outside StudyFuse as well: uninstalling the app deletes its private projects.

## Use

1. Tap **Yeni proje** or **Dosya içe aktar**. Rename the project using the pencil button.
2. **Dosyalar** selects multiple files through Android's document picker. **Klasör** recursively imports a selected folder in name order. Imports copy bytes into private storage and calculate SHA-256; cloud-backed providers may need connectivity to retrieve a file before that local copy exists.
3. Long press and drag sources to reorder them. The source menu also has **Yukarı taşı / Aşağı taşı**, **Bölüm adını değiştir**, **Projeden kaldır**, and **PDF ile değiştir**. A replacement retains the original file, section and order.
4. Resolve every **PDF dönüşümü gerekiyor** warning. Open the original in its source app, export all pages/slides/sheets to PDF, select that PDF as a replacement, then preview it. StudyFuse cannot prove that a source application exported every original object.
5. Choose **AI Çalışma** for a table of contents, section pages, PDF bookmarks, global page numbers, and a completeness report. **Arşiv kalitesi** omits the contents and dividers. Both retain PDF vector graphics and original visual content within the processed bounds. Lossless image encoding is the default; choose **Orijinal boyut** when page normalization is unwanted.
6. **Otomatik A4** divides long/wide canvases into overlapping pages. Whitespace and embedded text-size heuristics guide splitting; they cannot understand every diagram or handwriting stroke. Preview every boundary that matters. Margins, overlap, portrait/landscape, compression, and volume size are selectable.
7. Optional **Basılı metin OCR** generates clearly labelled, unverified searchable transcript appendices. Original pages are retained. It does **not** recognize handwriting or mathematical notation reliably. OCR is off by default and available line/word confidence is recorded in JSON; a page-level score is not fabricated.
8. Tap **PDF oluştur**. Processing uses WorkManager and a foreground notification with cancellation. Individual source failures do not discard other sources. A failed or unsupported source gets a warning page; the PDF is named `StudyFuse-EKSIK.pdf` and its report says `complete: false`.
9. Preview, **PDF kaydet**, or **Paylaş** to ChatGPT or another application. The share chooser is user initiated. For volumes, save/share each or all; their printed numbers retain the full document's page numbers. A single page can exceed the selected volume limit and is explicitly identified.
10. Export the separate JSON and text reports. JSON includes source hashes, results, page ranges, geometric coverage, OCR provenance, output sizes and hashes. Deleting a project deletes its private originals and outputs, not files saved elsewhere.

On tablets the source list and export controls appear together; on phones they are separate tabs. Settings provide system/light/dark themes. PDF and image previews support page navigation, zoom and pan. Text and externally converted formats open in an installed source application.

## Build

Requirements: JDK **21** (compilation targets JVM 17), Android SDK platform **36**, Build Tools **35.0.0** (AGP default) and/or **36.0.0**, platform-tools, Android Studio supporting AGP 8.10.1. Accept the Android SDK licenses using `sdkmanager --licenses`. Set `ANDROID_HOME` or create `local.properties` with `sdk.dir=/absolute/path/to/android-sdk`.

```sh
./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
# HTML tests: core/build/reports/tests/test/ and app/build/reports/tests/testDebugUnitTest/
# Lint: app/build/reports/lint-results-debug.html
```

The wrapper pins Gradle 8.13. Build dependencies need network on the first build; the installed application does not. No API keys or secrets are required. If you use a corporate proxy, configure Gradle/JVM proxy and CA trust normally; no environment-specific proxy or SDK paths are included in this project.

Connected device tests (not part of the JVM suite):

```sh
./gradlew :app:connectedDebugAndroidTest
```

These exercise Android PDF preview rendering and region decoding on an actual Android runtime. They require an attached emulator/device. They are supplied separately because Robolectric does not implement the platform PdfRenderer faithfully.

Independent document checks, after JVM tests have generated artifacts:

```sh
python3 -m pip install pymupdf
python3 scripts/verify_pdf_artifacts.py
```

MuPDF is a **development-only** independent verifier, not an Android application dependency. See [validation evidence](docs/VALIDATION.md). A syntactically valid/openable PDF is not treated as proof of visual completeness.

## Project map

The [Android build workflow](https://github.com/atillahan00/studyfuse/actions/workflows/android.yml) runs tests, lint and APK builds on source pushes to `main`, pull requests and manual runs. Successful runs provide a debug APK artifact; test/lint reports are retained even when checks fail. Connected-device tests are compiled but require an actual device to execute. The directly downloadable APK above is the locally verified delivery build; future CI debug builds may use a different development signing key.

- `core/`: Android-independent format identification, export settings, coverage verifier, whitespace-aware pagination and randomized geometry tests.
- `app/.../data/`: Room entities/DAO, streaming SAF ingestion, original and replacement hashes, project persistence.
- `app/.../engine/`: adapter registry, PDF form transformations, EXIF-aware image decoding, UTF-8 text layout, OCR transcripts, assembly, verification, reports and measured-size volumes.
- `app/.../worker/`: cancellable foreground WorkManager job, progress, isolated generation directories and guarded publication.
- `app/.../ui/`: Turkish Compose home/project/export/settings UI and native PDF preview.
- `fixtures/`: synthetic long handwritten-style vector PDF and 200 known colour markers.
- `app/src/test/`: real PDF/image/text conversion tests, database/import tests, Compose interaction tests.
- `app/src/androidTest/`: tests requiring a real Android runtime.

See [architecture](docs/ARCHITECTURE.md), [known limitations](docs/KNOWN_LIMITATIONS.md), and [licenses](THIRD_PARTY_NOTICES.md). This is an evaluated debug deliverable; actual tablet/OEM, Android 15/16, handwriting OCR, and the fidelity of arbitrary Office exports must not be represented as universally certified.
