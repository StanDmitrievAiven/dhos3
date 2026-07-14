# Upstream contribution checklist

Complete before opening stacked PRs against `datahub-project/datahub`.

## Evidence

- [ ] Design spec linked
- [ ] Spike compatibility matrix complete (local + Aiven columns)
- [ ] Go/no-go is `GO` or `GO_WITH_DELTAS` (deltas implemented)
- [ ] ADR locked (client version, builders, IAM, PR stack)

## Product behavior

- [ ] `OPENSEARCH_3` selectable via config / env
- [ ] Auto-detect recognizes OpenSearch 3.x
- [ ] OS2 / ES7 / ES8 defaults unchanged
- [ ] Core search, PIT, bulk, reindex verified on local OS3
- [ ] Semantic/kNN verified (or explicitly waived with maintainer agreement)
- [ ] AWS IAM code path unit-tested
- [ ] Aiven OS3 GMS smoke passed (TLS + basic auth)

## Engineering quality

- [ ] Testcontainers OS3 IT(s) in `metadata-io`
- [ ] Call sites audited (`isOpenSearch()` vs `== OPENSEARCH_2`)
- [ ] No secrets in git history
- [ ] Dependency coexistence verified (HLRC 2.x + opensearch-java)
- [ ] Docs: shim guide support matrix + upgrade caveats
- [ ] Helm/values or env allow-list updated

## PR hygiene

- [ ] Stacked PRs with clear boundaries
- [ ] Each PR has test plan in description
- [ ] Links to `dhos3` spike artifacts
- [ ] DCO / CLA satisfied per DataHub norms
