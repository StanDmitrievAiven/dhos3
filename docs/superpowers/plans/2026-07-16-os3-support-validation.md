# OpenSearch 3 Support Validation Plan

> **For agentic workers:** Execute tasks sequentially with checkbox tracking. This is a **test/validation** plan (not an implementation plan). Record PASS/FAIL/PARTIAL with evidence.

**Goal:** Validate that DataHub’s OpenSearch 3 shim (B1 HLRC + `ELASTICSEARCH_SHIM_ENGINE_TYPE=OPENSEARCH_3`) works correctly against Aiven OpenSearch 3.6, and surface any functional or operational issues.

**Architecture:** Local Docker debug-min stack (GMS + frontend + Kafka + MySQL) with search redirected to Aiven OS3 via `docker/aiven-os3.override.yml`. Indices are created by SystemUpdate; search/lineage/browse go through GMS → SearchClientShim → Aiven.

**Tech Stack:** DataHub `feat/opensearch-3-shim`, Aiven OpenSearch 3.6, Docker Compose debug-min, GraphQL/OpenAPI, existing ingest scripts.

## Global Constraints

- Never commit `.env` or Aiven credentials
- Do not tear down the stack unless a check requires it
- Prefer GraphQL/API evidence over UI-only “looks fine”
- Treat known spike delta D1 (in-knn filter) as a tracked risk, not a surprise failure
- Pass criteria: core catalog search + entity fetch + browse work; lineage graph queries return expected counts without 5xx

## Environment Under Test

| Item | Value |
|---|---|
| Launcher | `./scripts/quickstart-aiven-os3.sh` |
| Override | `docker/aiven-os3.override.yml` |
| Profile | `debug-min` |
| Shim | `ELASTICSEARCH_SHIM_ENGINE_TYPE=OPENSEARCH_3`, auto-detect off |
| Search | Aiven OS 3.6 (HTTPS + basic auth) |
| UI / GMS | `http://localhost:9002` / `http://localhost:8080` |
| Branch | `vendor/datahub` @ `feat/opensearch-3-shim` |
| Seed data | `scripts/ingest_loadtest.py` (~20k datasets), `scripts/ingest_lineage_hub.py` |

## Risk areas (from spike + code)

1. **Index bootstrap / shard budget** — SystemUpdate creates ~60–80 indices; Aiven default `max_shards_per_node` too low
2. **PIT-based lineage** — `ESGraphQueryDAO` routes OS3 to `GraphQueryPITDAO`
3. **Search parity** — HLRC B1 path vs native `opensearch-java` (B2 not landed)
4. **D1 semantic kNN filter** — in-knn nested filter returned 0 hits in spike
5. **Replica settings** — must be 0 on single-node Aiven
6. **Auth/TLS** — HTTPS + basic auth through shim RestClient

---

### Task 1: Bootstrap & shim wiring

**Files / commands:**
- Run: `./scripts/quickstart-aiven-os3.sh smoke`
- Inspect: GMS logs for shim creation; Aiven `_cat/indices`

- [x] **Step 1: GMS + UI health**

```bash
./scripts/quickstart-aiven-os3.sh smoke
curl -sf http://localhost:8080/health
curl -sf -o /dev/null -w '%{http_code}\n' http://localhost:9002/
```

Expected: health 200, frontend 200, log line with `OPENSEARCH_3` / shim creation.

- [x] **Step 2: Confirm engine type in live logs**

```bash
docker logs datahub-datahub-gms-debug-min-1 2>&1 | rg -n 'Creating shim|OPENSEARCH_3|engine type'
```

Expected: `Creating shim with configured engine type: OPENSEARCH_3` and `Creating SearchClientShim for engine type: OPENSEARCH_3`.

- [x] **Step 3: Index inventory on Aiven**

```bash
# using .env credentials — do not print password
curl -sk -u "$USER:$PASS" "https://$HOST:$PORT/_cat/indices?v&h=index,health,status,pri,rep,docs.count" | rg 'index_v2|graph_service|system_metadata|usage_event'
```

Expected: ~70+ DataHub indices, all `green`/`open`, `pri=1`, `rep=0`. Fail if many `yellow`/`red` or `rep>0`.

- [x] **Step 4: Cluster shard headroom**

```bash
curl -sk -u "$USER:$PASS" "https://$HOST:$PORT/_cluster/health?pretty"
```

