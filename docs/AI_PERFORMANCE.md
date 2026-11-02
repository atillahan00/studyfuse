# AI page-processing performance — 1.3.4

The user's report is about **one region per page taking roughly a minute even with GPU language acceleration on**. Removing the other four regions cannot solve that alone. This build reduces unnecessary input/output work and makes the remaining cost measurable. It does not claim cloud-like speed on an offline tablet or a measured speedup on the physical MagicPad 2.

## Profiles

Open **Sınav atölyesi → İşlem süresi ve ayrıntı**:

- **Hızlı kaynak okuma:** direct PDF text on eligible text-only pages, otherwise one full-page image, faithful reading and visual observations first, no new exercise items, Gemma 4 visual budget 280. After reading, use **Okumadan çalışma kartları üret** or **AI öğrenme merkezi → Koç → Yeni çalışma kartları** to prepare selected topics from the source corpus. This is an initial reading, not a complete examination practice pack.
- **Dengeli çalışma:** one full-page view, normal study items, visual budget 560.
- **Tam görsel ayrıntı:** all five overlapping views per page, normal study items, budget 1120.

The settings can be adjusted independently. Keep high visual detail and enable reading-first if fine handwriting is essential. **Model ayarı** leaves the runtime's visual budget unchanged. The budget API is Gemma-4-specific; other manually imported model families may ignore it. It is applied through the pinned SDK's documented experimental [visualTokenBudget API](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.16.1/kotlin/java/com/google/ai/edge/litertlm/ExperimentalFlags.kt) (280/560/1120), not by discarding source images. Lower budgets can miss small text or diagram labels. The original PDF remains available and unchanged; examine it and add close-ups where needed.

With all-page detailed reading off, **Yakın plan eklenecek derlenmiş PDF sayfaları** accepts ranges such as `3,5-7`. These are the printed page numbers in the compiled PDF, not original-source page numbers. TOC, section-divider, excluded-reference and unavailable source pages are rejected rather than silently ignored. Ranges are represented as intervals without allocating every selected page number.

In reading-first mode the direct-text switch is enabled by default, including existing reading-first settings. It applies only to pages without all-page detail or selected close-ups. A conservative PDF paint traversal flags images, painted paths, annotations, shading, soft masks, unusual glyph/rendering states and unknown/failed operations. Clip-only paths from pagination are allowed. Any flag, insufficient text or ambiguous extraction keeps visual processing. The original page is always preserved. Direct text is labelled `native_pdf`, produces no invented exercises or translation, and is available to the coach. Formatting and extraction order still need source comparison. Disable the switch for full visual inspection.

A visual page-reading request still receives the image. For PDF text that can be extracted, the app stores the exact extracted text and asks the model only for extra visible writing/relationships, avoiding a redundant text echo. Handwritten/image-only pages still need visual transcription. PDF extraction itself may have ordering/glyph errors, so source-image inspection and uncertainty labels remain necessary. Full raw model responses remain in the existing private study records; source text is not silently truncated to speed inference.

## Work reuse

The displayed plan gives an upper bound on image requests before per-page inspection: one per source page plus four per selected close-up page, or five everywhere in all-page detailed mode. Eligible direct-text pages and previously completed compatible checkpoints avoid a new request. Coverage records show direct-text region counts and actual new inference calls. A direct-text checkpoint cannot satisfy a full-visual or selected-close-up request; a richer visual checkpoint may satisfy a direct-text reading request. Reading keys ignore the view plan and execution backend while preserving source/model/policy/language/focus/date/reference and quality/mode distinctions. Old single-view and detailed checkpoint keys are checked so reviewed item IDs survive reuse. New quality/mode combinations may need generation; the app does not pass lower-detail records off as higher-detail readings. Selected sources are still all represented in the plan/coverage manifest.

One PDFBox document and one Android native PDF parser are held at a time for an output volume. Cache hits do not open/render a PDF. Cancellation closes both parsers and deletes temporary region images. Each model/file integrity check is retained. No model re-download is required for these settings.

## Measurements

The worker still reuses its model engine throughout a batch; the first request may include model loading. Numeric measurements distinguish:

- per-region extraction/opening, rendering/PNG encoding and complete inference-call time;
- actual engine initialization, time to first nonempty text chunk, generation duration and output-character count;
- live stage/elapsed/output-character counters while processing.

Characters are UTF-16 string-length units, **not model tokens or correctness scores**. Observer failures cannot break generation, and telemetry contains no response text, prompt, source title or image. The numeric live snapshot is sampled locally; no measurement is uploaded. The last finished numeric timing is retained separately when a subsequent request starts. Finished request timing can be copied through **AI tanılamasını göster**. The UI does not re-read every checkpoint file for each progress event; **Tamamlanan okumaları yenile** refreshes partial readings explicitly.

Compare one representative page using the same cached E2B model/backend. If first-response latency dominates, image processing/prefill is the main cost; if generation after the first chunk dominates, reading-first and less redundant output may help more. Loading/thermal/memory state can change results. There is no promised multiplier, and scanned handwriting still requires expensive neural processing.

## Private sample check

Two supplied PDFs contain 35 + 1 original pages. Normal pagination splits the dense writing-plan source into two readable pages: 37 compiled study pages. Inspection of the compiled content chooses 34 direct-text pages and three visual requests (cover plus two sections with vector marks). Each direct-text checkpoint was compared verbatim with that compiled page’s extraction. This host check uses real Android-compatible PDF parsing/classification and controlled raster/inference adapters; it does not measure actual model execution or prove visual accuracy. The PDF inputs, images, names and extracted text stay in ignored private workspace storage. No seconds-per-page promise follows from host timings.
