package io.aiven.dhos3.spike.probes;

import io.aiven.dhos3.spike.Probe;
import io.aiven.dhos3.spike.ProbeContext;
import io.aiven.dhos3.spike.ProbeResult;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.InfoResponse;

/** P01 — cluster info / version. */
public final class ClusterInfoProbe implements Probe {
  @Override
  public ProbeResult run(ProbeContext ctx) throws Exception {
    OpenSearchClient client = ctx.client();
    InfoResponse info = client.info();
    String number = info.version().number();
    String distribution =
        info.version().distribution() != null ? info.version().distribution() : "unknown";
    if (number == null || !number.startsWith("3.")) {
      return ProbeResult.fail(
          "P01", "cluster info/version", "expected 3.x version, got number=" + number);
    }
    if (info.tagline() != null && info.tagline().toLowerCase().contains("you know")) {
      return ProbeResult.fail(
          "P01", "cluster info/version", "appears to be Elasticsearch: " + info.tagline());
    }
    return ProbeResult.pass(
        "P01",
        "cluster info/version",
        "distribution=" + distribution + " number=" + number + " tagline=" + info.tagline());
  }
}
