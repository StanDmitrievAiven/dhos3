package io.aiven.dhos3.spike;

import org.opensearch.client.opensearch.OpenSearchClient;

@FunctionalInterface
public interface Probe {
  ProbeResult run(OpenSearchClient client) throws Exception;
}
