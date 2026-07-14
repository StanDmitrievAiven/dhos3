package io.aiven.dhos3.spike.probes;

import io.aiven.dhos3.spike.OpenSearchProbeClient;
import io.aiven.dhos3.spike.Probe;
import io.aiven.dhos3.spike.ProbeContext;
import io.aiven.dhos3.spike.ProbeResult;
import java.util.Map;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.DeleteIndexRequest;
import org.opensearch.client.opensearch.indices.RefreshRequest;
import org.opensearch.client.opensearch.indices.RefreshResponse;

/** P07 — index refresh. */
public final class RefreshProbe implements Probe {
  @Override
  public ProbeResult run(ProbeContext ctx) throws Exception {
    OpenSearchClient client = ctx.client();
    String index = OpenSearchProbeClient.newIndex("dhos3-refresh");
    try {
      client
          .indices()
          .create(
              CreateIndexRequest.of(
                  c ->
                      c.index(index)
                          .mappings(
                              TypeMapping.of(
                                  m -> m.properties("t", Property.of(p -> p.text(tx -> tx)))))));
      client.index(
          i -> i.index(index).id("1").document(Map.of("t", "x")).refresh(Refresh.False));
      RefreshResponse refresh =
          client.indices().refresh(RefreshRequest.of(r -> r.index(index)));
      return ProbeResult.pass(
          "P07", "refresh", "shards=" + (refresh.shards() != null ? refresh.shards().successful() : "?"));
    } finally {
      try {
        client.indices().delete(DeleteIndexRequest.of(d -> d.index(index).ignoreUnavailable(true)));
      } catch (Exception ignored) {
      }
    }
  }
}
