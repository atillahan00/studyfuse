# StudyFuse 1.3.4 validation

## Local checks

**133 JVM/Robolectric tests passed** (38 core, 95 Android JVM), zero failures/errors/skips. Lint: **0 errors, 8 warnings**. Both ARM64 application and instrumentation APKs built with JDK 21, Gradle 8.13, SDK 36 and Build Tools 35.0.0.

Seven new direct-text tests cover Unicode/clip-only streams, images and painted vectors beside text, annotations/invisible text/unknown operators, nested compiled forms, verbatim extraction, cache reuse, full-visual overrides, close-ups and cancellation. Routing tests use deterministic raster/inference adapters; they do not claim native rendering fidelity or neural accuracy. Existing tests also cover the corrected reading-first schema and retention of completed numeric inference timing while the next request runs.

Independent MuPDF checks passed for all 200 tall-page markers, 29 report/output pages, mixed source colours and retained JPX imagery.

## Private source verification

Two user-supplied PDFs were inspected locally, including rendered representative pages. The Android-compatible PDF parser/paint traversal found 35 original pages in one source (34 text-only) and one page with vector marks in the other. Existing pagination turns these into **37 source study pages**: **34 direct-text units and 3 visual requests**. Each of the 34 direct-text checkpoint strings matched its compiled page extraction verbatim. This is real parsing/routing with controlled raster/inference adapters, not actual model execution. Inputs, extracted text, titles and the private harness are excluded from GitHub/source ZIP.

Independent MuPDF source/output render comparisons examined all 37 mapped regions using a coarse 12 × 16 grid of ink occupancy. No source ink cell above the check threshold disappeared; maximum cell occupancy difference was 0.0168. Representative output pages were also inspected visually. This is a practical completeness check with subpixel tolerance, not proof that every glyph is semantically correct.

## Hosted checks

Updated source is awaiting GitHub Actions build/model-catalog/native Android 15 / API 35 tablet-emulator checks. The added native test uses real Android parsing/rendering and a deterministic inference adapter to verify that plain text skips inference while a drawn line retains it, full-visual override is honored, Unicode remains readable and original input bytes remain unchanged. It is not a neural speed/accuracy benchmark.

## APK and limits

Version **1.3.4 / code 8**, `org.studyfuse`, ARM64, min API 29, target API 36. Signature and 16 KiB ZIP alignment pass. The development certificate remains `1bc145b386fae06945b5b41c38ec2ce29a4c26f97001f29b69308fc8f661c24b`; update in place to keep projects and cached weights. No SDK/model/permission change or model re-download is required.

Text-first applies to reading-first pages without detailed/selected close-ups. Ambiguous extraction or graphics retains vision; disabling the switch requests full visual reading. Text ordering, glyph mapping and formatting still need source review. Scans/handwriting remain expensive model work. No physical HONOR latency, thermal or accuracy benchmark was possible here. See [performance workflow](AI_PERFORMANCE.md), [limitations](KNOWN_LIMITATIONS.md) and the [previous validation record](VALIDATION-1.3.3.md).
