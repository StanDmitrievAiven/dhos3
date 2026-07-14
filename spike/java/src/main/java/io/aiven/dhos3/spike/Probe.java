package io.aiven.dhos3.spike;

@FunctionalInterface
public interface Probe {
  ProbeResult run(ProbeContext ctx) throws Exception;
}
