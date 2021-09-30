/*
 * Copyright (c) 2021 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.metrics;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.exporter.HTTPServer;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Use the prometheus library to publish prometheus metrics to a specified port. These metrics can
 * be consumed by any agent understanding the OpenMetrics format.
 *
 * This class mainly exists to help Airbyte instrument/debug application on Airbyte Cloud. Within
 * Airbyte Cloud, the metrics are consumed by a Datadog agent and transformed into Datadog metrics
 * as per https://docs.datadoghq.com/integrations/guide/prometheus-metrics/.
 *
 * Open source users are free to turn this on and consume the same metrics.
 */
public class MetricSingleton {

  private static final Logger LOGGER = LoggerFactory.getLogger(MetricSingleton.class);

  private final boolean publish;

  private final Map<String, Gauge> nameToGauge = new HashMap<>();
  private final Map<String, Counter> nameToCounter = new HashMap<>();
  private final Map<String, Histogram> nameToHistogram = new HashMap<>();

  public MetricSingleton(boolean publish) {
    this.publish = publish;
  }

  // Gauge. See
  // https://docs.datadoghq.com/metrics/agent_metrics_submission/?tab=gauge#monotonic-count.
  /**
   * Track value at a given timestamp.
   *
   * @param name of gauge
   * @param val to set
   */
  public void setGauge(String name, double val) {
    ifPublish(() -> {
      if (nameToGauge.containsKey(name)) {
        LOGGER.warn("Overriding existing metric, type: Gauge, name: {}", name);
      }

      if (!nameToGauge.containsKey(name)) {
        Gauge gauge = Gauge.build().name(name).help("").register();
        nameToGauge.put(name, gauge);
      }
      nameToGauge.get(name).set(val);
    });
  }

  /**
   * Increment value.
   *
   * @param name of gauge
   * @param val to increment
   */
  public void incrementGauge(String name, double val) {
    ifPublish(() -> {
      if (nameToGauge.containsKey(name)) {
        LOGGER.warn("Overriding existing metric, type: Gauge, name: {}", name);
      }

      if (!nameToGauge.containsKey(name)) {
        Gauge gauge = Gauge.build().name(name).help("").register();
        nameToGauge.put(name, gauge);
      }
      nameToGauge.get(name).inc(val);
    });
  }

  /**
   * Decrement value.
   *
   * @param name of gauge
   * @param val to decrement
   */
  public void decrementGauge(String name, double val) {
    ifPublish(() -> {
      if (nameToGauge.containsKey(name)) {
        LOGGER.warn("Overriding existing metric, type: Gauge, name: {}", name);
      }

      if (!nameToGauge.containsKey(name)) {
        Gauge gauge = Gauge.build().name(name).help("").register();
        nameToGauge.put(name, gauge);
      }
      nameToGauge.get(name).dec(val);
    });
  }

  // Counter - Monotonically Increasing. See
  // https://docs.datadoghq.com/metrics/agent_metrics_submission/?tab=count#monotonic-count.
  /**
   * Increment a monotoically increasing counter.
   *
   * @param name of counter
   * @param amt to increment
   */
  public void incrementCounter(String name, double amt) {
    ifPublish(() -> {
      if (nameToCounter.containsKey(name)) {
        LOGGER.warn("Overriding existing metric, type: Counter, name: {}", name);
      }

      if (!nameToCounter.containsKey(name)) {
        Counter counter = Counter.build().name(name).help("").register();
        nameToCounter.put(name, counter);
      }
      nameToCounter.get(name).inc(amt);
    });
  }

  // Histogram. See
  // https://docs.datadoghq.com/metrics/agent_metrics_submission/?tab=histogram#monotonic-count.
  /**
   * Time code execution.
   *
   * @param name of histogram
   * @param runnable to time
   * @return duration of code execution.
   */
  public double timeCode(String name, Runnable runnable) {
    var duration = new AtomicReference<>(0.0);
    ifPublish(() -> {
      if (nameToHistogram.containsKey(name)) {
        LOGGER.warn("Overriding existing metric, type: Histogram, name: {}", name);
      }

      if (!nameToHistogram.containsKey(name)) {
        Histogram hist = Histogram.build().name(name).help("").register();
        nameToHistogram.put(name, hist);
      }
      duration.set(nameToHistogram.get(name).time(runnable));
    });
    return duration.get();
  }

  /**
   * Submit a single execution time.
   *
   * @param name of the underlying histogram.
   * @param time to be recorded.
   */
  public void recordTime(String name, double time) {
    ifPublish(() -> {
      if (nameToHistogram.containsKey(name)) {
        LOGGER.warn("Overriding existing metric, type: Histogram, name: {}", name);
      }

      if (!nameToHistogram.containsKey(name)) {
        Histogram hist = Histogram.build().name(name).help("").register();
        nameToHistogram.put(name, hist);
      }
      nameToHistogram.get(name).observe(time);
    });
  }

  private void ifPublish(Runnable execute) {
    if (publish) {
      execute.run();
    }
  }

  /**
   * Stand up a separate thread to publish metrics to the specified port.
   *
   * @param monitorPort to publish metrics to
   */
  public void initializeMonitoringServiceDaemon(String monitorPort) {

    try {
      // The second constructor argument ('true') makes this server start as a separate daemon thread.
      // http://prometheus.github.io/client_java/io/prometheus/client/exporter/HTTPServer.html#HTTPServer-int-boolean-
      new HTTPServer(Integer.parseInt(monitorPort), true);
    } catch (IOException e) {
      LOGGER.error("Error starting up Prometheus publishing server..", e);
    }
  }

}
