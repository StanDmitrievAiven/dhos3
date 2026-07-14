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
import org.opensearch.client.opensearch.core.ReindexResponse;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.DeleteIndexRequest;

/** P15 — reindex submit + wait. */
public final class ReindexProbe implements Probe {
  @Override
  public ProbeResult run(ProbeContext ctx) throws Exception {
    OpenSearchClient client = ctx.client();
    String src = OpenSearchProbeClient.newIndex("dhos3-reindex-src");
    String dest = OpenSearchProbeClient.newIndex("dhos3-reindex-dst");
    try {
      client
          .indices()
          .create(
              CreateIndexRequest.of(
                  c ->
                      c.index(src)
                          .mappings(
                              TypeMapping.of(
                                  m -> m.properties("n", Property.of(p -> p.integer(i -> i)))))));
      client
          .indices()
          .create(
              CreateIndexRequest.of(
                  c ->
                      c.index(dest)
                          .mappings(
                              TypeMapping.of(
                                  m -> m.properties("n", Property.of(p -> p.integer(i -> i)))))));

      for (int i = 0; i < 3; i++) {
        final int n = i;
        client.index(
            idx ->
                idx.index(src).id(String.valueOf(n)).document(Map.of("n", n)).refresh(Refresh.True));
      }

      ReindexResponse reindex =
          client.reindex(
              r ->
                  r.source(s -> s.index(src))
                      .dest(d -> d.index(dest))
                      .refresh(Refresh.True)
                      .waitForCompletion(true));

      CountResponse count = client.count(c -> c.index(dest));
      if (count.count() != 3) {
        return ProbeResult.fail(
            "P15",
            "reindex",
            "destCount=" + count.count() + " created=" + reindex.created());
      }
      return ProbeResult.pass("P15", "reindex", "copied=" + count.count());
    } finally {
      try {
        client.indices().delete(DeleteIndexRequest.of(d -> d.index(src, dest).ignoreUnavailable(true)));
      } catch (Exception ignored) {
      }
    }
  }
}
