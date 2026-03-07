# Redis Rate Limiting in OBP-API

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Configuration](#configuration)
4. [Rate Limiting Mechanisms](#rate-limiting-mechanisms)
5. [Redis Data Structure](#redis-data-structure)
6. [Implementation Details](#implementation-details)
7. [API Response Headers](#api-response-headers)
8. [Monitoring and Debugging](#monitoring-and-debugging)
9. [Error Handling](#error-handling)
10. [Performance Considerations](#performance-considerations)

---

## Overview

The OBP-API uses **Redis** as a distributed counter backend for implementing API rate limiting. This system controls the number of API calls that consumers can make within specific time periods to prevent abuse and ensure fair resource allocation.

### Key Features

- **Multi-period rate limiting**: Enforces limits across 6 time periods (per second, minute, hour, day, week, month)
- **Distributed counters**: Uses Redis for atomic, thread-safe counter operations
- **Automatic expiration**: Leverages Redis TTL (Time-To-Live) for automatic counter reset
- **Anonymous access control**: IP-based rate limiting for unauthenticated requests
- **Fail-open design**: Defaults to allowing requests if Redis is unavailable
- **Standard HTTP headers**: Returns X-Rate-Limit-\* headers for client awareness

---

## Architecture

### High-Level Flow

```
┌─────────────────┐
│  API Request    │
└────────┬────────┘
         │
         ▼
┌─────────────────────────────────────────┐
│  Authentication (OAuth/DirectLogin)     │
└────────┬────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────┐
│  Rate Limiting Check                    │
│  (RateLimitingUtil.underCallLimits)     │
└────────┬────────────────────────────────┘
         │
         ├─── Consumer authenticated?
         │
         ├─── YES → Check 6 time periods
         │    │     (second, minute, hour, day, week, month)
         │    │
         │    ├─── Redis Key: {consumer_id}_{PERIOD}
         │    ├─── Check: current_count + 1 <= limit?
         │    │
         │    ├─── NO → Return 429 (Rate Limit Exceeded)
         │    │
         │    └─── YES → Increment Redis counters
         │              Set X-Rate-Limit-* headers
         │              Continue to API endpoint
         │
         └─── NO → Anonymous access
              │     Check per-hour limit only
              │
              ├─── Redis Key: {ip_address}_PER_HOUR
              ├─── Check: current_count + 1 <= limit?
              │
              ├─── NO → Return 429
              │
              └─── YES → Increment counter
                        Continue to API endpoint
```

### Component Architecture

```
┌──────────────────────────────────────────────────────────┐
│                    API Layer                             │
│  (AfterApiAuth trait - applies rate limiting to all      │
│   authenticated endpoints)                               │
└────────────────────┬─────────────────────────────────────┘
                     │
                     ▼
┌──────────────────────────────────────────────────────────┐
│              RateLimitingUtil                            │
│  - underCallLimits()          [Main enforcement]         │
│  - underConsumerLimits()      [Check individual period]  │
│  - incrementConsumerCounters()[Increment Redis counters] │
│  - consumerRateLimitState()   [Read current state]       │
└────────────────────┬─────────────────────────────────────┘
                     │
                     ▼
┌──────────────────────────────────────────────────────────┐
│                   Redis Layer                            │
│  - Redis.use()                [Abstraction wrapper]      │
│  - JedisPool                  [Connection pool]          │
│  - Atomic operations          [GET, SET, INCR, TTL]      │
└──────────────────────────────────────────────────────────┘
```

---

## Configuration

### Required Properties

Add these properties to your `default.props` file:

```properties
# Enable consumer-based rate limiting
use_consumer_limits=true

# Redis connection settings
cache.redis.url=127.0.0.1
cache.redis.port=6379
cache.redis.password=your_redis_password

# Optional: SSL configuration for Redis
redis.use.ssl=false
truststore.path.redis=/path/to/truststore.jks
truststore.password.redis=truststore_password
keystore.path.redis=/path/to/keystore.jks
keystore.password.redis=keystore_password

# Anonymous access limit (requests per hour)
user_consumer_limit_anonymous_access=1000

# System-wide default limits (when no RateLimiting records exist)
rate_limiting_per_second=-1
rate_limiting_per_minute=-1
rate_limiting_per_hour=-1
rate_limiting_per_day=-1
rate_limiting_per_week=-1
rate_limiting_per_month=-1
```

### Configuration Parameters Explained

| Parameter                              | Default     | Description                                              |
| -------------------------------------- | ----------- | -------------------------------------------------------- |
| `use_consumer_limits`                  | `false`     | Master switch for rate limiting feature                  |
| `cache.redis.url`                      | `127.0.0.1` | Redis server hostname or IP                              |
| `cache.redis.port`                     | `6379`      | Redis server port                                        |
| `cache.redis.password`                 | `null`      | Redis authentication password                            |
| `redis.use.ssl`                        | `false`     | Enable SSL/TLS for Redis connection                      |
| `user_consumer_limit_anonymous_access` | `1000`      | Per-hour limit for anonymous API calls                   |
| `rate_limiting_per_*`                  | `-1`        | Default limits when no DB records exist (-1 = unlimited) |

### Redis Pool Configuration

The system uses JedisPool with the following connection pool settings:

```scala
poolConfig.setMaxTotal(128)           // Maximum total connections
poolConfig.setMaxIdle(128)            // Maximum idle connections
poolConfig.setMinIdle(16)             // Minimum idle connections
poolConfig.setTestOnBorrow(true)      // Test connections before use
poolConfig.setTestOnReturn(true)      // Test connections on return
poolConfig.setTestWhileIdle(true)     // Test idle connections
poolConfig.setMinEvictableIdleTimeMillis(30*60*1000)  // 30 minutes
poolConfig.setTimeBetweenEvictionRunsMillis(30*60*1000)
poolConfig.setNumTestsPerEvictionRun(3)
poolConfig.setBlockWhenExhausted(true) // Block when no connections available
```

---

## Rate Limiting Mechanisms

### 1. Authorized Access (Authenticated Consumers)

For authenticated API consumers with valid OAuth tokens or DirectLogin credentials:

#### Six Time Periods

The system enforces limits across **6 independent time periods**:

1. **PER_SECOND** (1 second window)
2. **PER_MINUTE** (60 seconds window)
3. **PER_HOUR** (3,600 seconds window)
4. **PER_DAY** (86,400 seconds window)
5. **PER_WEEK** (604,800 seconds window)
6. **PER_MONTH** (2,592,000 seconds window, ~30 days)

#### Rate Limit Source

Rate limits are retrieved from the **RateLimiting** database table via the `getActiveRateLimitsWithIds()` function:

```scala
// Retrieves active rate limiting records for a consumer
def getActiveRateLimitsWithIds(consumerId: String, date: Date):
  Future[(CallLimit, List[String])]
```

This function:

- Queries the database for active RateLimiting records
- Aggregates multiple records (if configured for different APIs/banks)
- Returns a `CallLimit` object with limits for all 6 periods
- Falls back to system property defaults if no records exist

#### Limit Aggregation

When multiple RateLimiting records exist for a consumer:

- **Positive values** (> 0) are **summed** across records
- **Negative values** (-1) indicate "unlimited" for that period
- If all records have -1 for a period, the result is -1 (unlimited)

Example:

```
Record 1: per_minute = 100
Record 2: per_minute = 50
Aggregated: per_minute = 150
```

### 2. Anonymous Access (Unauthenticated Requests)

For requests without consumer credentials:

- **Only per-hour limits** are enforced
- Default limit: **1000 requests per hour** (configurable)
- Rate limiting key: **Client IP address**
- Designed to prevent abuse while allowing reasonable anonymous usage

---

## Redis Data Structure

### Key Format

Rate limiting counters are stored in Redis with keys following this pattern:

```
{consumer_id}_{PERIOD}
```

**Examples:**

```
consumer_abc123_PER_SECOND
consumer_abc123_PER_MINUTE
consumer_abc123_PER_HOUR
consumer_abc123_PER_DAY
consumer_abc123_PER_WEEK
consumer_abc123_PER_MONTH

192.168.1.100_PER_HOUR    // Anonymous access (IP-based)
```

### Value Format

Each key stores a **string representation** of the current call count:

```
"42"    // 42 calls made in current window
```

### Time-To-Live (TTL)

Redis TTL is set to match the time period:

| Period     | TTL (seconds) |
| ---------- | ------------- |
| PER_SECOND | 1             |
| PER_MINUTE | 60            |
| PER_HOUR   | 3,600         |
| PER_DAY    | 86,400        |
| PER_WEEK   | 604,800       |
| PER_MONTH  | 2,592,000     |

**Automatic Cleanup:** Redis automatically deletes keys when TTL expires, resetting the counter for the next time window.

### Redis Operations Used

| Operation       | Purpose                        | When Used                                  | Example                                |
| --------------- | ------------------------------ | ------------------------------------------ | -------------------------------------- |
| **GET**         | Read current counter value     | During limit check (`underConsumerLimits`) | `GET consumer_123_PER_MINUTE` → "42"   |
| **SET** (SETEX) | Initialize counter with TTL    | First call in time window                  | `SETEX consumer_123_PER_MINUTE 60 "1"` |
| **INCR**        | Atomically increment counter   | Subsequent calls in same window            | `INCR consumer_123_PER_MINUTE` → 43    |
| **TTL**         | Check remaining time in window | Before incrementing, for response headers  | `TTL consumer_123_PER_MINUTE` → 45     |
| **EXISTS**      | Check if key exists            | During limit check                         | `EXISTS consumer_123_PER_MINUTE` → 1   |
| **DEL**         | Delete counter (when limit=-1) | When limit changes to unlimited            | `DEL consumer_123_PER_MINUTE`          |

### SET vs INCR: When Each is Used

Understanding when to use SET versus INCR is critical to the rate limiting logic:

#### **SET (SETEX) - First Call in Time Window**

**When:** The counter key does NOT exist in Redis (TTL returns -2)

**Purpose:** Initialize the counter and set its expiration time

**Code Flow:**

```scala
val ttl = Redis.use(JedisMethod.TTL, key).get.toInt
ttl match {
  case -2 => // Key doesn't exist - FIRST CALL in this time window
    val seconds = RateLimitingPeriod.toSeconds(period).toInt
    Redis.use(JedisMethod.SET, key, Some(seconds), Some("1"))
    // Returns: (ttl_seconds, 1)
```

**Redis Command Executed:**

```redis
SETEX consumer_123_PER_MINUTE 60 "1"
```

**What This Does:**

1. Creates the key `consumer_123_PER_MINUTE`
2. Sets its value to `"1"` (first call)
3. Sets TTL to `60` seconds (will auto-expire after 60 seconds)

**Example Scenario:**

```
Time: 10:00:00
Action: Consumer makes first API call
Redis: Key doesn't exist (TTL = -2)
Operation: SETEX consumer_123_PER_MINUTE 60 "1"
Result: Counter = 1, TTL = 60 seconds
```

#### **INCR - Subsequent Calls in Same Window**

**When:** The counter key EXISTS in Redis (TTL returns positive number or -1)

**Purpose:** Atomically increment the existing counter

**Code Flow:**

```scala
ttl match {
  case _ => // Key exists - SUBSEQUENT CALL in same time window
    val cnt = Redis.use(JedisMethod.INCR, key).get.toInt
    // Returns: (remaining_ttl, new_count)
```

**Redis Command Executed:**

```redis
INCR consumer_123_PER_MINUTE
```

**What This Does:**

1. Atomically increments the value by 1
2. Returns the new value
3. Does NOT modify the TTL (it continues counting down)

**Example Scenario:**

```
Time: 10:00:15 (15 seconds after first call)
Action: Consumer makes second API call
Redis: Key exists (TTL = 45 seconds remaining)
Operation: INCR consumer_123_PER_MINUTE
Result: Counter = 2, TTL = 45 seconds (unchanged)
```

#### **Why Not Use SET for Every Call?**

❌ **Wrong Approach:**

```redis
SET consumer_123_PER_MINUTE "2" EX 60
SET consumer_123_PER_MINUTE "3" EX 60
```

**Problem:** Each SET resets the TTL to 60 seconds, extending the time window indefinitely!

✅ **Correct Approach:**

```redis
SETEX consumer_123_PER_MINUTE 60 "1"    # First call: TTL = 60
INCR consumer_123_PER_MINUTE             # Second call: Counter = 2, TTL = 59
INCR consumer_123_PER_MINUTE             # Third call: Counter = 3, TTL = 58
```

**Result:** TTL counts down naturally, window expires at correct time

#### **Complete Request Flow Example**

**Scenario:** Consumer with 100 requests/minute limit

```
10:00:00.000 - First request
├─ TTL consumer_123_PER_MINUTE → -2 (key doesn't exist)
├─ SETEX consumer_123_PER_MINUTE 60 "1"
└─ Response: Counter=1, TTL=60, Remaining=99

10:00:00.500 - Second request (0.5 seconds later)
├─ GET consumer_123_PER_MINUTE → "1"
├─ Check: 1 + 1 <= 100? YES (under limit)
├─ TTL consumer_123_PER_MINUTE → 59
├─ INCR consumer_123_PER_MINUTE → 2
└─ Response: Counter=2, TTL=59, Remaining=98

10:00:01.000 - Third request (1 second after first)
├─ GET consumer_123_PER_MINUTE → "2"
├─ Check: 2 + 1 <= 100? YES (under limit)
├─ TTL consumer_123_PER_MINUTE → 59
├─ INCR consumer_123_PER_MINUTE → 3
└─ Response: Counter=3, TTL=59, Remaining=97

... (more requests) ...

10:01:00.000 - Request after 60 seconds
├─ TTL consumer_123_PER_MINUTE → -2 (key expired and deleted)
├─ SETEX consumer_123_PER_MINUTE 60 "1"  (New window starts!)
└─ Response: Counter=1, TTL=60, Remaining=99
```

#### **Special Case: Limit Changes to Unlimited**

**When:** Rate limit for a period changes to `-1` (unlimited)

**Code Flow:**

```scala
case -1 => // Limit is not set for the period
  val key = createUniqueKey(consumerKey, period)
  Redis.use(JedisMethod.DELETE, key)
  (-1, -1)
```

**Redis Command:**

```redis
DEL consumer_123_PER_MINUTE
```

**Purpose:** Remove the counter entirely since there's no limit to track

#### **Atomic Operation Guarantee**

**Why INCR is Critical:**

The `INCR` operation is **atomic** in Redis, meaning:

- No race conditions between concurrent requests
- Thread-safe across multiple API instances
- Guaranteed correct count even under high load

**Example of Race Condition (if we used GET/SET):**

```
Thread A: GET counter → "42"
Thread B: GET counter → "42"  (reads same value!)
Thread A: SET counter "43"
Thread B: SET counter "43"     (overwrites A's increment!)
Result: Counter should be 44, but it's 43 (lost update!)
```

**With INCR (atomic):**

```
Thread A: INCR counter → 43
Thread B: INCR counter → 44  (atomic, no race condition)
Result: Counter is correctly 44
```

#### **Summary: Decision Tree**

```
Is this request within a rate limit period?
│
├─ Check TTL of Redis key
│  │
│  ├─ TTL = -2 (key doesn't exist)
│  │  └─ Use: SETEX key <period_seconds> "1"
│  │     Purpose: Start new time window
│  │
│  └─ TTL > 0 or TTL = -1 (key exists)
│     └─ Use: INCR key
│        Purpose: Increment counter in existing window
│
└─ After <period_seconds> pass
   └─ Redis automatically deletes key (TTL expires)
      Next request will use SETEX again
```

---

## Implementation Details

### Core Functions

#### 1. `underCallLimits()`

**Location:** `RateLimitingUtil.scala`

**Purpose:** Main rate limiting enforcement function called for every API request

**Flow:**

```scala
def underCallLimits(userAndCallContext: (Box[User], Option[CallContext])):
  (Box[User], Option[CallContext])
```

**Logic:**

1. Check if CallContext exists
2. Determine if consumer is authenticated (authorized) or anonymous
3. **Authorized path:**
   - Retrieve rate limits from CallContext.rateLimiting
   - Check all 6 time periods using `underConsumerLimits()`
   - If any limit exceeded → Return 429 error with appropriate message
   - If all checks pass → Increment all counters using `incrementConsumerCounters()`
   - Set X-Rate-Limit-\* headers
4. **Anonymous path:**
   - Check only PER_HOUR limit
   - Use IP address as rate limiting key
   - If limit exceeded → Return 429 error
   - Otherwise increment counter and continue

**Error Precedence:** Shorter periods take precedence in error messages:

```
PER_SECOND > PER_MINUTE > PER_HOUR > PER_DAY > PER_WEEK > PER_MONTH
```

#### 2. `underConsumerLimits()`

**Purpose:** Check if consumer is under limit for a specific time period

```scala
private def underConsumerLimits(consumerKey: String,
                                period: LimitCallPeriod,
                                limit: Long): Boolean
```

**Logic:**

1. If `use_consumer_limits=false` → Return `true` (allow)
2. If `limit <= 0` → Return `true` (unlimited)
3. If `limit > 0`:
   - Build Redis key: `{consumerKey}_{period}`
   - Check if key EXISTS in Redis
   - If exists: GET current count, check if `count + 1 <= limit`
   - If not exists: Return `true` (first call in window)
4. Return result (true = under limit, false = exceeded)

**Exception Handling:** Catches all Redis exceptions and returns `true` (fail-open)

#### 3. `incrementConsumerCounters()`

**Purpose:** Increment Redis counter for a specific time period

```scala
private def incrementConsumerCounters(consumerKey: String,
                                      period: LimitCallPeriod,
                                      limit: Long): (Long, Long)
```

**Logic:**

1. If `limit == -1` → DELETE the Redis key, return `(-1, -1)`
2. If `limit > 0`:
   - Build Redis key
   - Check TTL of key
   - If `TTL == -2` (key doesn't exist):
     - Initialize with `SETEX key ttl "1"`
     - Return `(ttl_seconds, 1)`
   - If key exists:
     - Atomically increment with `INCR key`
     - Return `(remaining_ttl, new_count)`
3. Return tuple: `(TTL_remaining, call_count)`

**Return Values:**

- `(-1, -1)`: Unlimited or error
- `(ttl, count)`: Active limit with remaining time and current count

#### 4. `consumerRateLimitState()`

**Purpose:** Read current state of all rate limit counters (for reporting/debugging)

```scala
def consumerRateLimitState(consumerKey: String):
  immutable.Seq[((Option[Long], Option[Long]), LimitCallPeriod)]
```

**Returns:** Sequence of tuples containing:

- `Option[Long]`: Current call count
- `Option[Long]`: Remaining TTL
- `LimitCallPeriod`: The time period

**Used by:** API endpoints that report rate limit status to consumers

---

## API Response Headers

### Standard Rate Limit Headers

The system sets three standard HTTP headers on successful responses:

```http
X-Rate-Limit-Limit: 1000
X-Rate-Limit-Remaining: 732
X-Rate-Limit-Reset: 2847
```

| Header                   | Description                          | Example |
| ------------------------ | ------------------------------------ | ------- |
| `X-Rate-Limit-Limit`     | Maximum requests allowed in period   | `1000`  |
| `X-Rate-Limit-Remaining` | Requests remaining in current window | `732`   |
| `X-Rate-Limit-Reset`     | Seconds until limit resets (TTL)     | `2847`  |

### Header Selection Priority

When multiple periods are active, headers reflect the **most restrictive active period**:

```scala
// Priority order (first active period wins)
if (PER_SECOND has TTL > 0) → Use PER_SECOND values
else if (PER_MINUTE has TTL > 0) → Use PER_MINUTE values
else if (PER_HOUR has TTL > 0) → Use PER_HOUR values
else if (PER_DAY has TTL > 0) → Use PER_DAY values
else if (PER_WEEK has TTL > 0) → Use PER_WEEK values
else if (PER_MONTH has TTL > 0) → Use PER_MONTH values
```

### Error Response (429 Too Many Requests)

When rate limit is exceeded:

```http
HTTP/1.1 429 Too Many Requests
X-Rate-Limit-Limit: 1000
X-Rate-Limit-Remaining: 0
X-Rate-Limit-Reset: 2847
Content-Type: application/json

{
  "error": "OBP-10006: Too Many Requests. We only allow 1000 requests per hour for this Consumer."
}
```

**Message Format:**

- Authorized: `"Too Many Requests. We only allow {limit} requests {period} for this Consumer."`
- Anonymous: `"Too Many Requests. We only allow {limit} requests {period} for anonymous access."`

---

## Monitoring and Debugging

### Redis CLI Commands

Useful Redis commands for monitoring rate limiting:

```bash
# Connect to Redis
redis-cli -h 127.0.0.1 -p 6379

# View all rate limit keys
KEYS *_PER_*

# Check specific consumer's counters
KEYS consumer_abc123_*

# Get current count
GET consumer_abc123_PER_MINUTE

# Check remaining time
TTL consumer_abc123_PER_MINUTE

# View all counters for a consumer
MGET consumer_abc123_PER_SECOND \
     consumer_abc123_PER_MINUTE \
     consumer_abc123_PER_HOUR \
     consumer_abc123_PER_DAY \
     consumer_abc123_PER_WEEK \
     consumer_abc123_PER_MONTH

# Delete a specific counter (reset limit)
DEL consumer_abc123_PER_MINUTE

# Delete all counters for a consumer (full reset)
DEL consumer_abc123_PER_SECOND \
    consumer_abc123_PER_MINUTE \
    consumer_abc123_PER_HOUR \
    consumer_abc123_PER_DAY \
    consumer_abc123_PER_WEEK \
    consumer_abc123_PER_MONTH

# Monitor Redis operations in real-time
MONITOR

# Check Redis memory usage
INFO memory

# Count rate limiting keys
KEYS *_PER_* | wc -l
```

### Application Logs

Enable debug logging in `logback.xml`:

```xml
<logger name="code.api.util.RateLimitingUtil" level="DEBUG"/>
<logger name="code.api.cache.Redis" level="DEBUG"/>
```

**Log Examples:**

```
DEBUG RateLimitingUtil - getCallCounterForPeriod: period=PER_MINUTE, key=consumer_123_PER_MINUTE, raw ttlOpt=Some(45)
DEBUG RateLimitingUtil - getCallCounterForPeriod: period=PER_MINUTE, key=consumer_123_PER_MINUTE, raw valueOpt=Some(42)
DEBUG Redis - KryoInjection started
DEBUG Redis - KryoInjection finished
ERROR RateLimitingUtil - Redis issue: redis.clients.jedis.exceptions.JedisConnectionException: Could not get a resource from the pool
```

### Health Check Endpoint

Check Redis connectivity:

```scala
Redis.isRedisReady  // Returns Boolean
```

**Usage:**

```bash
# Via API (if exposed)
curl https://api.example.com/health/redis

# Returns:
{
  "redis_ready": true,
  "url": "127.0.0.1",
  "port": 6379
}
```

---

## Error Handling

### Fail-Open Design

The system uses a **fail-open** approach for resilience:

```scala
try {
  // Redis operation
} catch {
  case e: Throwable =>
    logger.error(s"Redis issue: $e")
    true  // Allow request to proceed
}
```

**Rationale:** If Redis is unavailable, the API remains functional rather than blocking all requests.

### Redis Connection Failures

**Symptoms:**

- Logs show: `Redis issue: redis.clients.jedis.exceptions.JedisConnectionException`
- All rate limit checks return `true` (allow)
- Rate limiting is effectively disabled

**Resolution:**

1. Check Redis server is running: `redis-cli ping`
2. Verify network connectivity
3. Check Redis credentials and SSL configuration
4. Review connection pool settings
5. Monitor connection pool exhaustion

### Common Issues

#### 1. Rate Limits Not Enforced

**Check:**

```bash
# Is rate limiting enabled?
grep "use_consumer_limits" default.props

# Is Redis reachable?
redis-cli -h 127.0.0.1 -p 6379 ping

# Are there active RateLimiting records?
SELECT * FROM ratelimiting WHERE consumer_id = 'your_consumer_id';
```

#### 2. Inconsistent Rate Limiting

**Cause:** Multiple API instances with separate Redis instances

**Solution:** Ensure all API instances connect to the **same Redis instance**

#### 3. Counters Not Resetting

**Check TTL:**

```bash
# Should return positive number (seconds remaining)
TTL consumer_123_PER_MINUTE

# -1 means no expiry (bug)
# -2 means key doesn't exist
```

**Fix:**

```bash
# Manually reset if TTL is -1
DEL consumer_123_PER_MINUTE
```

#### 4. Memory Leak (Growing Redis Memory)

**Check:**

```bash
INFO memory
KEYS *_PER_* | wc -l
```

**Cause:** Keys created without TTL

**Prevention:** Always use `SETEX` (not `SET`) for rate limit counters

---

## Performance Considerations

### Redis Operations Cost

| Operation | Time Complexity | Performance Impact |
| --------- | --------------- | ------------------ |
| GET       | O(1)            | Negligible         |
| SET       | O(1)            | Negligible         |
| SETEX     | O(1)            | Negligible         |
| INCR      | O(1)            | Negligible         |
| TTL       | O(1)            | Negligible         |
| EXISTS    | O(1)            | Negligible         |
| DEL       | O(1)            | Negligible         |

**Per Request Cost:**

- Authorized: ~12-18 Redis operations (6 checks + 6 increments)
- Anonymous: ~2-3 Redis operations (1 check + 1 increment)

### Network Latency

**Typical Redis RTT:** 0.1-1ms (same datacenter)

**Per Request Latency:**

- Authorized: 1.2-18ms
- Anonymous: 0.2-3ms

### Optimization Tips

#### 1. Co-locate Redis with API

Deploy Redis on the same network/datacenter as OBP-API instances to minimize network latency.

#### 2. Connection Pooling

The default pool configuration is optimized for high throughput:

- 128 max connections supports 128 concurrent requests
- Adjust based on your load profile

#### 3. Redis Memory Management

**Estimate memory usage:**

```
Memory per key = ~100 bytes (key + value + metadata)
Active consumers = 1000
Periods = 6
Total memory = 1000 * 6 * 100 = 600 KB
```

**Monitor:**

```bash
INFO memory
CONFIG GET maxmemory
```

#### 4. Batch Operations

The current implementation checks all 6 periods sequentially. Future optimization could use Redis pipelining:

```scala
// Current: 6 round trips
underConsumerLimits(..., PER_SECOND, ...)
underConsumerLimits(..., PER_MINUTE, ...)
// ... 4 more

// Optimized: 1 round trip with pipeline
jedis.pipelined {
  get(key_per_second)
  get(key_per_minute)
  // ... etc
}
```

### Scalability

**Horizontal Scaling:**

- Multiple OBP-API instances → **Same Redis instance**
- Redis becomes a potential bottleneck at very high scale

**Redis Scaling Options:**

1. **Redis Sentinel**: High availability with automatic failover
2. **Redis Cluster**: Horizontal sharding for massive scale
3. **Redis Enterprise**: Commercial solution with advanced features

**Capacity Planning:**

- Single Redis instance: 50,000-100,000 ops/sec
- With 6 ops per authorized request: ~8,000-16,000 requests/sec
- With 2 ops per anonymous request: ~25,000-50,000 requests/sec

---

## API Endpoints for Rate Limit Management

### Get Rate Limiting Info

```http
GET /obp/v3.1.0/management/rate-limiting
```

**Response:**

```json
{
  "enabled": true,
  "technology": "REDIS",
  "service_available": true,
  "currently_active": true
}
```

### Get Consumer's Call Limits

```http
GET /obp/v6.0.0/management/consumers/{CONSUMER_ID}/consumer/call-limits
```

**Response:**

```json
{
  "per_second_call_limit": "10",
  "per_minute_call_limit": "100",
  "per_hour_call_limit": "1000",
  "per_day_call_limit": "10000",
  "per_week_call_limit": "50000",
  "per_month_call_limit": "200000",
  "redis_call_limit": {
    "per_second": {
      "calls_made": 5,
      "reset_in_seconds": 0
    },
    "per_minute": {
      "calls_made": 42,
      "reset_in_seconds": 37
    },
    "per_hour": {
      "calls_made": 732,
      "reset_in_seconds": 2847
    }
  }
}
```

---

## Summary

The Redis-based rate limiting system in OBP-API provides:

✅ **Distributed rate limiting** across multiple API instances
✅ **Multi-period enforcement** (second, minute, hour, day, week, month)
✅ **Automatic expiration** via Redis TTL
✅ **Atomic operations** for thread-safety
✅ **Fail-open reliability** when Redis is unavailable
✅ **Standard HTTP headers** for client awareness
✅ **Flexible configuration** via properties and database records
✅ **Anonymous access control** based on IP address

**Key Files:**

- `code/api/util/RateLimitingUtil.scala` - Main rate limiting logic
- `code/api/cache/Redis.scala` - Redis connection abstraction
- `code/api/AfterApiAuth.scala` - Integration point in request flow

**Configuration:**

- `use_consumer_limits=true` - Enable rate limiting
- `cache.redis.url` / `cache.redis.port` - Redis connection
- `user_consumer_limit_anonymous_access` - Anonymous limits

**Monitoring:**

- Redis CLI: `KEYS *_PER_*`, `GET`, `TTL`
- Application logs: Enable DEBUG on `RateLimitingUtil`
- API headers: `X-Rate-Limit-*`
