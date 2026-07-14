package io.aiven.dhos3.spike;

/** Result of a single spike probe. */
public record ProbeResult(String id, String name, Status status, String detail) {

  public enum Status {
    PASS,
    FAIL,
    SKIP,
    PARTIAL
  }

  public static ProbeResult pass(String id, String name, String detail) {
    return new ProbeResult(id, name, Status.PASS, detail);
  }

  public static ProbeResult fail(String id, String name, String detail) {
    return new ProbeResult(id, name, Status.FAIL, detail);
  }

  public static ProbeResult skip(String id, String name, String detail) {
    return new ProbeResult(id, name, Status.SKIP, detail);
  }

  public boolean requiredFailed() {
    return status == Status.FAIL;
  }

  @Override
  public String toString() {
    return String.format("%-4s %-8s %s — %s", id, status, name, detail);
  }
}
