# Dependencies and licensing

The application source written for StudyFuse is offered under Apache-2.0 (see LICENSE). Dependencies retain their own licenses. No copyleft document renderer is linked into the application.

| Component | Pinned version | License / compatibility decision |
|---|---|---|
| Android Gradle Plugin | 8.10.1 | Apache-2.0 tooling; compile/target 36, Gradle 8.13 |
| Kotlin / Compose compiler | 2.1.20 | Apache-2.0 |
| Compose BOM | 2025.04.01 | AndroidX Apache-2.0; UI/material3 1.8-era dependencies resolved by BOM |
| Activity | 1.10.1 | AndroidX Apache-2.0 |
| Lifecycle | 2.9.0 | AndroidX Apache-2.0 |
| Room | 2.7.1 | AndroidX Apache-2.0; KSP 2.1.20-1.0.32 |
| WorkManager | 2.10.1 | AndroidX Apache-2.0; foreground service type dataSync |
| DocumentFile | 1.0.1 | AndroidX Apache-2.0 |
| ExifInterface | 1.4.1 | AndroidX Apache-2.0 |
| PDFBox Android | 2.0.27.0 | Apache-2.0; port avoids desktop AWT. Published POM and source inspected; min SDK compatible with API 29. License and NOTICE, including bundled font notices, are included in APK assets. |
| Bouncy Castle PDFBox transitive components | 1.72 | Bouncy Castle permissive license; full notices in assets. Application does not offer encrypted-PDF import or TLS networking. Lint can flag unused TLS helper trust managers from this dependency. |
| ML Kit text recognition, bundled Latin | 16.0.1 | **ML Kit Terms of Service, not an open-source OCR engine.** Included as a free local printed-text OCR option with the model packaged in the APK, no mandatory cloud API/account/payment. Published POM explicitly names the ML Kit terms. Google Play services and ML Kit transitive artifacts retain their applicable SDK terms. |
| Kotlin coroutines / Play-services task bridge | 1.10.1 | Apache-2.0 |
| Noto Sans, Noto Sans Math | bundled TTF assets | SIL Open Font License 1.1. Exact font project license and notices included in assets. |
| Selected Material icons | sources from AndroidX 1.7.8 | Apache-2.0. Sixteen selected generated Kotlin files retain original copyright/license headers. Vendoring only used icons avoids packaging all thousands of extended icons in a debug APK. |
| JUnit, Robolectric, AndroidX test | test-only | EPL-1.0 / Apache-2.0 according to component; not shipped in main APK |

References inspected:

- https://repo.maven.apache.org/maven2/com/tom-roush/pdfbox-android/2.0.27.0/pdfbox-android-2.0.27.0.pom
- https://github.com/TomRoush/PdfBox-Android (README, published 2.0.27.0 source archive, LICENSE.txt, NOTICE.txt)
- https://dl.google.com/dl/android/maven2/com/google/mlkit/text-recognition/16.0.1/text-recognition-16.0.1.pom
- https://developers.google.com/ml-kit/terms
- https://developers.google.com/ml-kit/vision/text-recognition/v2/android
- https://github.com/notofonts/noto-fonts (hinted TTF fonts and OFL)
- https://dl.google.com/dl/android/maven2/androidx/compose/material/material-icons-extended-android/1.7.8/material-icons-extended-android-1.7.8-sources.jar

The build resolves the actual runtime dependency graph through Google Maven and Maven Central. Build-time downloads are not application cloud processing. Transitive component notices are included under `app/src/main/assets/licenses/`. Native-library 16 KiB alignment and APK alignment are checked in the delivery validation, not assumed merely from the target SDK. Compatibility of the dependency graph was verified by the recorded builds/tests; no claim is made of a full legal audit or physical-device certification.
