# OpenSearch 3 DataHub Support — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Validate OpenSearch 3 compatibility via spike research in `dhos3`, then implement contribution-ready `OPENSEARCH_3` support in DataHub using `opensearch-java` (Path B), with full OS2 parity (core search + semantic + AWS IAM code path), validated on local Docker and Aiven OS3.

**Architecture:** Hybrid `dhos3` repo holds spike harness + docs; DataHub lives in `vendor/datahub` submodule. New `OpenSearch3SearchClientShim` mirrors the ES8 pattern (`elasticsearch-java` → `opensearch-java`). OS2 HLRC shim remains unchanged. Upstream as stacked PRs after local+Aiven validation.

**Tech Stack:** OpenSearch 3.x, `opensearch-java`, Apache HttpClient 5, Java 21, Docker Compose, Testcontainers, DataHub GMS/metadata-io shim framework, Aiven managed OpenSearch 3.

**Spec:** [docs/specs/2026-07-14-opensearch3-datahub-design.md](../specs/2026-07-14-opensearch3-datahub-design.md)

**Ticket catalog:** [docs/tickets/epic-backlog.md](../tickets/epic-backlog.md)

---

## File map (target state)

### dhos3 (this repo)

| Path | Responsibility |
|---|---|
| `README.md` | Purpose, phase gates, how to run spike/Aiven |
| `CONTRIBUTING.md` | Spike → Path B → upstream workflow |
| `spike/docker/docker-compose.yml` | Pinned OpenSearch 3.x |
| `spike/java/` | Gradle probe module (`opensearch-java`) |
| `spike/scripts/run-local.sh` | Run probes vs local compose |
| `spike/scripts/run-aiven.sh` | Run probes vs Aiven (env-based creds) |
| `docs/spike/compatibility-matrix.md` | Operation × result matrix |
| `docs/spike/go-no-go.md` | Gate decision for Path B |
| `docs/spike/aiven-results.md` | Managed validation log |
| `docs/specs/…-design.md` | Architecture decisions |
| `docs/plans/…-plan.md` | This plan |
| `vendor/datahub` | Submodule: Path B implementation |

### DataHub submodule (Path B)

| Path | Responsibility |
|---|---|
| `metadata-utils/.../SearchClientShim.java` | Add `OPENSEARCH_3`; fix `requiresOpenSearchClient()` |
| `metadata-io/.../shim/impl/OpenSearch3SearchClientShim.java` | New shim (~parity with OS2/ES8 method surface) |
| `metadata-io/.../shim/SearchClientShimUtil.java` | Factory + auto-detect `3.*` |
| `metadata-service/factories/.../SearchClientShimFactory.java` | Config string → enum |
| `metadata-service/configuration/.../application.yaml` | Document `OPENSEARCH_3` |
| `build.gradle` / module deps | `opensearch-java` + HC5 transport |
| `.../builder/opensearch2/*` | Reuse if spike OK; else `opensearch3/` copies |
| `.../AwsRequestSigning*` / OS3 transport auth | IAM on HC5 |
| `.../OpenSearchTestContainer.java` or `OpenSearch3TestContainer.java` | OS 3.x test image |
| `docs/how/elasticsearch-search-client-shim.md` | Support matrix |
| Helm values / quickstart | Engine type + image notes |

---

## Phase 0 — Bootstrap

### Task 0.1: Repository scaffold and README

**Files:**
- Create: `README.md`
- Create: `CONTRIBUTING.md`
- Create: `.gitignore`
- Create: `.env.example`

- [ ] **Step 1: Add `.gitignore`**

```gitignore
.env
.env.*
!.env.example
**/build/
**/.gradle/
**/.idea/
**/*.iml
vendor/datahub/
!vendor/datahub/.gitkeep
.DS_Store
spike/java/.gradle/
spike/java/build/
```

- [ ] **Step 2: Add `.env.example` for Aiven (no secrets)**

```bash
# Local spike (defaults match docker-compose)
OPENSEARCH_HOST=localhost
OPENSEARCH_PORT=9200
OPENSEARCH_USE_SSL=false
OPENSEARCH_USERNAME=
OPENSEARCH_PASSWORD=

# Aiven OS3 (fill locally; never commit .env)
AIVEN_OPENSEARCH_HOST=
AIVEN_OPENSEARCH_PORT=443
AIVEN_OPENSEARCH_USE_SSL=true
AIVEN_OPENSEARCH_USERNAME=
AIVEN_OPENSEARCH_PASSWORD=
```

