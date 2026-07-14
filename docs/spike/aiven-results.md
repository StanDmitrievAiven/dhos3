# Aiven OS3 probe results

**Date:** 2026-07-14  
**OpenSearch version:** 3.6.0  
**Endpoint:** `*.a.aivencloud.com:14208` (HTTPS + basic auth; credentials in local `.env` only)

| Probe ID | Result | Notes |
|---|---|---|
| P01 | PASS | 3.6.0 |
| P02 | PASS | green |
| P03 | PASS | settings get |
| P04/P08 | PASS | |
| P05 | PASS | index.knn + knn_vector |
| P06 | PASS | |
| P07 | PASS | |
| P09 | PASS | |
| P10 | PASS | |
| P11 | PASS | |
| P12 | PASS | PIT |
| P13 | PASS | |
| P14 | PASS | |
| P15 | PASS | reindex |
| P16–P20 | PASS (P18 PARTIAL) | nested knn OK; in-knn filter empty; bool filter OK |
| P21 | PASS | deprecated knn index setting rejected |
| P22–P24 | PASS | HC5 + basic auth + TLS |

## TLS / auth

- Scheme: HTTPS + basic auth
- Client: `opensearch-java:3.7.0` + HC5 (spike trust-all)

## Follow-ups

- Path B delta D1: verify DataHub in-knn filter JSON on OS3
- Human sign-off on `go-no-go.md` before submodule implementation
