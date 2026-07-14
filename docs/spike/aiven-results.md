# Aiven OS3 probe results

**Date:** 2026-07-14  
**OpenSearch version:** 3.6.0  
**Endpoint:** `*.a.aivencloud.com:14208` (HTTPS + basic auth; credentials in local `.env` only)

| Probe ID | Result | Notes |
|---|---|---|
| P01 | PASS | distribution=opensearch number=3.6.0 |
| P02 | PASS | status=Green nodes=1 |
| P04/P08 | PASS | create index + document CRUD round-trip |
| P03, P05–P07, P09–P24 | TBD | not implemented in harness yet |

## TLS / auth

- Scheme: HTTPS + basic auth
- Client: `opensearch-java:3.7.0` over Apache HttpClient 5 with trust-all TLS (spike only)
- Local spike image remains pinned at 3.7.0; Aiven service is **3.6.0** — both accepted by `version.startsWith("3.")`

## Follow-ups

- Expand remaining probes against Aiven 3.6
- Consider dual-pin note in ADR: validate against min(3.6) and latest(3.7+)
- Rotate test credentials if this chat/log is retained long-term
