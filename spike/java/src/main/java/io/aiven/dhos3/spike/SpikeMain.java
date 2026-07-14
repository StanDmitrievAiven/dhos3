package io.aiven.dhos3.spike;

import io.aiven.dhos3.spike.probes.ClusterHealthProbe;
import io.aiven.dhos3.spike.probes.ClusterInfoProbe;
import io.aiven.dhos3.spike.probes.IndexDocumentProbe;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the OpenSearch 3 spike probe suite.
 *
 * <p>Env: OPENSEARCH_HOST/PORT/USE_SSL/USERNAME/PASSWORD (local defaults) or AIVEN_OPENSEARCH_*.
 */
public final class SpikeMain {

  public static void main(String[] args) throws Exception {
    List<Probe> probes =
        List.of(new ClusterInfoProbe(), new ClusterHealthProbe(), new IndexDocumentProbe());

    List<ProbeResult> results = new ArrayList<>();
    int failures = 0;

    try (OpenSearchProbeClient probeClient = OpenSearchProbeClient.fromEnv()) {
      System.out.println("dhos3 spike → " + probeClient.endpointDescription());
      System.out.println();

      for (Probe probe : probes) {
        ProbeResult result;
        try {
          result = probe.run(probeClient.client());
        } catch (Exception e) {
          String id = probe.getClass().getSimpleName();
          result =
              ProbeResult.fail(
                  id, probe.getClass().getSimpleName(), e.getClass().getSimpleName() + ": " + e.getMessage());
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
    System.out.printf(
        "Summary: %d PASS, %d FAIL, %d total%n", pass, failures, results.size());

    if (failures > 0) {
      System.exit(1);
    }
  }
}
