# Telemetry conventions

This document sets the rules for Telemetry in OBP-API: the aggregated numbers (counts, rates,
durations, sizes, current levels) that tell an operator how the running service is behaving. Apply
it whenever you add a counter, a timer or a cache, or change code on a request path.

It exists because, before it, each area of the code measured itself in its own way. The NMB sandbox
outage of 2026-09-23 (CPU above 600%, a full heap, hours of garbage-collection pauses) was found by
an external Nagios check, which could say that the service had failed but not why. The numbers that
would have explained it (live thread count, heap left after garbage collection, cache hit ratios,
log queue depth) were either not collected at all or were held in hand-written counters that nothing
exported.

Status, 2026-09-27: the first part is built (`code.telemetry`, the separate port, the v7.0.0
endpoint and the guard tests). Section 13 lists what is recorded today and what is not yet, and
section 12 the assumptions made while the DevOps answers are pending.

## 1. The name: Telemetry, not metrics

"Metrics" already has two fixed meanings in OBP, both documented, both exposed through endpoints,
and neither can be renamed:

| | What it is | Where it lives | Carries identities? |
|---|---|---|---|
| **API Metrics** | one record per API call: who called what, when, how long it took | OBP database, read through the metrics endpoints | yes: consumer, user |
| **Connector Metrics** | one record per connector call | OBP database, read through the connector metrics endpoint | per call |
| **Telemetry** | aggregated numbers about the running instance | served for Prometheus to collect, viewed in Grafana; never stored in the OBP database | never |

API Metrics and Connector Metrics are records you can query per consumer or per user, close to an
audit log. Telemetry only answers "how is the system behaving", and it is cheap because it never
writes to the database.

So new code in this area uses the word Telemetry: the package `code.telemetry`, the object
`Telemetry`, the props `telemetry.*`, the Role `CanGetTelemetry`. New classes, objects and props do
not put "Metric" in their own names. Library class names such as Micrometer's `GuavaCacheMetrics`
are fine; we did not name them. Inside Prometheus and Grafana every series is called a "metric";
that is those tools' vocabulary, not OBP's.

## 2. Library

OBP-API records Telemetry through **Micrometer**, which does for measurements what SLF4J does for
logging: one API in the code, with the backend chosen at start-up.

- For Prometheus, the backend is `micrometer-registry-prometheus`, which renders every series in the
  Prometheus text format.
- For OpenTelemetry, the backend is `micrometer-registry-otlp`, which pushes the same series to an
  OpenTelemetry Collector. Both can run at once through a `CompositeMeterRegistry`.

Code outside `code.telemetry` registers meters through the `Telemetry` object, not by calling
Micrometer's global registry directly. That keeps the prefix rule (section 5) and the tag rules
(section 6) in one place.

## 3. The four types of measurement

| Type | Question it answers | Examples |
|---|---|---|
| **Counter** (only goes up) | How often? | cache hits, cache misses, times a schema was generated, log entries dropped |
| **Gauge** (a current value) | How much, right now? | queue depth, live thread count, entries in a cache |
| **Distribution summary** (a histogram) | How big, and how spread out? | response body size in bytes, number of items in a returned list, size of a Redis value |
| **Timer** (a histogram of durations) | How long? | endpoint duration, connector call duration, time to build a missing cache entry |

"The size of a returned object" and "the count in a list" are distribution summaries, not counters:
what matters is the spread per endpoint (the median, the 99th percentile), not a total.

**Never store a ratio.** Record hits and misses as one counter with a `result` tag (`hit` or `miss`)
and compute the ratio when you query. A stored ratio cannot be added up across instances or across
time windows.

## 4. What to measure

Three standard checklists, from the Google Site Reliability Engineering book and the practice that
grew out of it:

- **RED**, for anything that serves requests (endpoints, connector methods): **R**ate,
  **E**rrors, **D**uration.
