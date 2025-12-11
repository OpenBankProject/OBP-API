# Endpoints That Need Role Documentation Fixes

This document identifies OBP API endpoints that have issues with role documentation or redundant role checks.

## Issue Types

1. **Missing Role in ResourceDoc**: Endpoint has `hasEntitlement` check in code but NO role specified in ResourceDoc `Some(List(...))`
2. **Duplicate Role Check**: Endpoint has role in ResourceDoc AND redundant `hasEntitlement` check in for comprehension

## Why This Matters

- **ResourceDoc roles** are the canonical source of truth for API documentation and are automatically enforced by the framework
- **Redundant hasEntitlement checks** in for comprehensions are unnecessary and violate DRY principle
- **Missing ResourceDoc roles** mean the API documentation doesn't reflect actual authorization requirements

## Methodology to Find Issues

```bash
# Find all hasEntitlement calls
grep -n "hasEntitlement.*callContext)" obp-api/src/main/scala/code/api/v*/APIMethods*.scala

# For each endpoint with hasEntitlement, check if ResourceDoc has role defined
# Look for the ResourceDoc definition above the endpoint and check for Some(List(...))
```

## Known Issues to Fix

### v3.1.0

#### getCustomerByCustomerId
- **File**: `obp-api/src/main/scala/code/api/v3_1_0/APIMethods310.scala`
- **Line**: ~1285
- **Issue**: Duplicate - has role in ResourceDoc AND hasEntitlement in code
- **ResourceDoc Role**: `Some(List(canGetCustomersAtOneBank))`
- **Code Check**: `_ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canGetCustomersAtOneBank, callContext)`
- **Action**: Remove hasEntitlement from for comprehension

#### getCustomerByCustomerNumber
- **File**: `obp-api/src/main/scala/code/api/v3_1_0/APIMethods310.scala`
- **Line**: ~1328
- **Issue**: Duplicate - has role in ResourceDoc AND hasEntitlement in code
- **ResourceDoc Role**: `Some(List(canGetCustomersAtOneBank))`
- **Code Check**: `_ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canGetCustomersAtOneBank, callContext)`
- **Action**: Remove hasEntitlement from for comprehension

### v5.1.0

#### getCustomersByLegalName
- **File**: `obp-api/src/main/scala/code/api/v5_1_0/APIMethods510.scala`
- **Line**: ~2915
- **Issue**: Duplicate - has role in ResourceDoc AND hasEntitlement in code
- **ResourceDoc Role**: `Some(List(canGetCustomersAtOneBank))`
- **Code Check**: `_ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canGetCustomersAtOneBank, callContext)`
- **Action**: Remove hasEntitlement from for comprehension
- **Status**: ⚠️ May have been fixed - verify

### v6.0.0

#### getCustomersByLegalName
- **Status**: ✅ FIXED - Redundant check removed

#### getCustomerByCustomerId
- **Status**: ✅ FIXED - Redundant check removed

#### getCustomerByCustomerNumber
- **Status**: ✅ FIXED - Redundant check removed

#### deleteEntitlement
- **File**: `obp-api/src/main/scala/code/api/v6_0_0/APIMethods600.scala`
- **Line**: ~2531
- **Issue**: Duplicate - has role in ResourceDoc AND hasEntitlement in code
- **ResourceDoc Role**: `Some(List(canDeleteEntitlementAtAnyBank))` (line 2524)
- **Code Check**: `_ <- NewStyle.function.hasEntitlement("", u.userId, canDeleteEntitlementAtAnyBank, callContext)` (line 2531)
- **Action**: TODO - Verify if this is intentional pattern or should remove hasEntitlement from for comprehension
- **Status**: ⚠️ TO REVIEW - Just added in v6.0.0, copied from v2.0.0 which also has duplicate check
- **Note**: This endpoint was newly added to v6.0.0 to support modified return values

