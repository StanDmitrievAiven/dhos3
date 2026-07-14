package io.aiven.dhos3.spike;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;

/** Builds an {@link OpenSearchClient} from OPENSEARCH_* or AIVEN_OPENSEARCH_* env vars. */
public final class OpenSearchProbeClient implements AutoCloseable {

  private final OpenSearchTransport transport;
  private final OpenSearchClient client;
  private final String endpointDescription;

  private OpenSearchProbeClient(
      OpenSearchTransport transport, OpenSearchClient client, String endpointDescription) {
    this.transport = transport;
    this.client = client;
    this.endpointDescription = endpointDescription;
  }

  public OpenSearchClient client() {
    return client;
  }

  public String endpointDescription() {
    return endpointDescription;
  }

  public static OpenSearchProbeClient fromEnv() throws Exception {
    String prefix = env("OPENSEARCH_HOST").isPresent() ? "OPENSEARCH" : "AIVEN_OPENSEARCH";
    if (env(prefix + "_HOST").isEmpty()) {
      prefix = "OPENSEARCH";
    }
    return fromPrefix(prefix);
  }

  public static OpenSearchProbeClient fromPrefix(String prefix) throws Exception {
    String host = required(prefix + "_HOST", "localhost");
    int port = Integer.parseInt(required(prefix + "_PORT", "9200"));
    boolean useSsl =
        Boolean.parseBoolean(required(prefix + "_USE_SSL", "false").toLowerCase(Locale.ROOT));
    String username = env(prefix + "_USERNAME").orElse("");
    String password = env(prefix + "_PASSWORD").orElse("");

    String scheme = useSsl ? "https" : "http";
    HttpHost httpHost = new HttpHost(scheme, host, port);

    ApacheHttpClient5TransportBuilder builder =
        ApacheHttpClient5TransportBuilder.builder(httpHost).setMapper(new JacksonJsonpMapper());

    builder.setHttpClientConfigCallback(
        (HttpAsyncClientBuilder httpClientBuilder) -> {
          if (!username.isBlank()) {
            BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                new AuthScope(httpHost),
                new UsernamePasswordCredentials(username, password.toCharArray()));
            httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
          }

          if (useSsl) {
            try {
              var sslContext =
                  SSLContextBuilder.create()
                      .loadTrustMaterial(null, (chains, authType) -> true)
                      .build();
              var tlsStrategy =
                  ClientTlsStrategyBuilder.create()
                      .setSslContext(sslContext)
                      .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                      .buildAsync();
              var connectionManager =
                  PoolingAsyncClientConnectionManagerBuilder.create()
                      .setTlsStrategy(tlsStrategy)
                      .build();
              httpClientBuilder.setConnectionManager(connectionManager);
            } catch (Exception e) {
              throw new IllegalStateException("Failed to configure TLS", e);
            }
          }
          return httpClientBuilder;
        });

    OpenSearchTransport transport = builder.build();
    OpenSearchClient client = new OpenSearchClient(transport);
    String desc = scheme + "://" + host + ":" + port;
    return new OpenSearchProbeClient(transport, client, desc);
  }

  private static String required(String key, String defaultValue) {
    return env(key).orElse(defaultValue);
  }

  private static Optional<String> env(String key) {
    String value = System.getenv(key);
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    return Optional.of(value.trim());
  }

  @Override
  public void close() throws Exception {
    transport.close();
  }
}
