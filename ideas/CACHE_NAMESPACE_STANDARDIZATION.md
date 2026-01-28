# Cache Namespace Standardization Plan

**Date**: 2024-12-27  
**Status**: Proposed  
**Author**: OBP Development Team

## Executive Summary

This document outlines the current state of cache key namespaces in the OBP API, proposes a standardization plan, and defines guidelines for future cache implementations.

## Current State

### Well-Structured Namespaces (Using Consistent Prefixes)

These namespaces follow the recommended `{category}_{subcategory}_` prefix pattern:

| Namespace                 | Prefix            | Example Key                              | TTL   | Location                     |
| ------------------------- | ----------------- | ---------------------------------------- | ----- | ---------------------------- |
| Resource Docs - Localized | `rd_localised_`   | `rd_localised_operationId:xxx-locale:en` | 3600s | `code.api.constant.Constant` |
| Resource Docs - Dynamic   | `rd_dynamic_`     | `rd_dynamic_{version}_{tags}`            | 3600s | `code.api.constant.Constant` |
| Resource Docs - Static    | `rd_static_`      | `rd_static_{version}_{tags}`             | 3600s | `code.api.constant.Constant` |
| Resource Docs - All       | `rd_all_`         | `rd_all_{version}_{tags}`                | 3600s | `code.api.constant.Constant` |
| Swagger Documentation     | `swagger_static_` | `swagger_static_{version}`               | 3600s | `code.api.constant.Constant` |

### Inconsistent Namespaces (Need Refactoring)

These namespaces lack clear prefixes and should be standardized:

| Namespace                     | Current Pattern         | Example                                                                                                                                                      | TTL        | Location                               |
| ----------------------------- | ----------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ | ---------- | -------------------------------------- |
| Rate Limiting - Counters      | `{consumerId}_{period}` | `abc123_PER_MINUTE`                                                                                                                                          | Variable   | `code.api.util.RateLimitingUtil`       |
| Rate Limiting - Active Limits | Complex path            | `code.api.cache.Redis.memoizeSyncWithRedis(Some((code.ratelimiting.MappedRateLimitingProvider,getActiveCallLimitsByConsumerIdAtDateCached,_2025-12-27-23)))` | 3600s      | `code.ratelimiting.MappedRateLimiting` |
| Connector Methods             | Simple string           | `getConnectorMethodNames`                                                                                                                                    | 3600s      | `code.api.v6_0_0.APIMethods600`        |
| Metrics - Stable              | Various                 | Method-specific keys                                                                                                                                         | 86400s     | `code.metrics.APIMetrics`              |
| Metrics - Recent              | Various                 | Method-specific keys                                                                                                                                         | 7s         | `code.metrics.APIMetrics`              |
| ABAC Rules                    | Rule ID only            | `{ruleId}`                                                                                                                                                   | Indefinite | `code.abacrule.AbacRuleEngine`         |

## Proposed Standardization

### Standard Prefix Convention

All cache keys should follow the pattern: `{category}_{subcategory}_{identifier}`

**Rules:**

1. Use lowercase with underscores
2. Prefix should clearly identify the cache category
3. Keep prefixes short but descriptive (2-3 parts max)
4. Use consistent terminology across the codebase

### Proposed Prefix Mappings

| Namespace                         | Current                 | Proposed Prefix   | Example Key                         | Priority |
| --------------------------------- | ----------------------- | ----------------- | ----------------------------------- | -------- |
| Resource Docs - Localized         | `rd_localised_`         | `rd_localised_`   | ✓ Already good                      | ✓        |
| Resource Docs - Dynamic           | `rd_dynamic_`           | `rd_dynamic_`     | ✓ Already good                      | ✓        |
| Resource Docs - Static            | `rd_static_`            | `rd_static_`      | ✓ Already good                      | ✓        |
| Resource Docs - All               | `rd_all_`               | `rd_all_`         | ✓ Already good                      | ✓        |
| Swagger Documentation             | `swagger_static_`       | `swagger_static_` | ✓ Already good                      | ✓        |
| **Rate Limiting - Counters**      | `{consumerId}_{period}` | `rl_counter_`     | `rl_counter_{consumerId}_{period}`  | **HIGH** |
| **Rate Limiting - Active Limits** | Complex path            | `rl_active_`      | `rl_active_{consumerId}_{dateHour}` | **HIGH** |
| Connector Methods                 | `{methodName}`          | `connector_`      | `connector_methods`                 | MEDIUM   |
| Metrics - Stable                  | Various                 | `metrics_stable_` | `metrics_stable_{hash}`             | MEDIUM   |
| Metrics - Recent                  | Various                 | `metrics_recent_` | `metrics_recent_{hash}`             | MEDIUM   |
| ABAC Rules                        | `{ruleId}`              | `abac_rule_`      | `abac_rule_{ruleId}`                | LOW      |