#### getMetrics
- **File**: `obp-api/src/main/scala/code/api/v6_0_0/APIMethods600.scala`
- **Line**: ~1447
- **Issue**: Duplicate - has role in ResourceDoc AND hasEntitlement in code
- **ResourceDoc Role**: `Some(List(canReadMetrics))` (line 1438)
- **Code Check**: `_ <- NewStyle.function.hasEntitlement("", u.userId, canReadMetrics, callContext)` (line 1447)
- **Action**: TODO - Verify if this is intentional pattern or should remove hasEntitlement from for comprehension
- **Status**: ⚠️ TO REVIEW - Just added in v6.0.0, copied from v5.1.0 which also has duplicate check
- **Note**: This endpoint has automatic from_date default and empty metrics warning log

### v2.0.0

#### getKycDocuments
- **File**: `obp-api/src/main/scala/code/api/v2_0_0/APIMethods200.scala`
- **Line**: ~487
- **Issue**: Duplicate - has role in ResourceDoc AND hasEntitlement in code
- **ResourceDoc Role**: `Some(List(canGetAnyKycDocuments))`
- **Code Check**: `_ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canGetAnyKycDocuments, callContext)`
- **Action**: Remove hasEntitlement from for comprehension
- **Status**: ✅ FIXED - Role already in ResourceDoc (line 477)

#### getKycMedia
- **File**: `obp-api/src/main/scala/code/api/v2_0_0/APIMethods200.scala`
- **Line**: ~521
- **Issue**: Duplicate - has role in ResourceDoc AND hasEntitlement in code
- **ResourceDoc Role**: `Some(List(canGetAnyKycMedia))`
- **Code Check**: `_ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canGetAnyKycMedia, callContext)`
- **Action**: Remove hasEntitlement from for comprehension
- **Status**: ✅ FIXED - Role already in ResourceDoc (line 514)

#### getKycChecks
- **File**: `obp-api/src/main/scala/code/api/v2_0_0/APIMethods200.scala`
- **Line**: ~555
- **Issue**: Duplicate - has role in ResourceDoc AND hasEntitlement in code
- **ResourceDoc Role**: `Some(List(canGetAnyKycChecks))`
- **Code Check**: `_ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canGetAnyKycChecks, callContext)`
- **Action**: Remove hasEntitlement from for comprehension
- **Status**: ✅ FIXED - Role already in ResourceDoc (line 548)

#### getKycStatuses
- **File**: `obp-api/src/main/scala/code/api/v2_0_0/APIMethods200.scala`
- **Line**: ~588
- **Issue**: Duplicate - has role in ResourceDoc AND hasEntitlement in code
- **ResourceDoc Role**: `Some(List(canGetAnyKycStatuses))`
- **Code Check**: `_ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canGetAnyKycStatuses, callContext)`
- **Action**: Remove hasEntitlement from for comprehension
- **Status**: ✅ FIXED - Role already in ResourceDoc (line 581)

#### getSocialMediaHandles
- **File**: `obp-api/src/main/scala/code/api/v2_0_0/APIMethods200.scala`
- **Line**: ~621
- **Issue**: Duplicate - has role in ResourceDoc AND hasEntitlement in code
- **ResourceDoc Role**: `Some(List(canGetSocialMediaHandles))`
- **Code Check**: `_ <- NewStyle.function.hasEntitlement(bank.bankId.value, u.userId, canGetSocialMediaHandles, callContext)`
- **Action**: Remove hasEntitlement from for comprehension
- **Status**: ✅ FIXED - Role already in ResourceDoc (line 614)

#### addKycDocument
- **File**: `obp-api/src/main/scala/code/api/v2_0_0/APIMethods200.scala`
- **Line**: ~660
- **Issue**: Duplicate - has role in ResourceDoc AND hasEntitlement in code
- **ResourceDoc Role**: `Some(List(canAddKycDocument))`
- **Code Check**: `_ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, ApiRole.canAddKycDocument, callContext)`
- **Action**: Remove hasEntitlement from for comprehension
- **Status**: ✅ FIXED - Role already in ResourceDoc (line 649)

