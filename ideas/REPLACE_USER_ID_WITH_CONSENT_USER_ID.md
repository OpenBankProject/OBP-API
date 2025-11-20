# Replacing User ID with Consent User ID at Connector Level

## Overview

This document explains where and how to replace the authenticated user's `user_id` with a `user_id` from a consent at the connector level in the OBP-API. This replacement should occur after security guards (authentication/authorization) but just before database operations or external messaging (RabbitMQ, REST, etc.).

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Key Concepts](#key-concepts)
3. [Where to Make Changes](#where-to-make-changes)
4. [Implementation Guide](#implementation-guide)
5. [Important Considerations](#important-considerations)

---

## Architecture Overview

### Call Flow

```
API Endpoint
    ↓
Authentication/Authorization (Security Guards)
    ↓
CallContext (contains user + consenter)
    ↓
┌─────────────────────────────────────────────┐
│ THIS IS WHERE USER_ID REPLACEMENT HAPPENS   │
└─────────────────────────────────────────────┘
    ↓
    ├──→ External Connectors (RabbitMQ, REST, Akka, etc.)
    │       ↓
    │   toOutboundAdapterCallContext()
    │       ↓
    │   External Adapter/System
    │
    └──→ LocalMappedConnector (Built-in Database)
            ↓
        Direct Database Operations
```

### CallContext Structure

The `CallContext` class (in `code.api.util.ApiSession`) contains:

```scala
case class CallContext(
  user: Box[User] = Empty,              // The authenticated user
  consenter: Box[User] = Empty,         // The user from consent (if present)
  consumer: Box[Consumer] = Empty,
  // ... other fields
)
```

When Berlin Group consents are applied, the `consenter` field is populated with the consent user:

```scala
// From ConsentUtil.scala line 596
val updatedCallContext = callContext.copy(consenter = user)
```

---

## Key Concepts

### 1. Consent User vs Authenticated User

- **Authenticated User**: The user/application that made the API request (in `callContext.user`)
- **Consent User**: The account holder who gave consent for access (in `callContext.consenter`)

In consent-based scenarios (e.g., Berlin Group PSD2), a TPP (Third Party Provider) authenticates, but operates on behalf of the PSU (Payment Service User) who gave consent.

### 2. Connector Types

#### External Connectors

- **RabbitMQConnector_vOct2024**
- **RestConnector_vMar2019**
- **AkkaConnector_vDec2018**
- **StoredProcedureConnector_vDec2019**
- **EthereumConnector_vSept2025**
- **CardanoConnector_vJun2025**

These send messages to external adapters/systems and use `OutboundAdapterCallContext`.

#### Internal Connector

- **LocalMappedConnector**

Works directly with the OBP database (Mapper/ORM layer) and does NOT use `OutboundAdapterCallContext`.

---

## Where to Make Changes

### For External Connectors

**Location**: `OBP-API/obp-api/src/main/scala/code/api/util/ApiSession.scala`

**Method**: `CallContext.toOutboundAdapterCallContext` (lines 65-115)

This is the **single transformation point** where `CallContext` is converted to `OutboundAdapterCallContext` before being sent to external systems.

Example of how it's used in connectors:

```scala
// From RabbitMQConnector_vOct2024.scala line 2204
override def makePaymentv210(..., callContext: Option[CallContext]): ... = {
  val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull, ...)
  val response: Future[Box[InBound]] = sendRequest[InBound]("obp_make_paymentv210", req, callContext)
}
```

### For LocalMappedConnector

**Challenge**: LocalMappedConnector does NOT use `toOutboundAdapterCallContext`. It works directly with database entities.

**Key Location**: `OBP-API/obp-api/src/main/scala/code/bankconnectors/LocalMappedConnector.scala`

Methods like `savePayment` (line 2223) and `getBankAccountsForUserLegacy` (line 624) work directly with parameters and database objects.

---

## Implementation Guide

### Option 1: Modify toOutboundAdapterCallContext (External Connectors Only)

This approach works for RabbitMQ, REST, Akka, and other external connectors.

#### Current Implementation

```scala
// ApiSession.scala lines 65-115
def toOutboundAdapterCallContext: OutboundAdapterCallContext = {
  for {
    user <- this.user
    username <- tryo(Some(user.name))
    currentResourceUserId <- tryo(Some(user.userId))  // <-- Uses authenticated user
    consumerId = this.consumer.map(_.consumerId.get).openOr("")
    permission <- Views.views.vend.getPermissionForUser(user)
    views <- tryo(permission.views)
    linkedCustomers <- tryo(CustomerX.customerProvider.vend.getCustomersByUserId(user.userId))
    // ...
    OutboundAdapterCallContext(
      correlationId = this.correlationId,
      sessionId = this.sessionId,
      consumerId = Some(consumerId),
      generalContext = Some(generalContextFromPassThroughHeaders),
      outboundAdapterAuthInfo = Some(OutboundAdapterAuthInfo(
        userId = currentResourceUserId,  // <-- Authenticated user's ID sent to adapter
        username = username,
        linkedCustomers = likedCustomersBasic,
        userAuthContext = basicUserAuthContexts,
        if (authViews.isEmpty) None else Some(authViews))),
      outboundAdapterConsenterInfo =
        if (this.consenter.isDefined){
          Some(OutboundAdapterAuthInfo(
            username = this.consenter.toOption.map(_.name)))
        } else {
          None
        }
    )
  }
}
```

#### Proposed Change

```scala
def toOutboundAdapterCallContext: OutboundAdapterCallContext = {
  for {
    user <- this.user

    // Determine the effective user: use consenter if present, otherwise authenticated user
    val effectiveUser = this.consenter.toOption.getOrElse(user)

    username <- tryo(Some(effectiveUser.name))
    currentResourceUserId <- tryo(Some(effectiveUser.userId))  // <-- NOW uses consent user if present
    consumerId = this.consumer.map(_.consumerId.get).openOr("")

    // Use effectiveUser for permissions and linked data
    permission <- Views.views.vend.getPermissionForUser(effectiveUser)
    views <- tryo(permission.views)
    linkedCustomers <- tryo(CustomerX.customerProvider.vend.getCustomersByUserId(effectiveUser.userId))
    likedCustomersBasic = if (linkedCustomers.isEmpty) None else Some(createInternalLinkedBasicCustomersJson(linkedCustomers))
    userAuthContexts <- UserAuthContextProvider.userAuthContextProvider.vend.getUserAuthContextsBox(effectiveUser.userId)
    basicUserAuthContextsFromDatabase = if (userAuthContexts.isEmpty) None else Some(createBasicUserAuthContextJson(userAuthContexts))
    generalContextFromPassThroughHeaders = createBasicUserAuthContextJsonFromCallContext(this)
    basicUserAuthContexts = Some(basicUserAuthContextsFromDatabase.getOrElse(List.empty[BasicUserAuthContext]))
    authViews <- tryo(
      for {
        view <- views
        (account, callContext) <- code.bankconnectors.LocalMappedConnector.getBankAccountLegacy(view.bankId, view.accountId, Some(this)) ?~! {BankAccountNotFound}
        internalCustomers = createAuthInfoCustomersJson(account.customerOwners.toList)
        internalUsers = createAuthInfoUsersJson(account.userOwners.toList)
        viewBasic = ViewBasic(view.viewId.value, view.name, view.description)
        accountBasic = AccountBasic(
          account.accountId.value,
          account.accountRoutings,
          internalCustomers.customers,
          internalUsers.users)
      } yield
        AuthView(viewBasic, accountBasic)
    )
  } yield {
    OutboundAdapterCallContext(
      correlationId = this.correlationId,
      sessionId = this.sessionId,
      consumerId = Some(consumerId),
      generalContext = Some(generalContextFromPassThroughHeaders),
      outboundAdapterAuthInfo = Some(OutboundAdapterAuthInfo(
        userId = currentResourceUserId,  // <-- Now contains consent user's ID
        username = username,              // <-- Now contains consent user's name
        linkedCustomers = likedCustomersBasic,
        userAuthContext = basicUserAuthContexts,
        if (authViews.isEmpty) None else Some(authViews))),
      outboundAdapterConsenterInfo =
        if (this.consenter.isDefined) {
          Some(OutboundAdapterAuthInfo(
            userId = Some(this.consenter.toOption.get.userId),  // <-- ADD this
            username = this.consenter.toOption.map(_.name)))
        } else {
          None
        }
    )
  }}.openOr(OutboundAdapterCallContext(
    this.correlationId,
    this.sessionId))
}
```

### Option 2: Add Helper Method (For Both Internal and External)

Add a convenience method to `CallContext` that returns the effective user:

```scala
// Add to CallContext class in ApiSession.scala
case class CallContext(
  // ... existing fields
) {
  // ... existing methods

  /**
   * Returns the consent user if present, otherwise returns the authenticated user.
   * Use this method when you need the "effective" user for operations.
   */
  def effectiveUser: Box[User] = consenter.or(user)

  /**
   * Returns the user ID of the effective user (consent user if present, otherwise authenticated user).
   * Throws exception if no user is available.
   */
  def effectiveUserId: String = effectiveUser.map(_.userId).openOrThrowException(UserNotLoggedIn)

  // ... rest of class
}
```

Then use throughout the codebase:

```scala
// In LocalMappedConnector or other places
override def getBankAccountsForUserLegacy(..., callContext: Option[CallContext]): ... = {
  // Instead of getting user from parameters
  val userId = callContext.map(_.effectiveUserId).getOrElse(...)
  val userAuthContexts = UserAuthContextProvider.userAuthContextProvider.vend.getUserAuthContextsBox(userId)
  // ...
}
```

### Option 3: Hybrid Approach (Recommended)

Combine both options:

1. **For External Connectors**: Implement the change in `toOutboundAdapterCallContext` (Option 1)
2. **For Internal Code**: Add helper methods (Option 2) and use them where appropriate
3. **At API Layer**: Consider handling critical consent-based operations at the endpoint level before calling connectors

---

## Important Considerations

### 1. LocalMappedConnector Limitations

The `LocalMappedConnector` typically doesn't use `CallContext` for user information in its core transaction methods. For example:

```scala
// LocalMappedConnectorInternal.scala line 613
def saveTransaction(
  fromAccount: BankAccount,
  toAccount: BankAccount,
  // ... other parameters
  // NOTE: No callContext parameter!
): Box[TransactionId] = {
  // Creates transaction directly from account objects
  mappedTransaction <- tryo(MappedTransaction.create
    .bank(fromAccount.bankId.value)
    .account(fromAccount.accountId.value)
    // ...
}
```

The user information is embedded in the `BankAccount` objects passed to these methods, not extracted from `CallContext`.

### 2. Consent Flow

The consent user is set in `ConsentUtil.scala`:

```scala
// ConsentUtil.scala line 596
case Full(storedConsent) =>
  val user = Users.users.vend.getUserByUserId(storedConsent.userId)
  val updatedCallContext = callContext.copy(consenter = user)
```

This happens during Berlin Group consent validation, so `callContext.consenter` will only be populated for consent-based requests.

### 3. OutboundAdapterAuthInfo Structure

The structure sent to external adapters:

```scala
// From CommonModel.scala line 1221
case class OutboundAdapterAuthInfo(
  userId: Option[String] = None,              // Main user ID
  username: Option[String] = None,            // Main username
  linkedCustomers: Option[List[BasicLinkedCustomer]] = None,
  userAuthContext: Option[List[BasicUserAuthContext]] = None,
  authViews: Option[List[AuthView]] = None,
)

// And in OutboundAdapterCallContext line 1207
case class OutboundAdapterCallContext(
  correlationId: String = "",
  sessionId: Option[String] = None,
  consumerId: Option[String] = None,
  generalContext: Option[List[BasicGeneralContext]] = None,
  outboundAdapterAuthInfo: Option[OutboundAdapterAuthInfo] = None,      // Main user
  outboundAdapterConsenterInfo: Option[OutboundAdapterAuthInfo] = None, // Consent user
)
```

Currently, `outboundAdapterAuthInfo` contains the authenticated user, and `outboundAdapterConsenterInfo` contains minimal consent user info. After the proposed change, `outboundAdapterAuthInfo` would contain the consent user when present.

### 4. Security Implications

**IMPORTANT**: This change means that operations will be performed using the consent user's identity rather than the authenticated user's identity. Ensure:

- Security guards have already validated that the authenticated user has permission to act on behalf of the consent user
- Audit logs capture both the authenticated user (who made the request) and the effective user (whose account is being accessed)
- The consent is valid and not expired before this transformation happens

### 5. Backward Compatibility

When implementing this change:

- Existing requests without consent should continue to work (use authenticated user)
- External adapters might need updates if they rely on specific user ID mappings
- Consider adding a feature flag to enable/disable this behavior during testing

### 6. Testing Strategy

Test scenarios:

1. **No Consent**: Request with authenticated user only → should use authenticated user's ID
2. **With Valid Consent**: Request with consent → should use consent user's ID
3. **Expired Consent**: Should fail before reaching connector level
4. **Mixed Operations**: Some endpoints with consent, some without → each should use correct user
5. **External vs Internal**: Verify both external connectors and LocalMappedConnector behave correctly

### 7. Dynamic Entities

**Challenge**: Dynamic Entities pass `userId` explicitly as a parameter rather than relying solely on `CallContext`.

**Location**: `OBP-API/obp-api/src/main/scala/code/api/dynamic/entity/APIMethodsDynamicEntity.scala`

#### Current Implementation

In the Dynamic Entity generic endpoint (line 132):

```scala
(box, _) <- NewStyle.function.invokeDynamicConnector(
  operation,
  entityName,
  None,
  Option(id).filter(StringUtils.isNotBlank),
  bankId,
  None,
  Some(u.userId),  // <-- Uses authenticated user's ID directly
  isPersonalEntity,
  Some(cc)
)
```

The `userId` parameter is extracted from the authenticated user (`u.userId`) and passed directly to the connector.

#### Flow Through to Connector

This userId flows through:

1. **NewStyle.function.invokeDynamicConnector** (ApiUtil.scala line 3372)
2. **Connector.dynamicEntityProcess** (Connector.scala line 1766)
3. **LocalMappedConnector.dynamicEntityProcess** (LocalMappedConnector.scala line 4324)
4. **DynamicDataProvider methods** (DynamicDataProvider.scala line 35-43)

Example in LocalMappedConnector:

```scala
case GET_ALL => Full {
  val dataList = DynamicDataProvider.connectorMethodProvider.vend
    .getAllDataJson(bankId, entityName, userId, isPersonalEntity)  // <-- userId used here
  JArray(dataList)
}
```

#### Proposed Solution

**Option A: Use effectiveUserId in API Layer**

Modify `APIMethodsDynamicEntity.scala` to use the consent user when present:

```scala
for {
  (Full(u), callContext) <- authenticatedAccess(callContext)

  // Determine effective user: consent user if present, otherwise authenticated user
  effectiveUserId = callContext.consenter.map(_.userId).openOr(u.userId)

  // ... other validations ...

  (box, _) <- NewStyle.function.invokeDynamicConnector(
    operation,
    entityName,
    None,
    Option(id).filter(StringUtils.isNotBlank),
    bankId,
    None,
    Some(effectiveUserId),  // <-- Now uses consent user if available
    isPersonalEntity,
    Some(cc)
  )
} yield {
  // ...
}
```

**Option B: Add Helper to CallContext (Recommended)**

Use the `effectiveUserId` helper method proposed in Option 2:

```scala
for {
  (Full(u), callContext) <- authenticatedAccess(callContext)

  // ... other validations ...

  (box, _) <- NewStyle.function.invokeDynamicConnector(
    operation,
    entityName,
    None,
    Option(id).filter(StringUtils.isNotBlank),
    bankId,
    None,
    Some(callContext.effectiveUserId),  // <-- Uses helper method
    isPersonalEntity,
    Some(cc)
  )
} yield {
  // ...
}
```

#### Impact Analysis

Dynamic Entities use `userId` for:

1. **Personal Entities (`isPersonalEntity = true`)**: Scoping data to specific users
   - GET_ALL: Filters data by userId
   - GET_ONE: Validates ownership by userId
   - CREATE: Associates new data with userId
   - UPDATE/DELETE: Validates user owns the data

2. **System/Bank Level Entities (`isPersonalEntity = false`)**: userId may be optional or used for audit

Example from `MappedDynamicDataProvider.scala`:

```scala
override def get(bankId: Option[String], entityName: String, id: String,
                 userId: Option[String], isPersonalEntity: Boolean): Box[DynamicDataT] = {
  if (bankId.isEmpty && isPersonalEntity) {
    DynamicData.find(
      By(DynamicData.DynamicEntityName, entityName),
      By(DynamicData.DynamicDataId, id),
      By(DynamicData.UserId, userId.get)  // <-- Filters by userId for personal entities
    ) match {
      case Full(dynamicData) => Full(dynamicData)
      case _ => Failure(s"$DynamicDataNotFound dynamicEntityName=$entityName, dynamicDataId=$id, userId = $userId")
    }
  }
  // ...
}
```

#### Recommendation

For Dynamic Entities with consent support:

1. **Implement Option B**: Use `callContext.effectiveUserId` helper
2. **Update all invocation points** in `APIMethodsDynamicEntity.scala` (lines 132, 213, 273, 280, 343, 351)
3. **Document behavior**: Personal Dynamic Entities will be scoped to the consent user when consent is present
4. **Security consideration**: Ensure consent validation happens before reaching Dynamic Entity operations

#### Files to Modify

- `OBP-API/obp-api/src/main/scala/code/api/dynamic/entity/APIMethodsDynamicEntity.scala` - Replace `Some(u.userId)` with `Some(callContext.effectiveUserId)`
- `OBP-API/obp-api/src/main/scala/code/api/util/ApiSession.scala` - Add `effectiveUserId` helper to CallContext

---

## Related Files

### Key Files to Modify

- `OBP-API/obp-api/src/main/scala/code/api/util/ApiSession.scala` - CallContext and toOutboundAdapterCallContext
- `OBP-API/obp-api/src/main/scala/code/bankconnectors/LocalMappedConnector.scala` - Internal database operations

### Files to Review

- `OBP-API/obp-api/src/main/scala/code/api/util/ConsentUtil.scala` - Consent application logic
- `OBP-API/obp-api/src/main/scala/code/bankconnectors/Connector.scala` - Base connector trait
- `OBP-API/obp-api/src/main/scala/code/bankconnectors/rabbitmq/RabbitMQConnector_vOct2024.scala` - RabbitMQ example
- `OBP-API/obp-api/src/main/scala/code/bankconnectors/rest/RestConnector_vMar2019.scala` - REST example
- `OBP-API/obp-commons/src/main/scala/com/openbankproject/commons/model/CommonModel.scala` - DTO structures

---

## Summary

To replace user_id at the connector level with consent user_id:

1. **Primary Solution (External Connectors)**: Modify `toOutboundAdapterCallContext` method in `ApiSession.scala` to use `consenter` when present instead of `user`

2. **Secondary Solution (LocalMappedConnector)**: Add helper methods like `effectiveUser` and `effectiveUserId` to `CallContext` and use them where user information is needed

3. **Best Practice**: Handle at API endpoint level where possible, before calling connector methods

4. **Key Insight**: The architectural difference between external connectors (which have a clear transformation boundary) and LocalMappedConnector (which works directly with domain objects) means there's no single universal solution

The `toOutboundAdapterCallContext` method is the ideal place for external connectors because it occurs:

- ✅ After security guards (authentication/authorization complete)
- ✅ Before external communication (database/RabbitMQ/REST)
- ✅ At a single transformation point (DRY principle)
- ✅ With access to both authenticated user and consent user

---

_Last Updated: 2024_
_OBP-API Version: v5.1.0+_
