# Team review pack — OpenSearch 3 × DataHub

Internal review **before** any PR to `datahub-project/datahub`. Nothing here has been submitted upstream.

## Repos

| Repo | Role | Branch / pin |
|---|---|---|
| [StanDmitrievAiven/dhos3](https://github.com/StanDmitrievAiven/dhos3) | Spike, ADR, RFC draft, Aiven validation, local tooling | `main` |
| [StanDmitrievAiven/datahub](https://github.com/StanDmitrievAiven/datahub) | Fork with B1 shim implementation | `feat/opensearch-3-shim` @ `dff672be93` |

`dhos3` vendors DataHub as a **submodule** pointed at the fork (so the shim commit is fetchable).

## Clone

```bash
git clone --recurse-submodules https://github.com/StanDmitrievAiven/dhos3.git
cd dhos3
git -C vendor/datahub status   # expect feat/opensearch-3-shim @ dff672be93
```

If the submodule did not check out:

```bash
git submodule update --init --recursive
git -C vendor/datahub fetch origin feat/opensearch-3-shim
git -C vendor/datahub checkout dff672be93
```

## What to read first

1. **RFC draft (primary for design review)**  
   [docs/rfcs/draft-opensearch-3-support.md](./rfcs/draft-opensearch-3-support.md)  
   Shaped for eventual upstream `docs/rfcs/active/` — not opened on datahub-project yet.

2. **Validation evidence (Aiven OS 3.6)**  
   [docs/superpowers/plans/2026-07-16-os3-support-validation.md](./superpowers/plans/2026-07-16-os3-support-validation.md)

3. **ADR / spike**  
   - [docs/specs/2026-07-14-opensearch3-shim-adr.md](./specs/2026-07-14-opensearch3-shim-adr.md)  
   - [docs/spike/go-no-go.md](./spike/go-no-go.md)

4. **Implementation (fork)**  
   Branch compare:  
   https://github.com/StanDmitrievAiven/datahub/compare/master...feat/opensearch-3-shim  
   Key pieces: `OPENSEARCH_3`, `OpenSearch3SearchClientShim`, `OpenSearch3KnnQueryBuilder` (D1).

## Local stack (optional)

Requires Aiven OS3 credentials in `.env` (never commit — see `.env.example`):

```bash
cp .env.example .env   # fill AIVEN_OPENSEARCH_*
./scripts/quickstart-aiven-os3.sh up
./scripts/quickstart-aiven-os3.sh smoke
```

Semantic / Locust / D1 helpers live under `scripts/` (see validation plan Task 8–9).

## Out of scope for this review

- Opening an RFC PR or implementation PR on **datahub-project/datahub**
- OSS UI wiring for semantic search (API-only in Core today)
- B2 native `opensearch-java` migration (called out as future work in the RFC)
