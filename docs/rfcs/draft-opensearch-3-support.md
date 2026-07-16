- Start Date: 2026-07-16
- RFC PR: _(local draft — not yet opened upstream)_
- Discussion Issue: _(none yet; open with RFC label before/alongside PR)_
- Implementation PR(s): _(leave empty until RFC is active)_
- Status: **Local draft** (dhos3). Intended upstream path:
  `docs/rfcs/active/000-opensearch-3-support.md` → rename with PR number after open.
- Local evidence (not part of upstream RFC body unless linked):
  - Spike go/no-go: `docs/spike/go-no-go.md`
  - ADR: `docs/specs/2026-07-14-opensearch3-shim-adr.md`
  - Aiven validation: `docs/superpowers/plans/2026-07-16-os3-support-validation.md`
  - Prototype branch (fork): `feat/opensearch-3-shim`

# OpenSearch 3.x support via SearchClientShim

## Summary

DataHub today supports OpenSearch **2.x** (and Elasticsearch 7/8/9) through the
`SearchClientShim` abstraction. Managed OpenSearch offerings and self-hosted
clusters are moving to OpenSearch **3.x**; connecting GMS with
`ELASTICSEARCH_IMPLEMENTATION=opensearch` against an OS3 cluster is unsupported
and fails engine selection / client assumptions.

This RFC proposes first-class **OpenSearch 3** support by extending the existing
shim:

1. **Phase B1 (this RFC’s implementation scope):** add `SearchEngineType.OPENSEARCH_3`,
   factory/auto-detect wiring, and an `OpenSearch3SearchClientShim` that reuses the
   proven OpenSearch 2 HLRC REST path for OS 3.6+ HTTP APIs, with an OS3-specific
   kNN query builder for a known filter-shape delta.
2. **Phase B2 (explicit future work):** migrate OS3 internals to native
   `opensearch-java` + Apache HttpClient 5 without changing the operator-facing
   engine type.

Default behavior for existing deployments remains unchanged: operators opt in via
config or rely on version auto-detect when pointing at an OS3 cluster.

## Basic example

Operator points DataHub at an OpenSearch 3.6+ cluster (HTTPS + basic auth shown):

```yaml
# docker-compose / Helm env (illustrative)
ELASTICSEARCH_HOST: search.example.com
ELASTICSEARCH_PORT: "443"
ELASTICSEARCH_USE_SSL: "true"
ELASTICSEARCH_USERNAME: datahub
ELASTICSEARCH_PASSWORD: "********"
ELASTICSEARCH_IMPLEMENTATION: opensearch
ELASTICSEARCH_SHIM_ENABLED: "true"
ELASTICSEARCH_SHIM_ENGINE_TYPE: OPENSEARCH_3
ELASTICSEARCH_SHIM_AUTO_DETECT: "false"
ELASTICSEARCH_NUM_SHARDS_PER_INDEX: "1"
ELASTICSEARCH_NUM_REPLICAS_PER_INDEX: "0"   # single-node / small clusters
```

Alternatively, with auto-detect enabled and a live OS3 `/` version response of
`3.x`, the factory selects `OPENSEARCH_3` without hard-coding the enum.

GMS boot log (expected):

```text
Creating shim with configured engine type: OPENSEARCH_3
Created OpenSearch 3.x shim for engine type: OPENSEARCH_3
```

No GraphQL or Rest.li API changes for catalog users.

## Motivation

### Constraints we need to solve

1. **Managed OpenSearch 3 is shipping.** Providers (including Aiven OpenSearch 3.6)
   expose clusters that DataHub cannot officially target today. Customers who
   standardize on OS3 cannot run DataHub search/lineage against that estate
   without unsupported forks.
2. **Search is not optional.** Catalog search, browse, autocomplete, aggregations,
   graph/lineage (including PIT-based queries), restoreIndices, and semantic kNN
   all go through the search client. “Just use OpenSearch 2 forever” is not a
   durable answer for new environments.
3. **We already have a multi-engine shim.** DataHub invested in `SearchClientShim`
   precisely to support ES7/ES8/ES9/OS2 without forking GMS. OS3 should be another
   engine type in that model—not a parallel stack.
4. **Big-bang client rewrites are high risk.** Jumping straight to a full
   `opensearch-java` rewrite for every call site blocks validation. We need a
   path that unlocks OS3 with reviewable PRs and proven REST compatibility first.

