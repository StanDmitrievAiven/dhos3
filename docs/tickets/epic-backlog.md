# Epic & ticket backlog — OpenSearch 3 / DataHub

Use these as GitHub issues in [StanDmitrievAiven/dhos3](https://github.com/StanDmitrievAiven/dhos3). Labels suggested: `phase-0`…`phase-5`, `spike`, `architecture`, `implementation`, `testing`, `docs`, `upstream`.

---

## Epic E0 — Bootstrap

| ID | Type | Title | Acceptance criteria |
|---|---|---|---|
| T0.1 | docs | Scaffold README, CONTRIBUTING, gitignore, env example | Repo cloneable; phases documented |
| T0.2 | chore | Add `vendor/datahub` submodule + pin file | SHA recorded in `docs/spike/datahub-pin.txt` |
| T0.3 | infra | Local OS3 docker-compose + healthcheck | `curl :9200` returns version 3.x |
| T0.4 | docs | Create empty spike matrix + go-no-go templates | Templates exist under `docs/spike/` |

## Epic E1 — Spike research

| ID | Type | Title | Acceptance criteria |
|---|---|---|---|
| T1.1 | spike | Java `opensearch-java` probe module skeleton | Runs; prints cluster info |
| T1.2 | spike | Probes P01–P15 (core ops) | Matrix rows filled for local |
| T1.3 | spike | Probes P16–P21 (semantic/kNN) | Matrix + builder delta notes |
| T1.4 | spike | Transport/HC5 notes | `transport-notes.md` written |
| T1.5 | spike | Aiven OS3 full probe run | `aiven-results.md` + matrix Aiven column |
| T1.6 | architecture | Go/no-go memo | `GO` / `GO_WITH_DELTAS` / `NO_GO` filed; Path B gated |

## Epic E2 — Architecture

| ID | Type | Title | Acceptance criteria |
|---|---|---|---|
| T2.1 | architecture | Path B ADR from spike | Client version, builder reuse, IAM, PR stack locked |
| T2.2 | architecture | Method coverage appendix | Every `SearchClientShim` method mapped |
| T2.3 | docs | Update design spec if deltas change approach | Spec consistent with ADR |

## Epic E3 — Path B implementation

| ID | Type | Title | Acceptance criteria |
|---|---|---|---|
| T3.1 | implementation | `OPENSEARCH_3` enum + factory + yaml | Unit tests green; OS2 default unchanged |
| T3.2 | implementation | Add `opensearch-java` deps | `metadata-io` compiles; OS2/ES8 tests still compile |
| T3.3 | implementation | OS3 shim connection + cluster | IT: version starts with `3.` |
| T3.4 | implementation | OS3 shim index/doc/search/PIT | ITs green on Testcontainers OS3 |
| T3.5 | implementation | OS3 shim bulk/reindex/tasks | Reindex IT green |
| T3.6 | implementation | Semantic/kNN + `isOpenSearch()` call-site audit | Semantic ITs green; no hard `== OPENSEARCH_2` bugs |
| T3.7 | implementation | AWS IAM HC5 signing path | Unit tests for signer; user/pass path unaffected |
| T3.8 | docs | Product docs + Helm/engine allow-list | Shim doc matrix includes OS3 |

## Epic E4 — Test engineering & validation

| ID | Type | Title | Acceptance criteria |
|---|---|---|---|
| T4.1 | testing | `OpenSearch3TestContainer` + shim IT class | CI-Runnable Gradle tests |
| T4.2 | testing | Regression suite ES7/ES8/OS2 subset | No enum/switch exhaustiveness failures |
| T4.3 | testing | Local GMS smoke checklist | `gms-local-smoke.md` |
| T4.4 | testing | Aiven GMS smoke checklist | `gms-aiven-smoke.md` (no secrets) |
| T4.5 | testing | Contribution checklist complete | All boxes in contribution checklist |

## Epic E5 — Upstream

| ID | Type | Title | Acceptance criteria |
|---|---|---|---|
| T5.1 | upstream | Upstream issue + spike evidence link | Issue opened on datahub-project/datahub |
| T5.2 | upstream | PR1 engine type + deps | CI green; no default behavior change |
| T5.3 | upstream | PR2 core shim + Testcontainers | Reviewable; ITs green |
| T5.4 | upstream | PR3 semantic + IAM | Parity claims evidenced |
| T5.5 | upstream | PR4 docs/Helm polish | Support matrix published |

---

## Issue body template

```markdown
### Context
Related epic: E#
Plan: docs/plans/2026-07-14-opensearch3-datahub-implementation.md
Spec: docs/specs/2026-07-14-opensearch3-datahub-design.md

### Goal
…

### Acceptance criteria
- [ ] …

### Test plan
- [ ] …

### Out of scope
…
```
