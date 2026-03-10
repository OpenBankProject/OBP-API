# Redis Read Access Functions

## Overview

Multiple functions in `RateLimitingUtil.scala` read counter data from Redis independently. This creates potential inconsistency and code duplication.

## Current Functions Reading Redis Counters

### 1. `underConsumerLimits` (line ~152-159)
- **Uses**: `EXISTS` + `GET`
- **Returns**: Boolean (are we under limit?)
- **Handles missing key**: Returns `true` (under limit)
- **Purpose**: Enforcement - check if request should be allowed

### 2. `incrementConsumerCounters` (line ~185-195)
- **Uses**: `TTL` + (`SET` or `INCR`)
- **Returns**: (ttl, count) as tuple
- **Handles missing key (TTL=-2)**: Creates new key with value 1
- **Purpose**: Tracking - increment counter after allowed request

### 3. `ttl` (line ~208-217)
- **Uses**: `TTL` only
- **Returns**: Long (normalized TTL)
- **Handles missing key (TTL=-2)**: Returns 0
- **Purpose**: Helper - get remaining time for a period

### 4. `getCallCounterForPeriod` (line ~223-250)
- **Uses**: `TTL` + `GET`
- **Returns**: ((Option[Long], Option[Long]), period)
- **Handles missing key (TTL=-2)**: Returns (Some(0), Some(0))
- **Purpose**: Reporting - display current usage to API consumers

## Redis TTL Semantics

- `-2`: Key does not exist
- `-1`: Key exists with no expiry (shouldn't happen in our rate limiting)
- `>0`: Seconds until key expires

## Issues

1. **Code duplication**: Redis interaction logic repeated across functions
2. **Inconsistency risk**: Each function interprets Redis state independently
3. **Multiple sources of truth**: No single canonical way to read counter state

## Recommendation

Refactor to have ONE canonical function that reads and normalizes counter state from Redis:

```scala
private def getCounterState(consumerKey: String, period: LimitCallPeriod): (Long, Long) = {
  // Single place to read and normalize Redis counter data
  // Returns (calls, ttl) with -2 handled as 0
}
```

All other functions should use this single source of truth.

## Status

- Enforcement functions work correctly
- Reporting improved (returns 0 instead of None for missing keys)
- Refactoring to single read function: **Not yet implemented**