### Expected outcome

- Operators can run GMS against OpenSearch **3.6+** with documented config.
- Core catalog + lineage paths work without engine-specific GraphQL changes.
- Semantic search remains functional on OS3 via an OS3-aware kNN builder.
- A clear follow-on path exists to replace HLRC internals (B2) without another
  config migration for operators.

## Requirements

- Add `OPENSEARCH_3` to `SearchEngineType` with correct `isOpenSearch()` /
  client-capability predicates for B1 (HLRC-compatible).
- Factory + config accept `ELASTICSEARCH_SHIM_ENGINE_TYPE=OPENSEARCH_3`.
- Auto-detect maps OpenSearch server major version `3` → `OPENSEARCH_3`.
- Implement `OpenSearch3SearchClientShim` covering DataHub-critical operations:
  index CRUD/settings/mappings, search/scroll/PIT, bulk index/update/delete,
  reindex/tasks, count, getMapping, and graph query paths used by lineage.
- Preserve OpenSearch **2** behavior unchanged (`OpenSearch2SearchClientShim` and
  `OpenSearch2KnnQueryBuilder` remain the OS2 path).
- Address semantic **filter-shape delta (D1):** on OS3, placing `filter` inside the
  nested `knn` clause (OS2 style) can return **0 hits**; OS3 builder must apply
  filters via a wrapping `bool` query (validated against OS 3.6 and 3.7).
- Integration tests via Testcontainers on a pinned OpenSearch 3.x image
  (proposed pin: **3.7.0** for CI; support matrix documents **3.6+** servers).
- Document support matrix, env vars, and replica/shard guidance for small clusters.
- **Non-breaking defaults:** existing OS2/ES deployments keep current engine
  selection unless they point at OS3 or set the new type.

### Extensibility

- Keep `OPENSEARCH_3` as the stable operator-facing engine type across B1→B2 so
  B2 can swap client internals without a second adoption wave.
- Continue routing engine-specific JSON builders (kNN, etc.) behind the shim so
  future OS minor deltas do not leak into GraphQL/DAO layers.
- Leave room for AWS SigV4 / IAM on HC5 in B2 without requiring B1 to rewrite IAM.

## Non-Requirements

- **Not** changing GraphQL search/lineage contracts or UI behavior.
- **Not** dropping OpenSearch 2 or Elasticsearch support in this RFC.
- **Not** requiring every deployment to upgrade search engines.
- **Not** delivering B2 (`opensearch-java` native client) as part of the first
  landed implementation—only designing for it.
- **Not** live-validating AWS IAM against OpenSearch Serverless/managed IAM in B1
  (unit-testable signer work stays in B2 / follow-up).
- **Not** changing Kafka/MAE/MySQL topology; search backend swap only.
- **Not** performance-optimizing MAE restore throughput (ops concern; orthogonal
  to engine compatibility).

## Detailed design

### Terminology

| Term | Meaning |
|---|---|
| **Shim** | `SearchClientShim` and engine-specific implementations that isolate GMS from ES/OS client differences |
| **B1** | OS3 support via HLRC REST compatibility + OS3 kNN builder |
| **B2** | Same `OPENSEARCH_3` type; internals move to `opensearch-java` + HC5 |
| **D1** | Spike/validation finding: in-knn nested `filter` empty on OS3; bool wrap works |

### Engine type and selection

```text
SearchEngineType
  ├── ELASTICSEARCH_7 / 8 / 9
  ├── OPENSEARCH_2
  └── OPENSEARCH_3   ← new
```

Selection order (unchanged pattern):

1. Explicit `ELASTICSEARCH_SHIM_ENGINE_TYPE`
2. Else auto-detect from cluster root version JSON when enabled
3. Else existing defaults for `ELASTICSEARCH_IMPLEMENTATION`

`OPENSEARCH_3` predicates for B1:

- `isOpenSearch() == true`
- `supportsEs7HighLevelClient() == true` (B1)
- `requiresOpenSearchClient() == false` until B2 flips it

### Class layout (B1)

```text
OpenSearch2SearchClientShim          (existing HLRC)
        ▲
        │ extends
OpenSearch3SearchClientShim          (engineType=OPENSEARCH_3)
        │
        └── searchKnn() → OpenSearch3KnnQueryBuilder

OpenSearch2KnnQueryBuilder           (unchanged; filter inside knn)
OpenSearch3KnnQueryBuilder           (filter via bool must+filter wrap)
```

