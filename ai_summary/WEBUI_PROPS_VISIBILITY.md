# WebUI Props Endpoint Visibility in API Explorer

## Question
**Why don't I see `/obp/v6.0.0/management/webui_props` in API Explorer II?**

---

## Answer

The endpoint **IS implemented** in v6.0.0, but it **requires authentication and a specific role**, which is why it may not appear in API Explorer II.

---

## Endpoint Details

### `/obp/v6.0.0/management/webui_props`

**File:** `obp-api/src/main/scala/code/api/v6_0_0/APIMethods600.scala`  
**Lines:** 3442-3498 (ResourceDoc), 3501-3542 (Implementation)

**Status:** ✅ **Implemented in v6.0.0**

**Authentication:** ✅ **Required** - Uses `authenticatedAccess(cc)`

**Authorization:** ✅ **Required** - Needs `CanGetWebUiProps` entitlement

**Tag:** `apiTagWebUiProps` (WebUi-Props)

**API Version:** `ApiVersion.v6_0_0`

---

## Why It's Not Visible in API Explorer II

### Reason 1: You're Not Logged In
API Explorer II may hide endpoints that require authentication when you're not logged in.

**Solution:** Log in to API Explorer II with a user account.

### Reason 2: You Don't Have the Required Role
The endpoint requires the `CanGetWebUiProps` entitlement.

**Code (line 3513):**
```scala
_ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canGetWebUiProps, callContext)
```

**Solution:** Grant yourself the `CanGetWebUiProps` role.

### Reason 3: API Explorer II Filters
API Explorer II may filter endpoints based on:
- Tags
- Authentication requirements
- Your current roles/entitlements
- API version selection

**Solution:** Check API Explorer II filters and settings.

---

## How to Verify the Endpoint Exists

### 1. Check via Direct API Call

```bash
# Get an authentication token first
curl -X POST https://your-api.com/obp/v6.0.0/my/logins/direct \
  -H "DirectLogin: username=YOUR_USERNAME, password=YOUR_PASSWORD, consumer_key=YOUR_CONSUMER_KEY"

# Then call the endpoint
curl -X GET https://your-api.com/obp/v6.0.0/management/webui_props \
  -H "Authorization: DirectLogin token=YOUR_TOKEN"
```

### 2. Check the ResourceDoc Endpoint

```bash
# Get all resource docs for v6.0.0
curl https://your-api.com/obp/v6.0.0/resource-docs/obp

# Search for webui_props
curl https://your-api.com/obp/v6.0.0/resource-docs/obp | grep -i "webui_props"
```

### 3. Search Code

```bash
cd OBP-API
grep -r "management/webui_props" obp-api/src/main/scala/code/api/v6_0_0/
```

**Output:**
```
APIMethods600.scala:      "/management/webui_props",
APIMethods600.scala:      case "management" :: "webui_props":: Nil JsonGet req => {
```

---

## Required Role

### Role Name
`CanGetWebUiProps`

### How to Grant This Role

#### Via API (requires admin access)
```bash
POST /obp/v4.0.0/users/USER_ID/entitlements

{
  "bank_id": "",
  "role_name": "CanGetWebUiProps"
}
```

#### Via Database (for development)
```sql
-- Check if user has the role
SELECT * FROM entitlement 
WHERE user_id = 'YOUR_USER_ID' 
AND role_name = 'CanGetWebUiProps';

-- Grant the role (if needed)
INSERT INTO entitlement (entitlement_id, user_id, role_name, bank_id)
VALUES (uuid(), 'YOUR_USER_ID', 'CanGetWebUiProps', '');
```

---

## All WebUI Props Endpoints in v6.0.0

### 1. Get All WebUI Props (Management)
```
GET /obp/v6.0.0/management/webui_props
GET /obp/v6.0.0/management/webui_props?what=active
GET /obp/v6.0.0/management/webui_props?what=database
GET /obp/v6.0.0/management/webui_props?what=config
```
- **Authentication:** Required
- **Role:** `CanGetWebUiProps`
- **Tag:** `apiTagWebUiProps`

### 2. Get Single WebUI Prop (Public-ish)
```
GET /obp/v6.0.0/webui-props/WEBUI_PROP_NAME
GET /obp/v6.0.0/webui-props/WEBUI_PROP_NAME?active=true
```
- **Authentication:** NOT required (anonymous access)
- **Role:** None
- **Tag:** `apiTagWebUiProps`