- [ ] **Step 3: Write `README.md` summarizing phases 0–5 and hard spike gate**

- [ ] **Step 4: Write `CONTRIBUTING.md` with upstream stacking rules and DCO note**

- [ ] **Step 5: Commit**

```bash
git add README.md CONTRIBUTING.md .gitignore .env.example docs/
git commit -m "docs: bootstrap dhos3 scaffold and OpenSearch 3 design"
```

### Task 0.2: Add DataHub submodule

**Files:**
- Create: `vendor/datahub` (submodule)
- Create: `vendor/README.md`

- [ ] **Step 1: Add submodule pinned to upstream master (record SHA in docs)**

```bash
git submodule add https://github.com/datahub-project/datahub.git vendor/datahub
git -C vendor/datahub rev-parse HEAD > docs/spike/datahub-pin.txt
```

- [ ] **Step 2: Document in `vendor/README.md` how to update the pin and that Path B commits happen on a branch inside the submodule**

- [ ] **Step 3: Commit submodule metadata**

```bash
git add .gitmodules vendor/datahub vendor/README.md docs/spike/datahub-pin.txt
git commit -m "chore: add datahub submodule for Path B implementation"
```

### Task 0.3: Local OpenSearch 3 Docker Compose

**Files:**
- Create: `spike/docker/docker-compose.yml`
- Create: `spike/docker/opensearch.yml` (optional security disable for local)
- Create: `spike/scripts/wait-for-os.sh`

- [ ] **Step 1: Pin image version** — at start of work, resolve latest stable 3.x tag from Docker Hub and set `OPENSEARCH_IMAGE=opensearchproject/opensearch:3.x.y`. Record in `docs/spike/opensearch-pin.txt`.

- [ ] **Step 2: Create compose** (security plugin disabled for local spike; single node)

```yaml
services:
  opensearch:
    image: ${OPENSEARCH_IMAGE:-opensearchproject/opensearch:3.1.0}
    environment:
      discovery.type: single-node
      DISABLE_SECURITY_PLUGIN: "true"
      OPENSEARCH_JAVA_OPTS: -Xms1g -Xmx1g
      bootstrap.memory_lock: "true"
    ulimits:
      memlock:
        soft: -1
        hard: -1
    ports:
      - "9200:9200"
    healthcheck:
      test: ["CMD-SHELL", "curl -s http://localhost:9200 >/dev/null || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 30
```

- [ ] **Step 3: Start and verify**

```bash
cd spike/docker && docker compose up -d
curl -s http://localhost:9200 | tee ../../docs/spike/local-cluster-info.json
```

Expected: JSON with `"version":{"number":"3...."}` and OpenSearch tagline (not Elasticsearch).

- [ ] **Step 4: Commit compose + pin files**

---

## Phase 1 — Spike research & testing

### Task 1.1: Java probe module skeleton

**Files:**
- Create: `spike/java/settings.gradle.kts`
- Create: `spike/java/build.gradle.kts`
- Create: `spike/java/src/main/java/io/aiven/dhos3/spike/SpikeMain.java`
- Create: `spike/java/src/main/java/io/aiven/dhos3/spike/OpenSearchProbeClient.java`

- [ ] **Step 1: Create Gradle project on Java 21 with dependencies**

```kotlin
// build.gradle.kts (representative)
plugins {
  java
  application
}
java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
repositories { mavenCentral() }
dependencies {
  implementation("org.opensearch.client:opensearch-java:3.1.0") // align to pin
  implementation("org.apache.httpcomponents.client5:httpclient5:5.4.1")
  // add transport deps required by chosen opensearch-java version (check release notes)
  implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
  implementation("org.slf4j:slf4j-simple:2.0.16")
}
application { mainClass.set("io.aiven.dhos3.spike.SpikeMain") }
```

Align `opensearch-java` major with server 3.x; record exact versions in `docs/spike/client-pin.txt`.

- [ ] **Step 2: Implement `OpenSearchProbeClient` factory** reading `OPENSEARCH_*` env vars (host/port/ssl/user/pass)

- [ ] **Step 3: Implement `SpikeMain` that runs all probes and exits non-zero on any required failure**

- [ ] **Step 4: Run against local OS3**

```bash
./spike/scripts/run-local.sh
```

