package io.aiven.dhos3.spike.probes;

import io.aiven.dhos3.spike.Probe;
import io.aiven.dhos3.spike.ProbeResult;
import java.util.Map;
import java.util.UUID;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.DeleteIndexRequest;
import org.opensearch.client.opensearch.indices.ExistsRequest;

/** P04 + P08 — create index, index/get/delete document, delete index. */
public final class IndexDocumentProbe implements Probe {
  @Override
  public ProbeResult run(OpenSearchClient client) throws Exception {
    String index = "dhos3-spike-" + UUID.randomUUID().toString().substring(0, 8);
    try {
      client
          .indices()
          .create(
              CreateIndexRequest.of(
                  c ->
                      c.index(index)
                          .mappings(
                              TypeMapping.of(
                                  m ->
                                      m.properties(
                                          "title",
                                          Property.of(p -> p.text(t -> t)))))));

      boolean exists =
          client.indices().exists(ExistsRequest.of(e -> e.index(index))).value();
      if (!exists) {
        return ProbeResult.fail("P04", "create index", "exists=false after create");
      }

      client.index(
          IndexRequest.of(
              i ->
                  i.index(index)
                      .id("1")
                      .document(Map.of("title", "hello dhos3"))
                      .refresh(org.opensearch.client.opensearch._types.Refresh.True)));

      var get = client.get(g -> g.index(index).id("1"), Map.class);
      if (!get.found()) {
        return ProbeResult.fail("P08", "index/get/delete document", "document not found");
      }

      client.delete(d -> d.index(index).id("1"));
      return ProbeResult.pass(
          "P04/P08", "create index + document CRUD", "index=" + index + " round-trip ok");
    } finally {
      try {
        client.indices().delete(DeleteIndexRequest.of(d -> d.index(index).ignoreUnavailable(true)));
      } catch (Exception ignored) {
        // best-effort cleanup
      }
    }
  }
}
