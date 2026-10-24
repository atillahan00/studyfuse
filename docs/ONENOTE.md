# OneNote feasibility and supported route

Research checked on 24 October 2026 against the official Microsoft Graph documentation repository and the PDFBox Android published sources/POM. Research is not a claim that an authenticated Microsoft tenant was tested.

## What is implemented

Android shares of actual PDF/image document streams are accepted via ACTION_SEND and ACTION_SEND_MULTIPLE. A web/notebook link alone is not a local document and produces a clear message. Android does not grant one application direct access to another application's private notebook storage.

PDF and image exports are the supported visual-preservation route. A tall page is tiled into A4 pages using a coordinate-coverage check and configurable overlap. PDF content is copied into form XObjects and transformed/clipped without rasterizing the exported PDF. The MediaBox/CropBox union prevents accidentally narrowing an already larger media canvas. Region decoding avoids materializing entire tall screenshots when supported.

StudyFuse retains `.one`/`.onepkg` files but does not render them. **PDF ile değiştir** keeps the original and replaces the active representation at the same place in the project. A replacement is not proof of complete export; its provenance and separate hash appear in the report.

## Official options and boundaries

- The OneNote desktop application can export/print a page or section to PDF, depending on version/platform. Use the full note, inspect far-right/far-bottom content, and compare the source before importing. Page/section/notebook and web export availability differ by OneNote edition and account. This app does not promise an Android OneNote export menu that may not exist.
- Graph has an official `/me/onenote/pages/{id}/content` endpoint returning page HTML, with resource URLs for images/files. It requires a valid bearer token and delegated Notes permissions, account/tenant consent, registered application identity, and network access. HTML plus resources must not be assumed to be a faithful handwriting/canvas renderer. It is not a direct local `.one` conversion endpoint.
- A complete Graph integration would require OAuth, resource traversal, absolute-position/canvas layout handling, tenant testing and independent ink/fidelity validation. No client ID is fabricated, no authentication is bypassed, and no private APIs are used. Graph is not implemented or advertised as an active button.
- A desktop PDF export, or sequential full-resolution overlapping images where necessary, is the reliable implemented fallback. If the source export is already cropped, StudyFuse cannot reconstruct the missing material.

## References

- [Microsoft Graph OneNote resource](https://learn.microsoft.com/graph/api/resources/onenote?view=graph-rest-1.0)
- [Microsoft Graph Get page/content](https://learn.microsoft.com/graph/api/page-get?view=graph-rest-1.0)
- [Official resource documentation source inspected](https://github.com/microsoftgraph/microsoft-graph-docs-contrib/blob/main/api-reference/v1.0/resources/onenote.md)
- [Official content endpoint documentation source inspected](https://github.com/microsoftgraph/microsoft-graph-docs-contrib/blob/main/api-reference/v1.0/api/page-get.md)

For a real acceptance batch, first export the 2 PowerPoint files and 1 Word file to PDF, replace their project entries, then compile with the 3 original PDFs, 15 screenshots and long OneNote PDF. Selecting the raw Office files without that step deliberately yields an incomplete report.