Factory (`SearchClientShimUtil` / `SearchClientShimFactory`) gains an
`OPENSEARCH_3` case returning `OpenSearch3SearchClientShim`.

### kNN / semantic (D1)

OS2 (unchanged) shape — filter inside `knn` params for prefiltering:

```json
{
  "query": {
    "nested": {
      "path": "embeddings.model.chunks",
      "score_mode": "max",
      "query": {
        "knn": {
          "embeddings.model.chunks.vector": {
            "vector": [...],
            "k": 10,
            "filter": { "...": "..." }
          }
        }
      }
    }
  }
}
```

OS3 builder shape — nested knn for scoring; filters in outer `bool`:

```json
{
  "query": {
    "bool": {
      "must": [
        {
          "nested": {
            "path": "embeddings.model.chunks",
            "score_mode": "max",
            "query": {
              "knn": {
                "embeddings.model.chunks.vector": {
                  "vector": [...],
                  "k": 10
                }
              }
            }
          }
        }
      ],
      "filter": [ { "...": "..." } ]
    }
  }
}
```

Unfiltered nested knn remains shared/simple (no bool wrap required).

### Graph / PIT

`ESGraphQueryDAO` (and related switches) treat `OPENSEARCH_3` like other
PIT-capable engines (same routing as OS2 toward `GraphQueryPITDAO`). No new
lineage API; validation must cover `searchAcrossLineage` / entity lineage.

### Config & ops notes (docs, not code requirements)

- Single-node / small managed clusters: `number_of_replicas=0` to avoid yellow
  indices.
- SystemUpdate creates many indices; cluster `max_shards_per_node` must allow
  ~60–80+ DataHub indices (ops guidance in support matrix).
- TLS + basic auth continue through existing RestClient settings.

### Testing strategy

| Layer | What |
|---|---|
| Unit | Engine predicates; `OpenSearch3KnnQueryBuilder` JSON shape |
| IT | Testcontainers OpenSearch 3.x — shim create, search, bulk, PIT smoke |
| Manual / partner | Optional Aiven OS 3.6 HTTPS smoke (search, lineage, restoreIndices) |

### Proposed PR stack (after RFC active)

1. Enum + factory + auto-detect + minimal shim wiring  
2. Core shim ops + Testcontainers IT  
3. Semantic (`OpenSearch3KnnQueryBuilder`) + call-site audit (`isOpenSearch()` etc.)  
4. Docs / Helm examples / support matrix  

(Exact split can adjust for reviewability; RFC acceptance is the gate.)

## How we teach this