#### addKycMedia
- **File**: `obp-api/src/main/scala/code/api/v2_0_0/APIMethods200.scala`
- **Line**: ~710
- **Issue**: Duplicate - has role in ResourceDoc AND hasEntitlement in code
- **ResourceDoc Role**: `Some(List(canAddKycMedia))`
- **Code Check**: `_ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, ApiRole.canAddKycMedia, callContext)`
- **Action**: Remove hasEntitlement from for comprehension
- **Status**: ✅ FIXED - Role already in ResourceDoc (line 701)

#### addKycCheck
- **File**: `obp-api/src/main/scala/code/api/v2_0_0/APIMethods200.scala`
- **Line**: ~760
- **Issue**: Duplicate - has role in ResourceDoc AND hasEntitlement in code
- **ResourceDoc Role**: `Some(List(canAddKycCheck))`
- **Code Check**: `_ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, ApiRole.canAddKycCheck, callContext)`
- **Action**: Remove hasEntitlement from for comprehension
- **Status**: ✅ FIXED - Role already in ResourceDoc (line 751)

#### addKycStatus
- **File**: `obp-api/src/main/scala/code/api/v2_0_0/APIMethods200.scala`
- **Line**: ~811
- **Issue**: Duplicate - has role in ResourceDoc AND hasEntitlement in code
- **ResourceDoc Role**: `Some(List(canAddKycStatus))`
- **Code Check**: `_ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, ApiRole.canAddKycStatus, callContext)`
- **Action**: Remove hasEntitlement from for comprehension
- **Status**: ✅ FIXED - Role already in ResourceDoc (line 802)

#### addSocialMediaHandle
- **File**: `obp-api/src/main/scala/code/api/v2_0_0/APIMethods200.scala`
- **Line**: ~861
- **Issue**: Duplicate - has role in ResourceDoc AND hasEntitlement in code
- **ResourceDoc Role**: `Some(List(canAddSocialMediaHandle))`
- **Code Check**: `_ <- NewStyle.function.hasEntitlement(bank.bankId.value, u.userId, canAddSocialMediaHandle, cc.callContext)`
- **Action**: Remove hasEntitlement from for comprehension
- **Status**: ✅ FIXED - Role already in ResourceDoc (line 852)

#### deleteEntitlement
- **File**: `obp-api/src/main/scala/code/api/v2_0_0/APIMethods200.scala`
- **Line**: ~1916
- **Issue**: Duplicate - has role in ResourceDoc AND hasEntitlement in code
- **ResourceDoc Role**: `Some(List(canDeleteEntitlementAtAnyBank))`
- **Code Check**: `_ <- NewStyle.function.hasEntitlement("", u.userId, canDeleteEntitlementAtAnyBank, cc.callContext)`
- **Action**: Remove hasEntitlement from for comprehension
- **Status**: ✅ FIXED - Role added to ResourceDoc (line 1910)

#### getAllEntitlements
- **File**: `obp-api/src/main/scala/code/api/v2_0_0/APIMethods200.scala`
- **Line**: ~1954
- **Issue**: Duplicate - has role in ResourceDoc AND hasEntitlement in code
- **ResourceDoc Role**: `Some(List(canGetEntitlementsForAnyUserAtAnyBank))`
- **Code Check**: `_ <- NewStyle.function.hasEntitlement("", u.userId, canGetEntitlementsForAnyUserAtAnyBank, callContext)`
- **Action**: Remove hasEntitlement from for comprehension
- **Status**: ✅ FIXED - Role added to ResourceDoc (line 1949)

### v2.1.0

#### sandboxDataImport
- **File**: `obp-api/src/main/scala/code/api/v2_1_0/APIMethods210.scala`
- **Line**: ~140
- **Issue**: Duplicate - has role in ResourceDoc AND hasEntitlement in code
- **ResourceDoc Role**: `Some(List(canCreateSandbox))`
- **Code Check**: `_ <- NewStyle.function.hasEntitlement("", u.userId, canCreateSandbox, cc.callContext)`
- **Action**: Remove hasEntitlement from for comprehension
- **Status**: ✅ FIXED - Role already in ResourceDoc (line 131)

