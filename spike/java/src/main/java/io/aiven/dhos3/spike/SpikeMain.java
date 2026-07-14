package io.aiven.dhos3.spike;

import io.aiven.dhos3.spike.probes.AliasProbe;
import io.aiven.dhos3.spike.probes.BulkProbe;
import io.aiven.dhos3.spike.probes.ClusterHealthProbe;
import io.aiven.dhos3.spike.probes.ClusterInfoProbe;
import io.aiven.dhos3.spike.probes.ClusterSettingsProbe;
import io.aiven.dhos3.spike.probes.CountExplainProbe;
import io.aiven.dhos3.spike.probes.DeprecatedKnnSettingsProbe;
import io.aiven.dhos3.spike.probes.IndexDocumentProbe;
import io.aiven.dhos3.spike.probes.IndexKnnSettingProbe;
import io.aiven.dhos3.spike.probes.PitProbe;
import io.aiven.dhos3.spike.probes.RefreshProbe;
import io.aiven.dhos3.spike.probes.ReindexProbe;
import io.aiven.dhos3.spike.probes.ScrollProbe;
import io.aiven.dhos3.spike.probes.SearchAggProbe;
import io.aiven.dhos3.spike.probes.SemanticKnnProbe;
import io.aiven.dhos3.spike.probes.TasksProbe;
import io.aiven.dhos3.spike.probes.TransportAuthProbe;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the OpenSearch 3 spike probe suite.
 *
 * <p>Env: OPENSEARCH_HOST/PORT/USE_SSL/USERNAME/PASSWORD
 */
public final class SpikeMain {

  public static void main(String[] args) throws Exception {
    List<Probe> probes =
        List.of(
            new ClusterInfoProbe(),
            new ClusterHealthProbe(),
            new ClusterSettingsProbe(),
            new IndexDocumentProbe(),
            new IndexKnnSettingProbe(),
            new AliasProbe(),
            new RefreshProbe(),
            new BulkProbe(),
            new SearchAggProbe(),
            new ScrollProbe(),
            new PitProbe(),
            new CountExplainProbe(),
            new TasksProbe(),
            new ReindexProbe(),
            new SemanticKnnProbe(),
            new DeprecatedKnnSettingsProbe(),
            new TransportAuthProbe());

    List<ProbeResult> results = new ArrayList<>();
    int failures = 0;

    try (OpenSearchProbeClient probeClient = OpenSearchProbeClient.fromEnv()) {
      System.out.println("dhos3 spike → " + probeClient.endpointDescription());
      System.out.println();

      for (Probe probe : probes) {
        ProbeResult result;
        try {
          result = probe.run(probeClient.context());
        } catch (Exception e) {
          result =
              ProbeResult.fail(
                  probe.getClass().getSimpleName(),
                  probe.getClass().getSimpleName(),
                  e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        results.add(result);
        System.out.println(result);
        if (result.requiredFailed()) {
          failures++;
        }
      }
    }

    System.out.println();
    long pass = results.stream().filter(r -> r.status() == ProbeResult.Status.PASS).count();
    System.out.printf("Summary: %d PASS, %d FAIL, %d total%n", pass, failures, results.size());

    if (failures > 0) {
      System.exit(1);
    }
  }
}