Expected: at least cluster info probe PASS before adding the rest.

### Task 1.2: Core operation probes

**Files:**
- Create: `spike/java/src/main/java/io/aiven/dhos3/spike/probes/*.java`
- Modify: `docs/spike/compatibility-matrix.md`

Probes to implement (one class or method group each):

| ID | Probe | Pass criteria |
|---|---|---|
| P01 | Cluster info / version | Parses major=3, distribution opensearch |
| P02 | Cluster health | Returns status |
| P03 | Cluster get/update settings | Round-trip a benign transient setting or read-only get |
| P04 | Create index + mappings + delete | Acked |
| P05 | Index settings including `index.knn=true` | Settings visible on get |
| P06 | Aliases | Add/remove alias |
| P07 | Refresh | Succeeds |
| P08 | Index / get / delete document | Doc round-trip |
| P09 | Bulk index | All items successful |
| P10 | Search bool + aggs | Hits + agg buckets |
| P11 | Scroll | Drain results |
| P12 | PIT create → search → delete | Full lifecycle |
| P13 | Count + explain | Non-error responses |
| P14 | List tasks | Returns |
| P15 | Reindex submit + completion poll | Dest docs == source |

- [ ] **Step 1: Write failing/empty matrix rows for P01–P15 in `docs/spike/compatibility-matrix.md`**

- [ ] **Step 2: Implement probes; run `./spike/scripts/run-local.sh`**

- [ ] **Step 3: Fill matrix Status column (`PASS`/`FAIL`/`PARTIAL`) with notes and API calls used**

- [ ] **Step 4: Commit probes + matrix updates**

### Task 1.3: Semantic / kNN probes

**Files:**
- Create: `spike/java/.../probes/SemanticKnnProbe.java`
- Modify: `docs/spike/compatibility-matrix.md`

| ID | Probe | Pass criteria |
|---|---|---|
| P16 | Create index with `knn_vector` + field `method` (hnsw/faiss/cosinesimil) + `index.knn=true` | Create acked |
| P17 | Index nested embedding docs (DataHub-like shape) | Bulk OK |
| P18 | Nested kNN query with filter inside knn | Returns expected neighbor |
| P19 | `method_parameters.ef_search` | Accepted / affects recall or at least no error |
| P20 | Nested depth under default `index.query.max_nested_depth` | DataHub-like nesting succeeds |
| P21 | Confirm deprecated index settings (`index.knn.algo_param.*`) rejected or unused | Document that DataHub field-level method is correct |

- [ ] **Step 1: Copy representative mapping JSON from DataHub `OpenSearch2SemanticIndexMapper` into the probe**

- [ ] **Step 2: Implement P16–P21; update matrix**

- [ ] **Step 3: Note any JSON deltas required vs OS2 builders (these become Path B adapter tasks)**

### Task 1.4: Transport / auth probes (local)

| ID | Probe | Pass criteria |
|---|---|---|
| P22 | HttpClient 5 transport connectivity | Client builds and talks to OS3 |
| P23 | Basic auth (enable security briefly OR document N/A for DISABLE_SECURITY local) | Documented |
| P24 | TLS to HTTPS endpoint (may be Aiven-only) | Deferred to Task 1.5 if local has no TLS |

- [ ] **Step 1: Document HC5 dependency tree and any conflicts observed with sample DataHub classpath notes**

- [ ] **Step 2: Record findings under `docs/spike/transport-notes.md`**

### Task 1.5: Aiven OS3 probe run

**Files:**
- Create: `spike/scripts/run-aiven.sh`
- Create: `docs/spike/aiven-results.md`

- [ ] **Step 1: Obtain Aiven OS3 connection (user provides host/user/password; store only in local `.env`)**

- [ ] **Step 2: Run full probe suite**

```bash
set -a && source .env && set +a
./spike/scripts/run-aiven.sh
```

- [ ] **Step 3: Write `docs/spike/aiven-results.md`** with date, OpenSearch version, PASS/FAIL per probe, TLS notes

- [ ] **Step 4: Append Aiven column to compatibility matrix**

### Task 1.6: Go / no-go decision

**Files:**
- Create: `docs/spike/go-no-go.md`

- [ ] **Step 1: Summarize required vs optional probe outcomes**

- [ ] **Step 2: Explicit decision: `GO` | `GO_WITH_DELTAS` | `NO_GO`**

