# WebUI Props API - Logging Guide

## Overview

The WebUI Props endpoints in v6.0.0 have **extensive logging** to help with debugging and monitoring. This guide shows you what to search for in your logs.

---

## Logged Endpoints

### 1. Get All WebUI Props
**Endpoint:** `GET /obp/v6.0.0/management/webui_props`

### 2. Get Single WebUI Prop
**Endpoint:** `GET /obp/v6.0.0/webui-props/{PROP_NAME}`

---

## Log Patterns for GET /management/webui_props

### Entry Log
```
========== GET /obp/v6.0.0/management/webui_props called with what={VALUE} ==========
```

**Search for:**
```bash
grep "GET /obp/v6.0.0/management/webui_props called" logs/obp-api.log
```

**Example output:**
```
2025-01-15 10:23:45 INFO  - ========== GET /obp/v6.0.0/management/webui_props called with what=active ==========
```

---

### Result Summary Log
```
========== GET /obp/v6.0.0/management/webui_props returning {COUNT} records ==========
```

**Search for:**
```bash
grep "GET /obp/v6.0.0/management/webui_props returning" logs/obp-api.log
```

**Example output:**
```
2025-01-15 10:23:45 INFO  - ========== GET /obp/v6.0.0/management/webui_props returning 65 records ==========
```

---

### Individual Property Logs
```
  - name: {PROP_NAME}, value: {PROP_VALUE}, webUiPropsId: {ID}
```

**Search for:**
```bash
grep "name: webui_" logs/obp-api.log
```

**Example output:**
```
2025-01-15 10:23:45 INFO  -   - name: webui_api_explorer_url, value: https://apiexplorer.example.com, webUiPropsId: Some(web-ui-props-id)
2025-01-15 10:23:45 INFO  -   - name: webui_header_logo_left_url, value: https://static.example.com/logo.png, webUiPropsId: Some(default)
```

---

### Exit Log
```
========== END GET /obp/v6.0.0/management/webui_props ==========
```

**Search for:**
```bash
grep "END GET /obp/v6.0.0/management/webui_props" logs/obp-api.log
```

---

## Log Patterns for GET /webui-props/{PROP_NAME}

### No Explicit Entry/Exit Logs

The single property endpoint (`GET /webui-props/{PROP_NAME}`) does **NOT** have dedicated entry/exit logs like the management endpoint.

However, you can still track it through:

### Standard API Request Logs
```bash
grep "GET /obp/v6.0.0/webui-props/" logs/obp-api.log
```

### Error Logs (if property not found)
```
OBP-08003: WebUi prop not found. Please specify a valid value for WEBUI_PROP_NAME.
```

**Search for:**
```bash
grep "OBP-08003" logs/obp-api.log
grep "WebUi prop not found" logs/obp-api.log
```

---

## Complete Log Sequence Example

When calling `GET /obp/v6.0.0/management/webui_props?what=active`:

```
2025-01-15 10:23:45.123 [http-nio-8080-exec-1] INFO  code.api.v6_0_0.APIMethods600$ - ========== GET /obp/v6.0.0/management/webui_props called with what=active ==========
2025-01-15 10:23:45.234 [http-nio-8080-exec-1] INFO  code.api.v6_0_0.APIMethods600$ - ========== GET /obp/v6.0.0/management/webui_props returning 65 records ==========
2025-01-15 10:23:45.235 [http-nio-8080-exec-1] INFO  code.api.v6_0_0.APIMethods600$   - name: webui_agree_terms_url, value: https://example.com/terms, webUiPropsId: Some(default)
2025-01-15 10:23:45.236 [http-nio-8080-exec-1] INFO  code.api.v6_0_0.APIMethods600$   - name: webui_api_documentation_url, value: https://docs.example.com, webUiPropsId: Some(default)
2025-01-15 10:23:45.237 [http-nio-8080-exec-1] INFO  code.api.v6_0_0.APIMethods600$   - name: webui_api_explorer_url, value: https://apiexplorer.example.com, webUiPropsId: Some(web-ui-123)
...
(63 more property logs)
...
2025-01-15 10:23:45.300 [http-nio-8080-exec-1] INFO  code.api.v6_0_0.APIMethods600$ - ========== END GET /obp/v6.0.0/management/webui_props ==========
```

---

## Useful grep Commands

