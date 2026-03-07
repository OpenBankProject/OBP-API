# Next Steps

## Problem: `reset_in_seconds` always showing 0 when keys actually exist

### Observed Behavior

API response shows:

```json
{
  "per_second": {
    "calls_made": 0,
    "reset_in_seconds": 0,
    "status": "ACTIVE"
  },
  "per_minute": { ... },  // All periods show same pattern
  ...
}
```

All periods show `reset_in_seconds: 0`, BUT:

- Counters ARE persisting across calls (not resetting)
- Calls ARE being tracked and incremented
- This means Redis keys DO exist with valid TTL values

**The issue**: TTL is being reported as 0 when it should show actual seconds remaining.

### What This Indicates

Since counters persist and don't reset between calls, we know:

1. ✓ Redis is working
2. ✓ Keys exist and are being tracked
3. ✓ `incrementConsumerCounters` is working correctly
4. ✗ `getCallCounterForPeriod` is NOT reading or normalizing TTL correctly

### Debug Logging Added

Added logging to `getCallCounterForPeriod` to see raw Redis values:

```scala
logger.debug(s"getCallCounterForPeriod: period=$period, key=$key, raw ttlOpt=$ttlOpt")
logger.debug(s"getCallCounterForPeriod: period=$period, key=$key, raw valueOpt=$valueOpt")
```

### Investigation Steps

1. **Check the logs after making an API call**
   - Look for "getCallCounterForPeriod" debug messages
   - What are the raw `ttlOpt` values from Redis?
   - Are they -2, -1, 0, or positive numbers?

2. **Possible bugs in our normalization logic**

   ```scala
   val normalizedTtl = ttlOpt match {
     case Some(-2) => Some(0L)              // Key doesn't exist -> 0
     case Some(ttl) if ttl <= 0 => Some(0L) // ← This might be too aggressive
     case Some(ttl) => Some(ttl)            // Should return actual TTL
     case None => Some(0L)                  // Redis unavailable
   }
   ```

   **Question**: Are we catching valid TTL values in the `ttl <= 0` case incorrectly?

3. **Check if there's a mismatch in key format**
   - `getCallCounterForPeriod` uses: `createUniqueKey(consumerKey, period)`
   - `incrementConsumerCounters` uses: `createUniqueKey(consumerKey, period)`
   - Format: `{consumerKey}_{PERIOD}` (e.g., "abc123_PER_MINUTE")
   - Are we using the same consumer key in both places?

4. **Verify Redis TTL command is working**
   - Connect to Redis directly
   - Find keys: `KEYS *_PER_*`
   - Check TTL: `TTL {key}`
   - Should return positive number (e.g., 59 for a minute period)

### Hypotheses to Test

**Hypothesis 1: Wrong consumer key**

- `incrementConsumerCounters` uses one consumer ID
- `getCallCounterForPeriod` is called with a different consumer ID
- Result: Reading keys that don't exist (TTL = -2 → normalized to 0)

**Hypothesis 2: TTL normalization bug**

- Raw Redis TTL is positive (e.g., 45)
- But our match logic is catching it wrong
- Or `.map(_.toLong)` is failing somehow

**Hypothesis 3: Redis returns -1 for active keys**

- In some Redis configurations, active keys might return -1
- Our code treats -1 as "no expiry" and normalizes to 0
- This would be a misunderstanding of Redis behavior

**Hypothesis 4: Option handling issue**

- `ttlOpt` might be `None` when it should be `Some(value)`
- All `None` cases get normalized to 0
- Check if Redis.use is returning None unexpectedly

### Expected vs Actual

**Expected after making 1 call to an endpoint:**

```json
{
  "per_minute": {
    "calls_made": 1,
    "reset_in_seconds": 59, // ← Should be ~60 seconds
    "status": "ACTIVE"
  }
}
```

**Actual (what we're seeing):**

```json
{
  "per_minute": {
    "calls_made": 0,
    "reset_in_seconds": 0, // ← Wrong!
    "status": "ACTIVE"
  }
}
```

### Action Items

1. **Review logs** - Check what raw TTL values are being returned from Redis
2. **Test with actual API call** - Make a call, immediately check counters
3. **Verify consumer ID** - Ensure same ID used for increment and read
4. **Check Redis directly** - Manually verify keys exist with correct TTL
5. **Review normalization logic** - May need to adjust the `ttl <= 0` condition

### Related Files

- `RateLimitingUtil.scala` - Lines 223-252 (`getCallCounterForPeriod`)
- `JSONFactory6.0.0.scala` - Lines 408-418 (status mapping)
- `REDIS_READ_ACCESS_FUNCTIONS.md` - Documents multiple Redis read functions

### Note on Multiple Redis Read Functions

We have 4 different functions reading from Redis (see `REDIS_READ_ACCESS_FUNCTIONS.md`):

1. `underConsumerLimits` - Uses EXISTS + GET
2. `incrementConsumerCounters` - Uses TTL + SET/INCR
3. `ttl` - Uses TTL only
4. `getCallCounterForPeriod` - Uses TTL + GET

This redundancy may be contributing to inconsistencies. Consider refactoring to single source of truth.
