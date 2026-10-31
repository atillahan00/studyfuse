# Local AI startup recovery — 1.3.1

The 1.3 message **Yerel AI işlemi durdu; model/bellek/aygıt uyumluluğunu kontrol edin** means the separate native AI process disconnected. It does not identify a cause. A native library/driver crash, Android memory/process termination or another startup failure cannot be distinguished from the old message alone. A smaller download does not guarantee low peak runtime memory.

## Keep the download

1. Install the 1.3.1 APK over the existing app. Do not uninstall, clear app data or press the model-cache deletion button. The update retains the original development signing key.
2. Open **Ayarlar → Otomatik AI kurulumu**. Setup selects an existing catalog model download when present. Keep the smaller E2B selected if that is the file you downloaded.
3. Leave **Dil modeli için GPU'yu dene** off. This uses CPU language processing and the GPU image encoder. Completed files show **İndirilen modeli yeniden sına**; the same bytes are checked against the pinned hash without a new transfer. A partial file still needs its remaining download.
4. Wait for the actual image-input probe to complete. READY names the tested language backend. Opening the exam workshop or learning hub applies this device-tested backend to the project's settings; other exam settings are retained. A backend change invalidates incompatible cached study signatures rather than reusing results under false settings.
5. If it still fails, open **AI tanılamasını göster → Tanılamayı kopyala** and paste that diagnostic text into the support conversation. Opening the details refreshes the Android exit query. Do not download the larger model as a diagnostic experiment.

The optional GPU-language path tries CPU once after an exception. Cancellation propagates immediately; failed CPU probing never marks the model ready. Both paths still need GPU vision, so they cannot work around every vision-driver failure. The same 8192-token runtime configuration is used for probing and normal generation. CPU mode can be slower and still requires substantial RAM/storage. Per-model/backend compilation caches avoid sharing the previous generic cache; weights and source documents are kept.

## What diagnostics establish

Startup metadata records the child PID, stage, timestamp, language backend, whether an image was requested, available RAM and low-memory status. Stages distinguish engine creation/initialization from conversation creation and generation. A caught Java failure records only its class, not its message. The Android 11+ API may report LOW_MEMORY, CRASH_NATIVE, SIGNALED, ANR or another reason for the matching child process. Exit records can be delayed or unavailable. Android 10 cannot supply this API; the stage and memory snapshot remain available. No crash dump, stack contents, prompt, response, document title or document pixels are recorded or uploaded.

These data help narrow the failure; they do not identify a driver bug or prove model compatibility/handwriting accuracy. Inference stays in a separate private process so a native abort need not kill the interface. A bound-service priority hint reduces its background priority penalty but cannot prevent Android memory termination. The bundled LiteRT-LM version remains 0.16.1; no unverified runtime upgrade is represented as a cure. The [official Kotlin API](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.16.1/docs/api/kotlin/getting_started.md) documents CPU language processing with GPU vision.

The model download is private app data. Preserving it depends on updating in place and retaining app storage. If storage was cleared or the file fails its hash, setup must obtain a valid file again; the app does not pretend corrupt/missing bytes are usable.