Expected: status green/yellow-ok; `active_shards` well below `max_shards_per_node` (target 2000).

**Pass:** all four steps green.

---

### Task 2: Auth login + GraphQL search

- [x] **Step 1: Obtain token via frontend login**

```bash
TOKEN=$(python3 - <<'PY'
import json, base64, urllib.request
req = urllib.request.Request(
  'http://localhost:9002/logIn',
  data=b'{"username":"datahub","password":"datahub"}',
  headers={'Content-Type':'application/json'}, method='POST')
with urllib.request.urlopen(req) as resp:
  for c in (resp.headers.get_all('Set-Cookie') or []):
    if c.startswith('PLAY_SESSION='):
      val = c.split(';',1)[0].split('=',1)[1]
      pad = '=' * ((4 - len(val.split('.')[1]) % 4) % 4)
      print(json.loads(base64.urlsafe_b64decode(val.split('.')[1]+pad))['data']['token'])
      break
PY
)
```

Expected: non-empty JWT.

- [x] **Step 2: Keyword search for loadtest corpus**

```graphql
query {
  search(input: { type: DATASET, query: "loadtest.search", start: 0, count: 10 }) {
    total
    searchResults { entity { urn } }
  }
}
```

Expected: `total > 0` (ideally thousands if ingest completed). Fail on GraphQL errors or `total=0` with docs in `datasetindex_v2`.

- [x] **Step 3: Autocomplete / suggest**

```graphql
query {
  autoComplete(input: { type: DATASET, query: "loadtest", limit: 10 }) {
    suggestions
  }
}
```

Expected: non-empty suggestions or graceful empty (document which); no 5xx.

- [x] **Step 4: Faceted search (aggregations)**

```graphql
query {
  search(input: {
    type: DATASET
    query: "loadtest"
    start: 0
    count: 5
    filters: [{ field: "platform", values: ["snowflake"] }]
  }) {
    total
    facets { field aggregations { value count } }
  }
}
```

Expected: `total > 0`, facets present without errors.

**Pass:** login + keyword search work; facets do not error.

---

### Task 3: Entity read + browse paths

- [x] **Step 1: Fetch a known dataset entity**

```graphql
query {
  dataset(urn: "urn:li:dataset:(urn:li:dataPlatform:snowflake,loadtest.cll.mart_wide,PROD)") {
    urn
    name
    platform { name }
    schemaMetadata { fields { fieldPath } }
  }
}
```

Expected: entity resolves; schema fields present (CLL showcase).

- [x] **Step 2: Browse V2 / scroll**

```graphql
query {
  browseV2(input: { type: DATASET, path: [], query: "*", start: 0, count: 10 }) {
    total
    groups { name }
  }
}
```

Expected: no GraphQL errors; groups or entities returned (or documented empty if browse path unused).

- [x] **Step 3: Scroll / searchAcrossEntities**

```graphql
query {
  searchAcrossEntities(input: { types: [DATASET], query: "loadtest.cll", start: 0, count: 5 }) {
    total
    searchResults { entity { urn ... on Dataset { name } } }
  }
}
```

Expected: hits for CLL datasets.

**Pass:** entity fetch + at least one browse/search-across path works.

---

### Task 4: Lineage graph (PIT path — high risk)

Hub URN: `urn:li:dataset:(urn:li:dataPlatform:snowflake,loadtest.lineage.hub,PROD)`

- [x] **Step 1: Confirm graph index docs**

```bash
curl -sk -u "$USER:$PASS" "https://$HOST:$PORT/graph_service_v1/_count"
```

Expected: count > 0 after lineage ingest. If 0, lineage never indexed (ingest/MAE issue, not necessarily OS3 API).

- [x] **Step 2: searchAcrossLineage upstream/downstream**

```graphql
query {
  up: searchAcrossLineage(input: {
    urn: "urn:li:dataset:(urn:li:dataPlatform:snowflake,loadtest.lineage.hub,PROD)"
    direction: UPSTREAM, start: 0, count: 10
  }) { total }
  down: searchAcrossLineage(input: {
    urn: "urn:li:dataset:(urn:li:dataPlatform:snowflake,loadtest.lineage.hub,PROD)"
    direction: DOWNSTREAM, start: 0, count: 10
  }) { total }
}
```

