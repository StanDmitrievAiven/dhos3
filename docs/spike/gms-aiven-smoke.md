# GMS smoke — Aiven OpenSearch 3 (Docker debug profile)

**Mode:** Option B — locally built `debug` images + compose override; search = Aiven OS3.  
**Launcher:** `./scripts/quickstart-aiven-os3.sh` (default profile `debug-min`)  
**Override:** `docker/aiven-os3.override.yml`  
**Shim:** `ELASTICSEARCH_SHIM_ENGINE_TYPE=OPENSEARCH_3` (B1 HLRC)

## Prerequisites

- JDK 21 (`JAVA_HOME` → Homebrew `openjdk@21`)
- Docker Desktop running
- `OS3D/.env` filled with `AIVEN_OPENSEARCH_*` (never commit)
- Submodule on `feat/opensearch-3-shim` with B1 changes

## Run

```bash
./scripts/quickstart-aiven-os3.sh build   # first time / after Java changes
./scripts/quickstart-aiven-os3.sh up
./scripts/quickstart-aiven-os3.sh smoke
```

UI: http://localhost:9002 — GMS: http://localhost:8080

## Checklist

| # | Check | Result |
|---|---|---|
| 1 | GMS `/health` 200 | PASS (2026-07-16) |
| 2 | GMS/SystemUpdate logs show OpenSearch 3 / shim `OPENSEARCH_3` | PASS |
| 3 | SystemUpdate completes (indices created on Aiven) | PASS — exit 0; ~80 DH indices on Aiven 3.6 (`pri=1`,`rep=0`) |
| 4 | UI loads (`:9002`) | PASS — HTTP 200 |
| 5 | Search returns results (after sample ingest) | Manual — UI http://localhost:9002 (`datahub`/`datahub`) |
| 6 | Browse / entity page loads | Manual |
| 7 | Lineage graph query does not error | Manual |

### Resolved blocker

Raised Aiven `opensearch.cluster_max_shards_per_node` to **2000** (startup-4 plan + advanced config).

## Notes

- Local `opensearch` service is an Alpine stub (depends_on only); traffic goes to Aiven.
- Set `ELASTICSEARCH_NUM_REPLICAS_PER_INDEX=0` (already in override) for single-node Aiven.
- **Shard budget:** Aiven default `cluster.max_shards_per_node=20` is too low for full DataHub
  SystemUpdate (~60+ entity indices). Raise it in the Aiven console (OpenSearch advanced config /
  custom settings) to e.g. `2000`, then re-run `./scripts/quickstart-aiven-os3.sh up`.
- Do not record passwords or connection strings in this file.
- Tear down: `./scripts/quickstart-aiven-os3.sh down`
