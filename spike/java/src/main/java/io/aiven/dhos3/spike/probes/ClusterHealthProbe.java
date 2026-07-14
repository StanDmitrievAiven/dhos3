package io.aiven.dhos3.spike.probes;

import io.aiven.dhos3.spike.Probe;
import io.aiven.dhos3.spike.ProbeContext;
import io.aiven.dhos3.spike.ProbeResult;
import org.opensearch.client.opensearch._types.HealthStatus;
import org.opensearch.client.opensearch.cluster.HealthResponse;

/** P02 — cluster health. */
public final class ClusterHealthProbe implements Probe {
  @Override
  public ProbeResult run(ProbeContext ctx) throws Exception {
    HealthResponse health = ctx.client().cluster().health();
    HealthStatus status = health.status();
    if (status == HealthStatus.Red) {
      return ProbeResult.fail("P02", "cluster health", "status=red");
    }
    return ProbeResult.pass(
        "P02",
        "cluster health",
        "status="
            + status
            + " nodes="
            + health.numberOfNodes()
            + " shards="
            + health.activeShards());
  }
}