### 1. Find all webui_props calls
```bash
grep "GET /obp/v6.0.0/management/webui_props called" logs/obp-api.log
```

### 2. Count how many props were returned
```bash
grep "returning.*records" logs/obp-api.log | grep webui_props
```

### 3. See all property values for a specific call
```bash
# Get timestamp from entry log, then search around that time
grep "2025-01-15 10:23:45" logs/obp-api.log | grep "name: webui_"
```

### 4. Find specific property value
```bash
grep "name: webui_api_explorer_url" logs/obp-api.log
```

### 5. Monitor live calls
```bash
tail -f logs/obp-api.log | grep "webui_props"
```

### 6. Find errors related to webui props
```bash
grep -i "error" logs/obp-api.log | grep -i "webui"
grep "OBP-08" logs/obp-api.log  # WebUI props error codes
```

### 7. Get all logs for a single request (if you know the timestamp)
```bash
grep "2025-01-15 10:23:45" logs/obp-api.log | grep -A 100 "webui_props called"
```

---

## Log Levels

All webui_props logging uses **INFO** level:

```scala
logger.info(s"========== GET /obp/v6.0.0/management/webui_props called with what=$what ==========")
logger.info(s"========== GET /obp/v6.0.0/management/webui_props returning ${result.size} records ==========")
logger.info(s"  - name: ${prop.name}, value: ${prop.value}, webUiPropsId: ${prop.webUiPropsId}")
logger.info(s"========== END GET /obp/v6.0.0/management/webui_props ==========")
```

**Make sure your logging configuration includes INFO level for `code.api.v6_0_0.APIMethods600`**

---

## Code Reference

### Management Endpoint Logging
**File:** `obp-api/src/main/scala/code/api/v6_0_0/APIMethods600.scala`  
**Lines:** 3505, 3534-3540

```scala
logger.info(s"========== GET /obp/v6.0.0/management/webui_props called with what=$what ==========")
// ... processing ...
logger.info(s"========== GET /obp/v6.0.0/management/webui_props returning ${result.size} records ==========")
result.foreach { prop =>
  logger.info(s"  - name: ${prop.name}, value: ${prop.value}, webUiPropsId: ${prop.webUiPropsId}")
}
logger.info(s"========== END GET /obp/v6.0.0/management/webui_props ==========")
```

### Single Prop Endpoint
**File:** `obp-api/src/main/scala/code/api/v6_0_0/APIMethods600.scala`  
**Lines:** 3406-3438

**No explicit logging** - relies on standard API framework logging.

---

## Debugging Tips

### If you don't see logs:

1. **Check log level configuration:**
   ```properties
   # In logback.xml or similar
   <logger name="code.api.v6_0_0.APIMethods600" level="INFO"/>
   ```

2. **Verify the endpoint is being called:**
   ```bash
   # Look for any v6.0.0 API calls
   grep "v6.0.0" logs/obp-api.log
   ```

3. **Check for authentication errors:**
   ```bash
   grep "canGetWebUiProps" logs/obp-api.log
   ```

4. **Look for the call in access logs:**
   ```bash
   grep "management/webui_props" logs/access.log
   ```

### Common Issues

1. **No logs appear:**
   - User doesn't have `CanGetWebUiProps` entitlement
   - Wrong endpoint URL (check for typos: `webui_props` vs `webui-props`)
   - Log level set too high (WARN or ERROR instead of INFO)

2. **Logs show 0 records:**
   - No database props configured
   - No config file props found
   - Check `what` parameter value

3. **Property not found in logs:**
   - Typo in property name (case-sensitive)
   - Property not in database or config file
   - Using wrong `what` parameter

---

## Summary

**To track webui_props API calls, search for:**

```bash
# Primary search patterns
grep "GET /obp/v6.0.0/management/webui_props called" logs/obp-api.log
grep "GET /obp/v6.0.0/management/webui_props returning" logs/obp-api.log
grep "name: webui_" logs/obp-api.log
grep "END GET /obp/v6.0.0/management/webui_props" logs/obp-api.log

# Single property endpoint (less logging)
grep "GET /obp/v6.0.0/webui-props/" logs/obp-api.log

# Errors
grep "OBP-08003" logs/obp-api.log
```

**The management endpoint has comprehensive logging showing:**
- When it was called
- What parameter was used (`what=active/database/config`)
- How many records returned
- Every single property name, value, and ID
- When processing completed