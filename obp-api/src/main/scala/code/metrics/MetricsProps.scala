package code.metrics

import code.api.util.APIUtil

/**
 * Single source of truth for the metrics / metrics-archiving props: each prop is
 * read in exactly one place, with its fallback default defined once as a
 * constant here.
 *
 * Every consumer — `Boot` (scheduler wiring), `MetricsArchiveScheduler` (the job
 * itself), `WriteMetricUtil` / `Http4sSupport` (metric writing), and the
 * `/management/system/diagnostics/metrics` endpoint (reporting) — MUST read
 * through these accessors. Before this object existed, the diagnostics endpoint
 * and the scheduler each hardcoded their own fallback for
 * `retain_metrics_move_limit` (50000 vs 10000) and for the scheduler interval
 * (3600 vs 599), so the diagnostics reported effective values the scheduler was
 * not actually using.
 */
object MetricsProps {

  /** Whether each API call is recorded into the `metric` table. */
  val WriteMetricsDefault = false

  /** Whether the archive/cleanup scheduler runs at all. */
  val EnableMetricsSchedulerDefault = true

  /**
   * Default 599s (a prime, ~10 min) rather than a round 600: the odd interval
   * makes successive runs drift across the wall clock instead of phase-locking
   * to the top of every 10th minute (and to other periodic schedulers), so
   * archive load is spread out rather than coinciding with other spikes.
   */
  val RetainMetricsSchedulerIntervalInSecondsDefault = 599

  /** Days rows stay in the live `metric` table before being moved to the archive. */
  val RetainMetricsDaysDefault = 367L

  /** Days rows stay in the `metricarchive` table before being deleted. */
  val RetainArchiveMetricsDaysDefault: Long = 365L * 3

  /**
   * Max rows moved from `metric` to `metricarchive` per scheduler run.
   * Default tuned for smaller, more frequent runs: 10k rows every ~10 min
   * (see [[RetainMetricsSchedulerIntervalInSecondsDefault]]) = ~60k rows/hr
   * ≈ 16.7 rows/sec max drain rate, i.e. it keeps up with a sustained ~16
   * metric-generating calls/sec. Much shorter per-run passes than a single 50k
   * batch — fewer mid-run failures (stale locks) and less memory/lock contention.
   */
  val RetainMetricsMoveLimitDefault = 10000

  def writeMetrics: Boolean =
    APIUtil.getPropsAsBoolValue("write_metrics", WriteMetricsDefault)

  def enableMetricsScheduler: Boolean =
    APIUtil.getPropsAsBoolValue("enable_metrics_scheduler", EnableMetricsSchedulerDefault)

  def retainMetricsSchedulerIntervalInSeconds: Int =
    APIUtil.getPropsAsIntValue("retain_metrics_scheduler_interval_in_seconds", RetainMetricsSchedulerIntervalInSecondsDefault)

  def retainMetricsDays: Long =
    APIUtil.getPropsAsLongValue("retain_metrics_days", RetainMetricsDaysDefault)

  def retainArchiveMetricsDays: Long =
    APIUtil.getPropsAsLongValue("retain_archive_metrics_days", RetainArchiveMetricsDaysDefault)

  def retainMetricsMoveLimit: Int =
    APIUtil.getPropsAsIntValue("retain_metrics_move_limit", RetainMetricsMoveLimitDefault)
}
