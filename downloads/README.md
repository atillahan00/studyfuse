# StudyFuse downloads

- [StudyFuse-debug.apk](StudyFuse-debug.apk?raw=true): ARM64 Android 10+ evaluation build (version 1.2.0), suitable for HONOR MagicPad 2. Contains bundled printed-text OCR and the local AI runtime. Vision-language model weights must be imported separately.
- [StudyFuse-Android-Studio.zip](StudyFuse-Android-Studio.zip?raw=true): complete source project, Gradle wrapper, resources, fixtures, tests and documentation. SDKs, caches and signing keys are excluded.
- [StudyFuse-device-tests.apk](StudyFuse-device-tests.apk?raw=true): instrumentation tests for a connected Android device; the matching debug app must be installed first.
- [SHA256SUMS.txt](SHA256SUMS.txt): hashes of these download files.

The build runs JVM/Robolectric tests and lint; a separate GitHub API 35 job runs connected Android checks. See the [validation record](../docs/VALIDATION.md) for the actual tested commit/results. Physical MagicPad 2/model handwriting benchmarks remain separate. See [model setup](../docs/LOCAL_AI.md) and [known limitations](../docs/KNOWN_LIMITATIONS.md). Office/OneNote require source-app PDF replacement; ML Kit OCR is printed-text only, while local model interpretations remain unverified AI drafts. CI's general APK additionally supports x86_64 and may use a different debug key.

To run the optional instrumented tests with Android platform-tools:

```sh
adb install -r StudyFuse-debug.apk
adb install -r StudyFuse-device-tests.apk
adb shell am instrument -w org.studyfuse.test/androidx.test.runner.AndroidJUnitRunner
```