## Implementation Plan

### Phase 1: High Priority - Rate Limiting (✅ COMPLETED)

**Target**: Rate Limiting Counters and Active Limits

**Status**: ✅ Implemented successfully on 2024-12-27

**Changes Implemented:**

1. **✅ Rate Limiting Counters**
   - File: `obp-api/src/main/scala/code/api/util/RateLimitingUtil.scala`
   - Updated `createUniqueKey()` method to use `rl_counter_` prefix
   - Implementation:
     ```scala
     private def createUniqueKey(consumerKey: String, period: LimitCallPeriod) =
       "rl_counter_" + consumerKey + "_" + RateLimitingPeriod.toString(period)
     ```

2. **✅ Rate Limiting Active Limits**
   - File: `obp-api/src/main/scala/code/ratelimiting/MappedRateLimiting.scala`
   - Updated cache key generation in `getActiveCallLimitsByConsumerIdAtDateCached()`
   - Implementation:
     ```scala
     val cacheKey = s"rl_active_${consumerId}_${currentDateWithHour}"
     Caching.memoizeSyncWithProvider(Some(cacheKey))(3600 second) {
     ```

**Testing:**

- ✅ Rate limiting working correctly with new prefixes
- ✅ Redis keys using new standardized prefixes
- ✅ No old-format keys being created

**Migration Notes:**

- No active migration needed - old keys expired naturally
- Rate limiting counters: expired within minutes/hours/days based on period
- Active limits: expired within 1 hour

### Phase 2: Medium Priority - Connector & Metrics

**Target**: Connector Methods and Metrics caches

**Changes Required:**

1. **Connector Methods**
   - File: `obp-api/src/main/scala/code/api/v6_0_0/APIMethods600.scala`
   - Update cache key in `getConnectorMethodNames`:

     ```scala
     // FROM:
     val cacheKey = "getConnectorMethodNames"

     // TO:
     val cacheKey = "connector_methods"
     ```

2. **Metrics Caches**
   - Files: Various in `code.metrics`
   - Add prefix constants and update cache key generation
   - Use `metrics_stable_` for historical metrics
   - Use `metrics_recent_` for recent metrics

**Testing:**

- Verify connector method caching works
- Verify metrics queries return correct data
- Check Redis keys use new prefixes

**Migration Strategy:**

- Old keys will expire naturally (TTLs: 7s - 24h)
- Consider one-time cleanup script if needed

### Phase 3: Low Priority - ABAC Rules

**Target**: ABAC Rule caches

**Changes Required:**

1. **ABAC Rules**
   - File: `code.abacrule.AbacRuleEngine`
   - Add prefix to rule cache keys
   - Update `clearRuleFromCache()` method

**Testing:**

- Verify ABAC rules still evaluate correctly
- Verify cache clear operations work

**Migration Strategy:**

- May need active migration since TTL is indefinite
- Provide cleanup endpoint/script

## Benefits of Standardization

1. **Operational Benefits**
   - Easy to identify cache types in Redis: `KEYS rl_counter_*`
   - Simple bulk operations: delete all rate limit counters at once
   - Better monitoring: group metrics by cache namespace
   - Easier debugging: clear cache type quickly

2. **Development Benefits**
   - Consistent patterns reduce cognitive load
   - New developers can understand cache structure quickly
   - Easier to search codebase for cache-related code
   - Better documentation and maintenance

3. **Cache Management Benefits**
   - Enables namespace-based cache clearing endpoints
   - Allows per-namespace statistics and monitoring
   - Facilitates cache warming strategies
   - Supports selective cache invalidation

## Cache Management API (Future)

Once standardization is complete, we can implement:

### Endpoints

#### 1. GET /obp/v6.0.0/system/cache/namespaces (✅ IMPLEMENTED)

**Description**: Get all cache namespaces with statistics

**Authentication**: Required

**Authorization**: Requires role `CanGetCacheNamespaces`

**Response**: List of cache namespaces with:

- `prefix`: The namespace prefix (e.g., `rl_counter_`, `rd_localised_`)
- `description`: Human-readable description
- `ttl_seconds`: Default TTL for this namespace
- `category`: Category (e.g., "Rate Limiting", "Resource Docs")
- `key_count`: Number of keys in Redis with this prefix
- `example_key`: Example of a key in this namespace

