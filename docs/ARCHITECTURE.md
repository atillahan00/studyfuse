# Architecture and invariants

```
SAF / Android share streams
    -> private immutable originals + SHA-256
    -> Room project/source records, user-controlled order and titles
    -> WorkManager generation snapshot
    -> signature / extension detection
    -> ConversionAdapter registry
    -> normalized per-source PDF in disk-backed scratch storage
    -> per-source validation or explicit warning PDF
    -> optional unverified printed-OCR transcript appendices
    -> deterministic contents, section dividers and source page ranges
    -> vector PDF assembly, bookmarks and reserved-border page numbers
    -> output verification, measured-size volume splitting
    -> JSON / text completeness reports
    -> atomic database publication of the successful generation
    -> user-controlled SAF save / FileProvider share
```

`StudyViewModel` owns UI actions, flows and WorkManager observation. Room keeps projects, ordered sources, active/original paths, original/replacement hashes, export configuration and last published artifacts. No remote document repository or inference backend exists. Version 1.3 has a separate explicit public-model download client. Workers guard updates with their work ID, so a cancelled old generation cannot publish over a newer export. The UI prevents source/settings edits during active work and import. Schema 2 adds manual page-cut JSON and removal timestamps; an explicit 1→2 migration retains existing records. WorkManager initializes on demand through the Application configuration provider.

File copying uses 64 KiB buffers, a temporary `.part` file, SHA-256 and an fsync before rename. Internal paths use UUIDs rather than provider-supplied names. A failed or cancelled import removes its incomplete copy. Completed imports survive import cancellation. Replaced Office originals remain immutable. Source removal is reversible: timestamps hide rows without deleting original/replacement bytes. Undo restores the latest group; project deletion removes all private copies. Portable ZIP backups retain originals, active PDFs, settings, page cuts and undo state; restore publishes a new project only after every declared entry has passed path/size/hash checks.

A conversion has a distinct report entry even if it fails. A failed adapter's partial PDF is discarded in favor of a clearly labelled failure page. Other files continue. `complete: true` means that every adapter completed without a fallback/failure/review status; it is not a claim of mathematically proven visual equivalence. The final report states that limitation.

## Vector PDF geometry

Each original page's contents and cloned resources become a form XObject. The source view is the union of MediaBox and CropBox; source origin, 0/90/180/270-degree rotation and UserUnit determine a display transform. Every generated page places that same vector form behind a rectangular clip using a uniform scale. Handwriting encoded as annotations is flattened through its appearance stream first; unsupported visible appearances cause an explicit per-file failure rather than disappearing.

The Android-independent pagination engine covers the entire displayed rectangle. Coverage is checked across all horizontal strips, catching gaps in either dimension. It reserves margins, supports original size, chooses A4 orientation automatically, seeks earlier whitespace boundaries and adds overlap. Median embedded text size can raise the scale to target approximately 8 pt, with auto-landscape considered before horizontal tiling. This cannot estimate the size of text in images or understand equations, tables, or diagram boundaries.

Integer image-region bounds round outward. The original decoded pixels are encoded losslessly by default; no resolution reduction is used to satisfy a volume limit. Large images use BitmapRegionDecoder where available, with EXIF transforms and memory checks. The original bytes are retained, including metadata not represented by a decoded bitmap.

## Verification and publication

Original and active hashes are checked before conversion. Each normalized PDF is reopened, page counts are compared, and supported pages are rendered at bounded thumbnail resolution. Unsupported image codecs or oversized embedded bitmaps are preserved encoded, skip raster verification, and produce an explicit REVIEW status. Coverage records identify original page, global output page, source bounds, source size and scale. The assembly has independent expected-page arithmetic; volume page ranges must cover it exactly. Output hashes and actual byte sizes appear in JSON.

