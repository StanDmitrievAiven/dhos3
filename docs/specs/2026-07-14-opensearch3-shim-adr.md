# ADR: OpenSearch 3 Path B shim for DataHub

**Status:** Accepted  
**Date:** 2026-07-14  
**Spike:** [go-no-go.md](../spike/go-no-go.md) — human-approved `GO_WITH_DELTAS`

## Context

DataHub supports OpenSearch 2.x via HLRC (`OpenSearch2SearchClientShim`). OpenSearch 3.x is unsupported. Spike proved `opensearch-java` + Apache HttpClient 5 works against OS **3.6** (Aiven) and **3.7** (local) for all DataHub-critical operations, with one semantic filter-shape delta.

## Decision

Implement **Path B** in two phases:

### B1 — Engine + REST-compatible shim (this milestone)
- Add `OPENSEARCH_3` engine type, factory, auto-detect for version `3.*`
- `OpenSearch3SearchClientShim` extends `OpenSearch2SearchClientShim` (HLRC) for REST API compatibility with OS 3.6+ (spike proved the HTTP APIs DataHub needs)
- Semantic D1 verified/adapted in OS3-specific builders as needed
- Unlocks GMS against OS3 without rewriting the entire client in one PR

### B2 — Native `opensearch-java` (follow-on, same engine type)
- Replace HLRC internals with `opensearch-java` + Apache HttpClient 5
- Port AWS IAM signer to HC5 (delta D3)
- No change to `OPENSEARCH_3` config surface

| Topic | Choice |
|---|---|
| Supported servers | OpenSearch **3.6+**; Testcontainers pin **3.7.0** |
| Semantic builders | Reuse OS2 JSON; adapt filter shape if D1 persists (OS3-only) |
| IAM (B1) | Existing OS2 HC4 signer path |
| IAM (B2) | HC5 signer + unit tests |
| Defaults | Unchanged — select `OPENSEARCH_3` or auto-detect |

## Spike deltas carried forward

| ID | Mitigation |
|---|---|
| D1 | IT with DataHub `OpenSearch2KnnQueryBuilder` JSON on OS3; if in-knn filter empty, OS3 builder uses bool+nested filter (or corrected prefilter) |
| D2 | Docs: support matrix OpenSearch 3.6+; client 3.7.x |
| D3 | IAM unit tests on HC5 interceptor |

## Upstream PR stack

1. Enum + factory + auto-detect + deps (+ stub/minimal shim connection)  
2. Core shim ops + Testcontainers IT  
3. Semantic + IAM + `isOpenSearch()` call-site audit  
4. Docs / Helm / support matrix  

## Consequences

- Larger change than a version bump; reviewable via stacked PRs  
- HLRC remains for OS2 until a future removal  
- Contribution evidence lives in `dhos3` spike docs
