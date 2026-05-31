package net.hytaledepot.templates.mod.network;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NetworkModTemplate {
  private final Map<String, AtomicLong> actionCounters = new ConcurrentHashMap<>();
  private final Map<String, String> lastActionBySender = new ConcurrentHashMap<>();
  private final AtomicBoolean demoFlagEnabled = new AtomicBoolean(false);
  private final AtomicLong errorCount = new AtomicLong();
  Deque<Long> latencySamples = new ArrayDeque<>();
  AtomicLong inboundPackets = new AtomicLong();
  AtomicLong outboundPackets = new AtomicLong();
  AtomicLong resetCount = new AtomicLong();
  private volatile Path dataDirectory;

  public void onInitialize(Path dataDirectory) {
    this.dataDirectory = dataDirectory;
    latencySamples.clear();
  }

  public void onShutdown() {
    latencySamples.clear();
  }

  public void onHeartbeat(long tick) {
    actionCounters.computeIfAbsent("heartbeat", key -> new AtomicLong()).incrementAndGet();

  }

  public String runAction(String sender, String action, long heartbeatTicks) {
    String normalizedSender = String.valueOf(sender == null ? "unknown" : sender);
    String normalizedAction = normalizeAction(action);

    actionCounters.computeIfAbsent(normalizedAction, key -> new AtomicLong()).incrementAndGet();
    lastActionBySender.put(normalizedSender, normalizedAction);

    if ("toggle".equals(normalizedAction)) {
      boolean enabled = toggleFlag(demoFlagEnabled);
      return "[NetworkMod] demoFlag=" + enabled + ", heartbeatTicks=" + heartbeatTicks;
    }

    if ("info".equals(normalizedAction)) {
      return "[NetworkMod] " + diagnostics(normalizedSender, heartbeatTicks);
    }

    String domainResult = handleDomainAction(normalizedSender, normalizedAction, heartbeatTicks);
    if (domainResult != null) {
      return "[NetworkMod] " + domainResult;
    }

    return "[NetworkMod] unknown action='" + normalizedAction + "' (try: info, toggle, sample, latency-probe, simulate-packet, connection-reset)";
  }

  public String diagnostics(String sender, long heartbeatTicks) {
    String directory = dataDirectory == null ? "unset" : dataDirectory.toString();
    return "sender=" + sender
        + ", heartbeatTicks=" + heartbeatTicks
        + ", demoFlag=" + demoFlagEnabled.get()
        + ", ops=" + operationCount()
        + ", lastAction=" + lastActionBySender.getOrDefault(sender, "none")
        + ", errors=" + errorCount.get()
        + ", latencySamples=" + latencySamples.size() + ", avgLatency=" + averageLatency() + "ms, inbound=" + inboundPackets.get() + ", outbound=" + outboundPackets.get() + ", resets=" + resetCount.get() + ", dataDirectory=" + directory;
  }

  public long operationCount() {
    long total = 0;
    for (AtomicLong value : actionCounters.values()) {
      total += value.get();
    }
    return total;
  }

  public void incrementErrorCount() {
    errorCount.incrementAndGet();
  }

  private String handleDomainAction(String sender, String action, long heartbeatTicks) {
    if ("sample".equals(action) || "latency-probe".equals(action)) {
      long latency = 18L + (heartbeatTicks % 35L);
      recordLatency(latency);
      return "latency=" + latency + "ms, avg=" + averageLatency() + "ms";
    }
    if ("simulate-packet".equals(action)) {
      long inbound = inboundPackets.incrementAndGet();
      long outbound = outboundPackets.incrementAndGet();
      return "packets inbound=" + inbound + ", outbound=" + outbound;
    }
    if ("connection-reset".equals(action)) {
      resetCount.incrementAndGet();
      latencySamples.clear();
      return "connections reset count=" + resetCount.get();
    }
    return null;
  }

  private void recordLatency(long latency) {
    latencySamples.addLast(latency);
    while (latencySamples.size() > 24) {
      latencySamples.removeFirst();
    }
  }

  private long averageLatency() {
    if (latencySamples.isEmpty()) {
      return 0L;
    }
    long total = 0L;
    for (Long sample : latencySamples) {
      total += sample;
    }
    return total / latencySamples.size();
  }

  private static String normalizeAction(String action) {
    String normalized = String.valueOf(action == null ? "" : action).trim().toLowerCase();
    return normalized.isEmpty() ? "sample" : normalized;
  }

  private static boolean toggleFlag(AtomicBoolean flag) {
    while (true) {
      boolean current = flag.get();
      boolean next = !current;
      if (flag.compareAndSet(current, next)) {
        return next;
      }
    }
  }
}
