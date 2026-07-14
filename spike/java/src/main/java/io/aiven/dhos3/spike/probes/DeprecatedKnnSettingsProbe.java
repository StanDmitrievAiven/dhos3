package io.aiven.dhos3.spike.probes;

import io.aiven.dhos3.spike.OpenSearchProbeClient;
import io.aiven.dhos3.spike.Probe;
import io.aiven.dhos3.spike.ProbeContext;
import io.aiven.dhos3.spike.ProbeResult;
import io.aiven.dhos3.spike.RawJson;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.indices.DeleteIndexRequest;

/**
 * P21 — confirm deprecated index-level knn algo settings are not required (DataHub uses field
 * method). Attempts deprecated setting; PASS if rejected OR if unused path documented.
 */
public final class DeprecatedKnnSettingsProbe implements Probe {
  @Override
  public ProbeResult run(ProbeContext ctx) throws Exception {
    OpenSearchClient client = ctx.client();
    String index = OpenSearchProbeClient.newIndex("dhos3-knn-depr");
    try {
      String body =
          """
          {
            "settings": {
              "index": {
                "knn": true,
                "knn.algo_param.m": 16
              }
            },
            "mappings": {
              "properties": {
                "vec": {
                  "type": "knn_vector",
                  "dimension": 2,
                  "method": {
                    "name": "hnsw",
                    "space_type": "l2",
                    "engine": "faiss",
                    "parameters": { "ef_construction": 64, "m": 16 }
                  }
                }
              }
            }
          }
          """;
      try {
        RawJson.put(client, "/" + index, body);
        // If create succeeded, deprecated setting may still be tolerated — note PARTIAL/PASS with warning
        return ProbeResult.pass(
            "P21",
            "deprecated knn index settings",
            "create succeeded with index.knn.algo_param.m (tolerated); DataHub should keep field-level method only");
      } catch (Exception e) {
        String msg = e.getMessage() != null ? e.getMessage() : e.toString();
        if (msg.contains("knn.algo_param")
            || msg.contains("unknown setting")
            || msg.contains("illegal_argument")
            || msg.toLowerCase().contains("deprecated")
            || msg.contains("400")) {
          return ProbeResult.pass(
              "P21",
              "deprecated knn index settings",
              "rejected as expected: " + msg.replace('\n', ' ').substring(0, Math.min(180, msg.length())));
        }
        return ProbeResult.fail("P21", "deprecated knn index settings", msg);
      }
    } finally {
      try {
        client.indices().delete(DeleteIndexRequest.of(d -> d.index(index).ignoreUnavailable(true)));
      } catch (Exception ignored) {
      }
    }
  }
}
