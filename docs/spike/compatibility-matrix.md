# Compatibility matrix (spike)

**OpenSearch image pin:** see `opensearch-pin.txt`  
**Client pin:** see `client-pin.txt`  
**DataHub pin:** see `datahub-pin.txt`

| ID | Area | Operation | OS2 baseline (DataHub) | Local OS3 | Aiven OS3 | Notes / API used |
|---|---|---|---|---|---|---|
| P01 | Cluster | info/version | PASS | | | |
| P02 | Cluster | health | PASS | | | |
| P03 | Cluster | settings get/update | PASS | | | |
| P04 | Index | create/mappings/delete | PASS | | | |
| P05 | Index | settings `index.knn` | PASS | | | |
| P06 | Index | aliases | PASS | | | |
| P07 | Index | refresh | PASS | | | |
| P08 | Doc | index/get/delete | PASS | | | |
| P09 | Doc | bulk | PASS | | | |
| P10 | Search | bool + aggs | PASS | | | |
| P11 | Search | scroll | PASS | | | |
| P12 | Search | PIT lifecycle | PASS | | | |
| P13 | Search | count/explain | PASS | | | |
| P14 | Tasks | list | PASS | | | |
| P15 | Reindex | submit + poll | PASS | | | |
| P16 | Semantic | knn_vector mapping + method | PASS | | | |
| P17 | Semantic | nested embedding docs | PASS | | | |
| P18 | Semantic | nested knn + filter | PASS | | | |
| P19 | Semantic | ef_search | PASS | | | |
| P20 | Semantic | nested depth default | PASS | | | |
| P21 | Semantic | deprecated knn index settings | N/A (unused) | | | |
| P22 | Transport | HC5 client | N/A (HC4 today) | | | |
| P23 | Auth | basic auth | PASS | | | |
| P24 | Auth | TLS | PASS (prod) | | | |

Status values: `PASS` | `FAIL` | `PARTIAL` | `SKIP` | `TBD`
