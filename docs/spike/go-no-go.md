# Spike go / no-go

**Date:** 2026-07-14  
**Decision:** `GO_WITH_DELTAS`

## Summary

`opensearch-java:3.7.0` over Apache HttpClient 5 successfully exercised all DataHub-critical OpenSearch operations against **local OpenSearch 3.7.0** and **Aiven OpenSearch 3.6.0** (HTTPS + basic auth). Core search, PIT, bulk, reindex, and semantic `knn_vector` (field-level method + nested knn + `ef_search`) work. Path B should proceed with one semantic filter-shape delta to verify.

## Required probes

| Result | Count |
|---|---|
| PASS | 16 probe groups (P01–P17, P19–P24) fully green on both targets |
| PARTIAL | P18 (in-knn nested filter) |
| FAIL | 0 |

## Deltas for Path B (if `GO_WITH_DELTAS`)

| Delta ID | Finding | Path B mitigation | Plan task |
|---|---|---|---|
| D1 | Nested kNN **unfiltered** works; placing `filter` **inside** the `knn` block (DataHub `OpenSearch2KnnQueryBuilder` style) returned **0 hits** on OS 3.6 and 3.7. Equivalent filtering via `bool` + nested filter **works**. | Integration-test DataHub’s exact kNN JSON on OS3; if in-knn filter still empty, adapt OS3 builder (bool wrap or OS3-supported prefilter syntax) while keeping OS2 behavior unchanged. | Task 3.6 + semantic ITs |
| D2 | Spike client `3.7.0` talks to server `3.6.0` and `3.7.0`. | Document supported server range as OpenSearch **3.6+** (validate min in ADR); pin Testcontainers to a chosen 3.x. | Task 2.1 ADR |
| D3 | AWS IAM not live-tested (by design). | Implement HC5 signing path with unit tests; Aiven covers TLS+basic. | Task 3.7 |

## Blockers (if `NO_GO`)

None.

## Sign-off

- Spike owner: dhos3 automation (2026-07-14)
- Reviewer: **approved GO** (2026-07-14) — proceed to Path B / ADR
