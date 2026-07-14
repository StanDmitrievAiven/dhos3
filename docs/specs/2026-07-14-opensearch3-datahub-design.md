# OpenSearch 3 Support for DataHub OSS — Design Spec

**Status:** Approved (brainstorming 2026-07-14)  
**Repo:** [StanDmitrievAiven/dhos3](https://github.com/StanDmitrievAiven/dhos3)  
**Upstream target:** [datahub-project/datahub](https://github.com/datahub-project/datahub)  
**Approach:** Spike research → Path B (`opensearch-java` shim) → local + Aiven validation → upstream PR series

---

## 1. Problem

DataHub OSS supports OpenSearch **2.x** via `OpenSearch2SearchClientShim` (HLRC 2.19.x). OpenSearch **3.x** is documented as a future enhancement only. Auto-detection accepts only versions starting with `2.`, and `SearchEngineType.requiresOpenSearchClient()` returns `false` with an explicit “not yet implemented” comment.

Operators (including managed OpenSearch 3 such as Aiven) cannot run current DataHub against OS3 without unsupported hacks.

## 2. Goals

| Goal | Definition of done |
|---|---|
| Spike evidence | Compatibility matrix + go/no-go memo against local OS3 and Aiven OS3 |
| Path B | First-class `OPENSEARCH_3` engine using `opensearch-java` |
| Parity | Core search + semantic/kNN + AWS IAM **code path** (IAM unit-tested) |
| Non-regression | ES7 / ES8 / OS2 shims and tests remain green |
| Contribution quality | Design + spike linked; stacked upstream PRs; docs/Helm/CI updated |

## 3. Non-goals (v1)

- Removing or rewriting the OS2 HLRC shim
- Requiring a live AWS OpenSearch cluster for merge (IAM is unit-tested)
- OpenSearch Dashboards / non-DataHub plugins
- Supporting indexes created before OpenSearch 2.x (OS3 cluster upgrade rule; operators must reindex)

## 4. Decisions (locked)

| Decision | Choice |
|---|---|
| Strategy | **Approach 1:** thin spike, then full Path B shim |
| Client | **`opensearch-java`** (not HLRC 3.x) |
| Scope | Full OS2 parity: core + semantic + AWS IAM code |
| Validation home | **`dhos3` hybrid repo** first |
| Managed env | Local Docker OS3 → **Aiven OS3** (creds provided out-of-band) |
| AWS IAM | Implement + **unit/mock tests**; Aiven covers managed TLS/basic auth |
| Upstream | Early design/spike visibility; implementation validated here; then PR series to DataHub OSS |

## 5. Repository layout

```
dhos3/
  docs/
    specs/     # this design
    plans/     # implementation plan
    spike/     # matrix + go/no-go outputs
    tickets/   # epic/ticket catalog for tracking
  spike/
    docker/    # OpenSearch 3.x compose
    java/      # opensearch-java probe suite
    scripts/   # local + Aiven runners
  vendor/
    datahub/   # git submodule → datahub-project/datahub
  CONTRIBUTING.md
  README.md
```

**Hard gate:** No Path B coding in `vendor/datahub` until spike go/no-go (or “proceed with listed deltas”) is written under `docs/spike/`.

## 6. Phases

| Phase | Name | Exit criteria |
|---|---|---|
| 0 | Bootstrap | Scaffold, submodule pinned, local OS3 healthy |
| 1 | Spike | Matrix + go/no-go; Aiven probe results appended |
| 2 | Architecture lock | ADR finalized from spike deltas |
| 3 | Path B implementation | `OPENSEARCH_3` parity in submodule |
| 4 | Validation | Local + Aiven smoke; CI/ITs green |
| 5 | Upstream | Stacked PRs to `datahub-project/datahub` |

## 7. Spike design

### 7.1 Purpose

Prove that Path B on `opensearch-java` can cover DataHub OS2 shim operations before investing in the full shim.

### 7.2 Deliverables

1. Pinned OS3 Docker Compose (`spike/docker`)
2. Minimal Java probe module (`spike/java`) using `opensearch-java` + HttpClient 5 transport
3. Compatibility matrix (`docs/spike/compatibility-matrix.md`)
4. Go/no-go memo (`docs/spike/go-no-go.md`)
5. Aiven run log (`docs/spike/aiven-results.md`)

### 7.3 Probe areas (map to OS2 shim usage)

Cluster info/health/settings · index CRUD/mappings/settings/aliases/refresh · document CRUD · bulk · search/aggs · scroll · PIT · count/explain · tasks · reindex submit/poll · semantic `knn_vector` + `index.knn` + nested kNN filter + `ef_search` · nested depth under default `max_nested_depth` · basic auth + TLS.

### 7.4 Go / no-go

- **Go:** Required probes pass, or each failure has an explicit Path B mitigation.
- **No-go:** Fundamental gaps in PIT/bulk/kNN/auth that force HLRC or unsupported APIs.

## 8. Path B architecture

```
SearchClientShim
├── Es7CompatibilitySearchClientShim
├── Es8SearchClientShim              # elasticsearch-java
├── OpenSearch2SearchClientShim      # HLRC 2.x (unchanged)
└── OpenSearch3SearchClientShim      # NEW — opensearch-java
```

### 8.1 Engine type

Add `OPENSEARCH_3("opensearch", "3")`:

- `isOpenSearch()` → true  
- `supportsEs7HighLevelClient()` → false  
- `requiresEs8JavaClient()` → false  
- `requiresOpenSearchClient()` → true  

Auto-detect: version `3.*` → `OPENSEARCH_3`; `2.*` → `OPENSEARCH_2`.

### 8.2 Dependencies

- Add `opensearch-java` (+ Apache HttpClient 5 transport as required by the client).
- Coexist with HLRC 2.x and ES clients (same multi-client model as ES8 introduction).
- Isolate packages; no forced removal of OS2.

### 8.3 Semantic

Prefer reusing OS2 JSON builders if spike proves REST shape identical:

- `OpenSearch2KnnQueryBuilder`
- `OpenSearch2SemanticIndexMapper` (field-level `method` — already OS3-safe)
- `OpenSearch2SemanticIndexSettingsBuilder` (`knn: true`)

If deltas appear, add `.../builder/opensearch3/` with minimal copies. Default `faiss` + `cosinesimil`; avoid NMSLIB.

### 8.4 AWS IAM

Port signing interceptor to OS3/HttpClient 5 transport configuration. Unit-test canonical request signing. Not required for Aiven smoke.

### 8.5 Version gates

`ESIndexBuilder.isOpenSearch29OrHigher()` already returns true for `majorVersion > 2`. Verify all `isOpenSearch()` call sites treat OS3 correctly (not `OPENSEARCH_2` equality checks).

### 8.6 Upstream PR stacking (reviewability)

1. Engine type + factory + auto-detect + deps (no behavior change default)  
2. Core `OpenSearch3SearchClientShim`  
3. Semantic + IAM  
4. Testcontainers / CI / docs / Helm  

## 9. Testing strategy

| Layer | Coverage |
|---|---|
| Unit | Enum, factory, auto-detect, kNN JSON, IAM signer |
| Integration | Testcontainers OS 3.x mirroring OS2 IT suite |
| Spike harness | Local + Aiven probes |
| Manual smoke | GMS boot, UI search, lineage, semantic, system-update |
| Regression | ES7/ES8/OS2 CI green |

## 10. Risks

| Risk | Mitigation |
|---|---|
| HttpClient 4 → 5 breaks IAM/SSL helpers | Spike transport early; port interceptor with unit tests |
| Classpath clashes with HLRC/ES clients | Dependency isolation; smoke all engine types in CI |
| Nested kNN vs `max_nested_depth` | Spike probe; document setting override if needed |
| OS3 rejects pre-2.x indexes | Docs: reindex before cluster upgrade |
| Scope creep (HLRC dual path) | Path B is `opensearch-java` only |

## 11. Success definition

Local OS3 and Aiven OS3 run DataHub with `ELASTICSEARCH_SHIM_ENGINE_TYPE=OPENSEARCH_3` for search, reindex, and semantic search; AWS IAM code path covered by unit tests; upstream PR series includes design + spike evidence and passes DataHub CI.
