package io.aiven.dhos3.spike;

import org.opensearch.client.opensearch.OpenSearchClient;

/** Shared context for spike probes. */
public final class ProbeContext {
  private final OpenSearchClient client;
  private final String endpoint;
  private final boolean useSsl;
  private final boolean hasBasicAuth;

  public ProbeContext(
      OpenSearchClient client, String endpoint, boolean useSsl, boolean hasBasicAuth) {
    this.client = client;
    this.endpoint = endpoint;
    this.useSsl = useSsl;
    this.hasBasicAuth = hasBasicAuth;
  }

  public OpenSearchClient client() {
    return client;
  }

  public String endpoint() {
    return endpoint;
  }

  public boolean useSsl() {
    return useSsl;
  }

  public boolean hasBasicAuth() {
    return hasBasicAuth;
  }
}