- **USE**, for anything that is a resource (the database pool, the log dispatch queue, thread
  pools, Redis connections): **U**tilisation, **S**aturation (queue length, waiters),
  **E**rrors.
- **Caches**: hits, misses, evictions, current size, and the time taken to build a missing entry.
  Guava's `recordStats()` records all of these.

Add the standard JVM figures before anything else: heap used and heap left after garbage
collection, garbage-collection pause time, live thread count, loaded classes. The 2026-09-23 outage
was a saturation failure, and these are the numbers that show saturation.

## 5. Naming

- **OBP-API's own series start with `obp_api_`.** The other products keep their own prefixes,
  `obp_portal_` and `obp_mcp_`, so their series never collide with ours.
- **Standard library series stay as Micrometer names them**: `jvm_memory_used_bytes`,
  `jvm_gc_pause_seconds`, `jvm_threads_live_threads`, `hikaricp_connections_active`,
  `http_server_requests_seconds`. The ready-made community Grafana dashboards look for exactly
  these names, and would find nothing under a prefix. Which product a series came from is recorded
  anyway: Prometheus adds a `job` label per scrape target, and OpenTelemetry adds `service.name`.
- In Micrometer, write names in lowercase with dots: `obp.api.cache.gets`. Micrometer converts them
  for Prometheus to `obp_api_cache_gets_total`, adding `_total` to counters itself. Do not write
  `_total` into the name.
- Use base units, named in the metric: seconds and bytes, never milliseconds or kilobytes.
  Micrometer's timers already report seconds.
- Name the thing measured, not the code that measures it: `obp.api.cache.gets`, not
  `obp.api.json_schema_generator.cache_counter`. Put the specific cache in a tag.

## 6. Tags, and why their values must be few

Every distinct combination of tag values is stored by Prometheus as a separate series. A tag whose
value can be anything (a user id, a consent id, a raw URL) creates series without limit, and
Prometheus runs out of memory. This is the most common way a metrics system falls over.

| Allowed tag | Values come from | Example |
|---|---|---|
| `operation` | the ResourceDoc's operation id (a fixed list) | `OBPv7.0.0-getBanks` |
| `api_version` | the fixed list of API versions | `v7.0.0` |
| `status` | the HTTP status class | `2xx`, `4xx`, `5xx` |
| `connector_method` | the connector's method names | `getBankAccount` |
| `cache` | a name given in code | `json_schema`, `message_docs` |
| `result` | a fixed pair or small set | `hit`, `miss` |
| `level` | log levels | `WARN` |

Never use as a tag: user id, consumer id, consent id, account id, transaction id, customer id,
`api_instance_id` (see section 8), a raw URL or path, a free-text error message, a request header.
Bank id is also not a tag by default: the list of banks grows without an upper limit that the code
controls. Add a bank tag to one specific series only after deciding that its number of banks is
small and fixed.

## 7. Cost on the request path

- Counters and timers are cheap (an atomic add). Use them freely.
- Only measure a size where the value already exists. Record response bytes where the body has
  already been rendered; never render or serialise something a second time just to measure it.
- Never build a tag value by formatting or serialising anything on the request path.
- Percentile histograms cost memory per series. Turn them on for timers and distribution summaries
  at the shared points in section 9, not for every meter.

## 8. Which instance is reporting

Each running OBP-API process has its own id, `code.api.Constant.ApiInstanceId`
(`obp-api/src/main/scala/code/api/constant/constant.scala:102`). It is built from the
`api_instance_id` prop: used as it is when the prop ends in `final`, otherwise the prop plus a new
UUID at each start-up, or just a new UUID when the prop is not set. The same id is written to every
API Metrics row (`obp-api/src/main/scala/code/api/util/WriteMetricUtil.scala:230`) and every Redis
log entry (`obp-api/src/main/scala/code/api/cache/RedisLogger.scala:355`), so it ties Telemetry to
those records.

