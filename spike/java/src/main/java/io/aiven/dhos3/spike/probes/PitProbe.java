package io.aiven.dhos3.spike.probes;

import io.aiven.dhos3.spike.OpenSearchProbeClient;
import io.aiven.dhos3.spike.Probe;
import io.aiven.dhos3.spike.ProbeContext;
import io.aiven.dhos3.spike.ProbeResult;
import java.util.Map;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.core.CreatePitResponse;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Pit;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.DeleteIndexRequest;

/** P12 — Point-in-Time lifecycle (critical for DataHub graph/search). */
public final class PitProbe implements Probe {
  @Override
  public ProbeResult run(ProbeContext ctx) throws Exception {
    OpenSearchClient client = ctx.client();
    String index = OpenSearchProbeClient.newIndex("dhos3-pit");
    try {
      client
          .indices()
          .create(
              CreateIndexRequest.of(
                  c ->
                      c.index(index)
                          .mappings(
                              TypeMapping.of(
                                  m -> m.properties("n", Property.of(p -> p.integer(i -> i)))))));
      client.index(
          i -> i.index(index).id("1").document(Map.of("n", 1)).refresh(Refresh.True));

      CreatePitResponse pit =
          client.createPit(c -> c.index(index).keepAlive(Time.of(t -> t.time("1m"))));
      String pitId = pit.pitId();
      if (pitId == null || pitId.isBlank()) {
        return ProbeResult.fail("P12", "PIT lifecycle", "missing pitId");
      }

      SearchResponse<Map> search =
          client.search(
              s ->
                  s.size(10)
                      .pit(Pit.of(p -> p.id(pitId).keepAlive("1m")))
                      .query(q -> q.matchAll(m -> m)),
              Map.class);

      client.deletePit(d -> d.pitId(pitId));
      return ProbeResult.pass(
          "P12",
          "PIT lifecycle",
          "hits=" + search.hits().hits().size() + " create+search+delete ok");
    } finally {
      try {
        client.indices().delete(DeleteIndexRequest.of(d -> d.index(index).ignoreUnavailable(true)));
      } catch (Exception ignored) {
      }
    }
  }
}
