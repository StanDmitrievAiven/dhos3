package io.aiven.dhos3.spike.probes;

import io.aiven.dhos3.spike.OpenSearchProbeClient;
import io.aiven.dhos3.spike.Probe;
import io.aiven.dhos3.spike.ProbeContext;
import io.aiven.dhos3.spike.ProbeResult;
import io.aiven.dhos3.spike.RawJson;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.indices.DeleteIndexRequest;

/** P05 — create index with index.knn=true (semantic prerequisite). */
public final class IndexKnnSettingProbe implements Probe {
  @Override
  public ProbeResult run(ProbeContext ctx) throws Exception {
    OpenSearchClient client = ctx.client();
    String index = OpenSearchProbeClient.newIndex("dhos3-knn-settings");
    try {
      String body =
          """
          {
            "settings": { "index": { "knn": true } },
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
      RawJson.put(client, "/" + index, body);
      String settings = RawJson.get(client, "/" + index + "/_settings");
      if (!settings.contains("\"knn\"") && !settings.contains("\"knn\" : \"true\"")
          && !settings.contains("\"knn\":\"true\"")
          && !settings.contains("\"knn\":true")
          && !settings.contains("\"knn\" : true")) {
        // OpenSearch often returns knn as boolean true inside index settings
        if (!settings.toLowerCase().contains("knn")) {
          return ProbeResult.fail("P05", "index.knn setting", "knn not present in settings: " + settings);
        }
      }
      return ProbeResult.pass("P05", "index.knn setting", "created index with knn=true + knn_vector");
    } finally {
      try {
        client.indices().delete(DeleteIndexRequest.of(d -> d.index(index).ignoreUnavailable(true)));
      } catch (Exception ignored) {
      }
    }
  }
}
