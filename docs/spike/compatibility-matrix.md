# Compatibility matrix (spike)

**OpenSearch image pin:** `3.7.0` (see `opensearch-pin.txt`)  
**Client pin:** `opensearch-java:3.7.0` (see `client-pin.txt`)  
**DataHub pin:** see `datahub-pin.txt`  
**Last run:** 2026-07-14

| ID | Area | Operation | OS2 baseline (DataHub) | Local OS3 (3.7.0) | Aiven OS3 (3.6.0) | Notes / API used |
|---|---|---|---|---|---|---|
| P01 | Cluster | info/version | PASS | PASS | PASS | `opensearch-java` InfoResponse |
| P02 | Cluster | health | PASS | PASS | PASS | |
| P03 | Cluster | settings get | PASS | PASS | PASS | read-only; update skipped on managed |
| P04 | Index | create/mappings/delete | PASS | PASS | PASS | combined with P08 |
| P05 | Index | settings `index.knn` | PASS | PASS | PASS | + `knn_vector` field method |
| P06 | Index | aliases | PASS | PASS | PASS | |
| P07 | Index | refresh | PASS | PASS | PASS | |
| P08 | Doc | index/get/delete | PASS | PASS | PASS | |
| P09 | Doc | bulk | PASS | PASS | PASS | |
| P10 | Search | bool + aggs | PASS | PASS | PASS | |
| P11 | Search | scroll | PASS | PASS | PASS | |
| P12 | Search | PIT lifecycle | PASS | PASS | PASS | create → search → delete |
| P13 | Search | count/explain | PASS | PASS | PASS | |
| P14 | Tasks | list | PASS | PASS | PASS | |
| P15 | Reindex | submit + wait | PASS | PASS | PASS | `waitForCompletion=true` |
| P16 | Semantic | knn_vector mapping + method | PASS | PASS | PASS | field-level method (OS3-safe) |
| P17 | Semantic | nested embedding docs | PASS | PASS | PASS | DataHub-like shape |
| P18 | Semantic | nested knn + filter | PASS | PARTIAL | PARTIAL | Unfiltered nested knn PASS; **in-knn `filter` returned 0 hits**; bool+nested filter PASS — Path B delta |
| P19 | Semantic | ef_search | PASS | PASS | PASS | `method_parameters.ef_search` |
| P20 | Semantic | nested depth default | PASS | PASS | PASS | single nested knn OK |
| P21 | Semantic | deprecated knn index settings | N/A | PASS | PASS | `index.knn.algo_param.m` rejected (400) |
| P22 | Transport | HC5 client | N/A (HC4) | PASS | PASS | `ApacheHttpClient5Transport` |
| P23 | Auth | basic auth | PASS | SKIP (local) | PASS | |
| P24 | Auth | TLS | PASS | SKIP (local) | PASS | trust-all in spike only |

Status values: `PASS` | `FAIL` | `PARTIAL` | `SKIP` | `TBD`