Expected: totals match fan-out (~90+ each if hub script defaults). Fail if GraphQL errors or persistent 0 with non-zero graph docs.

- [x] **Step 3: entityLineage / lineage counts**

```graphql
query {
  entity(urn: "urn:li:dataset:(urn:li:dataPlatform:snowflake,loadtest.lineage.hub,PROD)") {
    ... on Dataset {
      upstream: lineage(input: { direction: UPSTREAM, start: 0, count: 10 }) {
        total
      }
      downstream: lineage(input: { direction: DOWNSTREAM, start: 0, count: 10 }) {
        total
      }
    }
  }
}
```

Expected: non-zero totals without errors.

- [x] **Step 4: GMS error scan for PIT/lineage**

```bash
docker logs datahub-datahub-gms-debug-min-1 2>&1 | rg -i 'pit|point.in.time|search_phase_execution|illegal_argument|parsing_exception|graphquery' | tail -40
```

Expected: no repeated OS query parse failures. Document any stack traces.

**Pass:** lineage totals > 0 both directions OR clear evidence the gap is data/MAE lag (not OS3 query rejection).

---

### Task 5: Index ops used by DataHub (reindex / restore / count)

- [x] **Step 1: OpenAPI ES getMapping / getTask style health**

```bash
curl -sf -H "Authorization: Bearer $TOKEN" \
  'http://localhost:8080/openapi/operations/elasticSearch/getTask?task=noop' || true
# Prefer a real count via search already covered; also:
curl -sk -u "$USER:$PASS" "https://$HOST:$PORT/datasetindex_v2/_count"
```

Expected: dataset index count aligns roughly with GraphQL search totals (eventual consistency OK).

- [x] **Step 2: restoreIndices dry probe (limited)**

```bash
curl -sf -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/openapi/operations/elasticSearch/restoreIndices?urnLike=%loadtest.cll.mart_wide%&batchSize=10&start=0&limit=10"
```

Expected: HTTP 200; no RestClient protocol errors.

- [x] **Step 3: Diff search total vs OS docs**

Compare `datasetindex_v2` `_count` vs GraphQL `search(query:"*")` / `loadtest` totals. Large persistent gaps → MAE consumer / indexing issue.

**Pass:** restoreIndices and counts work; no client protocol errors.

---

### Task 6: Negative / compatibility checks

- [x] **Step 1: GMS error log sweep (OS incompatibilities)**

```bash
docker logs datahub-datahub-gms-debug-min-1 --since 2h 2>&1 | \
  rg -i 'exception|error' | rg -i 'opensearch|elasticsearch|rest.?client|mapper_parsing|illegal_argument|version_conflict' | \
  sort | uniq -c | sort -rn | head -40
```

Classify: noise vs actionable OS3 incompatibilities.

- [x] **Step 2: Replica setting verification**

All DataHub indices `rep=0`. Any `rep=1` → yellow cluster risk on single-node Aiven.

- [x] **Step 3: Semantic / kNN (D1) — only if feature enabled**

If semantic search indices/embeddings exist, run a filtered semantic query. Expect possible PARTIAL per spike D1 (in-knn filter empty). Document actual filter shape used.

If semantic not enabled in this stack: mark **SKIP** with reason.

**Pass:** no systematic RestClient/query parse failures; D1 outcome documented.

---

### Task 7: Verdict report

- [x] **Step 1: Fill results table**

| Area | Result | Evidence |
|---|---|---|
| Bootstrap / shim | **PASS** | GMS/UI 200; log `Created OpenSearch 3.x shim for engine type: OPENSEARCH_3`; 80 DH indices all green, `pri=1`/`rep=0`; cluster green, 87 shards, `max_shards_per_node=2000`; OS 3.6.0 |
| Search | **PASS** | `loadtest.search` total **2641**; autocomplete suggestions; platform facets; `searchAcrossEntities` CLL hits |
| Browse / entity | **PASS** | browseV2 group `loadtest` count 2677; CLL entity + 100 schema fields; UI dataset page 200 |
| Lineage (PIT) | **PASS** | hub `searchAcrossLineage` up=100 / down=100; entity lineage same; CLL upstream=1; fineGrainedLineages present |
| Index ops | **PASS** | `restoreIndices` 200 (`rowsMigrated=7`); dataset/graph/system_metadata counts readable |
| Compatibility / D1 | **SKIP / OK** (superseded below) | Initially skipped (no product embeddings). Follow-up Task 8 re-probed D1 on Aiven. |

