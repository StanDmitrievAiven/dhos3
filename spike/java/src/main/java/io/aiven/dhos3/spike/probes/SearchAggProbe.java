package io.aiven.dhos3.spike.probes;

import io.aiven.dhos3.spike.OpenSearchProbeClient;
import io.aiven.dhos3.spike.Probe;
import io.aiven.dhos3.spike.ProbeContext;
import io.aiven.dhos3.spike.ProbeResult;
import java.util.Map;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.mapping.Property;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import org.opensearch.client.opensearch.indices.DeleteIndexRequest;

/** P10 — bool search + terms aggregation. */
public final class SearchAggProbe implements Probe {
  @Override
  public ProbeResult run(ProbeContext ctx) throws Exception {
    OpenSearchClient client = ctx.client();
    String index = OpenSearchProbeClient.newIndex("dhos3-search");
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
                                      m.properties("color", Property.of(p -> p.keyword(k -> k)))
                                          .properties(
                                              "title", Property.of(p -> p.text(t -> t)))))));

      client.index(
          i ->
              i.index(index)
                  .id("1")
                  .document(Map.of("color", "red", "title", "apple"))
                  .refresh(Refresh.True));
      client.index(
          i ->
              i.index(index)
                  .id("2")
                  .document(Map.of("color", "red", "title", "cherry"))
                  .refresh(Refresh.True));
      client.index(
          i ->
              i.index(index)
                  .id("3")
                  .document(Map.of("color", "blue", "title", "berry"))
                  .refresh(Refresh.True));

      SearchResponse<Map> response =
          client.search(
              s ->
                  s.index(index)
                      .query(
                          Query.of(
                              q ->
                                  q.bool(
                                      b ->
                                          b.must(
                                              m ->
                                                  m.match(
                                                      mt -> mt.field("title").query(fv -> fv.stringValue("apple")))))))
                      .aggregations(
                          "by_color",
                          Aggregation.of(a -> a.terms(t -> t.field("color")))),
              Map.class);

      long hits = response.hits().total() != null ? response.hits().total().value() : 0;
      boolean hasAgg = response.aggregations() != null && response.aggregations().containsKey("by_color");
      if (hits < 1 || !hasAgg) {
        return ProbeResult.fail("P10", "bool + aggs", "hits=" + hits + " hasAgg=" + hasAgg);
      }
      return ProbeResult.pass("P10", "bool + aggs", "hits=" + hits + " agg=by_color");
    } finally {
      try {
        client.indices().delete(DeleteIndexRequest.of(d -> d.index(index).ignoreUnavailable(true)));
      } catch (Exception ignored) {
      }
    }
  }
}