**Example Response**:

```json
{
  "namespaces": [
    {
      "prefix": "rl_counter_",
      "description": "Rate limiting counters per consumer and time period",
      "ttl_seconds": "varies",
      "category": "Rate Limiting",
      "key_count": 42,
      "example_key": "rl_counter_consumer123_PER_MINUTE"
    },
    {
      "prefix": "rl_active_",
      "description": "Active rate limit configurations",
      "ttl_seconds": 3600,
      "category": "Rate Limiting",
      "key_count": 15,
      "example_key": "rl_active_consumer123_2024-12-27-14"
    }
  ]
}
```

#### 2. DELETE /obp/v6.0.0/management/cache/namespaces/{NAMESPACE} (Future)

**Description**: Clear all keys in a namespace

**Example**: `DELETE .../cache/namespaces/rl_counter` clears all rate limit counters

**Authorization**: Requires role `CanDeleteCacheNamespace`

#### 3. DELETE /obp/v6.0.0/management/cache/keys/{KEY} (Future)

**Description**: Delete specific cache key

**Authorization**: Requires role `CanDeleteCacheKey`

### Role Definitions

```scala
// Cache viewing
case class CanGetCacheNamespaces(requiresBankId: Boolean = false) extends ApiRole
lazy val canGetCacheNamespaces = CanGetCacheNamespaces()

// Cache deletion (future)
case class CanDeleteCacheNamespace(requiresBankId: Boolean = false) extends ApiRole
lazy val canDeleteCacheNamespace = CanDeleteCacheNamespace()

case class CanDeleteCacheKey(requiresBankId: Boolean = false) extends ApiRole
lazy val canDeleteCacheKey = CanDeleteCacheKey()
```

## Guidelines for Future Cache Implementations

When implementing new caching functionality:

1. **Choose a descriptive prefix** following the pattern `{category}_{subcategory}_`
2. **Document the prefix** in `code.api.constant.Constant` if widely used
3. **Use consistent separator**: underscore `_`
4. **Keep prefixes short**: 2-3 components maximum
5. **Add to this document**: Update the namespace inventory
6. **Consider TTL carefully**: Document the chosen TTL and rationale
7. **Plan for invalidation**: How will stale cache be cleared?

## Constants File Organization

Recommended structure for `code.api.constant.Constant`:

```scala
// Resource Documentation Cache Prefixes
final val LOCALISED_RESOURCE_DOC_PREFIX = "rd_localised_"
final val DYNAMIC_RESOURCE_DOC_CACHE_KEY_PREFIX = "rd_dynamic_"
final val STATIC_RESOURCE_DOC_CACHE_KEY_PREFIX = "rd_static_"
final val ALL_RESOURCE_DOC_CACHE_KEY_PREFIX = "rd_all_"
final val STATIC_SWAGGER_DOC_CACHE_KEY_PREFIX = "swagger_static_"

// Rate Limiting Cache Prefixes
final val RATE_LIMIT_COUNTER_PREFIX = "rl_counter_"
final val RATE_LIMIT_ACTIVE_PREFIX = "rl_active_"

// Connector Cache Prefixes
final val CONNECTOR_PREFIX = "connector_"

// Metrics Cache Prefixes
final val METRICS_STABLE_PREFIX = "metrics_stable_"
final val METRICS_RECENT_PREFIX = "metrics_recent_"

// ABAC Cache Prefixes
final val ABAC_RULE_PREFIX = "abac_rule_"

// TTL Configurations
final val RATE_LIMIT_ACTIVE_CACHE_TTL: Int =
  APIUtil.getPropsValue("rateLimitActive.cache.ttl.seconds", "3600").toInt
// ... etc
```

## Conclusion

Standardizing cache namespace prefixes will significantly improve:

- Operational visibility and control
- Developer experience and maintainability
- Debugging and troubleshooting capabilities
- Foundation for advanced cache management features

The phased approach allows us to implement high-priority changes immediately while planning for comprehensive standardization over time.

## References

- Redis KEYS pattern matching: https://redis.io/commands/keys
- Redis SCAN for production: https://redis.io/commands/scan
- Cache key naming best practices: https://redis.io/topics/data-types-intro

## Changelog

- 2024-12-27: Initial document created
- 2024-12-27: Phase 1 (Rate Limiting) implementation started
- 2024-12-27: Phase 1 (Rate Limiting) implementation completed ✅
- 2024-12-27: Added GET /system/cache/namespaces endpoint specification
- 2024-12-27: Added `CanGetCacheNamespaces` role definition