- [ ] **Step 3: If `GO_WITH_DELTAS`, list each delta as a Path B requirement (ID → mitigation task)**

- [ ] **Step 4: Commit; do not start Phase 3 until this file exists with GO/GO_WITH_DELTAS**

**Gate:** Phase 3 blocked until Task 1.6 complete.

---

## Phase 2 — Architecture lock (ADR)

### Task 2.1: Write Path B ADR from spike results

**Files:**
- Create: `docs/specs/2026-07-14-opensearch3-shim-adr.md`
- Modify: design spec “Risks” if deltas change approach

ADR must lock:

1. Client library + exact version range  
2. Whether OS2 semantic builders are reused or forked to `opensearch3/`  
3. IAM approach on HC5 (class names, config flags)  
4. Auto-detect rules  
5. Testcontainers image pin  
6. Upstream PR stack boundaries  

- [ ] **Step 1: Draft ADR**

- [ ] **Step 2: Review against `SearchClientShim` method list — every method must map to an OS3 implementation strategy (native client call vs low-level REST)**

- [ ] **Step 3: Commit ADR; link from README**

---

## Phase 3 — Path B implementation (DataHub submodule)

Work on branch `feat/opensearch-3-shim` inside `vendor/datahub`.

### Task 3.1: Engine type + factory wiring (no OS3 client yet)

**Files:**
- Modify: `metadata-utils/src/main/java/com/linkedin/metadata/utils/elasticsearch/SearchClientShim.java`
- Modify: `metadata-service/factories/src/main/java/com/linkedin/gms/factory/search/SearchClientShimFactory.java`
- Modify: `metadata-io/.../SearchClientShimUtil.java` (detect + switch)
- Modify: `metadata-service/configuration/src/main/resources/application.yaml`
- Test: existing `SearchClientShimUtilTest`, `SearchClientShimTest`, `SearchClientShimIterationTest`

- [ ] **Step 1: Write failing unit tests for `OPENSEARCH_3` enum properties**

```java
assertTrue(SearchEngineType.OPENSEARCH_3.isOpenSearch());
assertFalse(SearchEngineType.OPENSEARCH_3.supportsEs7HighLevelClient());
assertFalse(SearchEngineType.OPENSEARCH_3.requiresEs8JavaClient());
assertTrue(SearchEngineType.OPENSEARCH_3.requiresOpenSearchClient());
assertEquals("3", SearchEngineType.OPENSEARCH_3.getMajorVersion());
```

- [ ] **Step 2: Add enum value and implement `requiresOpenSearchClient()` as `this == OPENSEARCH_3`**

- [ ] **Step 3: Parse `"OPENSEARCH_3"` in factory; update error message supported types list**

- [ ] **Step 4: Auto-detect: after OS2 attempt, if version starts with `3.` return `OPENSEARCH_3` (may temporarily throw “not implemented” until Task 3.3 — prefer stub that fails clearly)**

- [ ] **Step 5: Update yaml comment for engine types**

- [ ] **Step 6: Run unit tests; commit**

```bash
./gradlew :metadata-utils:test :metadata-service:factories:test --tests '*SearchClientShim*'
git commit -m "feat(search): add OPENSEARCH_3 engine type and factory wiring"
```

### Task 3.2: Add `opensearch-java` dependencies

**Files:**
- Modify: root `build.gradle` (version ext + dependency map)
- Modify: `metadata-io/build.gradle` (implementation deps)
- Create: short note in `docs/` or PR body on classpath coexistence

- [ ] **Step 1: Add version ext e.g. `opensearchJavaClientVersion = '…'` aligned to spike pin**

- [ ] **Step 2: Add dependencies without removing `opensearch-rest-high-level-client` used by OS2**

- [ ] **Step 3: Compile `metadata-io` and run OS2 + ES8 smoke unit tests to catch clashes early**

```bash
./gradlew :metadata-io:compileJava :metadata-io:test --tests '*SearchClientShimTest'
```

- [ ] **Step 4: Commit**

```bash
git commit -m "chore(deps): add opensearch-java for OpenSearch 3 shim"
```

### Task 3.3: `OpenSearch3SearchClientShim` — connection + cluster ops

**Files:**
- Create: `metadata-io/src/main/java/com/linkedin/metadata/search/elasticsearch/client/shim/impl/OpenSearch3SearchClientShim.java`
- Create: `metadata-io/src/test/java/.../OpenSearch3SearchClientShimConnectionTest.java` (Testcontainers or mocked)
- Modify: `SearchClientShimUtil.createShim` switch case `OPENSEARCH_3`

