package io.aiven.dhos3.spike.probes;

import io.aiven.dhos3.spike.Probe;
import io.aiven.dhos3.spike.ProbeContext;
import io.aiven.dhos3.spike.ProbeResult;
import org.opensearch.client.opensearch.cluster.GetClusterSettingsResponse;

/** P03 — cluster settings get (read-only; avoid mutating managed clusters). */
public final class ClusterSettingsProbe implements Probe {
  @Override
  public ProbeResult run(ProbeContext ctx) throws Exception {
    GetClusterSettingsResponse settings =
        ctx.client().cluster().getSettings(g -> g.includeDefaults(true));
    if (settings.persistent() == null && settings.transient_() == null && settings.defaults() == null) {
      return ProbeResult.fail("P03", "cluster settings get", "empty settings response");
    }
    int defaultKeys = settings.defaults() != null ? settings.defaults().size() : 0;
    return ProbeResult.pass(
        "P03", "cluster settings get", "defaultsKeys=" + defaultKeys + " (update skipped; read-only)");
  }
}
