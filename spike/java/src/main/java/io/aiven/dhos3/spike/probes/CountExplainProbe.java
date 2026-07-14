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
import org.opensearch.client.opensearch.core.CountResponse;
import org.opensearch.client.opensearch.core.ExplainResponse;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.DeleteIndexRequest;

/** P13 — count + explain. */
public final class CountExplainProbe implements Probe {
  @Override
  public ProbeResult run(ProbeContext ctx) throws Exception {
    OpenSearchClient client = ctx.client();
    String index = OpenSearchProbeClient.newIndex("dhos3-count");
    try {
      client
          .indices()
          .create(
              CreateIndexRequest.of(
                  c ->
                      c.index(index)
                          .mappings(
                              TypeMapping.of(
                                  m -> m.properties("title", Property.of(p -> p.text(t -> t)))))));
      client.index(
          i ->
              i.index(index)
                  .id("1")
                  .document(Map.of("title", "hello world"))
                  .refresh(Refresh.True));

      CountResponse count = client.count(c -> c.index(index).query(q -> q.matchAll(m -> m)));
      ExplainResponse<Map> explain =
          client.explain(
              e ->
                  e.index(index)
                      .id("1")
                      .query(q -> q.match(m -> m.field("title").query(fv -> fv.stringValue("hello")))),
              Map.class);

      if (count.count() < 1) {
        return ProbeResult.fail("P13", "count/explain", "count=" + count.count());
      }
      return ProbeResult.pass(
          "P13",
          "count/explain",
          "count=" + count.count() + " matched=" + explain.matched());
    } finally {
      try {
        client.indices().delete(DeleteIndexRequest.of(d -> d.index(index).ignoreUnavailable(true)));
      } catch (Exception ignored) {
      }
    }
  }
}