**Template:** Follow structure of `Es8SearchClientShim` (modern client) for lifecycle; use OS2 method surface as the parity checklist.

- [ ] **Step 1: List all `SearchClientShim` methods into a checklist in the PR description / ADR appendix**

- [ ] **Step 2: Implement constructor, SSL/basic auth, `getEngineType()`, `getEngineVersion()`, `close()`, cluster health/settings**

- [ ] **Step 3: Wire factory case `OPENSEARCH_3`**

- [ ] **Step 4: Integration test: Testcontainers OS3 → `getEngineVersion()` starts with `3.`**

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(search): OpenSearch3SearchClientShim connection and cluster ops"
```

### Task 3.4: Shim — index admin + document + search + scroll + PIT

**Files:**
- Modify: `OpenSearch3SearchClientShim.java`
- Create/Modify: `OpenSearch3SearchClientShimSearchTest.java`

- [ ] **Step 1: Implement index create/get/delete/clone, mappings, settings, aliases, refresh, analyze**

- [ ] **Step 2: Implement get/index/delete document, delete-by-query, count, explain**

- [ ] **Step 3: Implement search, scroll, clearScroll, createPit, deletePit**

- [ ] **Step 4: Integration tests covering search + PIT lifecycle on OS3 container**

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(search): OpenSearch3 shim index, document, search, and PIT ops"
```

### Task 3.5: Shim — bulk + reindex + tasks + low-level

**Files:**
- Modify: `OpenSearch3SearchClientShim.java`
- Mirror OS2 bulk processor / `submitReindexTask` semantics used by `ESIndexBuilder`

- [ ] **Step 1: Implement bulk processor shim compatible with existing `BulkListener`**

- [ ] **Step 2: Implement reindex task submission + task listing used by system-update**

- [ ] **Step 3: Implement `performLowLevelRequest` equivalent if still required by any caller for OS3**

