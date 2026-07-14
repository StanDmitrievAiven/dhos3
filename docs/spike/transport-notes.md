# Transport notes (spike)

## Client stack

| Component | Version / choice |
|---|---|
| Server (local) | `opensearchproject/opensearch:3.7.0` |
| Server (Aiven) | OpenSearch **3.6.0** |
| Java client | `org.opensearch.client:opensearch-java:3.7.0` |
| HTTP | Apache HttpClient **5** via `ApacheHttpClient5TransportBuilder` |
| JDK | 21 |

## Observations

1. **HC5 works** for HTTP and HTTPS with basic auth (Aiven).
2. Spike uses **trust-all TLS** for convenience — Path B must use proper trust stores / JVM defaults for production.
3. Typed `OpenSearchClient` covers cluster/index/doc/search/scroll/PIT/bulk/reindex/tasks.
4. **kNN mappings and queries** used `client.generic()` + raw JSON (`RawJson`), matching how DataHub often builds semantic requests.
5. No classpath conflict observed in the isolated spike module (DataHub multi-client coexistence still to prove in submodule).

## IAM

Not exercised live. OS2 DataHub uses HC4 `AwsRequestSigningApacheInterceptor`. Path B needs an HC5-compatible signer (delta D3).
