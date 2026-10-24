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

`StudyViewModel` owns UI actions, flows and WorkManager observation. Room keeps projects, ordered sources, active/original paths, original/replacement hashes, export configuration and last published artifacts. No network repository or backend exists. Workers guard updates with their work ID, so a cancelled old generation cannot publish over a newer export. The UI prevents source/settings edits during active work and import.

File copying uses 64 KiB buffers, a temporary `.part` file, SHA-256 and an fsync before rename. Internal paths use UUIDs rather than provider-supplied names. A failed or cancelled import removes its incomplete copy. Completed imports survive import cancellation. Replaced Office originals remain immutable. Removing a source or project removes the corresponding private copies; exported copies elsewhere are user-owned.

A conversion has a distinct report entry even if it fails. A failed adapter's partial PDF is discarded in favor of a clearly labelled failure page. Other files continue. `complete: true` means that every adapter completed without a fallback/failure/review status; it is not a claim of mathematically proven visual equivalence. The final report states that limitation.

## Vector PDF geometry

Each original page's contents and cloned resources become a form XObject. The source view is the union of MediaBox and CropBox; source origin, 0/90/180/270-degree rotation and UserUnit determine a display transform. Every generated page places that same vector form behind a rectangular clip using a uniform scale. Handwriting encoded as annotations is flattened through its appearance stream first; unsupported visible appearances cause an explicit per-file failure rather than disappearing.

The Android-independent pagination engine covers the entire displayed rectangle. Coverage is checked across all horizontal strips, catching gaps in either dimension. It reserves margins, supports original size, chooses A4 orientation automatically, seeks earlier whitespace boundaries and adds overlap. Median embedded text size can raise the scale to target approximately 8 pt, with auto-landscape considered before horizontal tiling. This cannot estimate the size of text in images or understand equations, tables, or diagram boundaries.

Integer image-region bounds round outward. The original decoded pixels are encoded losslessly by default; no resolution reduction is used to satisfy a volume limit. Large images use BitmapRegionDecoder where available, with EXIF transforms and memory checks. The original bytes are retained, including metadata not represented by a decoded bitmap.

## Verification and publication

Original and active hashes are checked before conversion. Each normalized PDF is reopened, page counts are compared, and supported pages are rendered at bounded thumbnail resolution. Unsupported image codecs or oversized embedded bitmaps are preserved encoded, skip raster verification, and produce an explicit REVIEW status. Coverage records identify original page, global output page, source bounds, source size and scale. The assembly has independent expected-page arithmetic; volume page ranges must cover it exactly. Output hashes and actual byte sizes appear in JSON.

Unit/integration tests go further: a synthetic 1000 × 18000-point handwritten-style PDF includes 200 known marker bands, Unicode text, equations and vector diagrams. Tests sample all bands in the actual output, verify final text and form XObjects, and independently re-render generated PDFs using MuPDF. These checks are practical evidence, not a proof for arbitrary third-party files.

The last generation publishes only after successful assembly/report writing and a guarded Room transaction. Scratch is deleted in a `finally` block. The worker removes unpublished failed generations and obsolete generations after publishing a new one. WorkManager restarts interrupted work from the originals; conversion checkpoints within a source are not implemented. Processing is incremental by source/page, but PDFBox still retains document/page/resource metadata. Cancellation is cooperative between I/O/conversion/render operations, not an instant interruption of every native call.

## Extending formats

Implement `ConversionAdapter`, write a PDF atomically, populate `FileResult`, and add the adapter to `Compiler`'s registry. A real importer must handle drawings, positioned content, embedded resources and failure cases. Text-only DOCX extraction or a thumbnail of a PowerPoint file is not accepted as a high-fidelity conversion. The explicit external PDF route remains active until an adapter passes fidelity tests.