- [ ] **Step 4: Integration test: bulk index N docs; reindex to new index; doc counts match**

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(search): OpenSearch3 shim bulk, reindex, and task ops"
```

### Task 3.6: Semantic search on OS3

**Files:**
- Modify: `OpenSearch3SearchClientShim.java` (`searchKnn`, `createSemanticIndex`, `indexEmbeddings`)
- Modify or reuse: `V2SemanticSearchMappingsBuilder` / `V2SemanticSearchSettingsBuilder` dispatch (`isOpenSearch()` not `== OPENSEARCH_2`)
- Optionally create: `.../builder/opensearch3/*` if spike deltas require
- Test: port `OpenSearch2*Semantic*Test` patterns to OS3

- [ ] **Step 1: Audit all `OPENSEARCH_2` equality checks; replace with `isOpenSearch()` or explicit multi-case where behavior differs**

- [ ] **Step 2: Implement semantic methods; reuse OS2 builders if ADR says reuse**

- [ ] **Step 3: Integration tests: create semantic index, index embeddings, knn search with filter**

- [ ] **Step 4: Commit**

```bash
git commit -m "feat(search): OpenSearch3 semantic/kNN support"
```

### Task 3.7: AWS IAM auth code path

**Files:**
- Create/Modify: HC5-compatible AWS request signing interceptor under shim package
- Modify: OS3 client bootstrap when `useAwsIamAuth=true`
- Test: unit tests asserting signed headers on mock requests (no live AWS)

- [ ] **Step 1: Write unit tests for signer (host, region, service `es`, chronologically stable fixtures)**

- [ ] **Step 2: Implement interceptor wiring for OS3 transport**

- [ ] **Step 3: Ensure Aiven path (user/pass) unaffected when IAM flag false**

- [ ] **Step 4: Commit**

```bash
git commit -m "feat(search): AWS IAM signing support for OpenSearch3 shim"
```

### Task 3.8: Auto-detect + config docs in product

**Files:**
- Modify: `SearchClientShimUtil.detectEngineType`
- Modify: `docs/how/elasticsearch-search-client-shim.md`
- Modify: `docs/how/updating-datahub.md` (engine type allow-list)
- Modify: Helm chart values comments / `values.yaml` if present in repo

- [ ] **Step 1: Prefer detecting OpenSearch via distribution/tagline + version major, not only “try OS2 client first”**

- [ ] **Step 2: Update support matrix: OS 3.x ✅ (with min version from spike)**

- [ ] **Step 3: Document migration: reindex indexes created before OS 2.x; nested depth; kNN field settings**

- [ ] **Step 4: Commit**

```bash
git commit -m "docs(search): document OpenSearch 3 shim support and detection"
```

---

## Phase 4 — Validation & test engineering

### Task 4.1: Testcontainers OS3

**Files:**
- Create or modify: `metadata-io/src/test/java/io/datahubproject/test/search/OpenSearch3TestContainer.java`
- Modify: search integration test bases to run OS3 profile

- [ ] **Step 1: Container image = spike pin; `DISABLE_SECURITY_PLUGIN=true`; heap settings like OS2 container**

- [ ] **Step 2: Wire at least one IT class `SearchClientShimOpenSearch3IntegrationTest` asserting engine type `OPENSEARCH_3`**

- [ ] **Step 3: Run**

```bash
./gradlew :metadata-io:test --tests '*OpenSearch3*'
```

### Task 4.2: Regression — other engines still pass

- [ ] **Step 1: Run OS2 + ES8 shim unit/IT subsets locally**

```bash
./gradlew :metadata-io:test --tests '*OpenSearch*' --tests '*Es8*' --tests '*SearchClientShim*'
```

- [ ] **Step 2: Fix any classpath or enum exhaustiveness breakages (`switch` without `OPENSEARCH_3`)**

### Task 4.3: Manual local GMS smoke

- [ ] **Step 1: Point quickstart/debug compose elasticsearch service at OS3 or external local OS3**

- [ ] **Step 2: Set `ELASTICSEARCH_SHIM_ENGINE_TYPE=OPENSEARCH_3` (and implementation/opensearch flags as required)**

- [ ] **Step 3: Verify: GMS health, GraphQL search, browse, lineage graph query, system-update/reindex job**

- [ ] **Step 4: Record results in `docs/spike/gms-local-smoke.md`**

### Task 4.4: Aiven GMS smoke

- [ ] **Step 1: Configure GMS/MAE/MCE with Aiven host, TLS, username/password**

- [ ] **Step 2: Repeat smoke checklist from 4.3**

- [ ] **Step 3: If semantic enabled in env, verify knn path; else note deferred**

- [ ] **Step 4: Write `docs/spike/gms-aiven-smoke.md` (no secrets)**

### Task 4.5: Contribution checklist gate

- [ ] **Step 1: Complete checklist in `docs/tickets/contribution-checklist.md`**

- [ ] **Step 2: Only then open upstream PRs (Phase 5)**

---

## Phase 5 — Upstream contribution

### Task 5.1: Maintainer outreach

- [ ] **Step 1: Open (or comment on) upstream issue linking design + spike go/no-go + matrix**

- [ ] **Step 2: Confirm stacked PR preference with maintainers if possible**

### Task 5.2: Stacked PRs

| PR | Contents |
|---|---|
| PR1 | Enum, factory, deps, auto-detect stub/wiring, docs note |
| PR2 | Core shim ops + Testcontainers IT |
| PR3 | Semantic + IAM + call-site `isOpenSearch()` fixes |
| PR4 | Helm/quickstart/docs support matrix polish |

- [ ] **Step 1: Rebase submodule branch onto latest upstream master**

- [ ] **Step 2: Open PRs with links to `dhos3` spike evidence**

- [ ] **Step 3: Address review; keep OS2 default behavior unchanged**

---

## Parallel ticket types (tracking)

Use [docs/tickets/epic-backlog.md](../tickets/epic-backlog.md) for GitHub issue creation. Every phase has:

- Research / spike tickets  
- Architecture / ADR tickets  
- Implementation tickets  
- Test engineering tickets  
- Documentation / upstream tickets  

---

## Self-review (plan)

| Spec requirement | Task coverage |
|---|---|
| Spike matrix + go/no-go | 1.2–1.6 |
| Local then Aiven | 0.3, 1.5, 4.3, 4.4 |
| Path B `opensearch-java` | 3.2–3.5 |
| Semantic parity | 1.3, 3.6 |
| AWS IAM unit-tested | 3.7 |
| Hybrid dhos3 layout | 0.1–0.2 |
| Upstream stacked PRs | 5.1–5.2 |
| Non-regression ES/OS2 | 4.2 |

No intentional Path A (HLRC 3) implementation path remains in this plan.
