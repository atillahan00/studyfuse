# Format support matrix

| Inputs | Implemented behavior | Fidelity / limitations |
|---|---|---|
| PDF | Native PDFBox Android adapter; vector forms, images, text, page rotations, nonzero origins, UserUnit; annotation appearance flattening | Retains visual content in the union of MediaBox/CropBox. Readability normalization can tile both axes. Unrepresentable visible annotations fail explicitly. Forms become static; attachments/layers trigger review. Encrypted PDFs require an unencrypted export. |
| PNG, JPEG/JPG, WEBP | Native Android decoding, EXIF orientation/mirroring, lossless PDF embedding or selected high-quality JPEG | Original pixel resolution, bounded regions for long canvases. Colour management is Android's decoded bitmap interpretation; archival originals stay unchanged. |
| HEIC/HEIF, BMP | Same adapter when Android can decode them | Device/codec-dependent. If regional decoding is unavailable, a guarded whole-image decode is attempted. A memory budget failure requests a PDF export; no silent downsampling. |
| TXT, Markdown, CSV | Strict UTF-8, embedded Unicode text fonts, streamed reflow | Markdown punctuation and CSV delimiters remain literal source text, not styled Markdown or a spreadsheet. Other encodings/unknown font glyphs fail explicitly. German, Turkish and common mathematical symbols are tested. |
| DOCX, DOC, RTF, ODT | Original retained; dedicated format route to explicit external-PDF fallback | Word/LibreOffice PDF export and replacement. No text-only extraction masquerading as a faithful conversion. |
| PPTX, PPT, ODP | Original retained; presentation PDF fallback | Export every slide, and notes separately if needed. Animations/video cannot become a static PDF. |
| XLSX, XLS, ODS | Original retained; spreadsheet PDF fallback | Export every sheet, include hidden data/notes intentionally, verify print areas and columns. |
| ONE, ONEPKG, ONETOC2 | Original retained; OneNote PDF/image export fallback | No local .one renderer, Graph sign-in, private API or authentication workaround. |
| HTML, HTM, MHTML/MHT | Explicit external-PDF fallback | Browser print/export including local assets. Remote content is not fetched by StudyFuse. |
| SVG | Explicit external vector-PDF fallback | No raster-only approximation of scripts, filters, fonts or embedded objects. |
| EPUB | Explicit reader-PDF fallback | No DRM circumvention. Reader printing/export may be restricted. |
| TIFF/TIF | Explicit multipage-PDF fallback | Android's common bitmap APIs are not a reliable all-frame TIFF importer. Never silently import only the first frame. |
| Unknown / malformed | Original retained; explicit failure/warning page | Batch continues where safe; report includes every stored source. |

Each detected format is routed through `ConversionAdapter`; unsupported groups have an `ExternalPdfAdapter` instance with format-specific instructions. Add a tested adapter to the registry to extend native support. Magic signatures take precedence for PDF/PNG/JPEG/WEBP; declared extensions identify formats needing an external converter. A claimed extension does not bypass structural validation.