- Present as a **continuation** of the existing search-client shim story
  ([`docs/how/elasticsearch-search-client-shim.md`](https://github.com/datahub-project/datahub/blob/master/docs/how/elasticsearch-search-client-shim.md)),
  not a new product feature with UI surface.
- Audiences:
  - **Operators / platform engineers:** env vars, support matrix (OS 3.6+),
    replica/shard notes.
  - **Backend developers:** new engine type + when to add OS3-specific builders.
  - **End users of the DataHub app:** no teaching change; catalog behaves the same.
- Docs updates: deploy/search backend page, shim how-to, release notes “OpenSearch
  3.x supported (opt-in / auto-detect)”.
- Terminology: prefer **“OpenSearch 3”** / **`OPENSEARCH_3`** over “OS3 shim” in
  user-facing docs; “B1/B2” can stay contributor-facing only.

## Drawbacks

- **HLRC on OS3 is transitional.** B1 leans on REST compatibility; some reviewers
  may prefer waiting for a pure `opensearch-java` implementation. Mitigation:
  scope B1 clearly; commit to B2 in Future Work; keep one engine type.
- **D1 means OS2 and OS3 semantic JSON diverge.** Two builders increase maintenance
  until/unless OpenSearch unifies filter behavior. Mitigation: isolate in
  `opensearch3/` package with tests locking both shapes.
- **Support surface grows.** CI must run another Testcontainers image; docs must
  state min server version (3.6+).
- **False confidence risk.** Declaring OS3 support without IAM live tests (B1)
  could confuse AWS-managed OS3 IAM users. Mitigation: docs explicitly list
  validated auth modes (TLS+basic in partner smoke; IAM = B2/follow-up).

## Alternatives

| Alternative | Why rejected / deferred |
|---|---|
| **Do nothing** | Blocks customers on OS3-managed search; forces unsupported forks |
| **B2-only (native client first)** | Larger, slower PR; delays validation of REST-compatible path already proven |
| **Reuse `OPENSEARCH_2` type against OS3** | Hides real version differences (D1); breaks honest auto-detect and support matrix |
| **Sidecar / external proxy translating APIs** | Operational complexity; not aligned with shim architecture |
| **Fork DataHub per cloud vendor** | Fractures upstream; high maintenance |

Prior art: DataHub’s own ES8/ES9 and OS2 shim split shows engine-typed clients
behind one interface are the project norm.

## Rollout / Adoption Strategy

- **Non-breaking.** No change unless the cluster is OS3 or the operator sets
  `OPENSEARCH_3`.
- Adoption paths:
  1. Set `ELASTICSEARCH_SHIM_ENGINE_TYPE=OPENSEARCH_3`, or
  2. Enable auto-detect against an OS3 cluster.
- Helm/quickstart: document values; optional example override for managed OS3.
- No data migration beyond normal index compatibility; existing restoreIndices /
  reindex tooling applies if operators rebuild search indices.
- Feature flag: engine type selection is sufficient; no separate product flag
  required beyond existing shim enablement.

## Future Work

- **B2:** `OpenSearch3SearchClientShim` internals → `opensearch-java` + HC5;
  flip `requiresOpenSearchClient()`; port AWS IAM signer tests to HC5.
- Expand CI matrix across OS 3.6 / 3.7 as images are available.
- Revisit D1 if upstream OpenSearch restores in-knn filter semantics—possibly
  converge builders.
- Performance soak guidance for remote managed OS (lineage/PIT latency baselines).

## Unresolved questions

1. **Minimum supported server:** propose **3.6+** (validated). Is 3.5 or earlier
   in scope for the support matrix, or explicitly unsupported?
2. **Auto-detect default:** should auto-detect prefer `OPENSEARCH_3` whenever
   major=3 even if an operator left `OPENSEARCH_2` in config, or always honor
   explicit config over detect?
3. **Helm default pin:** when should quickstart/dev images offer OS3 as an
   optional profile vs remaining on OS2?
4. **IAM timeline:** is B1 merge acceptable with docs-only IAM caveat, or should
   a minimal HC5 signer land in the same RFC implementation wave?
5. **Upstream PR granularity:** prefer four stacked PRs (as above) or two larger
   PRs (engine+core / semantic+docs)?

---

## Appendix A — Validation evidence (local; optional for upstream)

Companion repo validation against **Aiven OpenSearch 3.6** + DataHub debug-min
(prototype `OPENSEARCH_3` shim):

| Area | Result |
|---|---|
| Shim bootstrap / TLS+basic | PASS |
| Keyword search, facets, autocomplete, browse | PASS |
| Entity fetch | PASS |
| PIT lineage (`searchAcrossLineage`) | PASS (hub fan-out) |
| `restoreIndices` OpenAPI | PASS |
| Locust smoke (authenticated search/browse/lineage) | PASS (0% fail; lineage slower remotely) |
| D1 in-knn filter vs bool wrap | CONFIRMED (builder workaround correct) |
| Product semantic search (Ollama `nomic-embed-text` + GraphQL) | PASS — unfiltered relevance + filtered kNN (D1 product path) on Aiven OS 3.6 |

Ops note observed during load: bulk `restoreIndices` is MAE-throughput bound;
not an OS3 protocol rejection. Semantic dual-write can lag behind large restores;
query path (GMS `LocalEmbeddingProvider` → `OpenSearch3KnnQueryBuilder` → Aiven) validated.

## Appendix B — Mapping to upstream RFC process

When ready to submit (out of scope for this local draft):

1. Discuss in Slack `#contribute-code` / open Discussion Issue with **RFC** label.
2. Copy this file into a DataHub fork at
   `docs/rfcs/active/000-opensearch-3-support.md` (drop local-only appendices or
   replace with public links).
3. Open PR with **RFC** label; rename to `<PR#>-opensearch-3-support.md` and fill
   **RFC PR** header.
4. After merge (Active), land implementation PRs and link them here.
5. When shipped, move to `docs/rfcs/accepted/`.
