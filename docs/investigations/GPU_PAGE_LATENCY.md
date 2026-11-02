# GPU page latency investigation: 1.3.4

The physical HONOR user reports more than one minute per page with one region, GPU language/vision, reading-first and the 280 visual-token budget. The screenshot shows active generation (1016 characters at 14 seconds). A later copied diagnostic captured VISION_GENERATION with 2330 MiB available, low-memory false, and no matching process exit. These snapshots do not give a completed request's initialization/first-chunk/decoding breakdown or demonstrate a crash.

Two concrete issues were corrected on this branch:

- Reading-first's output example still showed a populated practice-item object despite an earlier instruction to return no items. It now demonstrates only `items: []` and excludes exercise schema/instructions in that mode. This can improve instruction consistency; no model throughput improvement is measured.
- The next request overwrote completed timing metadata immediately. A separate numeric-only last-completed record now persists backend, visual budget, completion time and timing fields across subsequent requests. Diagnostics distinguish this completed measurement from the current stage. No prompt, output text, document title or image is stored in that record.

The supplied private samples subsequently established a concrete reason to avoid unnecessary VLM requests: almost every poetry page has native selectable text and no visual paint. The new reading-first route extracts such pages directly, preserving the extracted string verbatim. Nested forms, painted vectors/images, annotations, shading, unusual text, soft masks and unsupported/failed inspection retain visual inference. Text-only caches cannot satisfy explicit full-visual requests.

Private host verification found 36 original pages, 37 compiled source pages, 34 direct-text readings and 3 controlled visual requests. All 34 native readings matched their compiled page text verbatim. Inputs/extracted text and the private test harness are not published. Controlled raster/inference adapters are not neural speed, rendering-fidelity or handwriting-accuracy evidence. See the current [validation record](../VALIDATION.md) for reproducible synthetic tests and build results.
