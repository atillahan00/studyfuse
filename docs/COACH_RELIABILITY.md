# Source-linked coach and complete-source card runs — 1.3.5

## Why the old error occurred

The previous validator stopped the whole request on the first source-ID/quote mismatch, even for a batch containing valid cards. This was particularly brittle when a multilingual model translated, paraphrased, shortened or reformatted the quote. The reported screenshot establishes the validator failure, not which exact quote the model produced; no private response was uploaded for inspection.

## Evidence references

Each selected source passage is split into Unicode-safe evidence spans with short request-local labels. The model selects a label; StudyFuse resolves it to the app's own passage ID, original quote and compiled-PDF page. Persisted references are canonical source IDs/quotes, not ephemeral labels. The model need not retype long identifiers or quotations. A conventional `id: E1` response is accepted as a label alias; older passage-ID/literal-quote responses still require an actual match. NFC and Unicode-space normalization handle representation differences without case folding, fuzzy matching, altered punctuation/numbers, translated quotes or removed negation.

The check establishes that cited text exists at that location. It does not prove entailment, truth, complete source understanding or correct handwriting recognition. Visual transcriptions remain marked unverified; direct native-text readings omit app status observations from the study corpus. Cards retain the existing student-review requirement before practice/mock-exam use.

Card summaries/counts come from the app. Invalid cards/overview references are reported separately and do not discard valid cards. No-source explanations can return an honest insufficiency result. Non-card source answers still require valid citations; Documents Only still rejects nonempty external additions. Inference failures/cancellation propagate; malformed JSON or invalid-only card content gets one bounded repair, not an unlimited retry loop.

## Full-source cards

Choose **AI öğrenme merkezi → Koç → Yeni çalışma kartları**. With **Seçili kaynakların tamamını sırayla işle** enabled, all available reading passages in the current topic scope are processed in bounded groups (up to four passages/6000 source characters per context). This is a model-context bound, not a source-count cap. Focus is optional. Disable the switch for lexical question-focused retrieval. Normal questions and source comparison keep bounded retrieval/search rewriting; a single answer does not claim to analyze the whole library.

Each completed group immediately saves valid cards/history. Batch keys include source/model/settings identity, exact query and actual passage text/IDs. Completed groups are revalidated and reused. Resuming an incomplete group sends only passages still without cards, merges the valid prior cards and keeps rejected metadata visible. Invalid/corrupt checkpoint quotes are regenerated; changed source text invalidates the key. Interrupted history/deck writes are reconciled by idempotent replay. No completed card is auto-approved or silently replaced by a wrong source.

The run report records processed/reused/failed groups, card counts and source fragments without a card. Those rows open their source page. **Eksik kartları tamamla / sürdür** repeats the same scoped request. A fragment containing a card may still contain additional untested facts; a cover/noneducational fragment may legitimately have no useful card. The report never translates passage counts into mastery or an exam-grade prediction. Failed/unavailable source readings cannot produce reliable cards and remain visibly incomplete.

Existing history/cards/source readings are retained. Batch checkpoints live in `coach.json`; `coach-run.json` adds the run coverage record. Both are included in project backups; model weights remain separate. No model re-download, source re-read, cloud account, external inference service or additional permission is required for this update.

## Validation limits

See the current [validation record](VALIDATION.md) for actual tests, builds and native-emulator results. Deterministic adapters test protocol, persistence, scope and failure handling. They are not evidence that a particular local model always chooses the right label or generates correct educational answers. Original diagrams, formulas and handwriting still require source comparison.