**Example:**
```bash
# No authentication needed!
curl https://your-api.com/obp/v6.0.0/webui-props/webui_api_explorer_url?active=true
```

---

## Code References

### ResourceDoc Definition
**File:** `obp-api/src/main/scala/code/api/v6_0_0/APIMethods600.scala`  
**Lines:** 3442-3498

```scala
staticResourceDocs += ResourceDoc(
  getWebUiProps,
  implementedInApiVersion,  // ApiVersion.v6_0_0
  nameOf(getWebUiProps),
  "GET",
  "/management/webui_props",
  "Get WebUiProps",
  s"""...""",
  EmptyBody,
  ListResult("webui_props", ...),
  List(
    UserNotLoggedIn,
    UserHasMissingRoles,
    UnknownError
  ),
  List(apiTagWebUiProps),
  Some(List(canGetWebUiProps))  // ← ROLE REQUIRED
)
```

### Implementation
**File:** `obp-api/src/main/scala/code/api/v6_0_0/APIMethods600.scala`  
**Lines:** 3501-3542

```scala
lazy val getWebUiProps: OBPEndpoint = {
  case "management" :: "webui_props":: Nil JsonGet req => {
    cc => implicit val ec = EndpointContext(Some(cc))
      val what = ObpS.param("what").getOrElse("active")
      for {
        (Full(u), callContext) <- authenticatedAccess(cc)  // ← AUTH REQUIRED
        ...
        _ <- NewStyle.function.hasEntitlement("", u.userId, 
             ApiRole.canGetWebUiProps, callContext)  // ← ROLE CHECK
        ...
      }
  }
}
```

### Role Definition
**File:** `obp-api/src/main/scala/code/api/util/ApiRole.scala`  
**Lines:** ~1300+

```scala
case class CanGetWebUiProps(requiresBankId: Boolean = false) extends ApiRole
lazy val canGetWebUiProps = CanGetWebUiProps()
```

---

## Comparison with Other Versions

### v3.1.0
- `GET /obp/v3.1.0/management/webui_props` - **Authentication + CanGetWebUiProps required**

### v5.1.0
- `GET /obp/v5.1.0/management/webui_props` - **No authentication required** (different implementation)

### v6.0.0
- `GET /obp/v6.0.0/management/webui_props` - **Authentication + CanGetWebUiProps required**
- `GET /obp/v6.0.0/webui-props/{NAME}` - **No authentication required** (new endpoint)

---

## Summary

| Aspect | Status | Details |
|--------|--------|---------|
| **Implemented in v6.0.0** | ✅ Yes | Line 3442-3542 in APIMethods600.scala |
| **Authentication Required** | ✅ Yes | Uses `authenticatedAccess(cc)` |
| **Role Required** | ✅ Yes | `CanGetWebUiProps` |
| **Tag** | `apiTagWebUiProps` | WebUi-Props category |
| **Why Not Visible** | Security | Hidden from non-authenticated users or users without role |
| **How to See It** | 1. Log in to API Explorer<br>2. Grant yourself `CanGetWebUiProps` role<br>3. Refresh API Explorer | |

---

## Alternative: Use the Public Endpoint

If you just want to **read** WebUI props without authentication, use the **single prop endpoint**:

```bash
# Public access - no authentication needed
curl https://your-api.com/obp/v6.0.0/webui-props/webui_api_explorer_url?active=true
```

This endpoint is available in v6.0.0 and does **NOT** require authentication or roles.

---

## Testing Commands

```bash
# 1. Check if you're logged in
curl https://your-api.com/obp/v6.0.0/users/current \
  -H "Authorization: DirectLogin token=YOUR_TOKEN"

# 2. Check your roles
curl https://your-api.com/obp/v6.0.0/users/current \
  -H "Authorization: DirectLogin token=YOUR_TOKEN" | grep -i "CanGetWebUiProps"

# 3. Try to call the endpoint
curl https://your-api.com/obp/v6.0.0/management/webui_props \
  -H "Authorization: DirectLogin token=YOUR_TOKEN"

# If you get UserHasMissingRoles error, you need to grant yourself the role
# If you get 200 OK, the endpoint works!
```