- [x] **Step 2: Issue list**

1. **MAJOR (ops / data completeness, not OS3 API):** Only ~2.8k of ~20k ingested datasets are in `datasetindex_v2`. Entities like `asset_19999` resolve via SQL/entity API but exact search returns 0. MAE consumer lag is 0 now; offsets were previously reset `--to-latest` during troubleshooting, dropping unconsumed MCL indexing. **Mitigation:** full `restoreIndices` (or re-ingest) — restore API itself works on Aiven OS3. → **Task 8 in progress** (restore emitted; MAE catch-up).
2. **MINOR:** `corpuserindex_v2` docs=0 → corp user search total 0, while `corpUser(datahub)` entity read works. User search/browse broken until users are reindexed. → **FIXED in Task 8** (`corpuserindex_v2` docs=1; GraphQL CORP_USER search finds `datahub`).
3. **INFO:** GMS logs both `Created OpenSearch 2.x shim` and `3.x shim` — expected because `OpenSearch3SearchClientShim extends OpenSearch2SearchClientShim`.
4. **INFO / deferred:** Semantic filtered kNN (spike D1) not validated on this stack. → **CONFIRMED in Task 8** via `scripts/probe_knn_d1.py` (engine behavior); product path uses `OpenSearch3KnnQueryBuilder` bool wrap.
5. **INFO:** B1 still uses HLRC (`requiresOpenSearchClient()==false`); not a functional fail for this validation.

- [x] **Step 3: Go / no-go recommendation**

**`GO_WITH_ISSUES`** (initial) → see Task 8 for post-remediation status.

OpenSearch 3 support via the B1 shim is functionally good for core DataHub paths against Aiven OS 3.6: bootstrap, TLS/basic auth, search, facets, autocomplete, browse, entity fetch, PIT lineage, CLL, and restoreIndices all work without engine incompatibilities. Remaining issues are index completeness / reindex hygiene and untested semantic D1 — not evidence the shim rejects OS3 APIs.

---

### Task 8: Remediation + Locust + D1 (2026-07-16 follow-up)

**Scripts added:** `scripts/restore_indices_paged.py`, `scripts/locust_os3_search.py`, `scripts/probe_knn_d1.py`

- [x] **Step 1: Reindex corpuser**

```bash
python3 scripts/restore_indices_paged.py --urn-like '%urn:li:corpuser:%'
```

Result: `rowsMigrated=53`; `corpuserindex_v2` count **1**; GraphQL `search(type:CORP_USER, query:"*")` total **1** (`urn:li:corpuser:datahub`).

- [x] **Step 2: Restore loadtest.search aspects**

```bash
# 120k aspects in MySQL for loadtest.search
python3 scripts/restore_indices_paged.py --urn-like '%loadtest.search%' --batch-size 500 --page-limit 500
# resumed once from --start 2500 after a short interruption
```

Result: OpenAPI restore completed (`rowsMigrated` totaling **120000** aspects across runs). MAE bulk-writes to Aiven are healthy (`BulkListener` success). Index catch-up is **throughput-bound** (~80–160 docs/min observed on `datasetindex_v2` after restore), not an OS3 API failure.

| Checkpoint | `datasetindex_v2` | GraphQL `loadtest.search` total |
|---|---|---|
| Pre-restore | ~2845 | ~2641 |
| ~10 min post-restore | ~3385 | ~3181 |
| +5 min sample | 3552 → **4052** (~+83–167/min) | (rising with index) |

ETA to ~20k docs at ~100 docs/min ≈ **2–3 hours** of MAE drain. Re-check:

```bash
curl -sk -u "$USER:$PASS" "https://$HOST:$PORT/datasetindex_v2/_count"
```

- [x] **Step 3: Locust (`vendor/datahub/perf-test`)**

Stock locustfiles (`search.py`, `browse.py`, `graph.py`, `ingest*.py`) hit Rest.li GMS **without auth**. This stack has `METADATA_SERVICE_AUTH_ENABLED`, so they 401 as-is.

Adapted runner: `scripts/locust_os3_search.py` (login → Bearer; Rest.li search/browse + GraphQL search/lineage).

```bash
pip3 install locust
locust -f scripts/locust_os3_search.py --headless -H http://localhost:8080 -u 10 -r 5 -t 60s
```

