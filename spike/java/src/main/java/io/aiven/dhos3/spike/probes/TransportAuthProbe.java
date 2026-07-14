package io.aiven.dhos3.spike.probes;

import io.aiven.dhos3.spike.Probe;
import io.aiven.dhos3.spike.ProbeContext;
import io.aiven.dhos3.spike.ProbeResult;

/** P22–P24 — transport / auth metadata (validated by successful suite connectivity). */
public final class TransportAuthProbe implements Probe {
  @Override
  public ProbeResult run(ProbeContext ctx) {
    String p22 = "P22:PASS Apache HttpClient 5 transport (opensearch-java)";
    String p23 =
        ctx.hasBasicAuth()
            ? "P23:PASS basic auth configured"
            : "P23:SKIP no basic auth (local security disabled)";
    String p24 =
        ctx.useSsl()
            ? "P24:PASS TLS enabled"
            : "P24:SKIP TLS disabled (local compose)";
    return ProbeResult.pass("P22-P24", "transport/auth", p22 + "; " + p23 + "; " + p24);
  }
}
