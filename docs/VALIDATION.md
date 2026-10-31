# StudyFuse 1.3.2 validation

Source: `0c41ffd92fc84909bee007fb6f34bf823104f25e`. Runtime remains LiteRT-LM 0.16.1; the production app now avoids its broken Flow completion adapter.

## Reproduced defect and correction

Inspection of the actual published SDK class `Conversation$sendMessageAsync$1$1` shows an interface-static call to `SendChannel.close$default` in `onDone`. The cached 1.10.1/1.10.2 coroutine interfaces lack that method. The regression test instantiates the actual SDK callback with a ProducerScope proxy and observes **NoSuchMethodError**, without model weights, GPU inference or substituted SDK bytecode. The production callback API plus app-compiled deferred completion avoids that SDK Flow class. Bytecode and tests are in `validation-evidence/sdk-completion-bytecode.txt` and `TEST-org.studyfuse.engine.CallbackGenerationTest.xml`.

The user's 1.3.1 screenshot reports CPU language/GPU vision, VISION_GENERATION and Android CRASH, matching this backend-independent completion failure. It does not contain the actual exception, so the physical tablet's precise failure is still an inference. Prior tests covered native startup/failure isolation and controlled inference adapters, but missed successful SDK completion; their passing results were not proof of usable model inference.

The bridge preserves received Unicode chunks within a 90,000-character bound, reports oversize output explicitly, handles threaded completion/errors, propagates registration failures, cancels on timeout/failure and ignores duplicate/late callbacks. Uncaught child errors now retain only the error class and up to 12 filtered class/method/line entries, with no exception messages, source paths, prompts, responses or raw crash dump. Android's existing crash handler still receives the exception.

## Local checks

The full suite passed **114 JVM/Robolectric tests** (34 core, 80 Android JVM), with zero failures, errors or skips. Lint: **0 errors, 8 warnings**. Both the ARM64 app and connected-test APK built successfully using JDK 21, Gradle 8.13, SDK 36 and Build Tools 35.0.0. An initial new assertion incorrectly required exception object identity across coroutine stack recovery; the corrected check verifies the error type/message and cancellation, and the complete suite passes.

Independent PyMuPDF checks passed for all 200 tall-page bands including the last, mixed source colours, 29 report/output pages and retained JPX data. XML/build logs and actual SDK bytecode are delivered under `validation-evidence/`.

## Hosted checks

[Run 41216232004](https://github.com/atillahan00/studyfuse/actions/runs/41216232004) validates the source above. The build/tests/lint/APK and public-model metadata/range jobs passed. The Android 15 / API 35 x86_64 tablet-emulator job **passed all seven connected tests**; all three workflow jobs completed successfully. Logs show Starting 7 tests, Finished 7 tests and BUILD SUCCESSFUL. The SDK completion ABI failure and successful app bridge were observed on Android as well as the JVM. The new connected test invokes the published SDK callback to reproduce the missing-method error on Android and then completes through the production app bridge. It does not load neural weights.

## Delivered APK

Version **1.3.2 / versionCode 6**, package `org.studyfuse`, min API 29, target API 36, ARM64. Size: **86,032,921 bytes** (about 82 MiB). Signature and 16 KiB ZIP alignment passed; every packaged native library matches the previously verified SHA/ELF alignment. Certificate SHA-256 is unchanged:

```
1bc145b386fae06945b5b41c38ec2ce29a4c26f97001f29b69308fc8f661c24b
```

Install over the existing direct-download app to retain private projects and downloaded models. Native library bytes, catalog weights and source-document conversion remain unchanged. No private key or model weights are included. The public APK was downloaded again and matched the delivered size and SHA-256: `726106464de8ee16459b6ec001b7cf751fef10dbb7f474ea68f999a795adb802`. See `public-apk-download.json`. The complete source ZIP is separately checksummed in downloads/SHA256SUMS.txt.

## Limits

No physical MagicPad 2 or full vision model is available locally. The actual SDK completion ABI failure is reproduced and the replacement is tested; these facts do not certify every driver, native runtime, handwriting interpretation or educational answer. Re-test the preserved model on the tablet. Failed image probes never mark a model ready, and CPU language mode still requires GPU vision. New fatal diagnostics supplement Android exit records when a different uncaught error occurs. Source review remains necessary.

See [troubleshooting](AI_TROUBLESHOOTING.md), [known limitations](KNOWN_LIMITATIONS.md), and the historical [1.3.1 record](VALIDATION-1.3.1.md).
