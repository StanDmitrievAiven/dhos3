# Contributing to dhos3

## Workflow

1. **Spike first** — extend probes and the compatibility matrix; never skip the go/no-go gate.
2. **ADR** — lock Path B details from spike deltas.
3. **Path B in submodule** — branch inside `vendor/datahub` (e.g. `feat/opensearch-3-shim`).
4. **Validate** — local Docker OS3, then Aiven OS3; fill smoke docs under `docs/spike/`.
5. **Upstream** — stacked PRs to `datahub-project/datahub` with links back to spike evidence here.

## Secrets

- Put Aiven (or any) credentials in `.env` only.
- Never commit `.env`, keystores, or connection URLs with embedded passwords.
- Smoke reports must redact hosts if considered sensitive; prefer version + PASS/FAIL only when needed.

## Submodule commits

Path B code reviews happen primarily on the upstream DataHub PR. Keep this repo’s commits focused on:

- spike harness and results  
- design/plan/ticket docs  
- submodule pointer updates when you want a reproducible pin  

## Upstream PR expectations

- OS2 default behavior unchanged  
- Tests for new behavior  
- Docs/support matrix updated  
- Design + spike linked in PR body  
- Prefer stacked PRs (engine wiring → core shim → semantic/IAM → docs/Helm)

Follow DataHub’s own contributing guide and DCO requirements when opening upstream PRs.
