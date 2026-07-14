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
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.DeleteIndexRequest;

/** P09 — bulk index. */
public final class BulkProbe implements Probe {
  @Override
  public ProbeResult run(ProbeContext ctx) throws Exception {
    OpenSearchClient client = ctx.client();
    String index = OpenSearchProbeClient.newIndex("dhos3-bulk");
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

      BulkResponse bulk =
          client.bulk(
              BulkRequest.of(
                  b -> {
                    for (int i = 0; i < 5; i++) {
                      final int n = i;
                      b.operations(
                          op ->
                              op.index(
                                  idx ->
                                      idx.index(index)
                                          .id(String.valueOf(n))
                                          .document(Map.of("n", n))));
                    }
                    return b.refresh(Refresh.True);
                  }));

      if (bulk.errors()) {
        return ProbeResult.fail("P09", "bulk", "bulk had errors");
      }
      return ProbeResult.pass("P09", "bulk", "items=" + bulk.items().size());
    } finally {
      try {
        client.indices().delete(DeleteIndexRequest.of(d -> d.index(index).ignoreUnavailable(true)));
      } catch (Exception ignored) {
      }
    }
  }
}
