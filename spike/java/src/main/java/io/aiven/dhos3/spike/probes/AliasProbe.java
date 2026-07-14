package io.aiven.dhos3.spike.probes;

import io.aiven.dhos3.spike.OpenSearchProbeClient;
import io.aiven.dhos3.spike.Probe;
import io.aiven.dhos3.spike.ProbeContext;
import io.aiven.dhos3.spike.ProbeResult;
import java.util.Map;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.DeleteIndexRequest;
import org.opensearch.client.opensearch.indices.UpdateAliasesRequest;

/** P06 — aliases add/remove. */
public final class AliasProbe implements Probe {
  @Override
  public ProbeResult run(ProbeContext ctx) throws Exception {
    OpenSearchClient client = ctx.client();
    String index = OpenSearchProbeClient.newIndex("dhos3-alias");
    String alias = index + "-alias";
    try {
      client
          .indices()
          .create(
              CreateIndexRequest.of(
                  c ->
                      c.index(index)
                          .mappings(
                              TypeMapping.of(
                                  m -> m.properties("k", Property.of(p -> p.keyword(kw -> kw)))))));

      client
          .indices()
          .updateAliases(
              UpdateAliasesRequest.of(
                  u -> u.actions(a -> a.add(add -> add.index(index).alias(alias)))));

      var getAlias = client.indices().getAlias(g -> g.name(alias));
      if (getAlias.result() == null || getAlias.result().isEmpty()) {
        return ProbeResult.fail("P06", "aliases", "alias not found after add");
      }

      client
          .indices()
          .updateAliases(
              UpdateAliasesRequest.of(
                  u -> u.actions(a -> a.remove(r -> r.index(index).alias(alias)))));

      return ProbeResult.pass("P06", "aliases", "add+remove alias=" + alias);
    } finally {
      try {
        client.indices().delete(DeleteIndexRequest.of(d -> d.index(index).ignoreUnavailable(true)));
      } catch (Exception ignored) {
      }
    }
  }
}