- **Do not put it on every series as a tag.** Unless the prop ends in `final` it changes on every
  restart, so every restart would start a fresh set of series. Prometheus already identifies each
  node with its own `instance` label (host and port).
- **Publish it once, in an info series**: `obp_api_instance_info{api_instance_id="…",
  git_commit="…"} 1`. The value is always 1; the labels carry the descriptive values, and Grafana
  can join on them.
- **Use `Constant.ApiInstanceId`, never the raw prop.** The cache configuration reads the raw
  `api_instance_id` prop (`obp-api/src/main/scala/code/api/v6_0_0/JSONFactory6.0.0.scala:2388`) to
  build the Redis key namespace, which instances share, so it deliberately has no per-process part.
  It names the deployment, not the process.

## 9. Where to measure: at the shared points, not in each function

Most of the codebase gets Telemetry without any per-function code, because every request passes
through a few shared points. Instrument those, and a developer adding an endpoint or a connector
method gets RED measurements without doing anything.

| Shared point | What it records |
|---|---|
| Endpoint middleware: `recordMetric` (`obp-api/src/main/scala/code/api/util/http4s/Http4sSupport.scala:197`) and `ResourceDocMiddleware` | RED for every endpoint, tagged by `operation`, `api_version` and `status`; response bytes; item count for list responses |
| The connector proxy that intercepts every connector call (`obp-api/src/main/scala/code/bankconnectors/package.scala`) | RED for every connector method, tagged by `connector_method` |
| `Caching.memoize*` (`obp-api/src/main/scala/code/api/cache/Caching.scala:40`, `:52`, `:64`, `:76`) | hits, misses and build time for every memoised function, tagged by `cache` |
| `Redis.use` (`obp-api/src/main/scala/code/api/cache/Redis.scala:203`) | operations, errors, duration and value size, tagged by the Redis command |
| The batch writers for API Metrics and Connector Metrics (`MetricBatchWriter.scala`, `ConnectorMetricBatchWriter.scala`) | queue depth, rows written, rows dropped: Telemetry watching the recording of API Metrics |
| Micrometer's ready-made binders | JVM memory, garbage collection, threads and classes; HikariCP; executor pools (including the log dispatch pool); every Guava `CacheBuilder` cache |

Hand-written meters are for business logic that no shared point can see, such as "times the JSON
Schema was actually generated". Register them through the `Telemetry` object.

Any new Guava cache is built with `recordStats()` and registered with `Telemetry`, so that it shows
up without anyone remembering to add it later.

## 10. How Telemetry is exposed

**For Prometheus: a separate port on each instance.**

