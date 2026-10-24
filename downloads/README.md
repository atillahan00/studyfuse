# StudyFuse downloads

- [StudyFuse-debug.apk](StudyFuse-debug.apk?raw=true): installable Android 10+ evaluation build. Contains the local printed-text OCR model.
- [StudyFuse-Android-Studio.zip](StudyFuse-Android-Studio.zip?raw=true): complete source project, Gradle wrapper, resources, fixtures, tests and documentation. SDKs, caches and signing keys are excluded.
- [StudyFuse-device-tests.apk](StudyFuse-device-tests.apk?raw=true): instrumentation tests for a connected Android device; the matching debug app must be installed first.
- [SHA256SUMS.txt](SHA256SUMS.txt): hashes of these download files.

The debug APK was built successfully and the 34 automated JVM/Robolectric tests passed. Physical-device tests are supplied but have not been run. See the [validation record](../docs/VALIDATION.md) and [known limitations](../docs/KNOWN_LIMITATIONS.md). Office and OneNote formats require source-app PDF conversion/replacement; OCR is printed-text only.

To run the optional instrumented tests with Android platform-tools:

```sh
adb install -r StudyFuse-debug.apk
adb install -r StudyFuse-device-tests.apk
adb shell am instrument -w org.studyfuse.test/androidx.test.runner.AndroidJUnitRunner
```