#### addCardForBank
- **File**: `obp-api/src/main/scala/code/api/v2_1_0/APIMethods210.scala`
- **Line**: ~995
- **Issue**: Duplicate - has role in ResourceDoc AND hasEntitlement in code
- **ResourceDoc Role**: `Some(List(canCreateCardsForBank))`
- **Code Check**: `_ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, ApiRole.canCreateCardsForBank, callContext)`
- **Action**: Remove hasEntitlement from for comprehension
- **Status**: ✅ FIXED - Role already in ResourceDoc (line 988)

### v1.3.0

#### getCardsForBank
- **File**: `obp-api/src/main/scala/code/api/v1_3_0/APIMethods130.scala`
- **Line**: ~105
- **Issue**: Duplicate - has role in ResourceDoc AND hasEntitlement in code
- **ResourceDoc Role**: `Some(List(canGetCardsForBank))`
- **Code Check**: `_ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, ApiRole.canGetCardsForBank, callContext)`
- **Action**: Remove hasEntitlement from for comprehension
- **Status**: ✅ FIXED - Role added to ResourceDoc (line 99)

## How to Fix

### For Missing Roles in ResourceDoc

1. Locate the ResourceDoc definition for the endpoint
2. Add the role to the ResourceDoc: `Some(List(roleNameHere))`
3. Example:
   ```scala
   resourceDocs += ResourceDoc(
     getKycDocuments,
     apiVersion,
     "getKycDocuments",
     "GET",
     "/banks/BANK_ID/customers/CUSTOMER_ID/kyc_documents",
     "Get KYC Documents",
     "...",
     EmptyBody,
     kycDocumentsJSON,
     List(UserNotLoggedIn, UnknownError),
     List(apiTagKyc),
     Some(List(canGetAnyKycDocuments))  // <-- ADD THIS
   )
   ```

### For Duplicate Role Checks

1. Verify the role is properly defined in ResourceDoc
2. Write tests that verify role checking works (401/403 errors without role, 200 with role)
3. Remove the redundant `hasEntitlement` line from the for comprehension
4. Remove the `authenticatedAccess` line if it's only used for the hasEntitlement check
5. Example:
   ```scala
   // BEFORE (redundant)
   for {
     (Full(u), callContext) <- authenticatedAccess(cc)
     _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, canGetCustomersAtOneBank, callContext)
     (customer, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, callContext)
   } yield { ... }
   
   // AFTER (clean)
   for {
     (customer, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, cc.callContext)
   } yield { ... }
   ```

## Testing Requirements

Before removing any hasEntitlement check:
1. Ensure the role is in ResourceDoc
2. Write comprehensive tests covering:
   - Test without credentials → expects 401
   - Test without role → expects 403 with UserHasMissingRoles
   - Test with role → expects success (200/201/etc)

## Progress Tracking

- ✅ Roles in ResourceDoc: 17 endpoints
  - 3 in v6.0.0 (with tests)
  - 10 in v2.0.0 (4 GET KYC + 4 Add KYC + 2 Entitlement - 2 roles just added)
  - 2 in v2.1.0
  - 1 in v1.3.0 (role just added)
  - All 17 still have redundant hasEntitlement checks that should be removed
- 🔍 To Review: v3.1.0 (3 endpoints), v5.1.0 (1 endpoint)
- ⚠️ Dynamic endpoints may need special handling

## Notes

- Dynamic endpoints and dynamic entities have their own role management system
- Some v2.0.0 endpoints are marked as "OldStyle" and may have different patterns
- Priority should be given to newer API versions (v5.x, v6.x) that are actively used

## Related Documentation

- See `developer_notes_roles.md` for role naming conventions
- See `release_notes.md` (18/11/2025) for role name changes
- See test files in `obp-api/src/test/scala/code/api/v6_0_0/CustomerTest.scala` for testing patterns

Last Updated: 2025-01-XX