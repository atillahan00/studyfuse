# Unreleased investigation: GPU page latency after 1.3.3

The physical HONOR user reports more than one minute per page with one region, GPU language/vision, reading-first and the 280 visual-token budget. The screenshot shows active generation (1016 characters at 14 seconds). A later copied diagnostic captured VISION_GENERATION with 2330 MiB available, low-memory false, and no matching process exit. These snapshots do not give a completed request's initialization/first-chunk/decoding breakdown or demonstrate a crash.

Two concrete issues were corrected on this branch:

- Reading-first's output example still showed a populated practice-item object despite an earlier instruction to return no items. It now demonstrates only `items: []` and excludes exercise schema/instructions in that mode. This can improve instruction consistency; no model throughput improvement is measured.
- The next request overwrote completed timing metadata immediately. A separate numeric-only last-completed record now persists backend, visual budget, completion time and timing fields across subsequent requests. Diagnostics distinguish this completed measurement from the current stage. No prompt, output text, document title or image is stored in that record.

Focused validation: 15 JVM/Robolectric tests passed across AiStartupTest and ExamPerformanceTest, including new prompt-schema separation and timing retention during the next request. This branch has not produced or published a new APK, has not run a physical model benchmark, and does not replace the released 1.3.3 build. Do not present these checks as a speedup.

Next required input is a representative original PDF page (or PDF) to check selectable/native text, raster/scanned content, writing/graphics and whether extraction can avoid full VLM transcription. No user document should be committed as a public fixture. Printed OCR can provide unverified hints; it must not be represented as reliable handwriting recognition or silently substitute for visual coverage. GPU-label/config confirmation alone cannot prove where wall time is spent.
