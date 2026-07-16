# dhos3 — OpenSearch 3 × DataHub OSS

Validation and implementation workspace for adding **OpenSearch 3** support to [DataHub](https://github.com/datahub-project/datahub), intended for upstream contribution.

## Why this repo

DataHub OSS today supports OpenSearch **2.x** only (`OpenSearch2SearchClientShim`). This repo holds:

1. **Spike research** — compatibility probes vs local OS3 and Aiven OS3  
2. **Design + plan + tickets** — contribution-ready architecture  
3. **Path B implementation** — in a DataHub git submodule using `opensearch-java`

## Team review (pre-upstream)

Share this pack with colleagues **before** opening anything on `datahub-project/datahub`:

→ **[docs/TEAM_REVIEW.md](docs/TEAM_REVIEW.md)** — clone instructions, RFC, validation, fork branch links.

| Repo | Link |
|---|---|
| This workspace | https://github.com/StanDmitrievAiven/dhos3 |
| DataHub fork (B1 shim) | https://github.com/StanDmitrievAiven/datahub/tree/feat/opensearch-3-shim |

## Documents

| Doc | Purpose |
|---|---|
| [Team review pack](docs/TEAM_REVIEW.md) | Where to start for internal review |
| [RFC draft](docs/rfcs/draft-opensearch-3-support.md) | Upstream-shaped RFC (local only) |
| [Aiven OS3 validation](docs/superpowers/plans/2026-07-16-os3-support-validation.md) | Live stack evidence |
| [Design spec](docs/specs/2026-07-14-opensearch3-datahub-design.md) | Goals, decisions, architecture |
| [Implementation plan](docs/plans/2026-07-14-opensearch3-datahub-implementation.md) | Phased tasks with checkboxes |
| [Epic backlog](docs/tickets/epic-backlog.md) | Ticket catalog |
| [Contribution checklist](docs/tickets/contribution-checklist.md) | Upstream gate |
| [Compatibility matrix](docs/spike/compatibility-matrix.md) | Spike results |
| [Go/no-go](docs/spike/go-no-go.md) | Path B gate |

## Phase gates

```
0 Bootstrap → 1 Spike → 2 ADR → 3 Path B → 4 Validate (local+Aiven) → 5 Upstream PRs
```

**Hard rule:** do not implement Path B in `vendor/datahub` until `docs/spike/go-no-go.md` is `GO` or `GO_WITH_DELTAS`.

## Quick start (after bootstrap tasks)

```bash
# Local OpenSearch 3
cd spike/docker && docker compose up -d

# Probes (once Java module exists)
./spike/scripts/run-local.sh

# Aiven (creds in .env — never commit)
cp .env.example .env   # fill AIVEN_* 
./spike/scripts/run-aiven.sh
```

## Scope (v1)

- Core search parity + semantic/kNN + AWS IAM **code path** (unit-tested)
- Managed validation on **Aiven OpenSearch 3**
- Stacked upstream PRs to DataHub OSS

See [CONTRIBUTING.md](CONTRIBUTING.md).