| Endpoint | #reqs | Fail% | Avg (ms) | p95 (ms) |
|---|---|---|---|---|
| `/entities?action=search` | 106 | 0 | 923 | 1200 |
| `/api/graphql search` | 69 | 0 | 978 | 1200 |
| `/entities?action=browse` | 36 | 0 | 1726 | 2200 |
| `/api/graphql lineage` | 32 | 0 | 6541 | 7900 |
| **Aggregated** | **243** | **0** | 1797 | 6700 |

Pass: zero failures under light concurrent load. Note: lineage ~6–8s avg on this debug-min + remote Aiven path (ops latency, not 5xx).

- [x] **Step 4: D1 kNN re-probe on Aiven OS 3.6**

```bash
python3 scripts/probe_knn_d1.py
```

| Query shape | Hits | Notes |
|---|---|---|
| Nested knn unfiltered | 2 | PASS |
| Filter **inside** `knn` (OS2 / `OpenSearch2KnnQueryBuilder`) | **0** | D1 still present on Aiven 3.6 |
| `bool` must(knn) + filter | 1 (`urn=a`) | PASS — matches `OpenSearch3KnnQueryBuilder` |

**Verdict:** D1 **CONFIRMED** on live Aiven; B1 OS3 builder workaround is the correct product path. Temp index deleted by probe.

- [x] **Step 5: Updated recommendation**

**`GO_WITH_ISSUES` → trending `GO`** once MAE finishes drain (dataset search totals ≈ MySQL 20k).

Shim functional verdict unchanged/stronger: restoreIndices at scale, authenticated Locust (0% fail), and D1 probe all consistent with OS3 support. Remaining gap is **MAE indexing throughput** after bulk restore, not query/protocol rejection.

---

### Task 9: Product semantic search (local Ollama + Aiven OS3)

**Setup**
- Host Ollama with `nomic-embed-text` (768d)
- `docker/aiven-os3.override.yml` enables:
  - `ELASTICSEARCH_SEMANTIC_SEARCH_ENABLED=true`
  - `SEARCH_SERVICE_SEMANTIC_SEARCH_ENABLED=true`
  - `EMBEDDING_PROVIDER_TYPE=local`
  - `LOCAL_EMBEDDING_ENDPOINT=http://host.docker.internal:11434/v1/embeddings`
- GMS logs: `Initialized LocalEmbeddingProvider`, `modelEmbeddingKey=nomic_embed_text`, `SemanticSearchPlugin` registered
- Index `documentindex_v2_semantic` created on Aiven (mapping includes `nomic_embed_text`)

**E2E script:** `scripts/semantic_os3_e2e.py`

| Check | Result |
|---|---|
| AppConfig semantic enabled + local provider | PASS |
| Create Document + emit `semanticContent` (MySQL) | PASS |
| MAE dual-write to `documentindex_v2(_semantic)` | **BLOCKED** by prior loadtest restore backlog (MAE still bulk-indexing datasets) |
| Direct seed to `documentindex_v2_semantic` (workaround) | PASS — 3 docs |
| `semanticSearchAcrossEntities` “how do I request access…” | **PASS** — total 3; **#1 = Data Access Request Process** (query embedded via Ollama on GMS) |
| Filtered semantic (`typeNames=model`) | **PASS** — total 1 (Churn Prediction) — product path uses `OpenSearch3KnnQueryBuilder` bool wrap; **D1 workaround validated end-to-end** |

**Note:** Dual-write blockage is ops/backlog, not OS3 rejection. Once MAE drains (or docs are restored after lag=0), remove `--seed-opensearch` and rely on UpdateIndices dual-write.

---

## Execution log (2026-07-16)

Executed inline against live `debug-min` + Aiven OS 3.6. Tasks 1–7 completed as above.

Follow-up Task 8 (same day): corpuser restore DONE; loadtest restore emitted + MAE catch-up IN PROGRESS; Locust PASS; D1 CONFIRMED.

---

## Self-review

1. **Spec coverage:** Covers smoke checklist in `docs/spike/gms-aiven-smoke.md` items 1–7, spike risks D1/PIT/shards, and live GraphQL paths.
2. **No placeholders:** Commands and queries are concrete.
3. **Execution order:** Bootstrap → search → entity → lineage → ops → negatives → verdict → remediation/locust/D1.
)
