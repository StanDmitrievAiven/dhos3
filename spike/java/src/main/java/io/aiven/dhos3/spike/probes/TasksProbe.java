package io.aiven.dhos3.spike.probes;

import io.aiven.dhos3.spike.Probe;
import io.aiven.dhos3.spike.ProbeContext;
import io.aiven.dhos3.spike.ProbeResult;
import org.opensearch.client.opensearch.tasks.ListResponse;

/** P14 — list tasks. */
public final class TasksProbe implements Probe {
  @Override
  public ProbeResult run(ProbeContext ctx) throws Exception {
    ListResponse tasks = ctx.client().tasks().list();
    int nodes = tasks.nodes() != null ? tasks.nodes().size() : 0;
    return ProbeResult.pass("P14", "list tasks", "nodesWithTasks=" + nodes);
  }
}
