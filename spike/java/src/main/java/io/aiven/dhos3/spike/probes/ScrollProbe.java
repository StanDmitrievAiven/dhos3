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
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.DeleteIndexRequest;

/** P11 — scroll. */
public final class ScrollProbe implements Probe {
  @Override
  public ProbeResult run(ProbeContext ctx) throws Exception {
    OpenSearchClient client = ctx.client();
    String index = OpenSearchProbeClient.newIndex("dhos3-scroll");
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
      for (int i = 0; i < 3; i++) {
        final int n = i;
        client.index(
            idx ->
                idx.index(index)
                    .id(String.valueOf(n))
                    .document(Map.of("n", n))
                    .refresh(Refresh.True));
      }

      SearchResponse<Map> first =
          client.search(
              s ->
                  s.index(index)
                      .size(1)
                      .scroll(Time.of(t -> t.time("1m")))
                      .query(q -> q.matchAll(m -> m)),
              Map.class);
      String scrollId = first.scrollId();
      if (scrollId == null || scrollId.isBlank()) {
        return ProbeResult.fail("P11", "scroll", "missing scrollId");
      }
      SearchResponse<Map> second =
          client.scroll(sc -> sc.scrollId(scrollId).scroll(Time.of(t -> t.time("1m"))), Map.class);
      client.clearScroll(c -> c.scrollId(scrollId));
      return ProbeResult.pass(
          "P11",
          "scroll",
          "firstHits="
              + first.hits().hits().size()
              + " secondHits="
              + second.hits().hits().size());
    } finally {
      try {
        client.indices().delete(DeleteIndexRequest.of(d -> d.index(index).ignoreUnavailable(true)));
      } catch (Exception ignored) {
      }
    }
  }
}
