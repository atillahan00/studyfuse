# StudyFuse 1.3.1 validation

Source: `d3cebfc105010e90759ead46b7ce51586707fb9b`. Runtime remains LiteRT-LM 0.16.1. This patch addresses recovery and diagnostics after the reported native-process stop; it does not establish that the user's original device crash is cured.

## Local checks

The complete core/JVM/Robolectric suite passed **105 tests**, with zero failures, errors or skips (34 core, 71 Android JVM tests). Lint: **0 errors, 8 warnings**. The ARM64 application and connected-test APKs built successfully with JDK 21, Gradle 8.13, SDK 36 and Build Tools 35.0.0. XML and build logs are in `validation-evidence/`.

New checks exercise GPU→CPU recovery, CPU failure, both-backend failure, cancellation without a new attempt, unchanged downloaded bytes, privacy-limited startup fields, Android 10's lack of exit-info support, model/device-bound backend selection, and actual cached-choice/CPU-default/diagnostic-copy controls in Compose. Backend recovery uses controlled failure adapters, not full weight loading or a handwriting benchmark. The initial new UI check incorrectly attempted to scroll a nonscrolling settings dialog; the corrected full suite passes.

Independent PyMuPDF checks passed for mixed source colours, all 200 tall-page bands including the final band, 29 report/output pages and retained JPX data. Existing document preservation and source-linked study behavior remain covered.

## Hosted Android checks

[Run 41207594062](https://github.com/atillahan00/studyfuse/actions/runs/41207594062) validates the source above. All three hosted jobs **passed**: build/test/lint/APKs, pinned model metadata/range verification, and **six connected Android 15 / API 35 tablet-emulator tests**. Logs show Starting 6 tests, Finished 6 tests and BUILD SUCCESSFUL. See `github-build.txt`, `model-catalog.txt` and `android-runtime.txt` in the evidence directory. The connected suite also asserts that native-startup diagnostics were recorded in the separate child PID, with no prompt/response fields. This checks failure isolation and reporting, not full-model compatibility on the physical MagicPad 2.

## Delivered APK

Version **1.3.1 / versionCode 5**, package `org.studyfuse`, min API 29, target API 36, ARM64. Size: **86,032,921 bytes** (about 82 MiB). APK signature and 16 KiB ZIP/ELF alignment passed. Certificate SHA-256 is unchanged:

```
1bc145b386fae06945b5b41c38ec2ce29a4c26f97001f29b69308fc8f661c24b
```

Updating in place preserves private projects and downloaded weights; uninstalling or clearing app storage does not. The native libraries remain identical to 1.3, and no model weights/private key are shipped. Internet/network-state permissions are for explicit model transfer; broad storage permissions remain absent. The public APK was downloaded again and matched the delivered size and SHA-256: `41656aec53874805ef08447bbcbb98584d862ea58b2229157fc7eba632979c26`; see `public-apk-download.json`. The source ZIP is separately checksummed in downloads/SHA256SUMS.txt.

## Limits

The user's screenshot establishes that the old separate AI process disconnected; it does not distinguish native crash, memory termination or another exit. No physical MagicPad 2 or complete model is available here. Android process-exit reasons can be delayed/unavailable, and Android 10 has no historical-exit API. CPU language mode still requires GPU vision, and a successful randomized image probe is not proof of handwriting/math correctness or exam accuracy. Source review remains necessary. The patch retains 0.16.1 rather than claiming that an untested SDK upgrade fixes an unidentified failure.

See [recovery steps](AI_TROUBLESHOOTING.md), [known limitations](KNOWN_LIMITATIONS.md), and the historical [1.3 record](VALIDATION-1.3.md).