Unit/integration tests go further: a synthetic 1000 × 18000-point handwritten-style PDF includes 200 known marker bands, Unicode text, equations and vector diagrams. Tests sample all bands in the actual output, verify final text and form XObjects, and independently re-render generated PDFs using MuPDF. These checks are practical evidence, not a proof for arbitrary third-party files.

The last generation publishes only after successful assembly/report writing and a guarded Room transaction. Scratch is deleted in a `finally` block. The worker removes unpublished failed generations and obsolete generations after publishing a new one. SHA-verified per-source PDF/metadata checkpoints persist outside generation scratch. Their keys include source identity/content, converter version, layout/compression/OCR options and manual cuts, excluding section names, volume size and export dividers. Completed sources are reused after interruption; partial work within one source restarts. Processing is incremental by source/page, but PDFBox still retains document/page/resource metadata. Cancellation is cooperative between I/O/conversion/render operations, not an instant interruption of every native call.

## Extending formats

Implement `ConversionAdapter`, write a PDF atomically, populate `FileResult`, and add the adapter to `Compiler`'s registry. A real importer must handle drawings, positioned content, embedded resources and failure cases. Text-only DOCX extraction or a thumbnail of a PowerPoint file is not accepted as a high-fidelity conversion. The explicit external PDF route remains active until an adapter passes fidelity tests.

## AI companion and document viewer

The companion extractor evaluates PDF glyph positions in the normalized page coordinate system, admits glyph centers only within the actual visible clip, and de-duplicates intentional overlap using original-page coordinates. The PDF remains vector-based and may still expose clipped operators to other extractors. Per-page text/provenance is written to an offline search index; OCR entries are separately marked unverified. A German study prompt is generated locally with page/source references and can be edited or copied.

The document viewer renders a screen-sized viewport with scale/translation passed to Android PdfRenderer. Zoom does not require a full-height long-page bitmap. Image viewports use EXIF-aware region decoding. Thumbnail grids render bounded cells; search uses the companion index for generated outputs and bounded glyph extraction for other PDFs.

## Local visual AI and exam preparation

Version 1.3 adds `ModelDownloader`/`ModelCatalog` and `ModelSetupWorker` for streamed, pinned HTTPS model installation. `StudyTools`, `StudySearch`, `CoachWorker` and `StudyHub` implement bounded evidence retrieval, local source-cited requests, card persistence, timed mocks and daily planning. A shared inference mutex prevents setup testing and study inference from competing; a separate transfer mutex serializes resumed downloads. Coach/deck records retain the existing source/config signature boundary. Detailed behavior and limitations are in [AUTOMATIC_STUDY.md](AUTOMATIC_STUDY.md).

`learning/ModelStore` streams and hashes imported weights; `LocalAiClient` binds to the non-exported `:local_ai` `LocalAiService`. Only that service loads the actual LiteRT-LM native engine. The boundary reports disconnection/native failures without depending on the UI process surviving a native abort. No network/tool executor is exposed. A randomized image-code capability test is keyed to model SHA, backend and Android fingerprint.

`ExamWorker` guards the project with its work ID and shares the global model-execution mutex with setup. `ExamCompiler` enumerates actual visual source pages from the PDF report, renders bounded whole/overlapping region inputs, checks output PDF hashes, and requests structured model output. `ExamResponse` validates structure, source-policy labels and exact supporting excerpts, preserving uncertainties/rejections. These are attribution/format checks, not semantic accuracy proofs. Completed unit JSON checkpoints are written atomically; cancelled or failed units can resume. Source/config/model signatures isolate enriched and document-only results.

`ExamStore` retains settings, session coverage, unit readings and separate student review/correction/history. Questions require review before entering the interleaved due queue. The core scheduler provides transparent expanding intervals and self-assessment grades. `ExamExport` puts questions before answer keys and appends original vector PDFs as a visual appendix. Backup schema 2 includes declared study JSON entries with hashes, bounded sizes and strict paths; schema 1 remains readable. Details and limitations are in [LOCAL_AI.md](LOCAL_AI.md).
