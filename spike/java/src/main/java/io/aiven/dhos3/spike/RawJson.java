package io.aiven.dhos3.spike;

import java.io.IOException;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.generic.Request;
import org.opensearch.client.opensearch.generic.Requests;
import org.opensearch.client.opensearch.generic.Response;

/** Raw JSON REST helpers for operations not covered by typed builders (e.g. kNN). */
public final class RawJson {

  private RawJson() {}

  public static String put(OpenSearchClient client, String path, String json) throws IOException {
    return perform(client, "PUT", path, json);
  }

  public static String post(OpenSearchClient client, String path, String json) throws IOException {
    return perform(client, "POST", path, json);
  }

  public static String get(OpenSearchClient client, String path) throws IOException {
    return perform(client, "GET", path, null);
  }

  public static String delete(OpenSearchClient client, String path) throws IOException {
    return perform(client, "DELETE", path, null);
  }

  private static String perform(OpenSearchClient client, String method, String path, String json)
      throws IOException {
    String endpoint = path.startsWith("/") ? path : "/" + path;
    var builder = Requests.builder().method(method).endpoint(endpoint);
    if (json != null) {
      builder.json(json);
    }
    Request request = builder.build();
    try (Response response = client.generic().execute(request)) {
      String body =
          response.getBody().map(b -> b.bodyAsString()).orElse("");
      if (response.getStatus() >= 300) {
        throw new IOException(
            "HTTP " + response.getStatus() + " " + method + " " + endpoint + ": " + body);
      }
      return body;
    }
  }
}