- Props `telemetry.port.enabled` (default `false`), `telemetry.host` (default `0.0.0.0`) and
  `telemetry.port` (default `9464`), documented in `sample.props.template`. They control the port
  only: Telemetry is always recorded, because recording costs an atomic add and the endpoint below
  reads the same registry. The port is off by default because, on a bare host, any open port may be
  reachable; each deployment switches it on deliberately. `9464` is the conventional port (it is
  the OpenTelemetry Prometheus exporter's default).
- The path is `/telemetry`, not Prometheus's default `/metrics`, to keep the word "metrics" to its
  OBP meaning. The scrape configuration sets `metrics_path: /telemetry`.
- The port is served by a small server on its own thread, not by the http4s server, so it keeps
  answering while the main request pool is saturated: which is exactly when it is needed.
- The port is never published outside the host or cluster. It needs no credentials because the
  network keeps it private.

Why Prometheus must not scrape through the API itself: behind a load balancer each scrape would
reach a different node, so counters would jump between nodes' values; the request would pass
through authentication, the Role lookup and the request transaction, and so fail under the same
load it is meant to reveal; and every scrape would write an API Metrics row.

**For people: a Role-gated endpoint.**

- `GET /obp/v7.0.0/management/telemetry`, Role `CanGetTelemetry` with `requiresBankId = false`.
  Telemetry is about the instance, which belongs to no bank, so the Role is held at the empty bank
  id, like `CanReadMetrics` and `CanGetConfig`. It is a separate Role from `CanReadMetrics` because
  JVM and cache figures are different information from API usage records.
- The response names the instance that answered (`api_instance_id`, section 8) and the build
  commit, then lists every meter with its tags and current values, sorted by name. The query
  parameter `name_prefix` narrows the list to one area, for example `?name_prefix=obp.api.endpoint`.
  A reader behind a load balancer can tell which node the figures describe.
- It reads the same registry as the port, so the two views cannot disagree.
- Its ResourceDoc description states this instance's actual settings, taken from the props at
  start-up (for example, the port Prometheus should scrape and the path, or that the port is off),
  and why Prometheus should use the port rather than this endpoint. It gives no instructions on
  which props to set; those belong in `sample.props.template`.

## 11. Tests

- **Budget tests** make real requests and check what they cost (generator runs, cache hits, log
  lines), not only what they return. `PerformanceBudgetTest` and `LoggingCostBudgetTest` are the
  first. Once the registry exists they read from it instead of from bespoke getters, so tests and
  production dashboards look at the same numbers.
- **`TelemetryConventionsTest`** (`obp-api/src/test/scala/code/telemetry/`) holds the code to this
  document. It fails when:
  - a file outside `code.telemetry` holds more `AtomicLong(` counters than its allowlist entry
    (the allowlist only shrinks; a lower count than the entry also fails, so the entry is lowered);
  - a file builds a Guava cache (`CacheBuilder.newBuilder`) without wrapping it in
    `Telemetry.monitorCache`;
  - the running server registered a meter that is neither `obp.api.*` nor a standard family
    (`jvm.`, `process.`, `system.`, `hikaricp.`, `cache.`);
  - a series carries a tag key that names an identifier (user, consumer, consent, account,
    transaction, customer, bank, URL, path, correlation id, `api_instance_id` outside the info
    series).
  A limit on the number of distinct values per tag is not checked yet.
- **`TelemetryTest`** checks the naming rule, the Prometheus form of counters, endpoint and Connector
  timers (including the fixed buckets), and the separate port (served at `/telemetry`, 404
  elsewhere), without a server.
- **`TelemetryEndpointTest`** checks the v7.0.0 endpoint: 401, 403 without the Role, the instance
  id, the standard and start-up meters, the middleware's count of the endpoint's own requests, and
  `name_prefix`.

The hand-written counters that existed when Telemetry arrived are now exported through
`TelemetryBindings`, which reads their getters, so the code that counts is unchanged:

| Site | Counter | Exported as |
|---|---|---|
| `obp-api/src/main/scala/code/util/Helper.scala:330`–`:332` | `mdcLogDropped`, `mdcLogDispatched`, `mdcLogInline` | `obp.api.log.dispatch.entries{result=dropped/dispatched/inline}`; queue depth as `obp.api.log.dispatch.queue.depth` |
| `obp-api/src/main/scala/code/util/SecureLogging.scala:169` | `maskCallsCounter` | `obp.api.log.masking.calls` |
| `obp-api/src/main/scala/code/api/util/JsonSchemaGenerator.scala:82` | `generatorCallsCounter` | `obp.api.json_schema.generations` |
| `obp-api/src/main/scala/code/api/v2_2_0/MessageDocsJsonCache.scala:62`–`:65` | `generatorCallsCounter`, `sharedGets`, `sharedHits`, `sharedSets` | `obp.api.message_docs.generations`, `obp.api.message_docs.shared.gets{result=hit/miss}`, `obp.api.message_docs.shared.sets` |
| `obp-api/src/main/scala/code/metricsstream/MetricsEventBus.scala` and `code/logcache/LogCacheEventBus.scala` | per-subscriber `dropped` (logged only) | `obp.api.stream.messages.dropped{stream=metrics/log_cache}`, counted at the drop site across all subscribers |
| `obp-api/src/main/scala/code/api/cache/RedisLogger.scala:111` | `consecutiveFailures` | `obp.api.redis_logger.consecutive_failures` (a gauge) |

## 12. Assumptions pending the DevOps answers

These are working assumptions, to be confirmed or replaced. The code does not depend on any of
them except the fifth, because Micrometer keeps the backend a start-up choice.

1. Prometheus collects and Grafana displays. OpenTelemetry export and tracing come later.
2. Telemetry is served on a separate port, off by default, never published outside the host or
   cluster.
3. Nothing depends on Kubernetes. Outside Kubernetes, Prometheus lists each instance in
   `static_configs`; inside it, a `ServiceMonitor` or `PodMonitor` finds them. Only that discovery
   setting changes.
4. Nagios stays as the external check of what users experience (up, fast, answering correctly).
   Telemetry is the view from inside the service. Page people on symptoms; use Telemetry for
   dashboards, diagnosis, and a few alerts on saturation that predicts an outage (heap after
   garbage collection staying high, thread count climbing).
5. OBP-API's own series are prefixed `obp_api_` (decided, not an assumption; listed here because
   the other products' prefixes depend on it).

Open questions for DevOps: whether the Prometheus Operator is in use (so `ServiceMonitor`
resources); whether an OpenTelemetry Collector exists or is planned, and when tracing is wanted;
where dashboards and alerts live; and whether a separate port suits, or a path on the main port is
preferred.

Before OBP-API itself carries Telemetry, the JVM figures can be collected from any running instance
with no code change: the Prometheus JMX exporter is a Java agent added to the JVM start command
(`-javaagent:jmx_prometheus_javaagent.jar=9404:config.yaml`), which serves heap, garbage-collection,
thread and class figures on its own port. Remove it once the Micrometer JVM binders are in.

## 13. What is recorded today

| Meter | Type | Tags | Recorded at |
|---|---|---|---|
| `obp.api.endpoint.requests` | timer, fixed buckets | `operation`, `api_version`, `status` | `ResourceDocMiddleware`, once per request, by the hop that matched a ResourceDoc |
| `obp.api.endpoint.response.size` | distribution summary, bytes | `operation` | the same, when the response states its length |
| `obp.api.connector.calls` | timer, fixed buckets | `connector`, `connector_method`, `result` | the Connector proxy (`code/bankconnectors/package.scala`) |
| `obp.api.redis.commands` | timer | `command`, `result` | `Redis.use` |
| `cache.gets`, `cache.puts`, `cache.evictions`, `cache.size` | standard | `cache` = `in_memory`, `json_schema`, `message_docs`, `on_behalf_of` | every Guava cache, through `Telemetry.monitorCache` |
| `hikaricp.*` | standard | `pool` | the database pool (`CustomDBVendor`) |
| `jvm.*`, `process.*`, `system.*` | standard | | JVM memory, heap after garbage collection (`jvm.memory.usage.after.gc`), garbage collection, threads, classes, CPU, uptime, open files |
| `obp.api.instance.info` | gauge, always 1 | `api_instance_id`, `git_commit` | start-up |
| the counters in section 11 | | | `TelemetryBindings` |

Not recorded yet:
- hits and misses of `Caching.memoize*` with the Redis provider (the in-memory provider is covered
  through the `in_memory` cache);
- the batch writers for API Metrics and Connector Metrics: their queues are
  `ConcurrentLinkedQueue`s, whose size costs a walk of the whole queue, so they need their own
  counters rather than a gauge on `size()`;
- item counts of list responses;
- endpoints without a ResourceDoc (Dynamic Entity records, Dynamic Endpoints, the unversioned
  routes), which do not pass through the matching branch of `ResourceDocMiddleware`.

