# WebUI Props v6.0.0 Improvements

## Summary

Enhanced the v6.0.0 `/webui-props` endpoint with better filtering, source tracking, and proper precedence handling.

## Changes Made

### 1. Fixed Endpoint Precedence in v6.0.0

**Problem:** v6.0.0 was using v5.1.0's `getWebUiProps` endpoint instead of its own because v5.1.0 routes were listed first.

**Solution:** Changed route ordering in `OBPAPI6_0_0.scala`:

```scala
// Before:
private val endpoints: List[OBPEndpoint] = endpointsOf5_1_0_without_root ++ endpointsOf6_0_0

// After:
private val endpoints: List[OBPEndpoint] = endpointsOf6_0_0.toList ++ endpointsOf5_1_0_without_root
```

**Result:** v6.0.0 endpoints now take precedence over earlier versions automatically.

### 2. Fixed `what=active` Logic

**Problem:** `what=active` was returning ALL props (database + config), creating duplicates when the same prop existed in both sources.

**Before:**
```scala
case "active" =>
  val implicitWebUiPropsRemovedDuplicated = if(explicitWebUiProps.nonEmpty){
    val duplicatedProps = explicitWebUiProps.map(explicitWebUiProp => 
      implicitWebUiProps.filter(_.name == explicitWebUiProp.name)).flatten
    implicitWebUiProps diff duplicatedProps
  } else {
    implicitWebUiProps.distinct
  }
  explicitWebUiProps ++ implicitWebUiPropsRemovedDuplicated
```

**After:**
```scala
case "active" =>
  // Return one value per prop: database value if exists, otherwise config value
  val databasePropNames = explicitWebUiPropsWithSource.map(_.name).toSet
  val configPropsNotInDatabase = implicitWebUiProps.distinct.filterNot(prop => 
    databasePropNames.contains(prop.name))
  explicitWebUiPropsWithSource ++ configPropsNotInDatabase
```

**Result:** Returns ONE value per property name - database value if it exists, otherwise config value.

### 3. Added `source` Field to Track Prop Origin

**Problem:** Frontend had no way to know if a prop was editable (database) or read-only (config).

**Solution:** Added `source` field to `WebUiPropsCommons`:

```scala
case class WebUiPropsCommons(
  name: String,
  value: String, 
  webUiPropsId: Option[String] = None,
  source: String = "database"
) extends WebUiPropsT with JsonFieldReName
```

Each prop now includes:
- `source="database"` for props stored in the database (editable via API)
- `source="config"` for props from configuration file (read-only)

### 4. Updated Documentation

Enhanced ResourceDoc descriptions to clarify:
- `what=active`: Returns one value per prop (database overrides config)
- `what=database`: Returns ONLY database props
- `what=config`: Returns ONLY config props
- Added `source` field explanation in response fields section

## Query Parameters

### GET /obp/v6.0.0/webui-props

**`what` parameter (optional, default: "active"):**

| Value | Behavior | Use Case |
|-------|----------|----------|
| `active` | One value per prop: database if exists, else config | Frontend display - get effective values |
| `database` | ONLY database-stored props | Admin UI - see what's been customized |
| `config` | ONLY config file defaults | Admin UI - see available defaults |

### GET /obp/v6.0.0/webui-props/{PROP_NAME}

**`active` parameter (optional boolean string, default: "false"):**

| Value | Behavior |
|-------|----------|
| `false` or omitted | Only database prop (fails if not in database) |
| `true` | Database prop, or fallback to config default |

## Response Format

```json
{
  "webui_props": [
    {
      "name": "webui_api_explorer_url",
      "value": "https://custom.example.com",
      "webui_props_id": "550e8400-e29b-41d4-a716-446655440000",
      "source": "database"
    },
    {
      "name": "webui_hello_message",
      "value": "Welcome to OBP",
      "webui_props_id": "default",
      "source": "config"
    }
  ]
}
```

## Examples

### Get active props (effective values)
```bash
GET /obp/v6.0.0/webui-props
GET /obp/v6.0.0/webui-props?what=active
```
Returns all props with database values taking precedence over config defaults.

### Get only customized props
```bash
GET /obp/v6.0.0/webui-props?what=database
```
Shows which props have been explicitly set via API.

### Get only config defaults
```bash
GET /obp/v6.0.0/webui-props?what=config
```
Shows all available default values from `sample.props.template`.

### Get single prop with fallback
```bash
GET /obp/v6.0.0/webui-props/webui_api_explorer_url?active=true
```
Returns database value if exists, otherwise config default.

## Frontend Integration

The `source` field enables UIs to:

1. **Show edit buttons only for editable props:**
   ```javascript
   if (prop.source === "database" || canCreateWebUiProps) {
     showEditButton();
   }
   ```

2. **Display visual indicators:**
   ```javascript
   const icon = prop.source === "database" ? "custom" : "default";
   const tooltip = prop.source === "database" 
     ? "Custom value (editable)" 
     : "Default from config (read-only)";
   ```

3. **Prevent edit attempts on config props:**
   ```javascript
   if (prop.source === "config") {
     showWarning("This is a config default. Create a database override to customize.");
   }
   ```

## Migration Notes

- **Backward Compatibility:** v5.1.0 and v3.1.0 endpoints unchanged
- **Default Value:** `source` defaults to `"database"` for backward compatibility
- **No Schema Changes:** Uses existing `WebUiPropsCommons` case class with new optional field

## Files Changed

1. `obp-api/src/main/scala/code/webuiprops/WebUiProps.scala`
   - Added `source` field to `WebUiPropsCommons`

2. `obp-api/src/main/scala/code/api/v6_0_0/APIMethods600.scala`
   - Fixed `what=active` logic to return one value per prop
   - Added `source` field to all WebUiPropsCommons instantiations
   - Updated ResourceDoc for both endpoints

3. `obp-api/src/main/scala/code/api/v6_0_0/OBPAPI6_0_0.scala`
   - Changed endpoint order to prioritize v6.0.0 over v5.1.0

## Testing

Test the different query modes:

```bash
# Get all active props (database + config, no duplicates)
curl http://localhost:8080/obp/v6.0.0/webui-props?what=active

# Get only database props
curl http://localhost:8080/obp/v6.0.0/webui-props?what=database

# Get only config props
curl http://localhost:8080/obp/v6.0.0/webui-props?what=config

# Get single prop (database only)
curl http://localhost:8080/obp/v6.0.0/webui-props/webui_api_explorer_url

# Get single prop with config fallback
curl http://localhost:8080/obp/v6.0.0/webui-props/webui_api_explorer_url?active=true
```

Verify that:
1. No duplicate property names in `what=active` response
2. Each prop includes `source` field
3. Database props have `source="database"`
4. Config props have `source="config"`
5. v6.0.0 endpoint is actually being called (check logs)