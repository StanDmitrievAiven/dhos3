package io.aiven.dhos3.spike;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
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

/** Builds an {@link OpenSearchClient} from OPENSEARCH_* env vars. */
public final class OpenSearchProbeClient implements AutoCloseable {

  private final OpenSearchTransport transport;
  private final ProbeContext context;

  private OpenSearchProbeClient(OpenSearchTransport transport, ProbeContext context) {
    this.transport = transport;
    this.context = context;
  }

  public ProbeContext context() {
    return context;
  }

  public OpenSearchClient client() {
    return context.client();
  }

  public String endpointDescription() {
    return context.endpoint();
  }

  public static OpenSearchProbeClient fromEnv() throws Exception {
    return fromPrefix("OPENSEARCH");
  }

  public static OpenSearchProbeClient fromPrefix(String prefix) throws Exception {
    String host = required(prefix + "_HOST", "localhost");
    int port = Integer.parseInt(required(prefix + "_PORT", "9200"));
    boolean useSsl =
        Boolean.parseBoolean(required(prefix + "_USE_SSL", "false").toLowerCase(Locale.ROOT));
    String username = env(prefix + "_USERNAME").orElse("");
    String password = env(prefix + "_PASSWORD").orElse("");
    boolean hasBasicAuth = !username.isBlank();

    String scheme = useSsl ? "https" : "http";
    HttpHost httpHost = new HttpHost(scheme, host, port);

    ApacheHttpClient5TransportBuilder builder =
        ApacheHttpClient5TransportBuilder.builder(httpHost).setMapper(new JacksonJsonpMapper());

    builder.setHttpClientConfigCallback(
        (HttpAsyncClientBuilder httpClientBuilder) -> {
          if (hasBasicAuth) {
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
    return new OpenSearchProbeClient(
        transport, new ProbeContext(client, desc, useSsl, hasBasicAuth));
  }

  public static String newIndex(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
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
