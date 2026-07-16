# Local RFC drafts (dhos3)

This directory holds **local** Request for Comments drafts shaped for eventual submission to upstream DataHub:

[`datahub-project/datahub` → `docs/rfcs/`](https://github.com/datahub-project/datahub/tree/master/docs/rfcs)

Upstream process (summary):

1. Read [`docs/rfc.md`](https://github.com/datahub-project/datahub/blob/master/docs/rfc.md)
2. Copy upstream `docs/rfcs/template.md` → `docs/rfcs/active/000-<feature>.md`
3. Open a PR with the **RFC** label; rename file to include the PR number once opened
4. After acceptance, implement; move to `docs/rfcs/accepted/` when shipped

Nothing here is pushed upstream unless explicitly decided. Treat these as working drafts for Slack `#contribute-code` / discussion issues first.

| Draft | Intent |
|---|---|
| [draft-opensearch-3-support.md](./draft-opensearch-3-support.md) | Add first-class OpenSearch 3.x support via search client shim |
