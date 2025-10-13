package code.api.ResourceDocs1_4_0

import code.api.Constant
import code.api.Constant._
import code.api.UKOpenBanking.v2_0_0.JSONFactory_UKOpenBanking_200
import code.api.UKOpenBanking.v2_0_0.JSONFactory_UKOpenBanking_200.{Account, AccountBalancesUKV200, AccountInner, AccountList, Accounts, BalanceJsonUKV200, BalanceUKOpenBankingJson, BankTransactionCodeJson, CreditLineJson, DataJsonUKV200, Links, MetaBisJson, MetaInnerJson, TransactionCodeJson, TransactionInnerJson, TransactionsInnerJson, TransactionsJsonUKV200}
import code.api.dynamic.endpoint.helper.practise.PractiseEndpoint
import code.api.util.APIUtil.{defaultJValue, _}
import code.api.util.ApiRole._
import code.api.util.ExampleValue._
import code.api.util.{ApiRole, ApiTrigger, ExampleValue}
import code.api.v2_2_0.JSONFactory220.{AdapterImplementationJson, MessageDocJson, MessageDocsJson}
import code.api.v3_0_0.JSONFactory300.createBranchJsonV300
import code.api.v3_0_0._
import code.api.v3_1_0._
import code.api.v4_0_0._
import code.api.v5_0_0._
import code.api.v5_1_0._
import code.api.v6_0_0._
import code.branches.Branches.{Branch, DriveUpString, LobbyString}
import code.connectormethod.{JsonConnectorMethod, JsonConnectorMethodMethodBody}
import code.consent.ConsentStatus
import code.dynamicMessageDoc.JsonDynamicMessageDoc
import code.dynamicResourceDoc.JsonDynamicResourceDoc
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model
import com.openbankproject.commons.model.PinResetReason.{FORGOT, GOOD_SECURITY_PRACTICE}
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.TransactionRequestTypes._
import com.openbankproject.commons.model.enums.{AttributeCategory, CardAttributeType, ChallengeType, TransactionRequestStatus}
import com.openbankproject.commons.util.{ApiVersion, FieldNameApiVersions, ReflectUtils}
import net.liftweb.json

import java.net.URLEncoder
import java.util.Date

/**
  * This object prepare all the JSON case classes for Swagger .
  * For now, just support all the endpoints for V220.
  * Because different versions, has different case classes.
  * It is hard to mapping all these case class dynamicaly for now.
  * May be it can be fixed later.
  *
  */
object SwaggerDefinitionsJSON {

  implicit def convertStringToBoolean(value:String) = value.toBoolean

  lazy val regulatedEntitiesJsonV510: RegulatedEntitiesJsonV510 = RegulatedEntitiesJsonV510(List(regulatedEntityJsonV510))
  lazy val regulatedEntityJsonV510: RegulatedEntityJsonV510 = RegulatedEntityJsonV510(
    entity_id = entityIdExample.value,
    certificate_authority_ca_owner_id = certificateAuthorityCaOwnerIdExample.value,
    entity_certificate_public_key = entityCertificatePublicKeyExample.value,
    entity_name = entityNameExample.value,
    entity_code = entityCodeExample.value,
    entity_type = entityTypeExample.value,
    entity_address = entityAddressExample.value,
    entity_town_city = entityTownCityExample.value,
    entity_post_code = entityPostCodeExample.value,
    entity_country = entityCountryExample.value,
    entity_web_site = entityWebSiteExample.value,
    services = json.parse(servicesExample.value),
    attributes = Some(List(RegulatedEntityAttributeSimple(
        attributeType=attributeTypeExample.value,
        name=attributeNameExample.value,
        value=attributeValueExample.value)
      ))
  )

  lazy val regulatedEntityPostJsonV510: RegulatedEntityPostJsonV510 = RegulatedEntityPostJsonV510(
    certificate_authority_ca_owner_id = certificateAuthorityCaOwnerIdExample.value,
    entity_certificate_public_key = entityCertificatePublicKeyExample.value,
    entity_name = entityNameExample.value,
    entity_code = entityCodeExample.value,
    entity_type = entityTypeExample.value,
    entity_address = entityAddressExample.value,
    entity_town_city = entityTownCityExample.value,
    entity_post_code = entityPostCodeExample.value,
    entity_country = entityCountryExample.value,
    entity_web_site = entityWebSiteExample.value,
    services = json.parse(servicesExample.value),
    attributes = Some(List(RegulatedEntityAttributeSimple(
      attributeType=attributeTypeExample.value,
      name=attributeNameExample.value,
      value=attributeValueExample.value)
    ))
  )

  lazy val license =  License(
    id = licenseIdExample.value,
    name = licenseNameExample.value
  )

  lazy val routing = Routing(
    scheme ="String",
    address ="String"
  )

  lazy val branchId = BranchId(value = ExampleValue.branchIdExample.value)

  // from code.model, not from normal version JSON Factory
  ///////////////////////////////////////////////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////

  lazy val amountOfMoney = AmountOfMoney(
    currency = "EUR",
    amount = "100"
  )

  lazy val accountRouting =  AccountRouting(
    scheme = "accountNumber",
    address = "123456"
  )

  lazy val coreAccount = CoreAccount(
    id ="123",
    label=" work",
    bankId="123123",
    accountType="330",
    accountRoutings= List(accountRouting)
  )


  lazy val accountHeld = AccountHeld(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    label = "My Account",
    bankId = "gh.29.uk",
    number = "String",
    accountRoutings = List(accountRouting)
  )

  lazy val createViewJsonV300 = CreateViewJsonV300(
    name = "_test",
    description = "This view is for family",
    metadata_view ="_test",
    is_public = true,
    which_alias_to_use = "family",
    hide_metadata_if_alias_used = false,
    allowed_actions = List(
      CAN_EDIT_OWNER_COMMENT,
      CAN_SEE_TRANSACTION_THIS_BANK_ACCOUNT,
      CAN_SEE_TRANSACTION_OTHER_BANK_ACCOUNT,
      CAN_SEE_TRANSACTION_METADATA,
      CAN_SEE_TRANSACTION_AMOUNT,
      CAN_SEE_TRANSACTION_TYPE,
      CAN_SEE_TRANSACTION_CURRENCY,
      CAN_SEE_TRANSACTION_START_DATE,
      CAN_SEE_TRANSACTION_FINISH_DATE,
      CAN_SEE_TRANSACTION_BALANCE,
      CAN_SEE_COMMENTS,
      CAN_SEE_TAGS,
      CAN_SEE_IMAGES,
      CAN_SEE_BANK_ACCOUNT_OWNERS,
      CAN_SEE_BANK_ACCOUNT_TYPE,
      CAN_SEE_BANK_ACCOUNT_BALANCE,
      CAN_SEE_BANK_ACCOUNT_CURRENCY,
      CAN_SEE_BANK_ACCOUNT_LABEL,
      CAN_SEE_BANK_ACCOUNT_NATIONAL_IDENTIFIER,
      CAN_SEE_BANK_ACCOUNT_SWIFT_BIC,
      CAN_SEE_BANK_ACCOUNT_IBAN,
      CAN_SEE_BANK_ACCOUNT_NUMBER,
      CAN_SEE_BANK_ACCOUNT_BANK_NAME,
      CAN_SEE_OTHER_ACCOUNT_NATIONAL_IDENTIFIER,
      CAN_SEE_OTHER_ACCOUNT_SWIFT_BIC,
      CAN_SEE_OTHER_ACCOUNT_IBAN,
      CAN_SEE_OTHER_ACCOUNT_BANK_NAME,
      CAN_SEE_OTHER_ACCOUNT_NUMBER,
      CAN_SEE_OTHER_ACCOUNT_METADATA,
      CAN_SEE_OTHER_ACCOUNT_KIND,
      CAN_SEE_MORE_INFO,
      CAN_SEE_URL,
      CAN_SEE_IMAGE_URL,
      CAN_SEE_OPEN_CORPORATES_URL,
      CAN_SEE_CORPORATE_LOCATION,
      CAN_SEE_PHYSICAL_LOCATION,
      CAN_SEE_PUBLIC_ALIAS,
      CAN_SEE_PRIVATE_ALIAS,
      CAN_ADD_MORE_INFO,
      CAN_ADD_URL,
      CAN_ADD_IMAGE_URL,
      CAN_ADD_OPEN_CORPORATES_URL,
      CAN_ADD_CORPORATE_LOCATION,
      CAN_ADD_PHYSICAL_LOCATION,
      CAN_ADD_PUBLIC_ALIAS,
      CAN_ADD_PRIVATE_ALIAS,
      CAN_DELETE_CORPORATE_LOCATION,
      CAN_DELETE_PHYSICAL_LOCATION,
      CAN_ADD_COMMENT,
      CAN_DELETE_COMMENT,
      CAN_ADD_TAG,
      CAN_DELETE_TAG,
      CAN_ADD_IMAGE,
      CAN_DELETE_IMAGE,
      CAN_ADD_WHERE_TAG,
      CAN_SEE_WHERE_TAG,
      CAN_DELETE_WHERE_TAG,
      //V300 New
      CAN_SEE_BANK_ROUTING_SCHEME,
      CAN_SEE_BANK_ROUTING_ADDRESS,
      CAN_SEE_BANK_ACCOUNT_ROUTING_SCHEME,
      CAN_SEE_BANK_ACCOUNT_ROUTING_ADDRESS,
      CAN_SEE_OTHER_BANK_ROUTING_SCHEME,
      CAN_SEE_OTHER_BANK_ROUTING_ADDRESS,
      CAN_SEE_OTHER_ACCOUNT_ROUTING_SCHEME,
      CAN_SEE_OTHER_ACCOUNT_ROUTING_ADDRESS,
      //v310
      CAN_QUERY_AVAILABLE_FUNDS,
      CAN_ADD_TRANSACTION_REQUEST_TO_OWN_ACCOUNT,
      CAN_ADD_TRANSACTION_REQUEST_TO_ANY_ACCOUNT,
      CAN_SEE_BANK_ACCOUNT_CREDIT_LIMIT,
      //v400
      CAN_CREATE_DIRECT_DEBIT,
      CAN_CREATE_STANDING_ORDER,

      //payments
      CAN_ADD_TRANSACTION_REQUEST_TO_ANY_ACCOUNT
    )
  )

  lazy val createSystemViewJsonV300 = createViewJsonV300.copy(name = "test", metadata_view = "test", is_public = false)

  lazy val allowedActionsV500 = List(
    CAN_EDIT_OWNER_COMMENT,
    CAN_SEE_TRANSACTION_THIS_BANK_ACCOUNT,
    CAN_SEE_TRANSACTION_OTHER_BANK_ACCOUNT,
    CAN_SEE_TRANSACTION_METADATA,
    CAN_SEE_TRANSACTION_AMOUNT,
    CAN_SEE_TRANSACTION_TYPE,
    CAN_SEE_TRANSACTION_CURRENCY,
    CAN_SEE_TRANSACTION_START_DATE,
    CAN_SEE_TRANSACTION_FINISH_DATE,
    CAN_SEE_TRANSACTION_BALANCE,
    CAN_SEE_COMMENTS,
    CAN_SEE_TAGS,
    CAN_SEE_IMAGES,
    CAN_SEE_BANK_ACCOUNT_OWNERS,
    CAN_SEE_BANK_ACCOUNT_TYPE,
    CAN_SEE_BANK_ACCOUNT_BALANCE,
    CAN_SEE_BANK_ACCOUNT_CURRENCY,
    CAN_SEE_BANK_ACCOUNT_LABEL,
    CAN_SEE_BANK_ACCOUNT_NATIONAL_IDENTIFIER,
    CAN_SEE_BANK_ACCOUNT_SWIFT_BIC,
    CAN_SEE_BANK_ACCOUNT_IBAN,
    CAN_SEE_BANK_ACCOUNT_NUMBER,
    CAN_SEE_BANK_ACCOUNT_BANK_NAME,
    CAN_SEE_OTHER_ACCOUNT_NATIONAL_IDENTIFIER,
    CAN_SEE_OTHER_ACCOUNT_SWIFT_BIC,
    CAN_SEE_OTHER_ACCOUNT_IBAN,
    CAN_SEE_OTHER_ACCOUNT_BANK_NAME,
    CAN_SEE_OTHER_ACCOUNT_NUMBER,
    CAN_SEE_OTHER_ACCOUNT_METADATA,
    CAN_SEE_OTHER_ACCOUNT_KIND,
    CAN_SEE_MORE_INFO,
    CAN_SEE_URL,
    CAN_SEE_IMAGE_URL,
    CAN_SEE_OPEN_CORPORATES_URL,
    CAN_SEE_CORPORATE_LOCATION,
    CAN_SEE_PHYSICAL_LOCATION,
    CAN_SEE_PUBLIC_ALIAS,
    CAN_SEE_PRIVATE_ALIAS,
    CAN_ADD_MORE_INFO,
    CAN_ADD_URL,
    CAN_ADD_IMAGE_URL,
    CAN_ADD_OPEN_CORPORATES_URL,
    CAN_ADD_CORPORATE_LOCATION,
    CAN_ADD_PHYSICAL_LOCATION,
    CAN_ADD_PUBLIC_ALIAS,
    CAN_ADD_PRIVATE_ALIAS,
    CAN_DELETE_CORPORATE_LOCATION,
    CAN_DELETE_PHYSICAL_LOCATION,
    CAN_ADD_COMMENT,
    CAN_DELETE_COMMENT,
    CAN_ADD_TAG,
    CAN_DELETE_TAG,
    CAN_ADD_IMAGE,
    CAN_DELETE_IMAGE,
    CAN_ADD_WHERE_TAG,
    CAN_SEE_WHERE_TAG,
    CAN_DELETE_WHERE_TAG,
    //V300 New
    CAN_SEE_BANK_ROUTING_SCHEME,
    CAN_SEE_BANK_ROUTING_ADDRESS,
    CAN_SEE_BANK_ACCOUNT_ROUTING_SCHEME,
    CAN_SEE_BANK_ACCOUNT_ROUTING_ADDRESS,
    CAN_SEE_OTHER_BANK_ROUTING_SCHEME,
    CAN_SEE_OTHER_BANK_ROUTING_ADDRESS,
    CAN_SEE_OTHER_ACCOUNT_ROUTING_SCHEME,
    CAN_SEE_OTHER_ACCOUNT_ROUTING_ADDRESS,

    //v310
    CAN_QUERY_AVAILABLE_FUNDS,
    CAN_ADD_TRANSACTION_REQUEST_TO_OWN_ACCOUNT,
    CAN_ADD_TRANSACTION_REQUEST_TO_ANY_ACCOUNT,
    CAN_SEE_BANK_ACCOUNT_CREDIT_LIMIT,
    //v400
    CAN_CREATE_DIRECT_DEBIT,
    CAN_CREATE_STANDING_ORDER,

    //payments
    CAN_ADD_TRANSACTION_REQUEST_TO_ANY_ACCOUNT,

    CAN_SEE_TRANSACTION_REQUEST_TYPES,
    CAN_SEE_TRANSACTION_REQUESTS,
    CAN_SEE_AVAILABLE_VIEWS_FOR_BANK_ACCOUNT,
    CAN_UPDATE_BANK_ACCOUNT_LABEL,
    CAN_CREATE_CUSTOM_VIEW,
    CAN_DELETE_CUSTOM_VIEW,
    CAN_UPDATE_CUSTOM_VIEW,
    CAN_SEE_VIEWS_WITH_PERMISSIONS_FOR_ONE_USER,
    CAN_SEE_VIEWS_WITH_PERMISSIONS_FOR_ALL_USERS,
    CAN_GRANT_ACCESS_TO_CUSTOM_VIEWS,
    CAN_REVOKE_ACCESS_TO_CUSTOM_VIEWS,
    CAN_SEE_TRANSACTION_STATUS
  )
  
  lazy val createCustomViewJson = CreateCustomViewJson(
    name = viewNameExample.value,
    description= viewDescriptionExample.value,
    metadata_view= metadataViewExample.value,
    is_public = isPublicExample.value,
    which_alias_to_use = whichAliasToUseExample.value,
    hide_metadata_if_alias_used = hideMetadataIfAliasUsedExample.value.toBoolean,
    allowed_permissions= allowedActionsV500,
  )
  
  lazy val customViewJsonV510 = CustomViewJsonV510(
    id = viewIdExample.value,
    name = viewNameExample.value,
    description = viewDescriptionExample.value,
    metadata_view = metadataViewExample.value,
    is_public = isPublicExample.value,
    alias = whichAliasToUseExample.value,
    hide_metadata_if_alias_used = hideMetadataIfAliasUsedExample.value.toBoolean,
    allowed_permissions = allowedActionsV500
  )
  
  lazy val createSystemViewJsonV500 = CreateViewJsonV500(
    name = viewNameExample.value,
    description = viewDescriptionExample.value,
    metadata_view =viewDescriptionExample.value,
    is_public = isPublicExample.value,
    which_alias_to_use = whichAliasToUseExample.value,
    hide_metadata_if_alias_used = hideMetadataIfAliasUsedExample.value.toBoolean,
    allowed_actions = allowedActionsV500,
    // Version 5.0.0
    can_grant_access_to_views = Some(List(Constant.SYSTEM_OWNER_VIEW_ID)),
    can_revoke_access_to_views = Some(List(Constant.SYSTEM_OWNER_VIEW_ID))
  )
  
  lazy val updateCustomViewJson = UpdateCustomViewJson(
    description = viewDescriptionExample.value,
    metadata_view = metadataViewExample.value,
    is_public = isPublicExample.value,
    which_alias_to_use = whichAliasToUseExample.value,
    hide_metadata_if_alias_used = hideMetadataIfAliasUsedExample.value.toBoolean,
    allowed_permissions = allowedActionsV500
  )

  lazy val updateViewJsonV300 = UpdateViewJsonV300(
    description = "this is for family",
    is_public = true,
    metadata_view = SYSTEM_OWNER_VIEW_ID,
    which_alias_to_use = "family",
    hide_metadata_if_alias_used = true,
    allowed_actions = List(
      CAN_SEE_TRANSACTION_THIS_BANK_ACCOUNT,
      CAN_SEE_TRANSACTION_OTHER_BANK_ACCOUNT,
      CAN_SEE_TRANSACTION_METADATA,
      
      CAN_SEE_TRANSACTION_AMOUNT,
      CAN_SEE_TRANSACTION_TYPE,
      CAN_SEE_TRANSACTION_CURRENCY,
      CAN_SEE_TRANSACTION_START_DATE,
      CAN_SEE_TRANSACTION_FINISH_DATE,
      CAN_SEE_TRANSACTION_BALANCE,
      CAN_SEE_COMMENTS,
      CAN_SEE_TAGS,
      CAN_SEE_IMAGES,
      CAN_SEE_BANK_ACCOUNT_OWNERS,
      CAN_SEE_BANK_ACCOUNT_TYPE,
      CAN_SEE_BANK_ACCOUNT_BALANCE,
      CAN_SEE_BANK_ACCOUNT_CURRENCY,
      CAN_SEE_BANK_ACCOUNT_LABEL,
      CAN_SEE_BANK_ACCOUNT_NATIONAL_IDENTIFIER,
      CAN_SEE_BANK_ACCOUNT_SWIFT_BIC,
      CAN_SEE_BANK_ACCOUNT_IBAN,
      CAN_SEE_BANK_ACCOUNT_NUMBER,
      CAN_SEE_BANK_ACCOUNT_BANK_NAME,
      CAN_SEE_OTHER_ACCOUNT_NATIONAL_IDENTIFIER,
      CAN_SEE_OTHER_ACCOUNT_SWIFT_BIC,
      CAN_SEE_OTHER_ACCOUNT_IBAN,
      CAN_SEE_OTHER_ACCOUNT_BANK_NAME,
      CAN_SEE_OTHER_ACCOUNT_NUMBER,
      CAN_SEE_OTHER_ACCOUNT_METADATA,
      CAN_SEE_OTHER_ACCOUNT_KIND,
      CAN_SEE_MORE_INFO,
      CAN_SEE_URL,
      CAN_SEE_IMAGE_URL,
      CAN_SEE_OPEN_CORPORATES_URL,
      CAN_SEE_CORPORATE_LOCATION,
      CAN_SEE_PHYSICAL_LOCATION,
      CAN_SEE_PUBLIC_ALIAS,
      CAN_SEE_PRIVATE_ALIAS,
      CAN_ADD_MORE_INFO,
      CAN_ADD_URL,
      CAN_ADD_IMAGE_URL,
      CAN_ADD_OPEN_CORPORATES_URL,
      CAN_ADD_CORPORATE_LOCATION,
      CAN_ADD_PHYSICAL_LOCATION,
      CAN_ADD_PUBLIC_ALIAS,
      CAN_ADD_PRIVATE_ALIAS,
      CAN_DELETE_CORPORATE_LOCATION,
      CAN_DELETE_PHYSICAL_LOCATION,
  
      CAN_ADD_COMMENT,
      CAN_DELETE_COMMENT,
      CAN_ADD_TAG,
      CAN_DELETE_TAG,
      CAN_ADD_IMAGE,
      CAN_DELETE_IMAGE,
      CAN_ADD_WHERE_TAG,
      CAN_SEE_WHERE_TAG,
      CAN_DELETE_WHERE_TAG,
  
      //V300 New
      CAN_SEE_BANK_ROUTING_SCHEME,
      CAN_SEE_BANK_ROUTING_ADDRESS,
      CAN_SEE_BANK_ACCOUNT_ROUTING_SCHEME,
      CAN_SEE_BANK_ACCOUNT_ROUTING_ADDRESS,
      CAN_SEE_OTHER_BANK_ROUTING_SCHEME,
      CAN_SEE_OTHER_BANK_ROUTING_ADDRESS,
      CAN_SEE_OTHER_ACCOUNT_ROUTING_SCHEME,
      CAN_SEE_OTHER_ACCOUNT_ROUTING_ADDRESS,
      //v310
      CAN_QUERY_AVAILABLE_FUNDS
    )
  )
  lazy val updateSystemViewJson310 = updateViewJsonV300.copy(is_public = false, is_firehose = Some(false))
  
  lazy val updateViewJsonV500 = UpdateViewJsonV500(
    description = "this is for family",
    is_public = true,
    metadata_view = SYSTEM_OWNER_VIEW_ID,
    which_alias_to_use = "family",
    hide_metadata_if_alias_used = true,
    allowed_actions = allowedActionsV500,
    // Version 5.0.0
    can_grant_access_to_views = Some(List(Constant.SYSTEM_OWNER_VIEW_ID)),
    can_revoke_access_to_views = Some(List(Constant.SYSTEM_OWNER_VIEW_ID))
  )
  lazy val updateSystemViewJson500 = updateViewJsonV500.copy(is_public = false, is_firehose = Some(false))

  lazy val transactionTypeIdSwagger = TransactionTypeId(value = "123")

  lazy val bankIdSwagger = BankId(value = "gh.uk.9j")

  lazy val transactionRequestIdSwagger = TransactionRequestId(value = "123")

  lazy val counterpartyIdSwagger = CounterpartyId(counterpartyIdExample.value)

  lazy val accountIdSwagger = model.AccountId(value = "123")

  lazy val viewIdSwagger = ViewId(value = SYSTEM_OWNER_VIEW_ID)


  // from code.TransactionTypes.TransactionType, not from normal version Factory
  ///////////////////////////////////////////////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////
  import code.TransactionTypes.TransactionType._

  lazy val transactionType = TransactionType(
    id = transactionTypeIdSwagger,
    bankId = bankIdSwagger,
    shortCode = "80080",
    summary = SANDBOX_TAN.toString,
    description = "This is the sandbox mode, charging litter money.",
    charge = amountOfMoney
  )


  // code.transactionrequests.TransactionRequests
  ///////////////////////////////////////////////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////

  lazy val transactionRequestCharge = TransactionRequestCharge(
    summary = "String",
    value = amountOfMoney
  )

  lazy val transactionRequestChallenge = TransactionRequestChallenge(
    id= "String",
    allowed_attempts= 4,
    challenge_type= "String"
  )

  lazy val transactionRequestAccount = TransactionRequestAccount(
    bank_id= "String",
    account_id= "String"
  )

  lazy val transactionRequestCounterpartyId = TransactionRequestCounterpartyId (counterparty_id = counterpartyIdExample.value)
  
  lazy val transactionRequestAgentCashWithdrawal = TransactionRequestAgentCashWithdrawal(
    bank_id = bankIdExample.value,
    agent_number = agentNumberExample.value
  )

  lazy val transactionRequestIban =  TransactionRequestIban (iban = "String")

  lazy val transactionRequestBody = TransactionRequestBody(
    to = transactionRequestAccount,
    value= amountOfMoney,
    description= "String"
  )


  lazy val fromAccountTransfer = FromAccountTransfer(
    mobile_phone_number = ExampleValue.mobileNumberExample.value,
    nickname = "String"
  )

  lazy val toAccountTransferToPhone = ToAccountTransferToPhone(
    mobile_phone_number = ExampleValue.mobileNumberExample.value,
  )

  lazy val toAccountTransferToAtmKycDocument = ToAccountTransferToAtmKycDocument(
    `type` = "String",
    number = "String",
  )

  lazy val toAccountTransferToAtm = ToAccountTransferToAtm(
    legal_name = ExampleValue.legalNameExample.value,
    date_of_birth = "20181230",
    mobile_phone_number = ExampleValue.mobileNumberExample.value,
    kyc_document = toAccountTransferToAtmKycDocument
  )

  lazy val toAccountTransferToAccountAccount = ToAccountTransferToAccountAccount(
    number = "String",
    iban = "String"
  )

  lazy val toAccountTransferToAccount = ToAccountTransferToAccount(
    name = "String",
    bank_code = "String",
    branch_number = "String",
    account = toAccountTransferToAccountAccount
  )

  lazy val amountOfMoneyJsonV121 = AmountOfMoneyJsonV121(
    currency = "EUR",
    amount = "0"
  )

  lazy val transactionRequestTransferToPhone = TransactionRequestTransferToPhone(
    value = amountOfMoneyJsonV121,
    description = "String",
    message = "String",
    from = fromAccountTransfer,
    to = toAccountTransferToPhone
  )

  lazy val transactionRequestTransferToAtm = TransactionRequestTransferToAtm(
    value = amountOfMoneyJsonV121,
    description = "String",
    message = "String",
    from = fromAccountTransfer,
    to = toAccountTransferToAtm
  )

  lazy val transactionRequestTransferToAccount = TransactionRequestTransferToAccount(
    value = amountOfMoneyJsonV121,
    description = "String",
    transfer_type = "String",
    future_date = "20181230",
    to = toAccountTransferToAccount
  )

  lazy val sepaCreditTransfers = SepaCreditTransfers(
    debtorAccount = PaymentAccount(iban = "12345"),
    instructedAmount = amountOfMoneyJsonV121,
    creditorAccount = PaymentAccount(iban = "54321"),
    creditorName = "John Miles"
  )

  lazy val sepaCreditTransfersBerlinGroupV13 = SepaCreditTransfersBerlinGroupV13(
    debtorAccount = PaymentAccount(iban = "GB33BUKB20201555555555"),
    instructedAmount = amountOfMoneyJsonV121,
    creditorAccount = PaymentAccount(iban = "DE75512108001245126199"),
    creditorName = "John Miles"
  )

  lazy val periodicSepaCreditTransfersBerlinGroupV13 = PeriodicSepaCreditTransfersBerlinGroupV13(
    debtorAccount = PaymentAccount(iban = "GB33BUKB20201555555555"),
    instructedAmount = amountOfMoneyJsonV121,
    creditorAccount = PaymentAccount(iban = "DE75512108001245126199"),
    creditorName = "John Miles",
    frequency = "Monthly",
    startDate ="2018-03-01",
  )
  
  lazy val transactionRequestSimple= TransactionRequestSimple(
    otherBankRoutingScheme = bankRoutingSchemeExample.value,
    otherBankRoutingAddress = bankRoutingAddressExample.value,
    otherBranchRoutingScheme = branchRoutingSchemeExample.value,
    otherBranchRoutingAddress = branchRoutingAddressExample.value,
    otherAccountRoutingScheme = accountRoutingSchemeExample.value,
    otherAccountRoutingAddress = accountRoutingAddressExample.value,
    otherAccountSecondaryRoutingScheme = accountRoutingSchemeExample.value,
    otherAccountSecondaryRoutingAddress = accountRoutingAddressExample.value
  )

  lazy val transactionRequestBodyAllTypes = TransactionRequestBodyAllTypes (
    to_sandbox_tan = Some(transactionRequestAccount),
    to_sepa = Some(transactionRequestIban),
    to_counterparty = Some(transactionRequestCounterpartyId),
    to_simple = Some(transactionRequestSimple),
    to_transfer_to_phone = Some(transactionRequestTransferToPhone),
    to_transfer_to_atm = Some(transactionRequestTransferToAtm),
    to_transfer_to_account = Some(transactionRequestTransferToAccount),
    to_sepa_credit_transfers = Some(sepaCreditTransfers),
    to_agent = Some(transactionRequestAgentCashWithdrawal),
    value = amountOfMoney,
    description = descriptionExample.value
  )

  lazy val adapterImplementationJson = AdapterImplementationJson("CORE",3)

  lazy val messageDocJson = MessageDocJson(
    process = "getAccounts",
    message_format = "rest_vMar2019",
    inbound_topic = Some("from.obp.api.1.to.adapter.mf.caseclass.OutboundGetAccounts"),
    outbound_topic = Some("to.obp.api.1.caseclass.OutboundGetAccounts"),
    description = "get Banks",
    example_outbound_message = defaultJValue,
    example_inbound_message = defaultJValue,
    outboundAvroSchema = Some(defaultJValue),
    inboundAvroSchema = Some(defaultJValue),
    adapter_implementation = adapterImplementationJson,
    dependent_endpoints = List(
      EndpointInfo("getAccounts", ApiVersion.v3_0_0.fullyQualifiedVersion),
      EndpointInfo("getBalances", ApiVersion.v2_0_0.fullyQualifiedVersion)
    ),
    requiredFieldInfo = Some(FieldNameApiVersions)
  )

  lazy val messageDocsJson = MessageDocsJson(message_docs = List(messageDocJson))

  //V121 - code.api.v1_2_1
  ///////////////////////////////////////////////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////
  import code.api.v1_2_1._

  lazy val makePaymentJson = MakePaymentJson(
    bank_id = "gh.29.uk",
    account_id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    amount = "10"
  )

  lazy val transactionIdJson = TransactionIdJson(
    transaction_id = "123"
  )

  lazy val hostedBy = HostedBy(
    organisation = "String",
    email = "String",
    phone = "String",
    organisation_website = "String"
  )  
  
  lazy val hostedBy400 = HostedBy400(
    organisation = "String",
    email = "String",
    phone = "String",
    organisation_website = "String"
  )
  lazy val hostedAt400 = HostedAt400(
    organisation = "Amazon",
    organisation_website = "https://aws.amazon.com/"
  )
  lazy val energySource400 = EnergySource400(
    organisation = "Stromio",
    organisation_website = "https://www.stromio.de/"
  )

  lazy val rateLimiting = RateLimiting(true, "REDIS", true, true)

  lazy val apiInfoJson400 = APIInfoJson400(
    version = "String",
    version_status = "String",
    git_commit = "String",
    connector = "String",
    hostname = "String",
    local_identity_provider = "String",
    hosted_by = hostedBy400,
    hosted_at = hostedAt400,
    energy_source = energySource400,
    resource_docs_requires_role = false
  )
  lazy val apiInfoJSON = APIInfoJSON(
    version = "String",
    version_status = "String",
    git_commit = "String",
    connector = "String",
    hosted_by = hostedBy
  )

  /*  lazy val aggregateMetricsJSON = AggregateMetricJSON(
    total_api_calls = 591,
    average_duration = {"_1":["avg"],"_2":[["164.4940778341793570"]]},
    minimum_duration = {"_1":["min"],"_2":[["0"]]},
    maximum_duration = {"_1":["max"],"_2":[["2847"]]}
  )*/

  lazy val errorMessage = ErrorMessage(
    code = 500,
    message = "Internal Server Error"
  )

  lazy val postTransactionImageJSON = PostTransactionImageJSON(
    label = "String",
    URL = "String"
  )
  lazy val postTransactionCommentJSON = PostTransactionCommentJSON(
    value = "String"
  )
  lazy val postTransactionTagJSON = PostTransactionTagJSON(
    value = "String"
  )
  lazy val postAccountTagJSON = PostAccountTagJSON(
    value = "String"
  )
  lazy val aliasJSON = AliasJSON(
    alias = "String"
  )
  lazy val moreInfoJSON = MoreInfoJSON(
    more_info = "String"
  )
  lazy val urlJSON = UrlJSON(
    URL = "String"
  )
  lazy val imageUrlJSON = ImageUrlJSON(
    image_URL = "String"
  )
  lazy val openCorporateUrlJSON = OpenCorporateUrlJSON(
    open_corporates_URL = "String"
  )

  lazy val accountRoutingJsonV121 = AccountRoutingJsonV121(
    scheme = schemeExample.value,
    address = accountIdExample.value
  )

  lazy val bankAccountRoutingJson = BankAccountRoutingJson(
    bank_id = Some(bankIdExample.value),
    account_routing = accountRoutingJsonV121
  )

  lazy val accountRuleJsonV300 = AccountRuleJsonV300(
    scheme = "OVERDRAFT",
    value = "10"
  )
  lazy val userJSONV121 = UserJSONV121(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    provider = providerValueExample.value,
    display_name = "OBP"
  )

  lazy val viewJSONV121 = ViewJSONV121(
    id = "123",
    short_name = "short_name",
    description = "description",
    is_public = true,
    alias = "None",
    hide_metadata_if_alias_used = true,
    can_add_comment = true,
    can_add_corporate_location = true,
    can_add_image = true,
    can_add_image_url = true,
    can_add_more_info = true,
    can_add_open_corporates_url = true,
    can_add_physical_location = true,
    can_add_private_alias = true,
    can_add_public_alias = true,
    can_add_tag = true,
    can_add_url = true,
    can_add_where_tag = true,
    can_delete_comment = true,
    can_delete_corporate_location = true,
    can_delete_image = true,
    can_delete_physical_location = true,
    can_delete_tag = true,
    can_delete_where_tag = true,
    can_edit_owner_comment = true,
    can_see_bank_account_balance = true,
    can_see_bank_account_bank_name = true,
    can_see_bank_account_currency = true,
    can_see_bank_account_iban = true,
    can_see_bank_account_label = true,
    can_see_bank_account_national_identifier = true,
    can_see_bank_account_number = true,
    can_see_bank_account_owners = true,
    can_see_bank_account_swift_bic = true,
    can_see_bank_account_type = true,
    can_see_comments = true,
    can_see_corporate_location = true,
    can_see_image_url = true,
    can_see_images = true,
    can_see_more_info = true,
    can_see_open_corporates_url = true,
    can_see_other_account_bank_name = true,
    can_see_other_account_iban = true,
    can_see_other_account_kind = true,
    can_see_other_account_metadata = true,
    can_see_other_account_national_identifier = true,
    can_see_other_account_number = true,
    can_see_other_account_swift_bic = true,
    can_see_owner_comment = true,
    can_see_physical_location = true,
    can_see_private_alias = true,
    can_see_public_alias = true,
    can_see_tags = true,
    can_see_transaction_amount = true,
    can_see_transaction_balance = true,
    can_see_transaction_currency = true,
    can_see_transaction_description = true,
    can_see_transaction_finish_date = true,
    can_see_transaction_metadata = true,
    can_see_transaction_other_bank_account = true,
    can_see_transaction_start_date = true,
    can_see_transaction_this_bank_account = true,
    can_see_transaction_type = true,
    can_see_url = true,
    can_see_where_tag = true
  )

  lazy val createViewJsonV121 = CreateViewJsonV121(
    name = "_test",
    description = "This view is for family",
    is_public = true,
    which_alias_to_use = "family",
    hide_metadata_if_alias_used = false,
    allowed_actions = List(
      CAN_SEE_TRANSACTION_THIS_BANK_ACCOUNT,
      CAN_SEE_TRANSACTION_OTHER_BANK_ACCOUNT,
      CAN_SEE_TRANSACTION_METADATA,
      CAN_SEE_TRANSACTION_AMOUNT,
      CAN_SEE_TRANSACTION_TYPE,
      CAN_SEE_TRANSACTION_CURRENCY,
      CAN_SEE_TRANSACTION_START_DATE,
      CAN_SEE_TRANSACTION_FINISH_DATE,
      CAN_SEE_TRANSACTION_BALANCE,
      CAN_SEE_COMMENTS,
      CAN_SEE_TAGS,
      CAN_SEE_IMAGES,
      CAN_SEE_BANK_ACCOUNT_OWNERS,
      CAN_SEE_BANK_ACCOUNT_TYPE,
      CAN_SEE_BANK_ACCOUNT_BALANCE,
      CAN_SEE_BANK_ACCOUNT_CURRENCY,
      CAN_SEE_BANK_ACCOUNT_LABEL,
      CAN_SEE_BANK_ACCOUNT_NATIONAL_IDENTIFIER,
      CAN_SEE_BANK_ACCOUNT_SWIFT_BIC,
      CAN_SEE_BANK_ACCOUNT_IBAN,
      CAN_SEE_BANK_ACCOUNT_NUMBER,
      CAN_SEE_BANK_ACCOUNT_BANK_NAME,
      CAN_SEE_OTHER_ACCOUNT_NATIONAL_IDENTIFIER,
      CAN_SEE_OTHER_ACCOUNT_SWIFT_BIC,
      CAN_SEE_OTHER_ACCOUNT_IBAN,
      CAN_SEE_OTHER_ACCOUNT_BANK_NAME,
      CAN_SEE_OTHER_ACCOUNT_NUMBER,
      CAN_SEE_OTHER_ACCOUNT_METADATA,
      CAN_SEE_OTHER_ACCOUNT_KIND,
      CAN_SEE_MORE_INFO,
      CAN_SEE_URL,
      CAN_SEE_IMAGE_URL,
      CAN_SEE_OPEN_CORPORATES_URL,
      CAN_SEE_CORPORATE_LOCATION,
      CAN_SEE_PHYSICAL_LOCATION,
      CAN_SEE_PUBLIC_ALIAS,
      CAN_SEE_PRIVATE_ALIAS,
      CAN_ADD_MORE_INFO,
      CAN_ADD_URL,
      CAN_ADD_IMAGE_URL,
      CAN_ADD_OPEN_CORPORATES_URL,
      CAN_ADD_CORPORATE_LOCATION,
      CAN_ADD_PHYSICAL_LOCATION,
      CAN_ADD_PUBLIC_ALIAS,
      CAN_ADD_PRIVATE_ALIAS,
      CAN_DELETE_CORPORATE_LOCATION,
      CAN_DELETE_PHYSICAL_LOCATION,
      CAN_ADD_COMMENT,
      CAN_DELETE_COMMENT,
      CAN_ADD_TAG,
      CAN_DELETE_TAG,
      CAN_ADD_IMAGE,
      CAN_DELETE_IMAGE,
      CAN_ADD_WHERE_TAG,
      CAN_SEE_WHERE_TAG,
      CAN_DELETE_WHERE_TAG,
  
      //V300 New
      CAN_SEE_BANK_ROUTING_SCHEME,
      CAN_SEE_BANK_ROUTING_ADDRESS,
      CAN_SEE_BANK_ACCOUNT_ROUTING_SCHEME,
      CAN_SEE_BANK_ACCOUNT_ROUTING_ADDRESS,
      CAN_SEE_OTHER_BANK_ROUTING_SCHEME,
      CAN_SEE_OTHER_BANK_ROUTING_ADDRESS,
      CAN_SEE_OTHER_ACCOUNT_ROUTING_SCHEME,
      CAN_SEE_OTHER_ACCOUNT_ROUTING_ADDRESS
    )
  )

  lazy val updateViewJsonV121 = UpdateViewJsonV121(
    description = "This view is for family",
    is_public = true,
    which_alias_to_use = "family",
    hide_metadata_if_alias_used = false,
    allowed_actions = List(
      CAN_SEE_TRANSACTION_THIS_BANK_ACCOUNT,
      CAN_SEE_TRANSACTION_OTHER_BANK_ACCOUNT,
      CAN_SEE_TRANSACTION_METADATA,
      CAN_SEE_TRANSACTION_AMOUNT,
      CAN_SEE_TRANSACTION_TYPE,
      CAN_SEE_TRANSACTION_CURRENCY,
      CAN_SEE_TRANSACTION_START_DATE,
      CAN_SEE_TRANSACTION_FINISH_DATE,
      CAN_SEE_TRANSACTION_BALANCE,
      CAN_SEE_COMMENTS,
      CAN_SEE_TAGS,
      CAN_SEE_IMAGES,
      CAN_SEE_BANK_ACCOUNT_OWNERS,
      CAN_SEE_BANK_ACCOUNT_TYPE,
      CAN_SEE_BANK_ACCOUNT_BALANCE,
      CAN_SEE_BANK_ACCOUNT_CURRENCY,
      CAN_SEE_BANK_ACCOUNT_LABEL,
      CAN_SEE_BANK_ACCOUNT_NATIONAL_IDENTIFIER,
      CAN_SEE_BANK_ACCOUNT_SWIFT_BIC,
      CAN_SEE_BANK_ACCOUNT_IBAN,
      CAN_SEE_BANK_ACCOUNT_NUMBER,
      CAN_SEE_BANK_ACCOUNT_BANK_NAME,
      CAN_SEE_OTHER_ACCOUNT_NATIONAL_IDENTIFIER,
      CAN_SEE_OTHER_ACCOUNT_SWIFT_BIC,
      CAN_SEE_OTHER_ACCOUNT_IBAN,
      CAN_SEE_OTHER_ACCOUNT_BANK_NAME,
      CAN_SEE_OTHER_ACCOUNT_NUMBER,
      CAN_SEE_OTHER_ACCOUNT_METADATA,
      CAN_SEE_OTHER_ACCOUNT_KIND,
      CAN_SEE_MORE_INFO,
      CAN_SEE_URL,
      CAN_SEE_IMAGE_URL,
      CAN_SEE_OPEN_CORPORATES_URL,
      CAN_SEE_CORPORATE_LOCATION,
      CAN_SEE_PHYSICAL_LOCATION,
      CAN_SEE_PUBLIC_ALIAS,
      CAN_SEE_PRIVATE_ALIAS,
      CAN_ADD_MORE_INFO,
      CAN_ADD_URL,
      CAN_ADD_IMAGE_URL,
      CAN_ADD_OPEN_CORPORATES_URL,
      CAN_ADD_CORPORATE_LOCATION,
      CAN_ADD_PHYSICAL_LOCATION,
      CAN_ADD_PUBLIC_ALIAS,
      CAN_ADD_PRIVATE_ALIAS,
      CAN_DELETE_CORPORATE_LOCATION,
      CAN_DELETE_PHYSICAL_LOCATION,
      CAN_ADD_COMMENT,
      CAN_DELETE_COMMENT,
      CAN_ADD_TAG,
      CAN_DELETE_TAG,
      CAN_ADD_IMAGE,
      CAN_DELETE_IMAGE,
      CAN_ADD_WHERE_TAG,
      CAN_SEE_WHERE_TAG,
      CAN_DELETE_WHERE_TAG,
  
      //V300 New
      CAN_SEE_BANK_ROUTING_SCHEME,
      CAN_SEE_BANK_ROUTING_ADDRESS,
      CAN_SEE_BANK_ACCOUNT_ROUTING_SCHEME,
      CAN_SEE_BANK_ACCOUNT_ROUTING_ADDRESS,
      CAN_SEE_OTHER_BANK_ROUTING_SCHEME,
      CAN_SEE_OTHER_BANK_ROUTING_ADDRESS,
      CAN_SEE_OTHER_ACCOUNT_ROUTING_SCHEME,
      CAN_SEE_OTHER_ACCOUNT_ROUTING_ADDRESS
    )
  )
  lazy val viewsJSONV121 = ViewsJSONV121(
    views = List(viewJSONV121)
  )

  lazy val accountJSON = AccountJSON(
    id = "123",
    label = "OBP",
    views_available = List(viewJSONV121),
    bank_id = bankIdExample.value
  )

  lazy val accountsJSON = AccountsJSON(
    accounts = List(accountJSON)
  )

  lazy val accountMinimalJson400 = AccountMinimalJson400(
    bank_id = bankIdExample.value,
    account_id = accountIdExample.value,
    view_id = viewIdExample.value
  )

  lazy val accountsMinimalJson400 = AccountsMinimalJson400(
    accounts = List(accountMinimalJson400)
  )

  lazy val bankRoutingJsonV121 = BankRoutingJsonV121(
    scheme = schemeExample.value,
    address = bankIdExample.value
  )

  lazy val bankJSON = BankJSON(
    id = bankIdExample.value,
    short_name = "short_name ",
    full_name = "full_name",
    logo = "logo",
    website = "www.openbankproject.com",
    bank_routing = bankRoutingJsonV121
  )

  lazy val banksJSON = BanksJSON(
    banks = List(bankJSON)
  )
  lazy val bankAttributeBankResponseJsonV400 = BankAttributeBankResponseJsonV400(
    name = nameExample.value,
    value = valueExample.value
  )
  
  lazy val bankAttributesResponseJson = BankAttributesResponseJson(
    list = List(bankAttributeBankResponseJsonV400)
  )
  
  lazy val postBankJson400 = PostBankJson400(
    id = bankIdExample.value,
    short_name = "short_name ",
    full_name = "full_name",
    logo = "logo",
    website = "www.openbankproject.com",
    bank_routings = List(bankRoutingJsonV121)
  )
  lazy val bankJson400 = BankJson400(
    id = bankIdExample.value,
    short_name = "short_name ",
    full_name = "full_name",
    logo = "logo",
    website = "www.openbankproject.com",
    bank_routings = List(bankRoutingJsonV121),
    attributes = Some(List(bankAttributeBankResponseJsonV400))
  )  
  lazy val bankJson500 = BankJson500(
    id = bankIdExample.value,
    bank_code = bankCodeExample.value,
    full_name = bankFullNameExample.value,
    logo = bankLogoUrlExample.value,
    website = bankLogoUrlExample.value,
    bank_routings = List(bankRoutingJsonV121),
    attributes = Some(List(bankAttributeBankResponseJsonV400))
  ) 
  
  lazy val postBankJson500 = PostBankJson500(
    id = Some(bankIdExample.value),
    bank_code = bankCodeExample.value,
    full_name = Some(fullNameExample.value),
    logo = Some(logoExample.value),
    website = Some(websiteExample.value),
    bank_routings = Some(List(bankRoutingJsonV121))
  )

  lazy val postBankJson600 = PostBankJson600(
    bank_id = bankIdExample.value,
    bank_code = bankCodeExample.value,
    full_name = Some(fullNameExample.value),
    logo = Some(logoExample.value),
    website = Some(websiteExample.value),
    bank_routings = Some(List(bankRoutingJsonV121))
  )

  lazy val banksJSON400 = BanksJson400(
    banks = List(bankJson400)
  )
  
  lazy val ibanCheckerPostJsonV400 = IbanAddress("DE75512108001245126199")
  
  lazy val ibanCheckerJsonV400 = IbanCheckerJsonV400(
    true, 
    Some(
      IbanDetailsJsonV400(
        bank_routings = List(BankRoutingJsonV121("BIC", "SOGEDEFF")) ,
        bank = "Societe Generale",
        branch = "",
        address = "Neue mainzer strasse 46-50",
        city = "Frankfurt am Main",
        postcode = "60311",
        phone = "",
        country = "Germany",
        attributes = List(
          AttributeJsonV400("country_iso", ""),
          AttributeJsonV400("sepa_credit_transfer", "YES"),
          AttributeJsonV400("sepa_direct_debit", "YES"),
          AttributeJsonV400("sepa_sdd_core", "YES"),
          AttributeJsonV400("sepa_b2b", "YES"),
          AttributeJsonV400("sepa_card_clearing", "YES"),
        )
      )
    )
  )

  lazy val accountHolderJSON = AccountHolderJSON(
    name = "OBP",
    is_alias = true
  )

  lazy val minimalBankJSON = MinimalBankJSON(
    national_identifier = "OBP",
    name = "OBP"
  )

  lazy val moderatedAccountJSON = ModeratedAccountJSON(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    label = "NoneLabel",
    number = "123",
    owners = List(userJSONV121),
    `type` = "OBP",
    balance = amountOfMoneyJsonV121,
    IBAN = "DE89 3704 0044 0532 0130 00",
    swift_bic = "OKOYFIHH",
    views_available = List(viewJSONV121),
    bank_id = bankIdExample.value,
    account_routing = accountRoutingJsonV121
  )

  lazy val thisAccountJSON = ThisAccountJSON(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    holders = List(accountHolderJSON),
    number = "123",
    kind = "AC",
    IBAN = "UK1234AD",
    swift_bic = "UK1234AD",
    bank = minimalBankJSON
  )

  lazy val locationJSONV121 = LocationJSONV121(
    latitude = 1.231,
    longitude = 1.231,
    date = DateWithDayExampleObject,
    user = userJSONV121
  )

  lazy val otherAccountMetadataJSON = OtherAccountMetadataJSON(
    public_alias = "NONE",
    private_alias = "NONE",
    more_info = "www.openbankproject.com",
    URL = "www.openbankproject.com",
    image_URL = "www.openbankproject.com",
    open_corporates_URL = "www.openbankproject.com",
    corporate_location = locationJSONV121,
    physical_location = locationJSONV121
  )

  lazy val otherAccountJSON = OtherAccountJSON(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    holder = accountHolderJSON,
    number = "123",
    kind = "3456",
    IBAN = "UK234DB",
    swift_bic = "UK12321DB",
    bank = minimalBankJSON,
    metadata = otherAccountMetadataJSON
  )

  lazy val transactionDetailsJSON = TransactionDetailsJSON(
    `type` = "AC",
    description = "this is for family",
    posted = DateWithDayExampleObject,
    completed = DateWithDayExampleObject,
    new_balance = amountOfMoneyJsonV121,
    value = amountOfMoneyJsonV121
  )

  lazy val transactionImageJSON = TransactionImageJSON(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    label = "NONE",
    URL = "www.openbankproject.com",
    date = DateWithDayExampleObject,
    user = userJSONV121
  )

  lazy val transactionImagesJSON = TransactionImagesJSON(
    images = List(transactionImageJSON)
  )

  lazy val transactionCommentJSON = TransactionCommentJSON(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    value = "OBP",
    date = DateWithDayExampleObject,
    user = userJSONV121
  )

  lazy val transactionTagJSON = TransactionTagJSON(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    value = "OBP",
    date = DateWithDayExampleObject,
    user = userJSONV121
  )

  lazy val transactionTagsJSON = TransactionTagsJSON(
    tags = List(transactionTagJSON)
  )

  lazy val accountTagJSON = AccountTagJSON(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    value = "OBP",
    date = DateWithDayExampleObject,
    user = userJSONV121
  )
  lazy val accountTagsJSON = AccountTagsJSON(
    tags = List(accountTagJSON)
  )

  lazy val transactionMetadataJSON = TransactionMetadataJSON(
    narrative = "NONE",
    comments = List(transactionCommentJSON),
    tags = List(transactionTagJSON),
    images = List(transactionImageJSON),
    where = locationJSONV121
  )

  lazy val transactionJSON = TransactionJSON(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    this_account = thisAccountJSON,
    other_account = otherAccountJSON,
    details = transactionDetailsJSON,
    metadata = transactionMetadataJSON
  )

  lazy val transactionsJSON = TransactionsJSON(
    transactions = List(transactionJSON)
  )

  lazy val successMessage = SuccessMessage(
    success = "Success"
  )

  lazy val otherAccountsJSON = OtherAccountsJSON(
    other_accounts = List(otherAccountJSON)
  )

  lazy val transactionNarrativeJSON = TransactionNarrativeJSON(
    narrative = "narative"
  )

  lazy val transactionCommentsJSON = TransactionCommentsJSON(
    comments = List(transactionCommentJSON)
  )

  lazy val transactionWhereJSON = TransactionWhereJSON(
    where = locationJSONV121
  )

  lazy val permissionJSON = PermissionJSON(
    user = userJSONV121,
    views = List(viewJSONV121)
  )

  lazy val permissionsJSON = PermissionsJSON(
    permissions = List(permissionJSON)
  )

  lazy val updateAccountJSON = UpdateAccountJSON(
    id = "123123",
    label = "label",
    bank_id = bankIdExample.value
  )
  lazy val updateAccountJsonV400 = UpdateAccountJsonV400(label = "updated label")

  lazy val viewIdsJson = ViewIdsJson(
    views = List("_family" ,"_work")
  )

  lazy val locationPlainJSON = LocationPlainJSON(
    latitude = 1.532,
    longitude = 1.535
  )

  lazy val postTransactionWhereJSON = PostTransactionWhereJSON(
    where = locationPlainJSON
  )

  lazy val corporateLocationJSON = CorporateLocationJSON(
    corporate_location = locationPlainJSON
  )
  lazy val physicalLocationJSON = PhysicalLocationJSON(
    physical_location = locationPlainJSON
  )

  //V130 -- code.api.v1_3_0
  ///////////////////////////////////////////////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////
  import code.api.v1_3_0._

  lazy val pinResetJSON = PinResetJSON(
    requested_date = DateWithDayExampleObject,
    reason_requested = FORGOT.toString
  )
  lazy val pinResetJSON1 = PinResetJSON(
    requested_date = new Date(),
    reason_requested = GOOD_SECURITY_PRACTICE.toString
  )

  lazy val replacementJSON = ReplacementJSON(
    requested_date = DateWithDayExampleObject,
    reason_requested = CardReplacementReason.RENEW.toString
  )

  lazy val physicalCardJSON = PhysicalCardJSON(
    bank_id = bankIdExample.value,
    bank_card_number = bankCardNumberExample.value,
    name_on_card = "String",
    issue_number = issueNumberExample.value,
    serial_number = serialNumberExample.value,
    valid_from_date = DateWithDayExampleObject,
    expires_date = DateWithDayExampleObject,
    enabled = true,
    cancelled = true,
    on_hot_list = true,
    technology = "String",
    networks = List("String"),
    allows = List("String"),
    account = accountJSON,
    replacement = replacementJSON,
    pin_reset = List(pinResetJSON),
    collected = DateWithDayExampleObject,
    posted = DateWithDayExampleObject
  )

  lazy val physicalCardsJSON = PhysicalCardsJSON(
    cards = List(physicalCardJSON)
  )

  lazy val postPhysicalCardJSON = PostPhysicalCardJSON(
    bank_card_number = bankCardNumberExample.value,
    name_on_card = "name_on_card",
    issue_number = issueNumberExample.value,
    serial_number = serialNumberExample.value,
    valid_from_date = DateWithDayExampleObject,
    expires_date = DateWithDayExampleObject,
    enabled = true,
    technology = "technology",
    networks = List("network1", "network2"),
    allows = List("credit", "debit"),
    account_id =accountIdExample.value,
    replacement = replacementJSON,
    pin_reset = List(pinResetJSON, pinResetJSON1),
    collected = DateWithDayExampleObject,
    posted = DateWithDayExampleObject
  )

  //V140 -- code.api.v1_4_0.JSONFactory1_4_0
  ///////////////////////////////////////////////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////
  import code.api.v1_4_0.JSONFactory1_4_0._

  lazy val transactionRequestBodyJson = TransactionRequestBodyJson (
    to = transactionRequestAccount,
    value = amountOfMoney,
    description = "String"
  )

  lazy val transactionRequestJson = TransactionRequestJson(
    id = transactionRequestIdSwagger,
    `type` = "String",
    from = transactionRequestAccount,
    details = transactionRequestBodyJson,
    body = transactionRequestBodyJson,
    transaction_ids = "String",
    status = "String",
    start_date = DateWithDayExampleObject,
    end_date = DateWithDayExampleObject,
    challenge = transactionRequestChallenge,
    charge = transactionRequestCharge,
    charge_policy = chargePolicyExample.value,
    counterparty_id = counterpartyIdSwagger,
    name = counterpartyNameExample.value,
    this_bank_id = bankIdSwagger,
    this_account_id = accountIdSwagger,
    this_view_id = viewIdSwagger,
    other_account_routing_scheme = counterpartyOtherAccountRoutingSchemeExample.value,
    other_account_routing_address = counterpartyOtherAccountRoutingAddressExample.value,
    other_bank_routing_scheme = counterpartyOtherBankRoutingSchemeExample.value,
    other_bank_routing_address = counterpartyOtherBankRoutingAddressExample.value,
    is_beneficiary = true
  )

  lazy val customerFaceImageJson = CustomerFaceImageJson(
    url = "www.openbankproject",
    date = DateWithDayExampleObject
  )

  lazy val locationJson = LocationJsonV140(
    latitude = 11.45,
    longitude = 11.45
  )

  lazy val transactionRequestChargeJsonV140 = TransactionRequestChargeJsonV140(
    summary = "The bank fixed charge",
    value = amountOfMoneyJsonV121 //amountOfMoneyJSON
  )

  lazy val transactionRequestTypeJsonV140 = TransactionRequestTypeJsonV140(
    value = "10",
    charge = transactionRequestChargeJsonV140
  )

  lazy val transactionRequestTypesJsonV140 = TransactionRequestTypesJsonV140(
    transaction_request_types = List(transactionRequestTypeJsonV140)
  )

  lazy val transactionRequestAccountJsonV140 = TransactionRequestAccountJsonV140(
    bank_id = bankIdExample.value,
    account_id =accountIdExample.value
  )

  lazy val challengeJsonV140 = ChallengeJsonV140(
    id = "be1a183d-b301-4b83-b855-5eeffdd3526f",
    allowed_attempts = 3,
    challenge_type = SANDBOX_TAN.toString
  )

  lazy val driveUpJson = DriveUpStringJson(
    hours = "5"
  )
  lazy val licenseJson = LicenseJsonV140(
    id = licenseIdExample.value,
    name = licenseNameExample.value
  )
  lazy val metaJson = MetaJsonV140(
    license = licenseJson
  )
  lazy val lobbyJson = LobbyStringJson(
    hours = "5"
  )
  lazy val addressJsonV140 = AddressJsonV140(
    line_1 = "Osloer Straße 16/17",
    line_2 = "Wedding",
    line_3 = "",
    city = "Berlin",
    state = "Berlin Brandenburg",
    postcode = "13359",
    country = "DE"
  )
  lazy val challengeAnswerJSON = ChallengeAnswerJSON(
    id = "This is challenge.id, you can get it from `Create Transaction Request.` response, only is useful if status ==`INITIATED` there.",
    answer = "123"
  )

  lazy val challengeAnswerJson400 = ChallengeAnswerJson400(
    id = "This is challenge.id, you can get it from `Create Transaction Request.` response, only is useful if status ==`INITIATED` there.",
    answer = "123",
    Some("[Optional] Reason code for REJECT answer (e.g. 'CUST')"),
    Some("[Optional] Additional description for REJECT answer")
  )

  lazy val postCustomerJson = PostCustomerJson(
    customer_number = ExampleValue.customerNumberExample.value,
    legal_name = ExampleValue.legalNameExample.value,
    mobile_phone_number = ExampleValue.mobileNumberExample.value,
    email = ExampleValue.emailExample.value,
    face_image = customerFaceImageJson,
    date_of_birth = DateWithDayExampleObject,
    relationship_status = ExampleValue.relationshipStatusExample.value,
    dependants = ExampleValue.dependantsExample.value.toInt,
    dob_of_dependants = List(DateWithDayExampleObject),
    highest_education_attained = ExampleValue.highestEducationAttainedExample.value,
    employment_status = ExampleValue.employmentStatusExample.value,
    kyc_status = ExampleValue.kycStatusExample.value.toBoolean,
    last_ok_date = oneYearAgoDate
  )

  lazy val customerJsonV140 = CustomerJsonV140(
    customer_id = "String",
    customer_number = ExampleValue.customerNumberExample.value,
    legal_name = ExampleValue.legalNameExample.value,
    mobile_phone_number = ExampleValue.mobileNumberExample.value,
    email = ExampleValue.emailExample.value,
    face_image = customerFaceImageJson,
    date_of_birth = DateWithDayExampleObject,
    relationship_status = ExampleValue.relationshipStatusExample.value,
    dependants = ExampleValue.dependantsExample.value.toInt,
    dob_of_dependants = List(DateWithDayExampleObject),
    highest_education_attained = ExampleValue.highestEducationAttainedExample.value,
    employment_status = ExampleValue.employmentStatusExample.value,
    kyc_status = ExampleValue.kycStatusExample.value.toBoolean,
    last_ok_date = oneYearAgoDate
  )

  lazy val customersJsonV140 = CustomersJsonV140(
    customers = List(customerJsonV140)
  )

  lazy val customerMessageJson = CustomerMessageJson(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    date = DateWithDayExampleObject,
    message = "String",
    from_department = "String",
    from_person = "String"
  )

  lazy val customerMessagesJson = CustomerMessagesJson(
    messages = List(customerMessageJson)
  )

  lazy val addCustomerMessageJson = AddCustomerMessageJson(
    message = "String",
    from_department = "String",
    from_person = "String"
  )

  lazy val branchRoutingJsonV141 = BranchRoutingJsonV141(
    scheme = schemeExample.value,
    address = branchIdExample.value
  )

  lazy val branchJson = BranchJson(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    name = "String",
    address = addressJsonV140,
    location = locationJson,
    lobby = lobbyJson,
    drive_up = driveUpJson,
    meta = metaJson,
    branch_routing = branchRoutingJsonV141
  )






  lazy val branchesJson = BranchesJson(branches = List(branchJson))




  // Internal data examples (none JSON format).
  // Use transform... to convert these to our various json formats for different API versions

  lazy val meta: Meta =  Meta(license = License (id = licenseIdExample.value, name = licenseNameExample.value))  // Note the meta  is V140
  lazy val openingTimesV300 =OpeningTimesV300(
    opening_time = "10:00",
    closing_time = "18:00")
  lazy val openingTimes = OpeningTimes(
    openingTime = "10:00",
    closingTime = "18:00"
  )

  lazy val address : Address = Address(
    line1 = "No 1 the Road",
    line2 = "The Place",
    line3 = "The Hill",
    city = "Berlin",
    county = Some("String"),
    state = "Brandenburg",
    postCode = "13359",
    countryCode = "DE"
  )

  lazy val lobby: Lobby = Lobby(
    monday = List(openingTimes),
    tuesday = List(openingTimes),
    wednesday = List(openingTimes),
    thursday = List(openingTimes),
    friday = List(openingTimes),
    saturday = List(openingTimes),
    sunday = List(openingTimes)
  )


  lazy val driveUp: DriveUp = DriveUp(
    monday = openingTimes,
    tuesday = openingTimes,
    wednesday = openingTimes,
    thursday = openingTimes,
    friday = openingTimes,
    saturday = openingTimes,
    sunday = openingTimes
  )

  lazy val branchRouting = Routing("OBP", "123abc")

  lazy val basicResourceUser = BasicResourceUser(
    userId= "String", // Should come from Resource User Id
    provider= " String",
    username= " String"
  )

  lazy val location : Location = Location (
    10.0,
    10.0,
    Some(DateWithDayExampleObject),
    Some(basicResourceUser))

  lazy val lobbyString = LobbyString (
    hours ="String "
  )

  lazy val driveUpString = DriveUpString (
    hours ="String "
  )

  lazy val branch: Branch = Branch(
    branchId = BranchId("branch-id-123"),
    bankId = BankId("bank-id-123"),
    name = "Branch by the Lake",
    address = address,
    location = location,
    meta = meta,
    lobbyString = Some(lobbyString),
    driveUpString = Some(driveUpString),
    lobby = Some(lobby),
    driveUp = Some(driveUp),
    branchRouting = Some(branchRouting),
    // Easy access for people who use wheelchairs etc.
    isAccessible = Some(true),
    accessibleFeatures = Some("wheelchair, atm usuable by the visually impaired"),
    branchType = Some("Full service store"),
    moreInfo = Some("short walk to the lake from here"),
    phoneNumber = Some("+381631954907"),
    isDeleted = Some(false)
  )


  lazy val lobbyJsonV330 = LobbyJsonV330(
    monday = List(openingTimesV300),
    tuesday = List(openingTimesV300),
    wednesday = List(openingTimesV300),
    thursday =  List(openingTimesV300),
    friday =  List(openingTimesV300),
    saturday =  List(openingTimesV300),
    sunday =  List(openingTimesV300)
  )

  lazy val driveUpJsonV330 = DriveUpJsonV330(
    monday = openingTimesV300,
    tuesday = openingTimesV300,
    wednesday = openingTimesV300,
    thursday =  openingTimesV300,
    friday =  openingTimesV300,
    saturday =  openingTimesV300,
    sunday =  openingTimesV300
  )


  lazy val branchJsonV300: BranchJsonV300 = createBranchJsonV300 (branch)
  lazy val branchesJsonV300 = BranchesJsonV300(branches = List(branchJsonV300))

  lazy val postBranchJsonV300 = PostBranchJsonV300(
    branchJsonV300.bank_id,
    branchJsonV300.name,
    branchJsonV300.address,
    branchJsonV300.location,
    branchJsonV300.meta,
    branchJsonV300.lobby,
    branchJsonV300.drive_up,
    branchJsonV300.branch_routing,
    branchJsonV300.is_accessible,
    branchJsonV300.accessibleFeatures,
    branchJsonV300.branch_type,
    branchJsonV300.more_info,
    branchJsonV300.phone_number 
  )



  lazy val atmJson = AtmJson(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    name = "String",
    address = addressJsonV140,
    location = locationJson,
    meta = metaJson
  )

  lazy val atmsJson = AtmsJson(atms = List(atmJson))



  lazy val addressJsonV300 = AddressJsonV300(
    line_1 = "No 1 the Road",
    line_2 = "The Place",
    line_3 = "The Hill",
    city = "Berlin",
    county = "",
    state = "Brandenburg",
    postcode = "13359",
    //ISO_3166-1_alpha-2
    country_code = "DE"
  )

  lazy val customerAddressJsonV310 = CustomerAddressJsonV310(
    customer_address_id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    customer_id = customerIdExample.value, 
    line_1 = "No 1 the Road",
    line_2 = "The Place",
    line_3 = "The Hill",
    city = "Berlin",
    county = "",
    state = "Brandenburg",
    postcode = "13359",
    //ISO_3166-1_alpha-2
    country_code = "DE",
    tags = List("mailing", "home"),
    status = "OK",
    insert_date = DateWithDayExampleObject
  )
  lazy val customerAddressesJsonV310 = CustomerAddressesJsonV310(List(customerAddressJsonV310))

  lazy val postCustomerAddressJsonV310 = PostCustomerAddressJsonV310(
    line_1 = "No 1 the Road",
    line_2 = "The Place",
    line_3 = "The Hill",
    city = "Berlin",
    county = "",
    state = "Brandenburg",
    postcode = "13359",
    //ISO_3166-1_alpha-2
    country_code = "DE",
    tags = List("mailing", "home"),
    status = "OK"
  )
  
  lazy val atmJsonV300 = AtmJsonV300(
    id = "atm-id-123",
    bank_id = bankIdExample.value,
    name = "Atm by the Lake",
    address = addressJsonV300,
    location = locationJson,
    meta = metaJson,
    monday = openingTimesV300,
    tuesday = openingTimesV300,
    wednesday = openingTimesV300,
    thursday = openingTimesV300,
    friday = openingTimesV300,
    saturday = openingTimesV300,
    sunday = openingTimesV300,

    is_accessible = "true",
    located_at = "Full service store",
    more_info = "short walk to the lake from here",
    has_deposit_capability="true"

  )
  lazy val atmsJsonV300 = AtmsJsonV300(List(atmJsonV300))

  lazy val productJson = ProductJson(
    code = "String",
    name = "String",
    category = "String",
    family = "String",
    super_family = "String",
    more_info_url = "String",
    meta = metaJson
  )

  lazy val productsJson = ProductsJson(products = List(productJson))


  lazy val crmEventJson = CrmEventJson(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    bank_id = bankIdExample.value,
    customer_name = "String",
    customer_number = ExampleValue.customerNumberExample.value,
    category = "String",
    detail = "String",
    channel = "String",
    scheduled_date = DateWithDayExampleObject,
    actual_date = DateWithDayExampleObject,
    result = "String"
  )

  lazy val crmEventsJson = CrmEventsJson(crm_events = List(crmEventJson))

  lazy val implementedByJson = ImplementedByJson(
    version = "1_4_0",
    function = "getBranches"
  )
  // Used to describe the OBP API calls for documentation and API discovery purposes
  lazy val canCreateCustomerSwagger = CanCreateCustomer()

  lazy val transactionRequestBodyJsonV140 = TransactionRequestBodyJsonV140(
    to = transactionRequestAccountJsonV140,
    value = amountOfMoneyJsonV121,
    description = "String",
    challenge_type = "String"
  )
  lazy val transactionRequestJSON = TransactionRequestJsonV140(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    `type` = "String",
    from = transactionRequestAccountJsonV140,
    body = transactionRequestBodyJsonV140,
    transaction_ids = "String",
    status = "String",
    start_date = DateWithDayExampleObject,
    end_date = DateWithDayExampleObject,
    challenge = challengeJsonV140
  )

  //V200
  ///////////////////////////////////////////////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////
  import code.api.v2_0_0.JSONFactory200._
  import code.api.v2_0_0._

  lazy val basicViewJSON = BasicViewJson(
    id = "1",
    short_name = "HHH",
    is_public = true
  )

  lazy val basicAccountJSON = BasicAccountJSON(
    id = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
    label = "NoneLabel",
    bank_id = bankIdExample.value,
    views_available = List(basicViewJSON)
  )

  lazy val coreAccountJSON = CoreAccountJSON(
    id = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
    label = "NoneLabel",
    bank_id = bankIdExample.value,
    _links = defaultJValue
  )

  lazy val moderatedCoreAccountJSON = ModeratedCoreAccountJSON(
    id = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
    label = "NoneLabel",
    number = "123",
    owners = List(userJSONV121),
    `type` = "OBP",
    balance = amountOfMoneyJsonV121,
    IBAN = "GR1301720530005053000582373",
    swift_bic = "UKTF3049auf",
    bank_id = bankIdExample.value,
    account_routing = accountRoutingJsonV121
  )

  lazy val basicAccountsJSON = BasicAccountsJSON(
    accounts = List(basicAccountJSON)
  )
  lazy val coreAccountsJSON = CoreAccountsJSON(accounts = List(coreAccountJSON))

  lazy val kycDocumentJSON = KycDocumentJSON(
    bank_id = bankIdExample.value,
    customer_id = customerIdExample.value,
    id = "PlaceholderString",
    customer_number = ExampleValue.customerNumberExample.value,
    `type` = "PlaceholderString",
    number = "PlaceholderString",
    issue_date = DateWithDayExampleObject,
    issue_place = "PlaceholderString",
    expiry_date = DateWithDayExampleObject
  )

  lazy val kycDocumentsJSON = KycDocumentsJSON(
    documents = List(kycDocumentJSON)
  )
  lazy val kycMediaJSON = KycMediaJSON(
    bank_id = bankIdExample.value,
    customer_id = "PlaceholderString",
    id = "PlaceholderString",
    customer_number = ExampleValue.customerNumberExample.value,
    `type` = "PlaceholderString",
    url = "PlaceholderString",
    date = DateWithDayExampleObject,
    relates_to_kyc_document_id = "PlaceholderString",
    relates_to_kyc_check_id = "PlaceholderString"
  )
  lazy val kycMediasJSON = KycMediasJSON(medias = List(kycMediaJSON))


  lazy val kycCheckJSON = KycCheckJSON(
    bank_id = bankIdExample.value,
    customer_id = customerIdExample.value,
    id = "PlaceholderString",
    customer_number = ExampleValue.customerNumberExample.value,
    date = DateWithDayExampleObject,
    how = "PlaceholderString",
    staff_user_id = ExampleValue.userIdExample.value,
    staff_name = "PlaceholderString",
    satisfied = true,
    comments = "PlaceholderString"
  )
  lazy val kycChecksJSON = KycChecksJSON(checks = List(kycCheckJSON))

  lazy val kycStatusJSON = KycStatusJSON(
    customer_id = customerIdExample.value,
    customer_number = ExampleValue.customerNumberExample.value,
    ok = true,
    date = DateWithDayExampleObject
  )
  lazy val kycStatusesJSON = KycStatusesJSON(statuses = List(kycStatusJSON))

  lazy val socialMediaJSON = SocialMediaJSON(
    customer_number = ExampleValue.customerNumberExample.value,
    `type` = "PlaceholderString",
    handle = "PlaceholderString",
    date_added = DateWithDayExampleObject,
    date_activated = DateWithDayExampleObject
  )
  lazy val socialMediasJSON = SocialMediasJSON(checks = List(socialMediaJSON))

  lazy val entitlementJSON =
    code.api.v2_0_0.EntitlementJSON(
      entitlement_id = "6fb17583-1e49-4435-bb74-a14fe0996723",
      role_name = "CanQueryOtherUser",
      bank_id = bankIdExample.value
    )
    
  lazy val entitlementJSONs = EntitlementJSONs(
    list = List(entitlementJSON)
  )

  lazy val userJsonV200 = UserJsonV200(
    user_id = ExampleValue.userIdExample.value,
    email = ExampleValue.emailExample.value,
    provider_id = providerIdValueExample.value,
    provider = providerValueExample.value,
    username = usernameExample.value,
    entitlements = entitlementJSONs
  )
  
  lazy val userAgreementJson = UserAgreementJson(
    ExampleValue.typeExample.value,
    ExampleValue.textExample.value,
  )
  lazy val viewJSON300 = ViewJSON300(
    bank_id = bankIdExample.value,
    account_id = accountIdExample.value,
    view_id = viewIdExample.value
  )

  lazy val viewsJSON300 = ViewsJSON300(
    list = List(viewJSON300)
  )

  lazy val userJsonV300 = UserJsonV300(
    user_id = ExampleValue.userIdExample.value,
    email = ExampleValue.emailExample.value,
    provider_id = providerIdValueExample.value,
    provider = providerValueExample.value,
    username = usernameExample.value,
    entitlements = entitlementJSONs,
    views = Some(viewsJSON300)
  )
  
  lazy val userJsonV400 = UserJsonV400(
    user_id = ExampleValue.userIdExample.value,
    email = ExampleValue.emailExample.value,
    provider_id = providerIdValueExample.value,
    provider = providerValueExample.value,
    username = usernameExample.value,
    entitlements = entitlementJSONs,
    views = Some(viewsJSON300),
    agreements = Some(List(userAgreementJson)),
    is_deleted = false,
    last_marketing_agreement_signed_date = Some(DateWithDayExampleObject),
    is_locked = false
  )
  lazy val userIdJsonV400 = UserIdJsonV400(
    user_id = ExampleValue.userIdExample.value
  )
  
  lazy val userInvitationPostJsonV400 = PostUserInvitationJsonV400(
    first_name = ExampleValue.firstNameExample.value,
    last_name = ExampleValue.lastNameExample.value,
    email = ExampleValue.emailExample.value,
    company = ExampleValue.companyExample.value,
    country = ExampleValue.countryExample.value,
    purpose = ExampleValue.purposeExample.value,
  ) 
  lazy val userInvitationJsonV400 = UserInvitationJsonV400(
    first_name = ExampleValue.firstNameExample.value,
    last_name = ExampleValue.lastNameExample.value,
    email = ExampleValue.emailExample.value,
    company = ExampleValue.companyExample.value,
    country = ExampleValue.countryExample.value,
    purpose = ExampleValue.purposeExample.value,
    status = ExampleValue.statusExample.value,
  )

  lazy val entitlementRequestJSON =
    code.api.v3_0_0.EntitlementRequestJSON(
      user = userJsonV200,
      entitlement_request_id = "6fb17583-1e49-4435-bb74-a14fe0996723",
      role_name = "CanQueryOtherUser",
      bank_id = bankIdExample.value,
      created = DateWithDayExampleObject
    )

  lazy val entitlementRequestsJSON = EntitlementRequestsJSON(entitlement_requests = List(entitlementRequestJSON))


  lazy val coreTransactionDetailsJSON = CoreTransactionDetailsJSON(
    `type` = "AC",
    description = "OBP",
    posted = DateWithDayExampleObject,
    completed = DateWithDayExampleObject,
    new_balance = amountOfMoneyJsonV121,
    value = amountOfMoneyJsonV121
  )

  lazy val coreAccountHolderJSON = CoreAccountHolderJSON(
    name = "ZACK"
  )

  lazy val createEntitlementJSON = CreateEntitlementJSON(
    bank_id = bankIdExample.value,
    role_name = CanCreateBranch.toString()
  )

  lazy val coreCounterpartyJSON = CoreCounterpartyJSON(
    id = "123",
    holder = coreAccountHolderJSON,
    number = "1234",
    kind = "AV",
    IBAN = "UK12344DB",
    swift_bic = "UK12344DB",
    bank = minimalBankJSON
  )

  lazy val coreTransactionJSON = CoreTransactionJSON(
    id = "123",
    account = thisAccountJSON,
    counterparty = coreCounterpartyJSON,
    details = coreTransactionDetailsJSON
  )

  lazy val coreTransactionsJSON = CoreTransactionsJSON(
    transactions = List(coreTransactionJSON)
  )

  lazy val transactionRequestChargeJsonV200 = TransactionRequestChargeJsonV200(
    summary = "Rent the flat",
    value = amountOfMoneyJsonV121
  )

  lazy val transactionRequestWithChargeJson = TransactionRequestWithChargeJson(
    id = "82f92531-9c63-4246-abfc-96c20ec46188",
    `type` = SANDBOX_TAN.toString,
    from = transactionRequestAccountJsonV140,
    details = transactionRequestBody,
    transaction_ids = "666666-9c63-4246-abfc-96c20ec46188",
    status = TransactionRequestStatus.COMPLETED.toString,
    start_date = DateWithDayExampleObject,
    end_date = DateWithDayExampleObject,
    challenge = challengeJsonV140,
    charge = transactionRequestChargeJsonV200
  )

  lazy val transactionRequestBodyJsonV200 = TransactionRequestBodyJsonV200(
    to = transactionRequestAccountJsonV140,
    value = amountOfMoneyJsonV121,
    description = "this is for work"
  )

  lazy val transactionTypeJsonV200 = TransactionTypeJsonV200(
    id = transactionTypeIdSwagger,
    bank_id = bankIdExample.value,
    short_code = "PlaceholderString",
    summary = "PlaceholderString",
    description = "PlaceholderString",
    charge = amountOfMoneyJsonV121
  )

  lazy val transactionTypesJsonV200 = TransactionTypesJsonV200(
    transaction_types = List(transactionTypeJsonV200)
  )
  lazy val linkJson = LinkJson(
    href = "String",
    rel = "String",
    method = "String"
  )

  lazy val linksJson = LinksJson(
    _links = List(linkJson)
  )

  lazy val resultAndLinksJson = ResultAndLinksJson(
    result = defaultJValue,
    links = linksJson
  )

  lazy val createUserJson = CreateUserJson(
    email = emailExample.value,
    username = usernameExample.value,
    password = "String",
    first_name = "Simon",
    last_name = "Redfern"
  )

  lazy val createUserJSONs = CreateUsersJson(
    users = List(createUserJson)
  )

  lazy val createMeetingJson = CreateMeetingJson(
    provider_id = providerIdValueExample.value,
    purpose_id = "String"
  )

  lazy val meetingKeysJSON = MeetingKeysJson(
    session_id = "String",
    staff_token = "String",
    customer_token = "String"
  )

  lazy val meetingPresentJSON = MeetingPresentJson(
    staff_user_id = userIdExample.value,
    customer_user_id = userIdExample.value
  )

  lazy val meetingJson = MeetingJson(
    meeting_id = "String",
    provider_id = providerIdValueExample.value,
    purpose_id = "String",
    bank_id = bankIdExample.value,
    present = meetingPresentJSON,
    keys = meetingKeysJSON,
    when = DateWithDayExampleObject
  )

  lazy val meetingsJson = MeetingsJson(
    meetings = List(meetingJson)
  )


  lazy val userCustomerLinkJson = UserCustomerLinkJson(
    user_customer_link_id = uuidExample.value,
    customer_id = customerIdExample.value,
    user_id = userIdExample.value,
    date_inserted = DateWithDayExampleObject,
    is_active = true
  )

  lazy val userCustomerLinksJson = UserCustomerLinksJson(
    user_customer_links = List(userCustomerLinkJson)
  )

  lazy val createUserCustomerLinkJson = CreateUserCustomerLinkJson(
    user_id = userIdExample.value,
    customer_id = customerIdExample.value
  )

  lazy val createAccountJSON = CreateAccountJSON(
    user_id = userIdExample.value,
    label = "String",
    `type` = "String",
    balance = amountOfMoneyJsonV121
  )

  lazy val postKycDocumentJSON = PostKycDocumentJSON(
    customer_number = ExampleValue.customerNumberExample.value,
    `type` = "passport",
    number = "12345",
    issue_date = DateWithDayExampleObject,
    issue_place = "Berlin",
    expiry_date = DateWithDayExampleObject
  )

  lazy val postKycMediaJSON = PostKycMediaJSON(
    customer_number = ExampleValue.customerNumberExample.value,
    `type` = "image",
    url = "http://www.example.com/id-docs/123/image.png",
    date = DateWithDayExampleObject,
    relates_to_kyc_document_id = "123",
    relates_to_kyc_check_id = "123"
  )

  lazy val postKycCheckJSON = PostKycCheckJSON(
    customer_number = customerNumberExample.value,
    date = DateWithDayExampleObject,
    how = "online_meeting",
    staff_user_id = "67876",
    staff_name = "Simon",
    satisfied = true,
    comments = "String"
  )

  lazy val postKycStatusJSON = PostKycStatusJSON(
    customer_number = customerNumberExample.value,
    ok = true,
    date = DateWithDayExampleObject
  )

  lazy val createCustomerJson = CreateCustomerJson(
    title = ExampleValue.titleExample.value,
    branchId = ExampleValue.branchIdExample.value,
    nameSuffix = ExampleValue.nameSuffixExample.value,
    user_id = ExampleValue.userIdExample.value,
    customer_number = ExampleValue.customerNumberExample.value,
    legal_name = ExampleValue.legalNameExample.value,
    mobile_phone_number = ExampleValue.mobileNumberExample.value,
    email = ExampleValue.emailExample.value,
    face_image = customerFaceImageJson,
    date_of_birth = DateWithDayExampleObject,
    relationship_status = ExampleValue.relationshipStatusExample.value,
    dependants = ExampleValue.dependantsExample.value.toInt,
    dob_of_dependants = List(DateWithDayExampleObject),
    highest_education_attained = ExampleValue.highestEducationAttainedExample.value,
    employment_status = ExampleValue.employmentStatusExample.value,
    kyc_status = ExampleValue.kycStatusExample.value.toBoolean,
    last_ok_date = oneYearAgoDate
  )

  lazy val transactionRequestJsonV200 = TransactionRequestJsonV200(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    `type` = "String",
    from = transactionRequestAccountJsonV140,
    body = transactionRequestBodyJsonV200,
    transaction_ids = "String",
    status = "String",
    start_date = DateWithDayExampleObject,
    end_date = DateWithDayExampleObject,
    challenge = challengeJsonV140
  )

  lazy val transactionRequestWithChargesJson = TransactionRequestWithChargesJson(
    transaction_requests_with_charges = List(transactionRequestWithChargeJson)
  )

  lazy val usersJsonV200 = UsersJsonV200(
    users = List(userJsonV200)
  )
  lazy val usersJsonV400 = UsersJsonV400(
    users = List(userJsonV400)
  )

  lazy val counterpartiesJSON = CounterpartiesJSON(
    counterparties = List(coreCounterpartyJSON)
  )

  //V210
  ///////////////////////////////////////////////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////
  import code.api.v2_1_0._

  lazy val counterpartyIdJson = CounterpartyIdJson(
    counterparty_id = counterpartyIdExample.value
  )
  lazy val ibanJson = IbanJson(
    iban = "123"
  )

  lazy val metricJson = MetricJson(
    user_id = ExampleValue.userIdExample.value,
    url = "www.openbankproject.com",
    date = DateWithDayExampleObject,
    user_name = "OBP",
    app_name = "SOFI",
    developer_email = ExampleValue.emailExample.value,
    implemented_by_partial_function = "getBanks",
    implemented_in_version = "v210",
    consumer_id = "123",
    verb = "get",
    correlation_id = "v8ho6h5ivel3uq7a5zcnv0w1",
    duration = 39
  )

  lazy val metricJson510 = MetricJsonV510(
      user_id = ExampleValue.userIdExample.value,
      url = "www.openbankproject.com",
      date = DateWithDayExampleObject,
      user_name = "OBP",
      app_name = "SOFI",
      developer_email = ExampleValue.emailExample.value,
      implemented_by_partial_function = "getBanks",
      implemented_in_version = "v210",
      consumer_id = "123",
      verb = "get",
      correlation_id = "v8ho6h5ivel3uq7a5zcnv0w1",
      duration = 39,
      source_ip = "2001:0db8:3c4d:0015:0000:0000:1a2f:1a2b",
      target_ip = "2001:0db8:3c4d:0015:0000:0000:1a2f:1a2b",
      response_body = json.parse("""{"code":401,"message":"OBP-20001: User not logged in. Authentication is required!"}""")
  )

  lazy val resourceUserJSON = ResourceUserJSON(
    user_id = ExampleValue.userIdExample.value,
    email = ExampleValue.emailExample.value,
    provider_id = providerIdValueExample.value,
    provider = providerValueExample.value,
    username = usernameExample.value
  )

  lazy val availableRoleJSON = AvailableRoleJSON(
    role = "CanCreateBranch",
    requires_bank_id = true
  )

  lazy val transactionRequestTypeJSONV210 = TransactionRequestTypeJSONV210(
    transaction_request_type = "SandboxTan"
  )

  lazy val transactionRequestTypesJSON = TransactionRequestTypesJSON(
    transaction_request_types = List(transactionRequestTypeJSONV210)
  )

  lazy val transactionRequestAttributeJsonV400 = TransactionRequestAttributeJsonV400(
    name = transactionRequestAttributeNameExample.value,
    attribute_type = transactionRequestAttributeTypeExample.value,
    value = transactionRequestAttributeValueExample.value
  )
  
  lazy val transactionRequestBodyCounterpartyJSON = TransactionRequestBodyCounterpartyJSON(
    counterpartyIdJson,
    amountOfMoneyJsonV121,
    description = "A description for the transaction to the counterparty",
    chargePolicyExample.value,
    Some(futureDateExample.value),
    Some(List(transactionRequestAttributeJsonV400))
  )

  lazy val transactionRequestBodySEPAJSON = TransactionRequestBodySEPAJSON(
    amountOfMoneyJsonV121,
    ibanJson,
    "This is a SEPA Transaction Request",
    chargePolicyExample.value,
    Some(futureDateExample.value)
  )
  lazy val transactionRequestBodySEPAJsonV400 = TransactionRequestBodySEPAJsonV400(
    amountOfMoneyJsonV121,
    ibanJson,
    description = "This is a SEPA Transaction Request",
    charge_policy = chargePolicyExample.value,
    future_date = Some(futureDateExample.value),
    reasons = Some(List(
      TransactionRequestReasonJsonV400(
        code = "410",
        document_number = Some("2020/154"),
        amount = Some("100"),
        currency = Some("EUR"),
        description = Some("SEPA payment")
      )
    ))
  )
  lazy val transactionRequestBodyFreeFormJSON = TransactionRequestBodyFreeFormJSON(
    amountOfMoneyJsonV121,
    "This is a FREE_FORM Transaction Request",
  )

  lazy val customerCreditRatingJSON = CustomerCreditRatingJSON(
    rating = "OBP",
    source = "OBP"
  )

  lazy val customerJsonV210 = CustomerJsonV210(
    bank_id = bankIdExample.value,
    customer_id = customerIdExample.value,
    customer_number = ExampleValue.customerNumberExample.value,
    legal_name = ExampleValue.legalNameExample.value,
    mobile_phone_number = ExampleValue.mobileNumberExample.value,
    email = ExampleValue.emailExample.value,
    face_image = customerFaceImageJson,
    date_of_birth = DateWithDayExampleObject,
    relationship_status = ExampleValue.relationshipStatusExample.value,
    dependants = ExampleValue.dependantsExample.value.toInt,
    dob_of_dependants = List(DateWithDayExampleObject),
    credit_rating = Option(customerCreditRatingJSON),
    credit_limit = Option(amountOfMoneyJsonV121),
    highest_education_attained = ExampleValue.highestEducationAttainedExample.value,
    employment_status = ExampleValue.employmentStatusExample.value,
    kyc_status = ExampleValue.kycStatusExample.value.toBoolean,
    last_ok_date = oneYearAgoDate
  )
  
  lazy val customerJSONs = CustomerJSONs(customers = List(customerJsonV210))

  lazy val userJSONV210 = UserJSONV210(
    id = "123",
    provider = providerValueExample.value,
    username = usernameExample.value
  )

  lazy val locationJsonV210 =
    LocationJsonV210(
      latitude = 11.45,
      longitude = 11.45,
      date = DateWithDayExampleObject,
      user = userJSONV210
    )

  lazy val postCustomerJsonV210 =
    PostCustomerJsonV210(
      user_id = ExampleValue.userIdExample.value,
      customer_number = ExampleValue.customerNumberExample.value,
      legal_name = ExampleValue.legalNameExample.value,
      mobile_phone_number = ExampleValue.mobileNumberExample.value,
      email = ExampleValue.emailExample.value,
      face_image = customerFaceImageJson,
      date_of_birth = DateWithDayExampleObject,
      relationship_status = ExampleValue.relationshipStatusExample.value,
      dependants = ExampleValue.dependantsExample.value.toInt,
      dob_of_dependants = List(DateWithDayExampleObject),
      credit_rating = customerCreditRatingJSON,
      credit_limit = amountOfMoneyJsonV121,
      highest_education_attained = ExampleValue.highestEducationAttainedExample.value,
      employment_status = ExampleValue.employmentStatusExample.value,
      kyc_status = ExampleValue.kycStatusExample.value.toBoolean,
      last_ok_date = oneYearAgoDate
    )

  lazy val customerJsonV300 = CustomerJsonV300(
    bank_id = bankIdExample.value,
    customer_id = customerIdExample.value,
    customer_number = ExampleValue.customerNumberExample.value,
    legal_name = ExampleValue.legalNameExample.value,
    mobile_phone_number = ExampleValue.mobileNumberExample.value,
    email = ExampleValue.emailExample.value,
    face_image = customerFaceImageJson,
    date_of_birth = "19900101",
    relationship_status = ExampleValue.relationshipStatusExample.value,
    dependants = ExampleValue.dependantsExample.value.toInt,
    dob_of_dependants = List("19900101"),
    credit_rating = Option(customerCreditRatingJSON),
    credit_limit = Option(amountOfMoneyJsonV121),
    highest_education_attained = ExampleValue.highestEducationAttainedExample.value,
    employment_status = ExampleValue.employmentStatusExample.value,
    kyc_status = ExampleValue.kycStatusExample.value.toBoolean,
    last_ok_date = oneYearAgoDate,
    title  = ExampleValue.titleExample.value,
    branch_id = ExampleValue.branchIdExample.value,
    name_suffix = ExampleValue.nameSuffixExample.value
  )

  lazy val customersJsonV300 = code.api.v3_0_0.CustomerJSONsV300(List(customerJsonV300))
  
  lazy val customerMinimalJsonV400 = CustomerMinimalJsonV400(
    bank_id = bankIdExample.value,
    customer_id = customerIdExample.value
  )
  lazy val customersMinimalJsonV300 = code.api.v4_0_0.CustomersMinimalJsonV400(List(customerMinimalJsonV400))
  
  lazy val postCustomerJsonV310 =
    PostCustomerJsonV310(
      legal_name = ExampleValue.legalNameExample.value,
      mobile_phone_number = ExampleValue.mobileNumberExample.value,
      email = ExampleValue.emailExample.value,
      face_image = customerFaceImageJson,
      date_of_birth = DateWithDayExampleObject,
      relationship_status = ExampleValue.relationshipStatusExample.value,
      dependants = ExampleValue.dependantsExample.value.toInt,
      dob_of_dependants = List(DateWithDayExampleObject),
      credit_rating = customerCreditRatingJSON,
      credit_limit = amountOfMoneyJsonV121,
      highest_education_attained = ExampleValue.highestEducationAttainedExample.value,
      employment_status = ExampleValue.employmentStatusExample.value,
      kyc_status = ExampleValue.kycStatusExample.value.toBoolean,
      last_ok_date = oneYearAgoDate,
      title  = ExampleValue.titleExample.value,
      branch_id = ExampleValue.branchIdExample.value,
      name_suffix = ExampleValue.nameSuffixExample.value
    )  
  lazy val postCustomerJsonV500 =
    PostCustomerJsonV500(
      legal_name = ExampleValue.legalNameExample.value,
      customer_number = Some(ExampleValue.customerNumberExample.value),
      mobile_phone_number = ExampleValue.mobilePhoneNumberExample.value,
      email = Some(ExampleValue.emailExample.value),
      face_image = Some(customerFaceImageJson),
      date_of_birth = Some(DateWithDayExampleObject),
      relationship_status = Some(ExampleValue.relationshipStatusExample.value),
      dependants = Some(ExampleValue.dependantsExample.value.toInt),
      dob_of_dependants = Some(List(DateWithDayExampleObject)),
      credit_rating = Some(customerCreditRatingJSON),
      credit_limit = Some(amountOfMoneyJsonV121),
      highest_education_attained = Some(ExampleValue.highestEducationAttainedExample.value),
      employment_status = Some(ExampleValue.employmentStatusExample.value),
      kyc_status = Some(ExampleValue.kycStatusExample.value.toBoolean),
      last_ok_date = Some(oneYearAgoDate),
      title  = Some(ExampleValue.titleExample.value),
      branch_id = Some(ExampleValue.branchIdExample.value),
      name_suffix = Some(ExampleValue.nameSuffixExample.value)
    )
  
  lazy val customerJsonV310 = CustomerJsonV310(
    bank_id = bankIdExample.value,
    customer_id = ExampleValue.customerIdExample.value,
    customer_number = ExampleValue.customerNumberExample.value,
    legal_name = ExampleValue.legalNameExample.value,
    mobile_phone_number = ExampleValue.mobileNumberExample.value,
    email = ExampleValue.emailExample.value,
    face_image = customerFaceImageJson,
    date_of_birth = DateWithDayExampleObject,
    relationship_status = ExampleValue.relationshipStatusExample.value,
    dependants = ExampleValue.dependantsExample.value.toInt,
    dob_of_dependants = List(DateWithDayExampleObject),
    credit_rating = Option(customerCreditRatingJSON),
    credit_limit = Option(amountOfMoneyJsonV121),
    highest_education_attained = ExampleValue.highestEducationAttainedExample.value,
    employment_status = ExampleValue.employmentStatusExample.value,
    kyc_status = ExampleValue.kycStatusExample.value.toBoolean,
    last_ok_date = oneYearAgoDate,
    title  = ExampleValue.titleExample.value,
    branch_id = ExampleValue.branchIdExample.value,
    name_suffix = ExampleValue.nameSuffixExample.value
  )

  lazy val customerAttributeResponseJson = CustomerAttributeResponseJsonV300 (
    customer_attribute_id = customerAttributeIdExample.value,
    name = customerAttributeNameExample.value,
    `type` = customerAttributeTypeExample.value,
    value = customerAttributeValueExample.value
  )

  lazy val accountAttributeResponseJson500 = AccountAttributeResponseJson500(
    product_code = productCodeExample.value,
    account_attribute_id = "613c83ea-80f9-4560-8404-b9cd4ec42a7f",
    name = "OVERDRAFT_START_DATE",
    `type` = "DATE_WITH_DAY",
    value = "2012-04-23",
    contract_code = Some("LKJL98769F"),
  )

  lazy val customerOverviewFlatJsonV500 = CustomerOverviewFlatJsonV500(
    bank_id = bankIdExample.value,
    customer_id = ExampleValue.customerIdExample.value,
    customer_number = ExampleValue.customerNumberExample.value,
    legal_name = ExampleValue.legalNameExample.value,
    mobile_phone_number = ExampleValue.mobileNumberExample.value,
    email = ExampleValue.emailExample.value,
    date_of_birth = DateWithDayExampleObject,
    title  = ExampleValue.titleExample.value,
    branch_id = ExampleValue.branchIdExample.value,
    name_suffix = ExampleValue.nameSuffixExample.value,
    customer_attributes = List(customerAttributeResponseJson),
    accounts = List(
      AccountResponseJson500(
        account_id = accountIdExample.value,
        label = labelExample.value,
        product_code = parentProductCodeExample.value,
        balance = amountOfMoneyJsonV121,
        branch_id = branchIdExample.value,
        contracts = Some(List(
          ContractJsonV500(product_code = parentProductCodeExample.value, contract_code = "LKJL98769F", payment_method = Some("cache")))
        ),
        account_routings = List(accountRoutingJsonV121),
        account_attributes = List(accountAttributeResponseJson500)
      )
    )
  )
  
  lazy val accountResponseJson500 = AccountResponseJson500(
    account_id = accountIdExample.value,
    label = labelExample.value,
    product_code = parentProductCodeExample.value,
    balance = amountOfMoneyJsonV121,
    branch_id = branchIdExample.value,
    contracts = None,
    account_routings = List(accountRoutingJsonV121),
    account_attributes = List(accountAttributeResponseJson500)
  )
  lazy val customerOverviewJsonV500 = CustomerOverviewJsonV500(
    bank_id = bankIdExample.value,
    customer_id = ExampleValue.customerIdExample.value,
    customer_number = ExampleValue.customerNumberExample.value,
    legal_name = ExampleValue.legalNameExample.value,
    mobile_phone_number = ExampleValue.mobileNumberExample.value,
    email = ExampleValue.emailExample.value,
    face_image = customerFaceImageJson,
    date_of_birth = DateWithDayExampleObject,
    relationship_status = ExampleValue.relationshipStatusExample.value,
    dependants = ExampleValue.dependantsExample.value.toInt,
    dob_of_dependants = List(DateWithDayExampleObject),
    credit_rating = Option(customerCreditRatingJSON),
    credit_limit = Option(amountOfMoneyJsonV121),
    highest_education_attained = ExampleValue.highestEducationAttainedExample.value,
    employment_status = ExampleValue.employmentStatusExample.value,
    kyc_status = ExampleValue.kycStatusExample.value.toBoolean,
    last_ok_date = oneYearAgoDate,
    title  = ExampleValue.titleExample.value,
    branch_id = ExampleValue.branchIdExample.value,
    name_suffix = ExampleValue.nameSuffixExample.value,
    customer_attributes = List(customerAttributeResponseJson),
    accounts = List(accountResponseJson500)
  )  
  
  lazy val customerWithAttributesJsonV310 = CustomerWithAttributesJsonV310(
    bank_id = bankIdExample.value,
    customer_id = ExampleValue.customerIdExample.value,
    customer_number = ExampleValue.customerNumberExample.value,
    legal_name = ExampleValue.legalNameExample.value,
    mobile_phone_number = ExampleValue.mobileNumberExample.value,
    email = ExampleValue.emailExample.value,
    face_image = customerFaceImageJson,
    date_of_birth = DateWithDayExampleObject,
    relationship_status = ExampleValue.relationshipStatusExample.value,
    dependants = ExampleValue.dependantsExample.value.toInt,
    dob_of_dependants = List(DateWithDayExampleObject),
    credit_rating = Option(customerCreditRatingJSON),
    credit_limit = Option(amountOfMoneyJsonV121),
    highest_education_attained = ExampleValue.highestEducationAttainedExample.value,
    employment_status = ExampleValue.employmentStatusExample.value,
    kyc_status = ExampleValue.kycStatusExample.value.toBoolean,
    last_ok_date = oneYearAgoDate,
    title  = ExampleValue.titleExample.value,
    branch_id = ExampleValue.branchIdExample.value,
    name_suffix = ExampleValue.nameSuffixExample.value,
    customer_attributes = List(customerAttributeResponseJson)
  )

  lazy val customerWithAttributesJsonV300 = CustomerWithAttributesJsonV300(
    bank_id = bankIdExample.value,
    customer_id = ExampleValue.customerIdExample.value,
    customer_number = ExampleValue.customerNumberExample.value,
    legal_name = ExampleValue.legalNameExample.value,
    mobile_phone_number = ExampleValue.mobileNumberExample.value,
    email = ExampleValue.emailExample.value,
    face_image = customerFaceImageJson,
    date_of_birth = "19900101",
    relationship_status = ExampleValue.relationshipStatusExample.value,
    dependants = ExampleValue.dependantsExample.value.toInt,
    dob_of_dependants = List("19900101"),
    credit_rating = Option(customerCreditRatingJSON),
    credit_limit = Option(amountOfMoneyJsonV121),
    highest_education_attained = ExampleValue.highestEducationAttainedExample.value,
    employment_status = ExampleValue.employmentStatusExample.value,
    kyc_status = ExampleValue.kycStatusExample.value.toBoolean,
    last_ok_date = oneYearAgoDate,
    title  = ExampleValue.titleExample.value,
    branch_id = ExampleValue.branchIdExample.value,
    name_suffix = ExampleValue.nameSuffixExample.value,
    customer_attributes = List(customerAttributeResponseJson)
  )

  lazy val customersWithAttributesJsonV300 = CustomersWithAttributesJsonV300(List(customerWithAttributesJsonV300))

  lazy val putUpdateCustomerDataJsonV310 = PutUpdateCustomerDataJsonV310(
    face_image = customerFaceImageJson,
    relationship_status = ExampleValue.relationshipStatusExample.value,
    dependants = ExampleValue.dependantsExample.value.toInt,
    highest_education_attained = ExampleValue.highestEducationAttainedExample.value,
    employment_status = ExampleValue.employmentStatusExample.value
  )

  lazy val putCustomerBranchJsonV310 = PutUpdateCustomerBranchJsonV310(branch_id = "123")
  lazy val postCustomerNumberJsonV310 = PostCustomerNumberJsonV310(customer_number = ExampleValue.customerNumberExample.value)
  lazy val postCustomerLegalNameJsonV510 = PostCustomerLegalNameJsonV510(legal_name = ExampleValue.legalNameExample.value)
  lazy val postCustomerPhoneNumberJsonV400 = PostCustomerPhoneNumberJsonV400(mobile_phone_number = ExampleValue.mobileNumberExample.value)
  lazy val putUpdateCustomerEmailJsonV310 = PutUpdateCustomerEmailJsonV310("marko@tesobe.com")
  lazy val putUpdateCustomerNumberJsonV310 = PutUpdateCustomerNumberJsonV310(customerNumberExample.value)
  lazy val putUpdateCustomerMobileNumberJsonV310 = PutUpdateCustomerMobilePhoneNumberJsonV310("+381631954907")
  lazy val putUpdateCustomerCreditLimitJsonV310 = PutUpdateCustomerCreditLimitJsonV310(AmountOfMoney("EUR", "1000"))
  lazy val putUpdateCustomerCreditRatingAndSourceJsonV310 = PutUpdateCustomerCreditRatingAndSourceJsonV310("Good", "Bank")
  lazy val putUpdateCustomerIdentityJsonV310 = PutUpdateCustomerIdentityJsonV310(
    legal_name = ExampleValue.legalNameExample.value,
    date_of_birth = DateWithDayExampleObject,
    title  = ExampleValue.titleExample.value,
    name_suffix = ExampleValue.nameSuffixExample.value)

  lazy val taxResidenceV310 = TaxResidenceV310(domain = "Enter some domain", tax_number = "Enter some number", tax_residence_id = "902ba3bb-dedd-45e7-9319-2fd3f2cd98a1")
  lazy val postTaxResidenceJsonV310 = PostTaxResidenceJsonV310(domain = "Enter some domain", tax_number = "Enter some number")
  lazy val taxResidencesJsonV310 = TaxResidenceJsonV310(tax_residence = List(taxResidenceV310))
  lazy val postCustomerOverviewJsonV500 = PostCustomerOverviewJsonV500(customer_number = ExampleValue.customerNumberExample.value)


  lazy val transactionRequestWithChargeJSON210 = TransactionRequestWithChargeJSON210(
    id = "4050046c-63b3-4868-8a22-14b4181d33a6",
    `type` = SANDBOX_TAN.toString,
    from = transactionRequestAccountJsonV140,
    details = transactionRequestBodyAllTypes,
    transaction_ids = List("902ba3bb-dedd-45e7-9319-2fd3f2cd98a1"),
    status = TransactionRequestStatus.COMPLETED.toString,
    start_date = DateWithDayExampleObject,
    end_date = DateWithDayExampleObject,
    challenge = challengeJsonV140,
    charge = transactionRequestChargeJsonV200
  )

  lazy val transactionRequestWithChargeJSONs210 =
    TransactionRequestWithChargeJSONs210(
      transaction_requests_with_charges = List(
        transactionRequestWithChargeJSON210
      )
    )

  lazy val availableRolesJSON = AvailableRolesJSON(
    roles = List(availableRoleJSON)
  )

  lazy val consumerJSON = ConsumerJsonV210(
    consumer_id = 1213,
    app_name = "SOFI",
    app_type = "Web",
    description = "Account Management",
    developer_email = ExampleValue.emailExample.value,
    redirect_url = "www.openbankproject.com",
    created_by_user_id = ExampleValue.userIdExample.value,
    created_by_user = resourceUserJSON,
    enabled = true,
    created = DateWithDayExampleObject
  )
  lazy val pem = "-----BEGIN CERTIFICATE-----\nMIIFIjCCBAqgAwIBAgIIX3qsz7QQxngwDQYJKoZIhvcNAQELBQAwgZ8xCzAJBgNV\r\nBAYTAkRFMQ8wDQYDVQQIEwZCZXJsaW4xDzANBgNVBAcTBkJlcmxpbjEPMA0GA1UE\r\nChMGVEVTT0JFMRowGAYDVQQLExFURVNPQkUgT3BlcmF0aW9uczESMBAGA1UEAxMJ\r\nVEVTT0JFIENBMR8wHQYJKoZIhvcNAQkBFhBhZG1pbkB0ZXNvYmUuY29tMQwwCgYD\r\nVQQpEwNWUE4wHhcNMjMwNzE3MDg0MDAwWhcNMjQwNzE3MDg0MDAwWjCBizELMAkG\r\nA1UEBhMCREUxDzANBgNVBAgTBkJlcmxpbjEPMA0GA1UEBxMGQmVybGluMRQwEgYD\r\nVQQKEwtUZXNvYmUgR21iSDEPMA0GA1UECxMGc3lzb3BzMRIwEAYDVQQDEwlsb2Nh\r\nbGhvc3QxHzAdBgkqhkiG9w0BCQEWEGFkbWluQHRlc29iZS5jb20wggEiMA0GCSqG\r\nSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCwxGuWUN1H0d0IeYPYWdLA0I/5BXx4DLO6\r\nzfi1GGJlF8BIXRN0VTJckIY9C3J1RnXDs6p6ufA01iHe1PQdL6VzfcaC3j+jUSgV\r\n1z9ybEUPyUwq3PCCxqoVI9n8yh+O6FDn3dvu/9Q2NtBpJHUBDCLf7OO9TgsFU2sE\r\nMys+Hw5DuuX5n5OQ2VIwH+qlMTQnd+yw5y8FKHqAZT5hE60lF/x6sQnwi58hLGRW\r\nSqo/548c2ZpoeWtnyY1I6PyR7zUYGuhruLY8gVFfLE+610u/lj2wYTXMxntpV+tV\r\nralLFRMhvbqZXW/EpuDb/pEbCnLDNDxq5NarLVDzcHs7VhT9MPChAgMBAAGjggFy\r\nMIIBbjATBgNVHSUEDDAKBggrBgEFBQcDAjAaBgNVHREEEzARgglsb2NhbGhvc3SH\r\nBH8AAAEwggEGBggrBgEFBQcBAwSB+TCB9jAIBgYEAI5GAQEwOAYGBACORgEFMC4w\r\nLBYhaHR0cHM6Ly9leGFtcGxlLm9yZy9wa2lkaXNjbG9zdXJlEwdleGFtcGxlMIGI\r\nBgYEAIGYJwIwfjBMMBEGBwQAgZgnAQMMBlBTUF9BSTARBgcEAIGYJwEBDAZQU1Bf\r\nQVMwEQYHBACBmCcBAgwGUFNQX1BJMBEGBwQAgZgnAQQMBlBTUF9JQwwlRHVtbXkg\r\nRmluYW5jaWFsIFN1cGVydmlzaW9uIEF1dGhvcml0eQwHWFgtREZTQTAlBgYEAI5G\r\nAQYwGwYHBACORgEGAQYHBACORgEGAgYHBACORgEGAzARBglghkgBhvhCAQEEBAMC\r\nB4AwHgYJYIZIAYb4QgENBBEWD3hjYSBjZXJ0aWZpY2F0ZTANBgkqhkiG9w0BAQsF\r\nAAOCAQEAKTS7exS9A7rWJLRzWrlHoTu68Avm5g9Dz1GKjgt8rnvj3D21SE14Rf5p\r\n0JWHYH4SiCdnh8Tx+IA7o0TmPJ1JRfAXR3i/5R7TJi/HrnqL+V7SIx2Cuq/hkZEU\r\nAhVs07nnvHURcrlQGwcfn4TbgpCURpCPpYZlNsYySb6BS6I4qFaadHGqMTyEkphV\r\nwfXyB3brmzxj9V4Qgp0t+s/uFuFirWyIayRc9nSSC7vuNVYvib2Kim4y8kvuWpA4\r\nZ51+fFOmBqCqpmwfAADNgDsLJiA/741eBflVd/ZUeAzgOjMCMIaDGlwiwZlePKT7\r\n553GtfsGxZMf05oqfUrQEQfJaU+/+Q==\n-----END CERTIFICATE-----\n"
  lazy val certificateInfoJsonV510 = CertificateInfoJsonV510(
    subject_domain_name = "OID.2.5.4.41=VPN, EMAILADDRESS=admin@tesobe.com, CN=TESOBE CA, OU=TESOBE Operations, O=TESOBE, L=Berlin, ST=Berlin, C=DE",
    issuer_domain_name = "CN=localhost, O=TESOBE GmbH, ST=Berlin, C=DE",
    not_before = "2022-04-01T10:13:00.000Z",
    not_after = "2032-04-01T10:13:00.000Z",
    roles = None,
    roles_info = Some("PEM Encoded Certificate does not contain PSD2 roles.")
  )
  
  lazy val consumerJsonV510: ConsumerJsonV510 = ConsumerJsonV510(
    consumer_id = consumerIdExample.value,
    consumer_key = consumerKeyExample.value,
    app_name = appNameExample.value,
    app_type = appTypeExample.value,
    description = descriptionExample.value,
    developer_email = emailExample.value,
    company = companyExample.value,
    redirect_url = redirectUrlExample.value,
    certificate_pem = pem,
    certificate_info = Some(certificateInfoJsonV510),
    created_by_user = resourceUserJSON,
    enabled = true,
    created = DateWithDayExampleObject,
    logo_url = Some(logoURLExample.value)
  )
  lazy val consumerJsonOnlyForPostResponseV510: ConsumerJsonOnlyForPostResponseV510 = ConsumerJsonOnlyForPostResponseV510(
    consumer_id = consumerIdExample.value,
    consumer_key = consumerKeyExample.value,
    consumer_secret = consumerSecretExample.value,
    app_name = appNameExample.value,
    app_type = appTypeExample.value,
    description = descriptionExample.value,
    developer_email = emailExample.value,
    company = companyExample.value,
    redirect_url = redirectUrlExample.value,
    certificate_pem = pem,
    certificate_info = Some(certificateInfoJsonV510),
    created_by_user = resourceUserJSON,
    enabled = true,
    created = DateWithDayExampleObject,
    logo_url = Some(logoURLExample.value)
  )
  
  lazy val createConsumerRequestJsonV510 = CreateConsumerRequestJsonV510(
    appNameExample.value,
    appTypeExample.value,
    descriptionExample.value,
    emailExample.value,
    companyExample.value,
    redirectUrlExample.value,
    true,
    Some("-----BEGIN CERTIFICATE-----MIICsjCCAZqgAwIBAgIGAYwQ62R0MA0GCSqGSIb3DQEBCwUAMBoxGDAWBgNVBAMMD2FwcC5leGFtcGxlLmNvbTAeFw0yMzExMjcxMzE1MTFaFw0yNTExMjYxMzE1MTFaMBoxGDAWBgNVBAMMD2FwcC5leGFtcGxlLmNvbTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAK9WIodZHWzKyCcf9YfWEhPURbfO6zKuMqzHN27GdqHsVVEGxP4F/J4mso+0ENcRr6ur4u81iREaVdCc40rHDHVJNEtniD8Icbz7tcsqAewIVhc/q6WXGqImJpCq7hA0m247dDsaZT0lb/MVBiMoJxDEmAE/GYYnWTEn84R35WhJsMvuQ7QmLvNg6RkChY6POCT/YKe9NKwa1NqI1U+oA5RFzAaFtytvZCE3jtp+aR0brL7qaGfgxm6B7dEpGyhg0NcVCV7xMQNq2JxZTVdAr6lcsRGaAFulakmW3aNnmK+L35Wu8uW+OxNxwUuC6f3b4FVBa276FMuUTRfu7gc+k6kCAwEAATANBgkqhkiG9w0BAQsFAAOCAQEAAU5CjEyAoyTn7PgFpQD48ZNPuUsEQ19gzYgJvHMzFIoZ7jKBodjO5mCzWBcR7A4mpeAsdyiNBl2sTiZscSnNqxk61jVzP5Ba1D7XtOjjr7+3iqowrThj6BY40QqhYh/6BSY9fDzVZQiHnvlo6ZUM5kUK6OavZOovKlp5DIl5sGqoP0qAJnpQ4nhB2WVVsKfPlOXc+2KSsbJ23g9l8zaTMr+X0umlvfEKqyEl1Fa2L1dO0y/KFQ+ILmxcZLpRdq1hRAjd0quq9qGC8ucXhRWDgM4hslVpau0da68g0aItWNez3mc5lB82b3dcZpFMzO41bgw7gvw10AvvTfQDqEYIuQ==-----END CERTIFICATE-----"),
    Some(logoURLExample.value)
  )

  lazy val consumersJson = ConsumersJson(
    list = List(consumerJSON)
  )

  lazy val consumerJsonV310 = ConsumerJsonV310(
    consumer_id = "8e716299-4668-4efd-976a-67f57a9984ec",
    app_name = "SOFI",
    app_type = "Web",
    description = "Account Management",
    developer_email = ExampleValue.emailExample.value,
    redirect_url = "www.openbankproject.com",
    created_by_user = resourceUserJSON,
    enabled = true,
    created = DateWithDayExampleObject
  )
  
  lazy val consumerJsonV400 = ConsumerJson(
    consumer_id = ExampleValue.consumerIdExample.value,
    key = ExampleValue.consumerSecretExample.value,
    secret = ExampleValue.consumerKeyExample.value,
    app_name = "SOFI",
    app_type = "Web",
    description = "Account Management",
    client_certificate = """-----BEGIN CERTIFICATE-----
                           |client_certificate_content
                           |-----END CERTIFICATE-----""".stripMargin,
    developer_email = ExampleValue.emailExample.value,
    redirect_url = "www.openbankproject.com",
    created_by_user_id = ExampleValue.userIdExample.value,
    created_by_user = resourceUserJSON,
    enabled = true,
    created = DateWithDayExampleObject
  )
  
  lazy val consumersJson310 = ConsumersJsonV310(
    List(consumerJsonV310)
  )

  lazy val putEnabledJSON = PutEnabledJSON(
    enabled = false
  )

  lazy val productJsonV210 = ProductJsonV210(
    bank_id = "bankid123",
    code = "prod1",
    name = "product name",
    category = "category",
    family = "family",
    super_family = "super family",
    more_info_url = "www.example.com/prod1/more-info.html",
    details = "Details",
    description = "Description",
    meta = metaJson
  )

  lazy val productsJsonV210 = ProductsJsonV210(products = List(productJsonV210))




  lazy val grandparentProductTreeJsonV310 = ProductTreeJsonV310(
    bank_id="testBank2",
    code="GRANDPARENT_CODE",
    name="product name",
    category="category",
    family="family",
    super_family="super family",
    more_info_url="www.example.com/prod1/more-info.html",
    details="Details",
    description="Description",
    meta = metaJson,
    parent_product=None
  )
  lazy val parentProductTreeJsonV310 = ProductTreeJsonV310(
    bank_id="testBank2",
    code="PARENT_CODE",
    name="product name",
    category="category",
    family="family",
    super_family="super family",
    more_info_url="www.example.com/prod1/more-info.html",
    details="Details",
    description="Description",
    meta = metaJson,
    parent_product=Some(grandparentProductTreeJsonV310)
  )
  lazy val childProductTreeJsonV310 = ProductTreeJsonV310(
    bank_id="testBank2",
    code="PRODUCT_CODE",
    name="product name",
    category="category",
    family="family",
    super_family="super family",
    more_info_url="www.example.com/prod1/more-info.html",
    details="Details",
    description="Description",
    meta = metaJson,
    parent_product=Some(parentProductTreeJsonV310)
  )
  
  
  lazy val postCounterpartyBespokeJson = PostCounterpartyBespokeJson(
    key = "englishName",
    value = "english Name"
  )
  
  lazy val postCounterpartyJSON = PostCounterpartyJSON(
    name = "CounterpartyName",
    description ="My landlord",
    other_account_routing_scheme = counterpartyOtherAccountRoutingSchemeExample.value,
    other_account_routing_address = counterpartyOtherAccountRoutingAddressExample.value,
    other_account_secondary_routing_scheme = counterpartyOtherAccountSecondaryRoutingSchemeExample.value,
    other_account_secondary_routing_address = counterpartyOtherAccountSecondaryRoutingAddressExample.value,
    other_bank_routing_scheme = counterpartyOtherBankRoutingSchemeExample.value,
    other_bank_routing_address = counterpartyOtherBankRoutingAddressExample.value,
    other_branch_routing_scheme = counterpartyOtherBranchRoutingSchemeExample.value,
    other_branch_routing_address = counterpartyOtherBranchRoutingAddressExample.value, 
    is_beneficiary = true,
    bespoke =  List(postCounterpartyBespokeJson)
  )

  lazy val postCounterpartyJson400 = PostCounterpartyJson400(
    name = "CounterpartyName",
    description ="My landlord",
    currency = currencyExample.value,
    other_account_routing_scheme = counterpartyOtherAccountRoutingSchemeExample.value,
    other_account_routing_address = counterpartyOtherAccountRoutingAddressExample.value,
    other_account_secondary_routing_scheme = counterpartyOtherAccountSecondaryRoutingSchemeExample.value,
    other_account_secondary_routing_address = counterpartyOtherAccountSecondaryRoutingAddressExample.value,
    other_bank_routing_scheme = counterpartyOtherBankRoutingSchemeExample.value,
    other_bank_routing_address = counterpartyOtherBankRoutingAddressExample.value,
    other_branch_routing_scheme = counterpartyOtherBranchRoutingSchemeExample.value,
    other_branch_routing_address = counterpartyOtherBranchRoutingAddressExample.value,
    is_beneficiary = true,
    bespoke =  List(postCounterpartyBespokeJson)
  )

  lazy val dynamicEndpointHostJson400 = DynamicEndpointHostJson400(
    host = "dynamic_entity"
  )

  lazy val endpointTagJson400 = EndpointTagJson400(
    tag_name = tagNameExample.value
  )
  
  lazy val systemLevelEndpointTagResponseJson400 = SystemLevelEndpointTagResponseJson400(
    endpoint_tag_id = endpointTagIdExample.value,
    operation_id = operationIdExample.value,
    tag_name = tagNameExample.value
  )
  
  lazy val bankLevelEndpointTagResponseJson400 = BankLevelEndpointTagResponseJson400(
    bank_id = bankIdExample.value,
    endpoint_tag_id = endpointTagIdExample.value,
    operation_id = operationIdExample.value,
    tag_name = tagNameExample.value
  )
  
  lazy val mySpaces = MySpaces(
    bank_ids = List(bankIdExample.value),
  )
  
  lazy val metricsJson = MetricsJson(
    metrics = List(metricJson)
  )
  lazy val metricsJsonV510 = MetricsJsonV510(
    metrics = List(metricJson510)
  )

  lazy val branchJsonPut = BranchJsonPutV210("gh.29.fi", "OBP",
    addressJsonV140,
    locationJson,
    metaJson,
    lobbyJson,
    driveUpJson
  )

  lazy val branchJsonPost = BranchJsonPostV210("123", "gh.29.fi", "OBP",
    addressJsonV140,
    locationJson,
    metaJson,
    lobbyJson,
    driveUpJson
  )

  lazy val consumerRedirectUrlJSON = ConsumerRedirectUrlJSON(
    "http://localhost:8888"
  )

  //V220
  ///////////////////////////////////////////////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////
  import code.api.v2_2_0._

  lazy val viewJSONV220 = ViewJSONV220(
    id = "1234",
    short_name = "short_name",
    description = "description",
    is_public = true,
    alias = "No",
    hide_metadata_if_alias_used = true,
    can_add_comment = true,
    can_add_corporate_location = true,
    can_add_image = true,
    can_add_image_url = true,
    can_add_more_info = true,
    can_add_open_corporates_url = true,
    can_add_physical_location = true,
    can_add_private_alias = true,
    can_add_public_alias = true,
    can_add_tag = true,
    can_add_url = true,
    can_add_where_tag = true,
    can_delete_comment = true,
    can_add_counterparty = true,
    can_delete_corporate_location = true,
    can_delete_image = true,
    can_delete_physical_location = true,
    can_delete_tag = true,
    can_delete_where_tag = true,
    can_edit_owner_comment = true,
    can_see_bank_account_balance = true,
    can_see_bank_account_bank_name = true,
    can_see_bank_account_currency = true,
    can_see_bank_account_iban = true,
    can_see_bank_account_label = true,
    can_see_bank_account_national_identifier = true,
    can_see_bank_account_number = true,
    can_see_bank_account_owners = true,
    can_see_bank_account_swift_bic = true,
    can_see_bank_account_type = true,
    can_see_comments = true,
    can_see_corporate_location = true,
    can_see_image_url = true,
    can_see_images = true,
    can_see_more_info = true,
    can_see_open_corporates_url = true,
    can_see_other_account_bank_name = true,
    can_see_other_account_iban = true,
    can_see_other_account_kind = true,
    can_see_other_account_metadata = true,
    can_see_other_account_national_identifier = true,
    can_see_other_account_number = true,
    can_see_other_account_swift_bic = true,
    can_see_owner_comment = true,
    can_see_physical_location = true,
    can_see_private_alias = true,
    can_see_public_alias = true,
    can_see_tags = true,
    can_see_transaction_amount = true,
    can_see_transaction_balance = true,
    can_see_transaction_currency = true,
    can_see_transaction_description = true,
    can_see_transaction_finish_date = true,
    can_see_transaction_metadata = true,
    can_see_transaction_other_bank_account = true,
    can_see_transaction_start_date = true,
    can_see_transaction_this_bank_account = true,
    can_see_transaction_type = true,
    can_see_url = true,
    can_see_where_tag = true
  )

  lazy val viewsJSONV220 = ViewsJSONV220(
    views = List(viewJSONV220)
  )


  lazy val viewJsonV500 = ViewJsonV500(
    id = "1234",
    short_name = "short_name",
    description = "description",
    metadata_view = SYSTEM_OWNER_VIEW_ID,
    is_public = true,
    is_system = true,
    alias = "No",
    hide_metadata_if_alias_used = true,
    can_add_comment = true,
    can_add_corporate_location = true,
    can_add_image = true,
    can_add_image_url = true,
    can_add_more_info = true,
    can_add_open_corporates_url = true,
    can_add_physical_location = true,
    can_add_private_alias = true,
    can_add_public_alias = true,
    can_add_tag = true,
    can_add_url = true,
    can_add_where_tag = true,
    can_delete_comment = true,
    can_add_counterparty = true,
    can_delete_corporate_location = true,
    can_delete_image = true,
    can_delete_physical_location = true,
    can_delete_tag = true,
    can_delete_where_tag = true,
    can_edit_owner_comment = true,
    can_see_bank_account_balance = true,
    can_query_available_funds = true,
    can_see_bank_account_bank_name = true,
    can_see_bank_account_currency = true,
    can_see_bank_account_iban = true,
    can_see_bank_account_label = true,
    can_see_bank_account_national_identifier = true,
    can_see_bank_account_number = true,
    can_see_bank_account_owners = true,
    can_see_bank_account_swift_bic = true,
    can_see_bank_account_type = true,
    can_see_comments = true,
    can_see_corporate_location = true,
    can_see_image_url = true,
    can_see_images = true,
    can_see_more_info = true,
    can_see_open_corporates_url = true,
    can_see_other_account_bank_name = true,
    can_see_other_account_iban = true,
    can_see_other_account_kind = true,
    can_see_other_account_metadata = true,
    can_see_other_account_national_identifier = true,
    can_see_other_account_number = true,
    can_see_other_account_swift_bic = true,
    can_see_owner_comment = true,
    can_see_physical_location = true,
    can_see_private_alias = true,
    can_see_public_alias = true,
    can_see_tags = true,
    can_see_transaction_amount = true,
    can_see_transaction_balance = true,
    can_see_transaction_currency = true,
    can_see_transaction_description = true,
    can_see_transaction_finish_date = true,
    can_see_transaction_metadata = true,
    can_see_transaction_other_bank_account = true,
    can_see_transaction_start_date = true,
    can_see_transaction_this_bank_account = true,
    can_see_transaction_type = true,
    can_see_url = true,
    can_see_where_tag = true,
    //V300 new 
    can_see_bank_routing_scheme = true,
    can_see_bank_routing_address = true,
    can_see_bank_account_routing_scheme = true,
    can_see_bank_account_routing_address = true,
    can_see_other_bank_routing_scheme = true,
    can_see_other_bank_routing_address = true,
    can_see_other_account_routing_scheme = true,
    can_see_other_account_routing_address = true,
    can_add_transaction_request_to_own_account = true, //added following two for payments
    can_add_transaction_request_to_any_account = true,
    can_see_bank_account_credit_limit = true,
    can_create_direct_debit = true,
    can_create_standing_order = true,
    can_grant_access_to_views = List(Constant.SYSTEM_OWNER_VIEW_ID),
    can_revoke_access_to_views = List(Constant.SYSTEM_OWNER_VIEW_ID)
  )

  lazy val viewsJsonV500 = ViewsJsonV500(
    views = List(viewJsonV500)
  )

  lazy val viewIdJsonV500 = ViewIdJsonV500(id = Constant.SYSTEM_OWNER_VIEW_ID)
  lazy val viewIdsJsonV500 = ViewsIdsJsonV500(
    views = List(viewIdJsonV500)
  )

  lazy val fXRateJSON = FXRateJsonV220(
    bank_id = bankIdExample.value,
    from_currency_code = "EUR",
    to_currency_code = "GBP",
    conversion_value = 1.001,
    inverse_conversion_value = 0.998,
    effective_date = DateWithDayExampleObject
  )
  
  lazy val currenciesJsonV510 = CurrenciesJsonV510(currencies = List(CurrencyJsonV510(alphanumeric_code = "EUR")))

  lazy val counterpartyJsonV220 = CounterpartyJsonV220(
    name = postCounterpartyJSON.name,
    description = postCounterpartyJSON.description,
    created_by_user_id = ExampleValue.userIdExample.value,
    this_bank_id = bankIdExample.value,
    this_account_id =accountIdExample.value,
    this_view_id = SYSTEM_OWNER_VIEW_ID,
    counterparty_id = counterpartyIdExample.value,
    other_bank_routing_scheme = postCounterpartyJSON.other_bank_routing_scheme,
    other_bank_routing_address = postCounterpartyJSON.other_bank_routing_address,
    other_branch_routing_scheme = postCounterpartyJSON.other_branch_routing_scheme,
    other_branch_routing_address = postCounterpartyJSON.other_branch_routing_address,
    other_account_routing_scheme = postCounterpartyJSON.other_account_routing_scheme,
    other_account_routing_address = postCounterpartyJSON.other_account_routing_address,
    is_beneficiary = true,
    other_account_secondary_routing_scheme = counterpartyOtherAccountSecondaryRoutingSchemeExample.value,
    other_account_secondary_routing_address= counterpartyOtherAccountSecondaryRoutingAddressExample.value,
    bespoke = postCounterpartyJSON.bespoke
  )

  lazy val counterpartyJson400 = CounterpartyJson400(
    name = postCounterpartyJson400.name,
    description = postCounterpartyJson400.description,
    currency = postCounterpartyJson400.currency,
    created_by_user_id = ExampleValue.userIdExample.value,
    this_bank_id = bankIdExample.value,
    this_account_id =accountIdExample.value,
    this_view_id = SYSTEM_OWNER_VIEW_ID,
    counterparty_id = counterpartyIdExample.value,
    other_bank_routing_scheme = postCounterpartyJson400.other_bank_routing_scheme,
    other_bank_routing_address = postCounterpartyJson400.other_bank_routing_address,
    other_branch_routing_scheme = postCounterpartyJson400.other_branch_routing_scheme,
    other_branch_routing_address = postCounterpartyJson400.other_branch_routing_address,
    other_account_routing_scheme = postCounterpartyJson400.other_account_routing_scheme,
    other_account_routing_address = postCounterpartyJson400.other_account_routing_address,
    is_beneficiary = true,
    other_account_secondary_routing_scheme = counterpartyOtherAccountSecondaryRoutingSchemeExample.value,
    other_account_secondary_routing_address= counterpartyOtherAccountSecondaryRoutingAddressExample.value,
    bespoke = postCounterpartyJson400.bespoke
  )
  
  lazy val counterpartyMetadataJson = CounterpartyMetadataJson(
    public_alias = "String",
    more_info = "String",
    url = "String",
    image_url = "String",
    open_corporates_url = "String",
    corporate_location = locationJsonV210,
    physical_location = locationJsonV210,
    private_alias ="String"
  )
  
  lazy val counterpartyWithMetadataJson = CounterpartyWithMetadataJson(
    name = postCounterpartyJSON.name,
    description = postCounterpartyJSON.description,
    created_by_user_id = ExampleValue.userIdExample.value,
    this_bank_id = bankIdExample.value,
    this_account_id =accountIdExample.value,
    this_view_id = SYSTEM_OWNER_VIEW_ID,
    counterparty_id = counterpartyIdExample.value,
    other_bank_routing_scheme = postCounterpartyJSON.other_bank_routing_scheme,
    other_bank_routing_address = postCounterpartyJSON.other_bank_routing_address,
    other_branch_routing_scheme = postCounterpartyJSON.other_branch_routing_scheme,
    other_branch_routing_address = postCounterpartyJSON.other_branch_routing_address,
    other_account_routing_scheme = postCounterpartyJSON.other_account_routing_scheme,
    other_account_routing_address = postCounterpartyJSON.other_account_routing_address,
    is_beneficiary = true,
    other_account_secondary_routing_scheme = counterpartyOtherAccountSecondaryRoutingSchemeExample.value,
    other_account_secondary_routing_address= counterpartyOtherAccountSecondaryRoutingAddressExample.value,
    bespoke = postCounterpartyJSON.bespoke,
    metadata = counterpartyMetadataJson
  )

  lazy val counterpartyWithMetadataJson400 = CounterpartyWithMetadataJson400(
    name = postCounterpartyJson400.name,
    description = postCounterpartyJson400.description,
    currency = postCounterpartyJson400.currency,
    created_by_user_id = ExampleValue.userIdExample.value,
    this_bank_id = bankIdExample.value,
    this_account_id =accountIdExample.value,
    this_view_id = SYSTEM_OWNER_VIEW_ID,
    counterparty_id = counterpartyIdExample.value,
    other_bank_routing_scheme = postCounterpartyJson400.other_bank_routing_scheme,
    other_bank_routing_address = postCounterpartyJson400.other_bank_routing_address,
    other_branch_routing_scheme = postCounterpartyJson400.other_branch_routing_scheme,
    other_branch_routing_address = postCounterpartyJson400.other_branch_routing_address,
    other_account_routing_scheme = postCounterpartyJson400.other_account_routing_scheme,
    other_account_routing_address = postCounterpartyJson400.other_account_routing_address,
    is_beneficiary = true,
    other_account_secondary_routing_scheme = counterpartyOtherAccountSecondaryRoutingSchemeExample.value,
    other_account_secondary_routing_address= counterpartyOtherAccountSecondaryRoutingAddressExample.value,
    bespoke = postCounterpartyJson400.bespoke,
    metadata = counterpartyMetadataJson
  )

  lazy val counterpartiesJsonV220 = CounterpartiesJsonV220(
    counterparties = List(counterpartyJsonV220)
  )

  lazy val counterpartiesJson400 = CounterpartiesJson400(
    counterparties = List(counterpartyJson400)
  )

  lazy val bankJSONV220 = BankJSONV220(
    id = "gh.29.uk.x",
    full_name = "uk",
    short_name = "uk",
    logo_url = "https://static.openbankproject.com/images/sandbox/bank_x.png",
    website_url = "https://www.example.com",
    swift_bic = "IIIGGB22",
    national_identifier = "UK97ZZZ1234567890",
    bank_routing = BankRoutingJsonV121(
      scheme = "BIC",
      address = "OKOYFIHH"
    )
  )

  lazy val branchJsonV220 = BranchJsonV220(
    id = "123",
    bank_id = bankIdExample.value,
    name = "OBP",
    address = addressJsonV140,
    location = locationJson,
    meta = metaJson,
    lobby = lobbyJson,
    drive_up = driveUpJson,
    branch_routing = branchRoutingJsonV141
  )


  lazy val atmJsonV220 = AtmJsonV220(
    id = "123",
    bank_id = bankIdExample.value,
    name = "OBP",
    address = addressJsonV140,
    location = locationJson,
    meta = metaJson
  )

  lazy val productJsonV220 = ProductJsonV220(
    bank_id = bankIdExample.value,
    code = "prod1",
    name = "product name",
    category = "category",
    family = "family",
    super_family = "super family",
    more_info_url = "www.example.com/prod1/more-info.html",
    details = "Details",
    description = "Description",
    meta = metaJson
  )
  lazy val postPutProductJsonV310 = PostPutProductJsonV310(
    name = "product name",
    parent_product_code = "parent product name",
    category = "category",
    family = "family",
    super_family = "super family",
    more_info_url = "www.example.com/prod1/more-info.html",
    details = "Details",
    description = "Description",
    meta = metaJson
  )

  lazy val putProductCollectionsV310 = PutProductCollectionsV310("A", List("B", "C", "D"))

  lazy val postOrPutJsonSchemaV400 = JsonSchemaV400(
    "http://json-schema.org/draft-07/schema",
    "The demo json-schema",
    "The demo schema",
    List("xxx_id"),
    "object",
    Properties(XxxId("string", 2, 50,List("xxx_id_demo_value"))),
    true
  )
  lazy val responseJsonSchema = JsonValidationV400("OBPv4.0.0-createXxx", postOrPutJsonSchemaV400)


  lazy val fxJsonV220 = FXRateJsonV220(
    bank_id = bankIdExample.value,
    from_currency_code = "EUR",
    to_currency_code = "USD",
    conversion_value = 1.136305,
    inverse_conversion_value = 0.8800454103431737,
    effective_date = DateWithDayExampleObject
  )



  lazy val createAccountJSONV220 = CreateAccountJSONV220(
    user_id = userIdExample.value,
    label = "Label",
    `type` = "CURRENT",
    balance = AmountOfMoneyJsonV121(
      "EUR",
      "0"
    ),
    branch_id = "1234",
    account_routing = AccountRoutingJsonV121(
      scheme = "OBP",
      address = "UK123456"
    )
  )
  
  lazy val cachedFunctionJSON = CachedFunctionJSON(
    function_name = "getBanks",
    ttl_in_seconds = 5
  )
  lazy val portJSON = PortJSON(
    property = "default",
    value = "8080"
  )
  lazy val akkaJSON = AkkaJSON(
    ports = List(portJSON),
    log_level = "Debug",
    remote_data_secret_matched = Some(true)
  )
  lazy val metricsJSON = MetricsJsonV220(
    property = "String",
    value = "Mapper"
  )
  lazy val warehouseJSON = WarehouseJSON(
    property = "String",
    value = "ElasticSearch"
  )
  lazy val elasticSearchJSON = ElasticSearchJSON(
    metrics = List(metricsJSON),
    warehouse = List(warehouseJSON)
  )
  
  lazy val scopesJSON = ScopesJSON(
    require_scopes_for_all_roles = true, 
    require_scopes_for_listed_roles = List(CanCreateUserAuthContextUpdate.toString())
  )
  
  lazy val configurationJSON = ConfigurationJSON(
    akka = akkaJSON,
    elastic_search = elasticSearchJSON,
    cache = List(cachedFunctionJSON),
    scopesJSON
  )
  
  lazy val connectorMetricJson = ConnectorMetricJson(
    connector_name = "mapper",
    function_name = "getBanks",
    correlation_id = "12345",
    date = DateWithDayExampleObject,
    duration = 1000
  )
  
  lazy val connectorMetricsJson = ConnectorMetricsJson(
    metrics = List(connectorMetricJson)
  )
  
  //V300
  ///////////////////////////////////////////////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////
  import code.api.v3_0_0._

  lazy val viewJsonV300 =  ViewJsonV300(
    id = "1234",
    short_name = "short_name",
    description = "description",
    metadata_view = SYSTEM_OWNER_VIEW_ID,
    is_public = true,
    is_system = true,
    alias = "No",
    hide_metadata_if_alias_used = true,
    can_add_comment = true,
    can_add_corporate_location = true,
    can_add_image = true,
    can_add_image_url = true,
    can_add_more_info = true,
    can_add_open_corporates_url = true,
    can_add_physical_location = true,
    can_add_private_alias = true,
    can_add_public_alias = true,
    can_add_tag = true,
    can_add_url = true,
    can_add_where_tag = true,
    can_delete_comment = true,
    can_add_counterparty = true,
    can_delete_corporate_location = true,
    can_delete_image = true,
    can_delete_physical_location = true,
    can_delete_tag = true,
    can_delete_where_tag = true,
    can_edit_owner_comment = true,
    can_see_bank_account_balance = true,
    can_query_available_funds = true,
    can_see_bank_account_bank_name = true,
    can_see_bank_account_currency = true,
    can_see_bank_account_iban = true,
    can_see_bank_account_label = true,
    can_see_bank_account_national_identifier = true,
    can_see_bank_account_number = true,
    can_see_bank_account_owners = true,
    can_see_bank_account_swift_bic = true,
    can_see_bank_account_type = true,
    can_see_comments = true,
    can_see_corporate_location = true,
    can_see_image_url = true,
    can_see_images = true,
    can_see_more_info = true,
    can_see_open_corporates_url = true,
    can_see_other_account_bank_name = true,
    can_see_other_account_iban = true,
    can_see_other_account_kind = true,
    can_see_other_account_metadata = true,
    can_see_other_account_national_identifier = true,
    can_see_other_account_number = true,
    can_see_other_account_swift_bic = true,
    can_see_owner_comment = true,
    can_see_physical_location = true,
    can_see_private_alias = true,
    can_see_public_alias = true,
    can_see_tags = true,
    can_see_transaction_amount = true,
    can_see_transaction_balance = true,
    can_see_transaction_currency = true,
    can_see_transaction_description = true,
    can_see_transaction_finish_date = true,
    can_see_transaction_metadata = true,
    can_see_transaction_other_bank_account = true,
    can_see_transaction_start_date = true,
    can_see_transaction_this_bank_account = true,
    can_see_transaction_type = true,
    can_see_url = true,
    can_see_where_tag = true,
    //V300 new 
    can_see_bank_routing_scheme = true,
    can_see_bank_routing_address = true,
    can_see_bank_account_routing_scheme = true,
    can_see_bank_account_routing_address = true,
    can_see_other_bank_routing_scheme = true,
    can_see_other_bank_routing_address = true,
    can_see_other_account_routing_scheme = true,
    can_see_other_account_routing_address = true,
    can_add_transaction_request_to_own_account = true, //added following two for payments
    can_add_transaction_request_to_any_account = true,
    can_see_bank_account_credit_limit = true,
    can_create_direct_debit = true,
    can_create_standing_order = true
  )
  
  lazy val viewsJsonV300 =  ViewsJsonV300(
    views = List(viewJsonV300)
  )

  lazy val coreAccountJsonV300 = CoreAccountJsonV300(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    label = "String",
    bank_id = bankIdExample.value,
    account_routings = List(accountRoutingJsonV121)
  )
  
  lazy val viewBasicV300 = ViewBasicV300(
    id = viewIdExample.value,
    short_name =viewNameExample.value,
    description = viewDescriptionExample.value,
    is_public = false
  )
  
  lazy val coreAccountJson = CoreAccountJson(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    label = "String",
    bank_id = bankIdExample.value,
    account_type = "330",
    account_routings = List(accountRoutingJsonV121),
    views = List(viewBasicV300)
  )
  
  lazy val coreAccountsJsonV300 = CoreAccountsJsonV300(accounts = List(coreAccountJson))


  lazy val accountInnerJsonUKOpenBanking_v200 = AccountInner(
    SchemeName = "SortCodeAccountNumber",
    Identification = "80200110203345",
    Name = "Mr Kevin",
    SecondaryIdentification = Some("00021")
  )

  lazy val accountJsonUKOpenBanking_v200 = Account(
    AccountId = "22289",
    Currency = "GBP",
    AccountType = "Personal",
    AccountSubType = "CurrentAccount",
    Nickname = "Bills",
    Account = accountInnerJsonUKOpenBanking_v200
  )

  lazy val accountList = AccountList(List(accountJsonUKOpenBanking_v200))
  
  lazy val links =  Links(Self = s"${Constant.HostName}/open-banking/v2.0/accounts/")
  
  lazy val metaUK = JSONFactory_UKOpenBanking_200.MetaUK(1) 
  
  lazy val accountsJsonUKOpenBanking_v200 = Accounts(
    Data = accountList,
    Links = links,
    Meta = metaUK
  )
  
  lazy val accountIdJson = AccountIdJson(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf"
  )
  
  lazy val accountsIdsJsonV300 = AccountsIdsJsonV300(accounts = List(accountIdJson))

  lazy val logoutLinkV400 = LogoutLinkJson(link="127.0.0.1:8080/user_mgt/logout")

  lazy val adapterInfoJsonV300 = AdapterInfoJsonV300(
    name = "String",
    version = "String",
    git_commit = "String",
    date = "2013-01-21T23:08:00Z"
  )
  lazy val rateLimitingInfoV310 = RateLimitingInfoV310(
    enabled = true,
    technology = "REDIS",
    service_available = true,
    is_active = true
  )
  
  lazy val thisAccountJsonV300 = ThisAccountJsonV300(
    id ="String",
    bank_routing = bankRoutingJsonV121,
    account_routings = List(accountRoutingJsonV121),
    holders =  List(accountHolderJSON)
  )
  
  lazy val otherAccountJsonV300 = OtherAccountJsonV300(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    holder = accountHolderJSON,
    bank_routing = bankRoutingJsonV121,
    account_routings = List(accountRoutingJsonV121),
    metadata = otherAccountMetadataJSON
  )
  
  lazy val otherAccountsJsonV300 = OtherAccountsJsonV300(
    other_accounts = List(otherAccountJsonV300)
  )
  
  lazy val transactionJsonV300 = TransactionJsonV300(
    id= "String",
    this_account = thisAccountJsonV300,
    other_account = otherAccountJsonV300,
    details = transactionDetailsJSON,
    metadata = transactionMetadataJSON,
    transaction_attributes = List(TransactionAttributeResponseJson(
      transaction_attribute_id = transactionAttributeIdExample.value,
      name = transactionAttributeNameExample.value,
      `type` = transactionAttributeTypeExample.value,
      value = transactionAttributeValueExample.value
    ))
  )
  
  lazy val transactionsJsonV300 = TransactionsJsonV300(
    transactions = List(transactionJsonV300)
  )
  
  lazy val coreCounterpartyJsonV300 = CoreCounterpartyJsonV300(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    holder = accountHolderJSON,
    bank_routing = bankRoutingJsonV121,
    account_routings = List(accountRoutingJsonV121)
  )
  
  lazy val coreTransactionJsonV300 = CoreTransactionJsonV300(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    this_account = thisAccountJsonV300,
    other_account = coreCounterpartyJsonV300,
    details = coreTransactionDetailsJSON,
    transaction_attributes = List(TransactionAttributeResponseJson(
      transaction_attribute_id = transactionAttributeIdExample.value,
      name = transactionAttributeNameExample.value,
      `type` = transactionAttributeTypeExample.value,
      value = transactionAttributeValueExample.value
    ))
  )
  
  lazy val coreCounterpartiesJsonV300 =  CoreCounterpartiesJsonV300(
    counterparties = List(coreCounterpartyJsonV300)
  )
  
  lazy val coreTransactionsJsonV300 = CoreTransactionsJsonV300(
    transactions = List(coreTransactionJsonV300)
  )
  
  //ended -- Transaction relevant case classes /////
  
  //stated -- account relevant case classes /////
  
  
  lazy val accountHeldJson  = AccountHeldJson(
    id = "7b97bd26-583b-4c3b-8282-55ea9d934aad",
    label = "My Account",
    bank_id=  "123",
    number = "123",
    account_routings = List(accountRoutingJsonV121)
  )
  
  lazy val coreAccountsHeldJsonV300 = CoreAccountsHeldJsonV300(
    accounts= List(accountHeldJson)
  )
  lazy val moderatedAccountJsonV300 = ModeratedAccountJsonV300(
    id= "String",
    bank_id = bankIdExample.value,
    label = "String",
    number = "String",
    owners = List(userJSONV121),
    `type`= "String",
    balance =  amountOfMoneyJsonV121,
    views_available = List(viewJsonV300),
    account_routings = List(accountRoutingJsonV121)
  )

  lazy val accountAttributeJson = AccountAttributeJson(
    name = "OVERDRAFT_START_DATE",
    `type` = "DATE_WITH_DAY",
    value = "2012-04-23",
    product_instance_code = Some("LKJL98769F"),
  )
  lazy val accountAttributeResponseJson = AccountAttributeResponseJson(
    product_code = productCodeExample.value,
    account_attribute_id = "613c83ea-80f9-4560-8404-b9cd4ec42a7f",
    name = "OVERDRAFT_START_DATE",
    `type` = "DATE_WITH_DAY",
    value = "2012-04-23",
    product_instance_code = Some("LKJL98769F"),
  )
  
  lazy val moderatedCoreAccountJsonV300 = ModeratedCoreAccountJsonV300(
    id = accountIdExample.value,
    bank_id = bankIdExample.value,
    label= labelExample.value,
    number= numberExample.value,
    owners =  List(userJSONV121),
    `type`= typeExample.value,
    balance = amountOfMoneyJsonV121,
    account_routings = List(accountRoutingJsonV121),
    account_rules = List(accountRuleJsonV300),
    account_attributes= Some(List(accountAttributeResponseJson))
  )
  
  lazy val moderatedCoreAccountsJsonV300 = ModeratedCoreAccountsJsonV300(List(moderatedCoreAccountJsonV300))

  lazy val moderatedFirehoseAccountJsonV400 = ModeratedFirehoseAccountJsonV400(
    id = accountIdExample.value,
    bank_id = bankIdExample.value,
    label= labelExample.value,
    number= numberExample.value,
    owners =  List(userJSONV121),
    product_code = productCodeExample.value,
    balance = amountOfMoneyJsonV121,
    account_routings = List(accountRoutingJsonV121),
    account_rules = List(accountRuleJsonV300)
  )

  lazy val moderatedFirehoseAccountsJsonV400 = ModeratedFirehoseAccountsJsonV400(List(moderatedFirehoseAccountJsonV400))

  lazy val fastFirehoseAccountJsonV400 = FastFirehoseAccountJsonV400(
    id = accountIdExample.value,
    bank_id = bankIdExample.value,
    label = labelExample.value,
    number = numberExample.value,
    owners = List(FastFirehoseOwners(user_id="b27327a2-a822-41e5-a909-0150da688939",provider="https://finx22openplatform.fintech-galaxy.com,user_name:synth_user_1_54891", user_name="")),
    product_code = productCodeExample.value,
    balance = amountOfMoneyJsonV121,
    account_routings = List(FastFirehoseRoutings(bank_id="bisb.com",account_id="c590e38e-847c-466f-9a62-f2ad67daf106")),
    account_attributes= List(FastFirehoseAttributes(`type`="INTEGER",code="Loan1",value="0"), 
  FastFirehoseAttributes(`type`="STRING",code="Loan1",value="4421.783"))
      
  )

  lazy val fastFirehoseAccountsJsonV400  = FastFirehoseAccountsJsonV400(
    List(fastFirehoseAccountJsonV400)
  )
  lazy val aggregateMetricsJSONV300 = AggregateMetricJSON(
    count = 7076,
    average_response_time = 65.21,
    minimum_response_time = 1,
    maximum_response_time = 9039
  )
  
  //APIMethods_UKOpenBanking_200 
  
  lazy val bankTransactionCodeJson = BankTransactionCodeJson(
    Code = "ReceivedCreditTransfer",
    SubCode = "DomesticCreditTransfer"
  )

  lazy val balanceUKOpenBankingJson = BalanceUKOpenBankingJson(
    Amount = amountOfMoneyJsonV121,
    CreditDebitIndicator = "Credit",
    Type = "InterimBooked"
  )

  lazy val transactionCodeJson = TransactionCodeJson(
    Code = "Transfer",
    Issuer = "AlphaBank"
  )
  
  lazy val transactionInnerJson  = TransactionInnerJson(
    AccountId = accountIdSwagger.value,
    TransactionId  = "123",
    TransactionReference = "Ref 1",
    Amount = amountOfMoneyJsonV121,
    CreditDebitIndicator = "Credit",
    Status = "Booked",
    BookingDateTime = DateWithDayExampleObject,
    ValueDateTime = DateWithDayExampleObject,
    TransactionInformation = "Cash from Aubrey",
    BankTransactionCode = bankTransactionCodeJson,
    ProprietaryBankTransactionCode = transactionCodeJson,
    Balance = balanceUKOpenBankingJson
  )

  lazy val transactionsInnerJson =  TransactionsInnerJson(
    Transaction = List(transactionInnerJson)
  )

  lazy val metaInnerJson  = MetaInnerJson(
    TotalPages = 1,
    FirstAvailableDateTime = DateWithDayExampleObject,
    LastAvailableDateTime = DateWithDayExampleObject
  )

  lazy val transactionsJsonUKV200 = TransactionsJsonUKV200(
    Data = transactionsInnerJson,
    Links = links.copy(s"${Constant.HostName}/open-banking/v2.0/accounts/22289/transactions/"),
    Meta = metaInnerJson
  )
  
  lazy val creditLineJson = CreditLineJson(
    Included = true,
    Amount = amountOfMoneyJsonV121,
    Type = "Pre-Agreed"
  )
  
  lazy val balanceJsonUK200 = BalanceJsonUKV200(
    AccountId = "22289",
    Amount = amountOfMoneyJsonV121,
    CreditDebitIndicator = "Credit",
    Type = "InterimAvailable",
    DateTime = DateWithDayExampleObject,
    CreditLine = List(creditLineJson)
  )
  
  lazy val dataJsonUK200 = DataJsonUKV200(
    Balance = List(balanceJsonUK200)
  )
  
  lazy val metaBisJson =  MetaBisJson(
    TotalPages = 1
  )
  
  lazy val accountBalancesUKV200 = AccountBalancesUKV200(
    Data = dataJsonUK200,
    Links = links.copy(s"${Constant.HostName}/open-banking/v2.0/accounts/22289/balances/"),
    Meta = metaBisJson
  )
  
  lazy val createScopeJson =  CreateScopeJson(bank_id = bankIdExample.value, role_name = "CanGetEntitlementsForAnyUserAtOneBank")
   
  lazy val scopeJson = ScopeJson(
    scope_id = "88625da4-a671-435e-9d24-e5b6e5cc404f", 
    role_name = "CanGetEntitlementsForAnyUserAtOneBank", 
    bank_id = bankIdExample.value
  )
  lazy val scopeJsons = ScopeJsons(List(scopeJson))
  
  
  
  //V310 
  
  lazy val orderObjectJson = OrderObjectJson(
    order_id ="xjksajfkj",
    order_date = "07082013",
    number_of_checkbooks = "4",
    distribution_channel = "1201",
    status = "2",
    first_check_number = "5165276",
    shipping_code = "1"
  )
  
  lazy val orderJson = OrderJson(orderObjectJson)
  
  lazy val accountV310Json = AccountV310Json(
    bank_id = bankIdExample.value,
    account_id =accountIdExample.value ,
    account_type  ="330",
    account_routings  = List(accountRoutingJsonV121),
    branch_routings = List(branchRoutingJsonV141)
  )
  
  lazy val checkbookOrdersJson = CheckbookOrdersJson(
    account = accountV310Json ,
    orders = List(orderJson)
  )

  lazy val checkFundsAvailableJson = CheckFundsAvailableJson(
    "yes",
    new Date(),
    "c4ykz59svsr9b7fmdxk8ezs7"
  )
  
  lazy val cardObjectJson = CardObjectJson(
    card_type = "5",
    card_description= "good",
    use_type  ="3"
  )
  
  lazy val creditCardOrderStatusResponseJson = CreditCardOrderStatusResponseJson(
    cards = List(cardObjectJson)
  )
  
  lazy val creditLimitRequestJson = CreditLimitRequestJson(
    requested_current_rate_amount1 = "String",
    requested_current_rate_amount2 = "String",
    requested_current_valid_end_date = "String",
    current_credit_documentation = "String",
    temporary_requested_current_amount = "String",
    requested_temporary_valid_end_date = "String",
    temporary_credit_documentation = "String",
  )
  
  lazy val creditLimitOrderResponseJson = CreditLimitOrderResponseJson(
    execution_time = "String",
    execution_date = "String",
    token = "String",
    short_reference = "String"
  )
  
  lazy val creditLimitOrderJson = CreditLimitOrderJson(
    rank_amount_1 = "String",
    nominal_interest_1 = "String",
    rank_amount_2 = "String",
    nominal_interest_2 = "String"
  )
  
  lazy val topApiJson = TopApiJson(
    count = 7076,
    Implemented_by_partial_function = "getBanks",
    implemented_in_version = "v1.2.1"
  )
  
  lazy val topApisJson = TopApisJson(List(topApiJson))
  
  lazy val topConsumerJson = TopConsumerJson(
    count = 7076,
    consumer_id = consumerIdExample.value,
    app_name = "Api Explorer",
    developer_email = emailExample.value,
  )
  
  lazy val topConsumersJson = TopConsumersJson(List(topConsumerJson))

  lazy val glossaryDescriptionJsonV300 =  GlossaryDescriptionJsonV300 (markdown= "String", html = "String")

  lazy val glossaryItemJsonV300 = GlossaryItemJsonV300(
    title = ExampleValue.titleExample.value,
    description = glossaryDescriptionJsonV300
  )

  lazy val glossaryItemsJsonV300 = GlossaryItemsJsonV300 (glossary_items = List(glossaryItemJsonV300))
  
  lazy val badLoginStatusJson = BadLoginStatusJson(
    username = usernameExample.value,
    bad_attempts_since_last_success_or_reset = 0,
    last_failure_date = DateWithMsExampleObject
  )  
  lazy val userLockStatusJson = UserLockStatusJson(
    user_id = userIdExample.value,
    type_of_lock = "lock_via_api",
    last_lock_date = DateWithMsExampleObject
  )

  lazy val callLimitPostJson = CallLimitPostJson(
    from_date = DateWithDayExampleObject,
    to_date = DateWithDayExampleObject,
    per_second_call_limit = "-1",
    per_minute_call_limit = "-1",
    per_hour_call_limit = "-1",
    per_day_call_limit ="-1",
    per_week_call_limit = "-1",
    per_month_call_limit = "-1"
  )
  lazy val callLimitPostJsonV400 = CallLimitPostJsonV400(
    from_date = DateWithDayExampleObject,
    to_date = DateWithDayExampleObject,
    api_version = None,
    api_name = None,
    bank_id = None,
    per_second_call_limit = "-1",
    per_minute_call_limit = "-1",
    per_hour_call_limit = "-1",
    per_day_call_limit ="-1",
    per_week_call_limit = "-1",
    per_month_call_limit = "-1"
  )
  
  lazy val rateLimit = RateLimit(Some(-1),Some(-1))
  
  lazy val redisCallLimitJson = RedisCallLimitJson(
    Some(rateLimit),
    Some(rateLimit),
    Some(rateLimit),
    Some(rateLimit),
    Some(rateLimit),
    Some(rateLimit)
  )
  
  lazy val callLimitJson = CallLimitJson(
    per_second_call_limit = "-1",
    per_minute_call_limit = "-1",
    per_hour_call_limit = "-1",
    per_day_call_limit ="-1",
    per_week_call_limit = "-1",
    per_month_call_limit = "-1",
    Some(redisCallLimitJson)
  )

  lazy val callLimitsJson510Example: CallLimitsJson510 = CallLimitsJson510(
    limits = List(
      CallLimitJson510(
        rate_limiting_id = "80e1e0b2-d8bf-4f85-a579-e69ef36e3305",
        from_date = DateWithDayExampleObject,
        to_date = DateWithDayExampleObject,
        per_second_call_limit = "100",
        per_minute_call_limit = "100",
        per_hour_call_limit = "-1",
        per_day_call_limit = "-1",
        per_week_call_limit = "-1",
        per_month_call_limit = "-1",
        created_at = DateWithDayExampleObject,
        updated_at = DateWithDayExampleObject
      )
    )
  )

  lazy val callLimitPostJsonV600 = CallLimitPostJsonV600(
    from_date = DateWithDayExampleObject,
    to_date = DateWithDayExampleObject,
    api_version = Some("v6.0.0"),
    api_name = Some("getConsumerCallLimits"),
    bank_id = None,
    per_second_call_limit = "100",
    per_minute_call_limit = "1000",
    per_hour_call_limit = "-1",
    per_day_call_limit = "-1",
    per_week_call_limit = "-1",
    per_month_call_limit = "-1"
  )

  lazy val callLimitJsonV600 = CallLimitJsonV600(
    rate_limiting_id = "80e1e0b2-d8bf-4f85-a579-e69ef36e3305",
    from_date = DateWithDayExampleObject,
    to_date = DateWithDayExampleObject,
    api_version = Some("v6.0.0"),
    api_name = Some("getConsumerCallLimits"),
    bank_id = None,
    per_second_call_limit = "100",
    per_minute_call_limit = "1000",
    per_hour_call_limit = "-1",
    per_day_call_limit = "-1",
    per_week_call_limit = "-1",
    per_month_call_limit = "-1",
    created_at = DateWithDayExampleObject,
    updated_at = DateWithDayExampleObject
  )

  lazy val activeCallLimitsJsonV600 = ActiveCallLimitsJsonV600(
    call_limits = List(callLimitJsonV600),
    active_at_date = DateWithDayExampleObject,
    total_per_second_call_limit = 100,
    total_per_minute_call_limit = 1000,
    total_per_hour_call_limit = -1,
    total_per_day_call_limit = -1,
    total_per_week_call_limit = -1,
    total_per_month_call_limit = -1
  )

  lazy val accountWebhookPostJson = AccountWebhookPostJson(
    account_id =accountIdExample.value,
    trigger_name = ApiTrigger.onBalanceChange.toString(),
    url = "https://localhost.openbankproject.com",
    http_method = "POST",
    http_protocol = "HTTP/1.1",
    is_active = "true"
  )
  lazy val accountWebhookPutJson = AccountWebhookPutJson(
    account_webhook_id = "fc23a7e2-7dd2-4bdf-a0b4-ae31232a4762",
    is_active = "true"
  )
  lazy val accountWebhookJson =  AccountWebhookJson(
    account_webhook_id = "613c83ea-80f9-4560-8404-b9cd4ec42a7f",
    bank_id = bankIdExample.value,
    account_id =accountIdExample.value,
    trigger_name = ApiTrigger.onBalanceChange.toString(),
    url = "https://localhost.openbankproject.com",
    http_method = "POST",
    http_protocol = "HTTP/1.1",
    created_by_user_id = ExampleValue.userIdExample.value,
    is_active = true
  )

  lazy val accountWebhooksJson = AccountWebhooksJson(List(accountWebhookJson))
  
  lazy val postUserAuthContextJson = PostUserAuthContextJson(
    key = "CUSTOMER_NUMBER",
    value = "78987432"
  )

  lazy val postUserAuthContextUpdateJsonV310 = PostUserAuthContextUpdateJsonV310(answer = "123")
  
  lazy val userAuthContextJson = UserAuthContextJson(
    user_auth_context_id = "613c83ea-80f9-4560-8404-b9cd4ec42a7f",
    user_id = ExampleValue.userIdExample.value,
    key = "CUSTOMER_NUMBER",
    value = "78987432",
    time_stamp = parseDate(timeStampExample.value).getOrElse(sys.error("timeStampExample.value is not validate date format."))
  )

  lazy val userAuthContextUpdateJson = UserAuthContextUpdateJson(
    user_auth_context_update_id = "613c83ea-80f9-4560-8404-b9cd4ec42a7f",
    user_id = ExampleValue.userIdExample.value,
    key = "CUSTOMER_NUMBER",
    value = "78987432",
    status = UserAuthContextUpdateStatus.INITIATED.toString
  )
  
  lazy val userAuthContextsJson = UserAuthContextsJson(
    user_auth_contexts = List(userAuthContextJson)
  )
  
  lazy val obpApiLoopbackJson = ObpApiLoopbackJson("rest_vMar2019","f0acd4be14cdcb94be3433ec95c1ad65228812a0","10 ms")
  
  lazy val refresUserJson = RefreshUserJson("10 ms")
  
  lazy val productAttributeJson = ProductAttributeJson(
    name = "OVERDRAFT_START_DATE",
    `type` = "DATE_WITH_DAY",
    value = "2012-04-23"
  )  
  lazy val productAttributeJsonV400 = ProductAttributeJsonV400(
    name = "OVERDRAFT_START_DATE",
    `type` = "DATE_WITH_DAY",
    value = "2012-04-23",
    is_active = Some(true)
  )
  lazy val productAttributeResponseJson = ProductAttributeResponseWithoutBankIdJson(
    product_code = productCodeExample.value,
    product_attribute_id = "613c83ea-80f9-4560-8404-b9cd4ec42a7f",
    name = "OVERDRAFT_START_DATE",
    `type` = "DATE_WITH_DAY",
    value = "2012-04-23"
  )
  lazy val productAttributeResponseJsonV400 = ProductAttributeResponseJsonV400(
    bank_id = bankIdExample.value,
    product_code = productCodeExample.value,
    product_attribute_id = "613c83ea-80f9-4560-8404-b9cd4ec42a7f",
    name = "OVERDRAFT_START_DATE",
    `type` = "DATE_WITH_DAY",
    value = "2012-04-23",
    is_active = Some(true)
  )
  lazy val productAttributeResponseWithoutBankIdJsonV400 = ProductAttributeResponseWithoutBankIdJsonV400(
    product_code = productCodeExample.value,
    product_attribute_id = "613c83ea-80f9-4560-8404-b9cd4ec42a7f",
    name = "OVERDRAFT_START_DATE",
    `type` = "DATE_WITH_DAY",
    value = "2012-04-23",
    is_active = Some(true)
  )

  lazy val bankAttributeJsonV400 = BankAttributeJsonV400(
    name = "TAX_ID",
    `type` = "INTEGER",
    value = "12345678",
    is_active = Some(true)
  )
  lazy val atmAttributeJsonV510 = AtmAttributeJsonV510(
    name = "TAX_ID",
    `type` = "INTEGER",
    value = "12345678",
    is_active = Some(true)
  )
  lazy val bankAttributeResponseJsonV400 = BankAttributeResponseJsonV400(
    bank_id = bankIdExample.value,
    bank_attribute_id = "613c83ea-80f9-4560-8404-b9cd4ec42a7f",
    name = "OVERDRAFT_START_DATE",
    `type` = "DATE_WITH_DAY",
    value = "2012-04-23",
    is_active = Some(true)
  )
  lazy val bankAttributesResponseJsonV400 = BankAttributesResponseJsonV400(List(bankAttributeResponseJsonV400))
  
  lazy val atmAttributeResponseJsonV510 = AtmAttributeResponseJsonV510(
    bank_id = bankIdExample.value,
    atm_id = atmIdExample.value,
    atm_attribute_id = atmAttributeIdExample.value,
    name = nameExample.value,
    `type` = typeExample.value,
    value = valueExample.value,
    is_active = Some(activeExample.value.toBoolean)
  )

  lazy val atmAttributesResponseJsonV510 = AtmAttributesResponseJsonV510(
    List(atmAttributeResponseJsonV510)
  )
  
  lazy val moderatedAccountJSON310 = ModeratedAccountJSON310(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    label = "NoneLabel",
    number = "123",
    owners = List(userJSONV121),
    `type` = "OBP",
    balance = amountOfMoneyJsonV121,
    views_available = List(viewJSONV121),
    bank_id = bankIdExample.value,
    account_routings = List(accountRoutingJsonV121),
    account_attributes = List(accountAttributeResponseJson)
  )

  lazy val accountApplicationJson = AccountApplicationJson(
    product_code = productCodeExample.value,
    user_id = Some(ExampleValue.userIdExample.value),
    customer_id = Some(customerIdExample.value)
  )

  lazy val accountApplicationResponseJson = AccountApplicationResponseJson (
    account_application_id = "gc23a7e2-7dd2-4bdf-a0b4-ae31232a4763",
    product_code = productCodeExample.value,
    user = resourceUserJSON,
    customer = customerJsonV310,
    date_of_application = DateWithDayExampleObject,
    status = "REQUESTED"
  )
  lazy val accountApplicationUpdateStatusJson = AccountApplicationUpdateStatusJson(
    status = "ACCEPTED"
  )

  lazy val accountApplicationsJsonV310 = AccountApplicationsJsonV310(List(accountApplicationResponseJson))

  lazy val productJsonV310 = ProductJsonV310(
    bank_id = bankIdExample.value,
    code = productCodeExample.value,
    parent_product_code = "parent",
    name = "product name",
    category = "category",
    family = "family",
    super_family = "super family",
    more_info_url = "www.example.com/prod1/more-info.html",
    details = "Details",
    description = "Description",
    meta = metaJson,
    Some(List(productAttributeResponseJson))
  )
  lazy val productsJsonV310 = ProductsJsonV310(products = List(productJsonV310))

  lazy val productCollectionItemJsonV310 = ProductCollectionItemJsonV310(member_product_code = "A")
  lazy val productCollectionJsonV310 = ProductCollectionJsonV310(
    collection_code = "C",
    product_code = productCodeExample.value, items = List(productCollectionItemJsonV310, productCollectionItemJsonV310.copy(member_product_code = "B"))
  )
  lazy val productCollectionsJsonV310 = ProductCollectionsJsonV310(product_collection = List(productCollectionJsonV310))
  
  lazy val productCollectionJsonTreeV310 = ProductCollectionJsonTreeV310(collection_code = "A", products = List(productJsonV310))
  
  lazy val contactDetailsJson = ContactDetailsJson(
    name = "Simon ",
    mobile_phone = "+44 07972 444 876",
    email_address = ExampleValue.emailExample.value
  )
  
  lazy val inviteeJson = InviteeJson(
    contactDetailsJson,
    "String, eg: Good"
  )
  
  lazy val createMeetingJsonV310 = CreateMeetingJsonV310(
    provider_id = providerIdValueExample.value,
    purpose_id = "String, eg: onboarding",
    date = DateWithMsExampleObject,
    creator = contactDetailsJson,
    invitees = List(inviteeJson)
  )
  
  lazy val meetingJsonV310 = MeetingJsonV310(
    meeting_id = "UUID-String",
    provider_id = providerIdValueExample.value,
    purpose_id = "String, eg: onboarding",
    bank_id = bankIdExample.value,
    present = meetingPresentJSON,
    keys = meetingKeysJSON,
    when = DateWithDayExampleObject,
    creator = contactDetailsJson,
    invitees = List(inviteeJson)
  )
  
  lazy val meetingsJsonV310 = MeetingsJsonV310(List(meetingJsonV310))
  
  case class SeverJWK(kty: String = "RSA",
                      e: String = "AQAB",
                      use: String = "sig",
                      kid: String = "fr6-BxXH5gikFeZ2O6rGk0LUmJpukeswASN_TMW8U_s",
                      n: String = "hrB0OWqg6AeNU3WCnhheG18R5EbQtdNYGOaSeylTjkj2lZr0_vkhNVYvase-CroxO4HOT06InxTYwLnmJiyv2cZxReuoVjTlk--olGu-9MZooiFiqWez0JzndyKxQ27OiAjFsMh0P04kaUXeHKhXRfiU7K2FqBshR1UlnWe7iHLkq2p9rrGjxQc7ff0w-Uc0f-8PWg36Y2Od7s65493iVQwnI13egqMaSvgB1s8_dgm08noEjhr8C5m1aKmr5oipWEPNi-SBV2VNuiCLR1IEPuXq0tOwwZfv31t34KPO-2H2bbaWmzGJy9mMOGqoNrbXyGiUZoyeHRELaNtm1GilyQ")
  lazy val severJWK = SeverJWK()
  
  lazy val consentJsonV310 = ConsentJsonV310(
    consent_id = "9d429899-24f5-42c8-8565-943ffa6a7945",
    jwt = jwtExample.value,
    status = ConsentStatus.INITIATED.toString
  )   
  lazy val consentJsonV400 = ConsentJsonV400(
    consent_id = "9d429899-24f5-42c8-8565-943ffa6a7945",
    jwt = jwtExample.value,
    status = ConsentStatus.INITIATED.toString,
    api_standard = "Berlin Group",
    api_version = "v1.3"
  )    
  lazy val consentInfoJsonV400 = ConsentInfoJsonV400(
    consent_id = "9d429899-24f5-42c8-8565-943ffa6a7945",
    consumer_id = consumerIdExample.value,
    created_by_user_id = userIdExample.value,
    last_action_date = dateExample.value,
    last_usage_date = dateTimeExample.value,
    status = ConsentStatus.INITIATED.toString,
    api_standard = "Berlin Group",
    api_version = "v1.3"
  )

  lazy val helperInfoJson = HelperInfoJson(
    counterparty_ids = List(counterpartyIdExample.value)
  )
  
  lazy val roleJsonV510 = code.api.util.Role(
    role_name = roleNameExample.value,
    bank_id = bankIdExample.value
  )
  
  lazy val httpParam = net.liftweb.http.provider.HTTPParam(
    name = "tags", 
    values = List("static")
  )

  lazy val consentAccessAccountsJson =code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3.ConsentAccessAccountsJson(
    iban = Some(ibanExample.value),  
    bban = Some("BARC12345612345678"),  
    pan = Some("5409050000000000"),  
    maskedPan = Some("123456xxxxxx1234"),  
    msisdn = Some("+49 170 1234567"),  
    currency = Some(currencyExample.value) 
  )
  
  lazy val consentAccessJson = code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3.ConsentAccessJson(
    accounts = Some(List(consentAccessAccountsJson)),
    balances = Some(List(consentAccessAccountsJson)),
    transactions = Some(List(consentAccessAccountsJson)),
    availableAccounts = Some(accountIdExample.value),
    allPsd2 = None
  )
  
  lazy val consentView = code.api.util.ConsentView(
    bank_id = bankIdExample.value,
    account_id = accountIdExample.value,
    view_id = viewIdExample.value,
    helper_info = Some(helperInfoJson)
  )
  
  lazy val consentJWT = code.api.util.ConsentJWT(
    createdByUserId  = userIdExample.value,
    sub = subExample.value,
    iss = issExample.value,
    aud = audExample.value,
    jti = jtiExample.value,
    iat = iatExample.value.toLong,
    nbf = nbfExample.value.toLong,
    exp = expExample.value.toLong,
    request_headers = List(httpParam),
    name = Some(nameExample.value),
    email= Some(emailExample.value),
    entitlements = List(roleJsonV510),
    views = List(consentView),
    access = Some(consentAccessJson)
  )
  
  lazy val allConsentJsonV510 = AllConsentJsonV510(
    consent_reference_id = consentReferenceIdExample.value,
    consumer_id = consumerIdExample.value,
    created_by_user_id = userIdExample.value,
    provider = Some(providerValueExample.value),
    provider_id = Some(providerIdExample.value),
    last_action_date = dateExample.value,
    last_usage_date = dateTimeExample.value,
    status = ConsentStatus.INITIATED.toString,
    api_standard = "Berlin Group",
    api_version = "v1.3",
    jwt_payload = Some(consentJWT),
    note = """Tue, 15 Jul 2025 19:16:22
             ||---> Changed status from received to rejected for consent ID: 398""".stripMargin
  )
  lazy val consentInfoJsonV510 = ConsentInfoJsonV510(
    consent_reference_id = consentReferenceIdExample.value,
    consent_id = consentIdExample.value,
    consumer_id = consumerIdExample.value,
    created_by_user_id = userIdExample.value,
    status = statusExample.value,
    last_action_date = dateExample.value,
    last_usage_date = dateTimeExample.value,
    jwt = jwtExample.value,
    jwt_payload = Some(consentJWT),
    api_standard = "Berlin Group",
    api_version = "v1.3",
  )
  
  lazy val consentsInfoJsonV510 = ConsentsInfoJsonV510(
    consents =  List(consentInfoJsonV510)
  )
  
  lazy val consentsJsonV510 = ConsentsJsonV510(number_of_rows = 1, List(allConsentJsonV510))

  lazy val revokedConsentJsonV310 = ConsentJsonV310(
    consent_id = "9d429899-24f5-42c8-8565-943ffa6a7945",
    jwt = jwtExample.value,
    status = ConsentStatus.REJECTED.toString
  )

  lazy val postConsentEmailJsonV310 = PostConsentEmailJsonV310(
    everything = false,
    views = List(PostConsentViewJsonV310(bankIdExample.value, accountIdExample.value, viewIdExample.value)),
    entitlements = List(PostConsentEntitlementJsonV310(bankIdExample.value, "CanGetCustomer")),
    consumer_id = Some(consumerIdExample.value),
    email = emailExample.value,
    valid_from = Some(new Date()),
    time_to_live = Some(3600)
  )
  
  lazy val postConsentPhoneJsonV310 = PostConsentPhoneJsonV310(
    everything = false,
    views = List(PostConsentViewJsonV310(bankIdExample.value, accountIdExample.value, viewIdExample.value)),
    entitlements = List(PostConsentEntitlementJsonV310(bankIdExample.value, "CanGetCustomer")),
    consumer_id = Some(consumerIdExample.value),
    phone_number = mobileNumberExample.value,
    valid_from = Some(new Date()),
    time_to_live = Some(3600)
  )
  
  lazy val postConsentImplicitJsonV310 = PostConsentImplicitJsonV310(
    everything = false,
    views = List(PostConsentViewJsonV310(bankIdExample.value, accountIdExample.value, viewIdExample.value)),
    entitlements = List(PostConsentEntitlementJsonV310(bankIdExample.value, "CanGetCustomer")),
    consumer_id = Some(consumerIdExample.value),
    valid_from = Some(new Date()),
    time_to_live = Some(3600)
  )
  lazy val postConsentRequestJsonV310 = postConsentPhoneJsonV310.copy(consumer_id = None)
  
  lazy val consentsJsonV310 = ConsentsJsonV310(List(consentJsonV310))
  
  lazy val consentsJsonV400 = ConsentsJsonV400(List(consentJsonV400))

  lazy val consentInfosJsonV400 = ConsentInfosJsonV400(List(consentInfoJsonV400))

  lazy val oAuth2ServerJWKURIJson = OAuth2ServerJWKURIJson("https://www.googleapis.com/oauth2/v3/certs")
  
  lazy val oAuth2ServerJwksUrisJson = OAuth2ServerJwksUrisJson(List(oAuth2ServerJWKURIJson))
  
  lazy val updateAccountRequestJsonV310 = UpdateAccountRequestJsonV310(
    label = "Label",
    `type` = "CURRENT",
    branch_id = "1234",
    account_routings = List(accountRoutingJsonV121)
  )

  lazy val updateAccountResponseJsonV310 = UpdateAccountResponseJsonV310(
    bank_id = bankIdExample.value,
    account_id =accountIdExample.value,
    label = "Label",
    `type` = "CURRENT",
    branch_id = "1234",
    account_routings = List(AccountRoutingJsonV121(accountRoutingSchemeExample.value, accountRoutingAddressExample.value))
  )
  lazy val createPhysicalCardJsonV310 = CreatePhysicalCardJsonV310(
    card_number = cardNumberExample.value,
    card_type = cardTypeExample.value,
    name_on_card = nameOnCardExample.value,
    issue_number = issueNumberExample.value,
    serial_number = serialNumberExample.value,
    valid_from_date = DateWithDayExampleObject,
    expires_date = DateWithDayExampleObject,
    enabled = true,
    technology = technologyExample.value,
    networks = List(networksExample.value),
    allows = allowsExample.value.replaceAll(""""""","").replace("""[""","")
      .replace("""]""","").split(",").toList,
    account_id =accountIdExample.value,
    replacement = Some(replacementJSON),
    pin_reset = List(pinResetJSON, pinResetJSON1),
    collected = Some(DateWithDayExampleObject),
    posted = Some(DateWithDayExampleObject),
    customer_id = customerIdExample.value,
  )

  lazy val updatePhysicalCardJsonV310 = UpdatePhysicalCardJsonV310(
    card_type = cardTypeExample.value,
    name_on_card = nameOnCardExample.value,
    issue_number = issueNumberExample.value,
    serial_number = serialNumberExample.value,
    valid_from_date = DateWithDayExampleObject,
    expires_date = DateWithDayExampleObject,
    enabled = true,
    technology = "technology1",
    networks = List("network1", "network2"),
    allows = List(CardAction.CREDIT.toString.toLowerCase, CardAction.DEBIT.toString.toLowerCase),
    account_id = accountIdExample.value,
    replacement = replacementJSON,
    pin_reset = List(pinResetJSON, pinResetJSON1),
    collected = DateWithDayExampleObject,
    posted = DateWithDayExampleObject,
    customer_id = customerIdExample.value,
  )
  
  lazy val physicalCardJsonV310 = PhysicalCardJsonV310(
    card_id = cardIdExample.value,
    bank_id = bankIdExample.value,
    card_number = bankCardNumberExample.value,
    card_type = cardTypeExample.value,
    name_on_card = nameOnCardExample.value,
    issue_number = issueNumberExample.value,
    serial_number = serialNumberExample.value,
    valid_from_date = DateWithDayExampleObject,
    expires_date = DateWithDayExampleObject,
    enabled = true,
    cancelled = true,
    on_hot_list = true,
    technology = "technologyString1",
    networks = List("networks1"),
    allows = List(CardAction.CREDIT.toString.toLowerCase, CardAction.DEBIT.toString.toLowerCase),
    account = accountJSON,
    replacement = replacementJSON,
    pin_reset = List(pinResetJSON),
    collected = DateWithDayExampleObject,
    posted = DateWithDayExampleObject,
    customer_id = customerIdExample.value
  )

  lazy val createAccountResponseJsonV310 = CreateAccountResponseJsonV310(
    account_id = accountIdExample.value,
    user_id = userIdExample.value,
    label   = labelExample.value,
    product_code = productCodeExample.value,
    balance =  amountOfMoneyJsonV121,
    branch_id  = branchIdExample.value,
    account_routings = List(accountRoutingJsonV121),
    account_attributes=  List(accountAttributeResponseJson)
  )
  
  lazy val physicalCardsJsonV310 = PhysicalCardsJsonV310(List(physicalCardJsonV310))
  
  lazy val newModeratedCoreAccountJsonV300 = NewModeratedCoreAccountJsonV300(
    id = accountIdExample.value,
    bank_id= bankIdExample.value,
    label= labelExample.value,
    number= accountNumberExample.value,
    owners =  List(userJSONV121),
    `type`= accountTypeExample.value,
    balance = amountOfMoneyJsonV121,
    account_routings = List(accountRoutingJsonV121),
    views_basic = List(viewBasicV300)
  )  
  lazy val moderatedCoreAccountJsonV400 = ModeratedCoreAccountJsonV400(
    id = accountIdExample.value,
    bank_id= bankIdExample.value,
    label= labelExample.value,
    number= accountNumberExample.value,
    product_code= accountTypeExample.value,
    balance = amountOfMoneyJsonV121,
    account_routings = List(accountRoutingJsonV121),
    views_basic = List(viewIdExample.value)
  )

  lazy val moderatedAccountJSON400 = ModeratedAccountJSON400(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    label = "NoneLabel",
    number = "123",
    owners = List(userJSONV121),
    product_code = productCodeExample.value,
    balance = amountOfMoneyJsonV121,
    views_available = List(viewJSONV121),
    bank_id = bankIdExample.value,
    account_routings = List(accountRoutingJsonV121),
    account_attributes = List(accountAttributeResponseJson),
    tags = List(accountTagJSON)
  )

  lazy val moderatedAccountsJSON400 = ModeratedAccountsJSON400(
    accounts = List(moderatedAccountJSON400)
  )

  lazy val historicalTransactionAccountJsonV310 = HistoricalTransactionAccountJsonV310(
    bank_id = Some(bankIdExample.value),
    account_id = Some(accountIdExample.value),
    counterparty_id = Some(counterpartyIdExample.value)
  )
  
  lazy val postHistoricalTransactionJson = PostHistoricalTransactionJson(
    from = historicalTransactionAccountJsonV310,
    to = historicalTransactionAccountJsonV310,
    value = amountOfMoneyJsonV121,
    description = "this is for work",
    posted = DateWithSecondsExampleString,
    completed= DateWithSecondsExampleString,
    `type`= SANDBOX_TAN.toString,
    charge_policy= chargePolicyExample.value
  )  
  lazy val postHistoricalTransactionAtBankJson = PostHistoricalTransactionAtBankJson(
    from_account_id = "",
    to_account_id = "",
    value = amountOfMoneyJsonV121,
    description = "this is for work",
    posted = DateWithSecondsExampleString,
    completed= DateWithSecondsExampleString,
    `type`= SANDBOX_TAN.toString,
    charge_policy = chargePolicyExample.value
  )

  lazy val postHistoricalTransactionResponseJson = PostHistoricalTransactionResponseJson(
    transaction_id = transactionIdExample.value,
    from = historicalTransactionAccountJsonV310,
    to = historicalTransactionAccountJsonV310,
    value = amountOfMoneyJsonV121,
    description = "this is for work",
    posted = DateWithMsExampleObject,
    completed= DateWithMsExampleObject,
    transaction_request_type= SANDBOX_TAN.toString,
    charge_policy = chargePolicyExample.value
  )
  
  lazy val viewBasicCommons = ViewBasic(
    id = viewIdExample.value,
    name =viewNameExample.value,
    description = viewDescriptionExample.value,
  )
  
  lazy val accountBasicV310 = AccountBasicV310(
    id = accountIdExample.value,
    label = labelExample.value,
    views_available = List(viewBasicCommons),
    bank_id =  bankIdExample.value
  )

  lazy val canGetCustomersJson = ApiRole.canGetCustomers
  
  lazy val cardAttributeCommons = CardAttributeCommons(
    bankId = Some(BankId(bankIdExample.value)),
    cardId = Some(cardIdExample.value),
    cardAttributeId = Some(cardAttributeIdExample.value),
    name = cardAttributeNameExample.value,
    attributeType = CardAttributeType.STRING,
    value = cardAttributeValueExample.value
  )
  
  lazy val physicalCardWithAttributesJsonV310 = PhysicalCardWithAttributesJsonV310(
    card_id = cardIdExample.value,
    bank_id = bankIdExample.value,
    card_number = bankCardNumberExample.value,
    card_type = cardTypeExample.value,
    name_on_card = nameOnCardExample.value,
    issue_number = issueNumberExample.value,
    serial_number = serialNumberExample.value,
    valid_from_date = DateWithDayExampleObject,
    expires_date = DateWithDayExampleObject,
    enabled = true,
    cancelled = true,
    on_hot_list = true,
    technology = "technologyString1",
    networks = List("networks1"),
    allows = List(CardAction.CREDIT.toString.toLowerCase, CardAction.DEBIT.toString.toLowerCase),
    account = accountBasicV310,
    replacement = replacementJSON,
    pin_reset = List(pinResetJSON),
    collected = DateWithDayExampleObject,
    posted = DateWithDayExampleObject,
    customer_id = customerIdExample.value,
    card_attributes = List(cardAttributeCommons)
  )
  lazy val emptyElasticSearch = EmptyElasticSearch(None)
  
  lazy val elasticSearchQuery = ElasticSearchQuery(emptyElasticSearch)
  
  lazy val elasticSearchJsonV300 = ElasticSearchJsonV300(elasticSearchQuery)

  lazy val accountBalanceV310 = AccountBalanceV310(
    id = accountIdExample.value,
    label = labelExample.value,
    bank_id = bankIdExample.value,
    account_routings = List(accountRouting),
    balance = amountOfMoney
  )
  
  lazy val accountBalancesV310Json = AccountsBalancesV310Json(
    accounts = List(accountBalanceV310),
    overall_balance = amountOfMoney,
    overall_balance_date = DateWithMsExampleObject
  )


  lazy val accountBalanceV400 = AccountBalanceJsonV400(
    account_id = accountIdExample.value,
    label = labelExample.value,
    bank_id = bankIdExample.value,
    account_routings = List(accountRouting),
    balances = List(BalanceJsonV400(`type` = "", currency = "EUR", amount = "10"))
  )

  lazy val accountBalancesV400Json = AccountsBalancesJsonV400(
    accounts = List(accountBalanceV400)
  )
  
  lazy val postDirectDebitJsonV400 = PostDirectDebitJsonV400(
    customer_id = customerIdExample.value,
    user_id = userIdExample.value,
    counterparty_id = counterpartyIdExample.value,
    date_signed = Some(DateWithDayExampleObject),
    date_starts = DateWithDayExampleObject,
    date_expires = Some(DateWithDayExampleObject)
  )  
  lazy val directDebitJsonV400 = DirectDebitJsonV400(
    direct_debit_id = "aa0533bd-eb22-4bff-af75-d45240361b05",
    bank_id = bankIdExample.value,
    account_id = accountIdExample.value,
    customer_id = customerIdExample.value,
    user_id = userIdExample.value,
    counterparty_id = counterpartyIdExample.value,
    date_signed = new Date(),
    date_starts = new Date(),
    date_expires = new Date(),
    date_cancelled = new Date(),
    active = true
  )  
  lazy val postStandingOrderJsonV400 = PostStandingOrderJsonV400(
    customer_id = customerIdExample.value,
    user_id = userIdExample.value,
    counterparty_id = counterpartyIdExample.value,
    amount = amountOfMoneyJsonV121,
    when = When(frequency = "YEARLY", detail = "LAST_DAY"),
    date_signed = Some(DateWithDayExampleObject),
    date_starts = DateWithDayExampleObject,
    date_expires = Some(DateWithDayExampleObject)
  )  
  lazy val standingOrderJsonV400 = StandingOrderJsonV400(
    standing_order_id = "aa0533bd-eb22-4bff-af75-d45240361b05",
    bank_id = bankIdExample.value,
    account_id = accountIdExample.value,
    customer_id = customerIdExample.value,
    user_id = userIdExample.value,
    counterparty_id = counterpartyIdExample.value,
    amount = amountOfMoneyJsonV121,
    when = When(frequency = "WEEKLY", detail = "FIRST_DAY"),
    date_signed = new Date(),
    date_starts = new Date(),
    date_expires = new Date(),
    date_cancelled = new Date(),
    active = true
  )

  lazy val createAccountRequestJsonV310 = CreateAccountRequestJsonV310(
    user_id = userIdExample.value,
    label   = labelExample.value,
    product_code = productCodeExample.value,
    balance =  amountOfMoneyJsonV121,
    branch_id  = branchIdExample.value,
    account_routings = List(accountRoutingJsonV121)
  )
  lazy val createAccountRequestJsonV500 = CreateAccountRequestJsonV500(
    user_id = Some(userIdExample.value),
    label   = labelExample.value,
    product_code = productCodeExample.value,
    balance =  Some(amountOfMoneyJsonV121),
    branch_id  = Some(branchIdExample.value),
    account_routings = Some(List(accountRoutingJsonV121))
  )

  lazy val settlementAccountRequestJson = SettlementAccountRequestJson(
    user_id = userIdExample.value,
    payment_system = paymentSystemExample.value,
    balance = amountOfMoneyJsonV121,
    label = labelExample.value,
    branch_id = branchIdExample.value,
    account_routings = List(accountRoutingJsonV121)
  )

  lazy val settlementAccountResponseJson = SettlementAccountResponseJson(
    account_id = accountIdExample.value,
    user_id = userIdExample.value,
    payment_system = paymentSystemExample.value,
    balance = amountOfMoneyJsonV121,
    label = labelExample.value,
    branch_id = branchIdExample.value,
    account_routings = List(accountRoutingJsonV121),
    account_attributes = List(accountAttributeResponseJson)
  )

  lazy val settlementAccountJson = SettlementAccountJson(
    account_id = accountIdExample.value,
    payment_system = paymentSystemExample.value,
    balance = amountOfMoneyJsonV121,
    label = labelExample.value,
    branch_id = branchIdExample.value,
    account_routings = List(accountRoutingJsonV121),
    account_attributes = List(accountAttributeResponseJson)
  )

  lazy val settlementAccountsJson = SettlementAccountsJson(
    settlement_accounts = List(settlementAccountJson)
  )

  lazy val doubleEntryTransactionJson = DoubleEntryTransactionJson(
    transaction_request = TransactionRequestBankAccountJson(
      bank_id = bankIdExample.value,
      account_id = accountIdExample.value,
      transaction_request_id = transactionRequestIdExample.value
    ),
    debit_transaction = TransactionBankAccountJson(
      bank_id = bankIdExample.value,
      account_id = accountIdExample.value,
      transaction_id = transactionIdExample.value
    ),
    credit_transaction = TransactionBankAccountJson(
      bank_id = bankIdExample.value,
      account_id = accountIdExample.value,
      transaction_id = transactionIdExample.value
    )
  )

  lazy val postAccountAccessJsonV400 = PostAccountAccessJsonV400(userIdExample.value, PostViewJsonV400(ExampleValue.viewIdExample.value, true))
  lazy val postCreateUserAccountAccessJsonV400 = PostCreateUserAccountAccessJsonV400(
    usernameExample.value,
    s"dauth.${providerExample.value}",
    List(PostViewJsonV400(viewIdExample.value, isSystemExample.value.toBoolean))
  )
  lazy val postCreateUserWithRolesJsonV400 = PostCreateUserWithRolesJsonV400(
    usernameExample.value,
    s"dauth.${providerExample.value}",
    List(createEntitlementJSON)
  )
  lazy val revokedJsonV400 = RevokedJsonV400(true)

  lazy val postRevokeGrantAccountAccessJsonV400 = PostRevokeGrantAccountAccessJsonV400(List("ReadAccountsBasic"))

  lazy val transactionRequestRefundTo = TransactionRequestRefundTo(
    bank_id = Some(bankIdExample.value),
    account_id = Some(accountIdExample.value),
    counterparty_id = Some(counterpartyIdExample.value)
  )

  lazy val transactionRequestRefundFrom = TransactionRequestRefundFrom(
    counterparty_id = counterpartyIdExample.value
  )
  lazy val transactionRequestBodyRefundJsonV400 = TransactionRequestBodyRefundJsonV400(
    to = Some(transactionRequestRefundTo),
    from = Some(transactionRequestRefundFrom),
    value = amountOfMoneyJsonV121,
    description = "A refund description. ",
    refund = RefundJson(transactionIdExample.value, transactionRequestRefundReasonCodeExample.value)
  )

  lazy val cardJsonV400 = CardJsonV400(
    card_type = cardTypeExample.value,
    brand = brandExample.value,
    cvv = cvvExample.value,
    card_number = cardNumberExample.value,
    name_on_card = nameOnCardExample.value,
    expiry_year = expiryYearExample.value,
    expiry_month = expiryMonthExample.value,
  )
  
  lazy val transactionRequestBodyCardJsonV400 = TransactionRequestBodyCardJsonV400(
    card = cardJsonV400,
    to = counterpartyIdJson,
    value = amountOfMoneyJsonV121,
    description = "A card payment description. "
  )
  lazy val customerAttributesResponseJson = CustomerAttributesResponseJson (
    customer_attributes = List(customerAttributeResponseJson)
  )
  lazy val customerAttributeJsonV400 = CustomerAttributeJsonV400(
    name = customerAttributeNameExample.value,
    `type` = customerAttributeTypeExample.value,
    value = customerAttributeValueExample.value
  )


  lazy val userAttributeResponseJson = UserAttributeResponseJsonV400 (
    user_attribute_id = userAttributeIdExample.value,
    name = userAttributeNameExample.value,
    `type` = userAttributeTypeExample.value,
    value = userAttributeValueExample.value,
    insert_date = new Date()
  )
  lazy val userAttributesResponseJson = UserAttributesResponseJson (
    user_attributes = List(userAttributeResponseJson)
  )

  lazy val userWithAttributesResponseJson = UserWithAttributesResponseJson(user_id = ExampleValue.userIdExample.value,
    email = ExampleValue.emailExample.value,
    provider_id = providerIdValueExample.value,
    provider = providerValueExample.value,
    username = usernameExample.value,
    user_attributes = List(userAttributeResponseJson))
  
  lazy val customerAndUsersWithAttributesResponseJson = CustomerAndUsersWithAttributesResponseJson(
    customer = customerJsonV310, users = List(userWithAttributesResponseJson)
  )
    
  lazy val correlatedUsersResponseJson = CorrelatedEntities(
    correlated_entities = List(customerAndUsersWithAttributesResponseJson)
  )
  
  lazy val userAttributeJsonV400 = UserAttributeJsonV400(
    name = userAttributeNameExample.value,
    `type` = userAttributeTypeExample.value,
    value = userAttributeValueExample.value
  )

  lazy val transactionAttributeResponseJson = TransactionAttributeResponseJson(
    transaction_attribute_id = transactionAttributeIdExample.value,
    name = transactionAttributeNameExample.value,
    `type` = transactionAttributeTypeExample.value,
    value = transactionAttributeValueExample.value
  )

  lazy val transactionAttributesResponseJson =  TransactionAttributesResponseJson(
    transaction_attributes = List(transactionAttributeResponseJson)
  )

  lazy val transactionAttributeJsonV400 = TransactionAttributeJsonV400(
    name = transactionAttributeNameExample.value,
    `type` = transactionAttributeTypeExample.value,
    value = transactionAttributeValueExample.value
  )

  lazy val transactionRequestAttributeResponseJson = TransactionRequestAttributeResponseJson(
    transaction_request_attribute_id = transactionRequestAttributeIdExample.value,
    name = transactionRequestAttributeNameExample.value,
    `type` = transactionRequestAttributeTypeExample.value,
    value = transactionRequestAttributeValueExample.value
  )

  lazy val transactionRequestAttributesResponseJson = TransactionRequestAttributesResponseJson(
    transaction_request_attributes = List(transactionRequestAttributeResponseJson)
  )
  
  lazy val templateAttributeDefinitionJsonV400 = AttributeDefinitionJsonV400(
    name = customerAttributeNameExample.value,
    category = AttributeCategory.Customer.toString,
    `type` = customerAttributeTypeExample.value,
    description = "description",
    can_be_seen_on_views = List("bank"),
    alias = attributeAliasExample.value,
    is_active = true
  )  
  lazy val templateAttributeDefinitionResponseJsonV400 = AttributeDefinitionResponseJsonV400(
    attribute_definition_id = uuidExample.value,
    bank_id = bankIdExample.value,
    name = templateAttributeNameExample.value,
    category = AttributeCategory.Customer.toString,
    `type` = templateAttributeTypeExample.value,
    description = "description",
    can_be_seen_on_views = List("bank"),
    alias = attributeAliasExample.value,
    is_active = true
  ) 
  
  lazy val customerAttributeDefinitionJsonV400 =
    templateAttributeDefinitionJsonV400.copy(category = AttributeCategory.Customer.toString)
    
  lazy val customerAttributeDefinitionResponseJsonV400 =
    templateAttributeDefinitionResponseJsonV400.copy(category = AttributeCategory.Customer.toString)
  
  lazy val accountAttributeDefinitionJsonV400 =
    templateAttributeDefinitionJsonV400.copy(category = AttributeCategory.Account.toString)
    
  lazy val accountAttributeDefinitionResponseJsonV400 =
    templateAttributeDefinitionResponseJsonV400.copy(category = AttributeCategory.Account.toString)
  
  lazy val productAttributeDefinitionJsonV400 =
    templateAttributeDefinitionJsonV400.copy(category = AttributeCategory.Product.toString)
  
  lazy val bankAttributeDefinitionJsonV400 =
    templateAttributeDefinitionJsonV400.copy(category = AttributeCategory.Bank.toString)
  
  lazy val productAttributeDefinitionResponseJsonV400 =
    templateAttributeDefinitionResponseJsonV400.copy(category = AttributeCategory.Product.toString)
  
  lazy val bankAttributeDefinitionResponseJsonV400 =
    templateAttributeDefinitionResponseJsonV400.copy(category = AttributeCategory.Bank.toString)
  
  lazy val transactionAttributeDefinitionJsonV400 = 
    templateAttributeDefinitionJsonV400.copy(category = AttributeCategory.Transaction.toString)
  
  lazy val transactionAttributeDefinitionResponseJsonV400 =
    templateAttributeDefinitionResponseJsonV400.copy(category = AttributeCategory.Transaction.toString)
  
  lazy val cardAttributeDefinitionJsonV400 = 
    templateAttributeDefinitionJsonV400.copy(category = AttributeCategory.Card.toString)
  
  lazy val cardAttributeDefinitionResponseJsonV400 =
    templateAttributeDefinitionResponseJsonV400.copy(category = AttributeCategory.Card.toString)
    
  lazy val transactionAttributeDefinitionsResponseJsonV400 = AttributeDefinitionsResponseJsonV400(
    attributes = List(transactionAttributeDefinitionResponseJsonV400)
  )    
  lazy val cardAttributeDefinitionsResponseJsonV400 = AttributeDefinitionsResponseJsonV400(
    attributes = List(cardAttributeDefinitionResponseJsonV400)
  )

  lazy val transactionRequestAttributeDefinitionJsonV400 =
    templateAttributeDefinitionJsonV400.copy(category = AttributeCategory.TransactionRequest.toString)

  lazy val transactionRequestAttributeDefinitionResponseJsonV400 =
    templateAttributeDefinitionResponseJsonV400.copy(category = AttributeCategory.TransactionRequest.toString)

  lazy val transactionRequestAttributeDefinitionsResponseJsonV400 = AttributeDefinitionsResponseJsonV400(
    attributes = List(transactionRequestAttributeDefinitionResponseJsonV400)
  )
  
  lazy val accountAttributeDefinitionsResponseJsonV400 = AttributeDefinitionsResponseJsonV400(
    attributes = List(accountAttributeDefinitionResponseJsonV400)
  )    
  lazy val customerAttributeDefinitionsResponseJsonV400 = AttributeDefinitionsResponseJsonV400(
    attributes = List(templateAttributeDefinitionResponseJsonV400)
  )    
  lazy val productAttributeDefinitionsResponseJsonV400 = AttributeDefinitionsResponseJsonV400(
    attributes = List(productAttributeDefinitionResponseJsonV400)
  )
  lazy val challengeJsonV400 = ChallengeJsonV400(
    id = transactionIdExample.value,
    user_id = userIdExample.value,
    allowed_attempts =3,
    challenge_type = ChallengeType.OBP_TRANSACTION_REQUEST_CHALLENGE.toString,
    link = "/obp/v4.0.0/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transaction-request-types/TRANSACTION_REQUEST_TYPE/transaction-requests/TRANSACTION_REQUEST_ID/challenge"
  )
  lazy val transactionRequestWithChargeJSON400 = TransactionRequestWithChargeJSON400(
    id = "4050046c-63b3-4868-8a22-14b4181d33a6",
    `type` = SANDBOX_TAN.toString,
    from = transactionRequestAccountJsonV140,
    details = transactionRequestBodyAllTypes,
    transaction_ids = List("902ba3bb-dedd-45e7-9319-2fd3f2cd98a1"),
    status = TransactionRequestStatus.COMPLETED.toString,
    start_date = DateWithDayExampleObject,
    end_date = DateWithDayExampleObject,
    challenges = List(challengeJsonV400),
    charge = transactionRequestChargeJsonV200,
    attributes=Some(List(bankAttributeBankResponseJsonV400)),
  )

  lazy val postSimpleCounterpartyJson400 = PostSimpleCounterpartyJson400(
    name = counterpartyNameExample.value,
    description = transactionDescriptionExample.value,
    other_account_routing_scheme = counterpartyOtherAccountRoutingSchemeExample.value,
    other_account_routing_address = counterpartyOtherAccountRoutingAddressExample.value,
    other_account_secondary_routing_scheme = counterpartyOtherAccountSecondaryRoutingSchemeExample.value,
    other_account_secondary_routing_address = counterpartyOtherAccountSecondaryRoutingAddressExample.value,
    other_bank_routing_scheme = counterpartyOtherBankRoutingSchemeExample.value,
    other_bank_routing_address = counterpartyOtherBankRoutingAddressExample.value,
    other_branch_routing_scheme = counterpartyOtherBranchRoutingSchemeExample.value,
    other_branch_routing_address = counterpartyOtherBranchRoutingAddressExample.value
  )
  
  lazy val transactionRequestBodySimpleJsonV400 = TransactionRequestBodySimpleJsonV400(
    to= postSimpleCounterpartyJson400,
    amountOfMoneyJsonV121,
    descriptionExample.value,
    chargePolicyExample.value,
    Some(futureDateExample.value)
  )
  
  lazy val postApiCollectionJson400 = PostApiCollectionJson400(apiCollectionNameExample.value, true, Some(descriptionExample.value))
  
  lazy val apiCollectionJson400 = ApiCollectionJson400(apiCollectionIdExample.value, userIdExample.value, apiCollectionNameExample.value, true, descriptionExample.value)
  lazy val apiCollectionsJson400 = ApiCollectionsJson400(List(apiCollectionJson400))

  lazy val postApiCollectionEndpointJson400 = PostApiCollectionEndpointJson400(operationIdExample.value)

  lazy val apiCollectionEndpointJson400 = ApiCollectionEndpointJson400(apiCollectionEndpointIdExample.value, apiCollectionIdExample.value, operationIdExample.value)
  lazy val apiCollectionEndpointsJson400 = ApiCollectionEndpointsJson400(List(apiCollectionEndpointJson400))

  lazy val jsonScalaConnectorMethod  = JsonConnectorMethod(Some(connectorMethodIdExample.value),"getBank", connectorMethodBodyScalaExample.value, "Scala")
  lazy val jsonScalaConnectorMethodMethodBody  = JsonConnectorMethodMethodBody(connectorMethodBodyScalaExample.value, "Scala")
  
  lazy val jsonJavaConnectorMethod  = JsonConnectorMethod(Some(connectorMethodIdExample.value),"getBank", connectorMethodBodyJavaExample.value, "Java")
  lazy val jsonJavaConnectorMethodMethodBody  = JsonConnectorMethodMethodBody(connectorMethodBodyJavaExample.value, "Java")
  
  lazy val jsonJsConnectorMethod  = JsonConnectorMethod(Some(connectorMethodIdExample.value),"getBank", connectorMethodBodyJsExample.value, "Js")
  lazy val jsonJsConnectorMethodMethodBody  = JsonConnectorMethodMethodBody(connectorMethodBodyJsExample.value, "Js")
  
  lazy val jsonDynamicResourceDoc = JsonDynamicResourceDoc(
    bankId = Some(bankIdExample.value),
    dynamicResourceDocId = Some(dynamicResourceDocIdExample.value),
    methodBody = dynamicResourceDocMethodBodyExample.value,
    partialFunctionName = dynamicResourceDocPartialFunctionNameExample.value,
    requestVerb = requestVerbExample.value, 
    requestUrl = requestUrlExample.value, 
    summary = dynamicResourceDocSummaryExample.value, 
    description = dynamicResourceDocDescriptionExample.value, 
    exampleRequestBody = Option(json.parse(exampleRequestBodyExample.value)),
    successResponseBody = Option(json.parse(successResponseBodyExample.value)),
    errorResponseBodies = errorResponseBodiesExample.value, 
    tags = tagsExample.value, 
    roles = rolesExample.value
  )
  
  lazy val jsonDynamicMessageDoc = JsonDynamicMessageDoc(
    bankId = Some(bankIdExample.value),
    dynamicMessageDocId = Some(dynamicMessageDocIdExample.value),
    process = processExample.value,
    messageFormat = messageFormatExample.value,
    description = descriptionExample.value,
    outboundTopic = outboundTopicExample.value,
    inboundTopic = inboundTopicExample.value,
    exampleOutboundMessage = json.parse(exampleOutboundMessageExample.value),
    exampleInboundMessage = json.parse(exampleInboundMessageExample.value),
    outboundAvroSchema = outboundAvroSchemaExample.value,
    inboundAvroSchema = inboundAvroSchemaExample.value,
    adapterImplementation = adapterImplementationExample.value,
    methodBody = connectorMethodBodyScalaExample.value,
    programmingLang = connectorMethodLangExample.value
  )

  lazy val jsonResourceDocFragment = ResourceDocFragment(
    requestVerbExample.value,
    requestUrlExample.value,
    exampleRequestBody = Option(json.parse(exampleRequestBodyExample.value)),
    successResponseBody = Option(json.parse(successResponseBodyExample.value))
  )

  lazy val jsonCodeTemplateJson = JsonCodeTemplateJson(
    URLEncoder.encode("""println("hello")""", "UTF-8")
  )

  lazy val supportedCurrenciesJson = SupportedCurrenciesJson(
    supportedCurrenciesExample.value
      .replaceAll(""""""","").replace("""[""","")
      .replace("""]""","").split(",").toList
  ) 
  
  lazy val atmSupportedCurrenciesJson = AtmSupportedCurrenciesJson(
    atmIdExample.value,
    supportedCurrenciesExample.value.replaceAll(""""""","")
      .replace("""[""","")
      .replace("""]""","")
      .split(",").toList
  )

  lazy val supportedLanguagesJson = SupportedLanguagesJson(
    supportedLanguagesExample.value
      .replaceAll(""""""","").replace("""[""","")
      .replace("""]""","").split(",").toList
  ) 
  
  lazy val atmSupportedLanguagesJson = AtmSupportedLanguagesJson(
    atmIdExample.value,
    supportedLanguagesExample.value.replaceAll(""""""","")
      .replace("""[""","")
      .replace("""]""","")
      .split(",").toList
  )
  

  lazy val accessibilityFeaturesJson = AccessibilityFeaturesJson(
    accessibilityFeaturesExample.value
      .replaceAll(""""""","").replace("""[""","")
      .replace("""]""","").split(",").toList
  ) 
  
  lazy val atmAccessibilityFeaturesJson = AtmAccessibilityFeaturesJson(
    atmIdExample.value,
    accessibilityFeaturesExample.value.replaceAll(""""""","")
      .replace("""[""","")
      .replace("""]""","")
      .split(",").toList
  )


  lazy val atmServicesJson = AtmServicesJsonV400(
    atmServicesExample.value
      .replaceAll(""""""","").replace("""[""","")
      .replace("""]""","").split(",").toList
  )

  lazy val atmServicesResponseJson = AtmServicesResponseJsonV400(
    atmIdExample.value,
    atmServicesExample.value.replaceAll(""""""","")
      .replace("""[""","")
      .replace("""]""","")
      .split(",").toList
  )


  lazy val atmNotesJson = AtmNotesJsonV400(
    atmNotesExample.value
      .replaceAll(""""""","").replace("""[""","")
      .replace("""]""","").split(",").toList
  )

  lazy val atmNotesResponseJson = AtmNotesResponseJsonV400(
    atmIdExample.value,
    atmNotesExample.value.replaceAll(""""""","")
      .replace("""[""","")
      .replace("""]""","")
      .split(",").toList
  )
  

  lazy val atmLocationCategoriesJsonV400 = AtmLocationCategoriesJsonV400(
    atmLocationCategoriesExample.value
      .replaceAll(""""""","").replace("""[""","")
      .replace("""]""","").split(",").toList
  )

  lazy val atmLocationCategoriesResponseJsonV400 = AtmLocationCategoriesResponseJsonV400(
    atmIdExample.value,
    atmLocationCategoriesExample.value.replaceAll(""""""","")
      .replace("""[""","")
      .replace("""]""","")
      .split(",").toList
  )
  lazy val atmJsonV400 = AtmJsonV400(
    id = Some(atmIdExample.value),
    bank_id = bankIdExample.value,
    name = atmNameExample.value,
    address = addressJsonV300,
    location = locationJson,
    meta = metaJson,
    monday = openingTimesV300,
    tuesday = openingTimesV300,
    wednesday = openingTimesV300,
    thursday = openingTimesV300,
    friday = openingTimesV300,
    saturday = openingTimesV300,
    sunday = openingTimesV300,

    is_accessible = isAccessibleExample.value,
    located_at = locatedAtExample.value,
    more_info = moreInfoExample.value,
    has_deposit_capability=hasDepositCapabilityExample.value,

    supported_languages = supportedLanguagesJson.supported_languages,
    services = atmServicesJson.services,
    accessibility_features = accessibilityFeaturesJson.accessibility_features,
    supported_currencies = supportedCurrenciesJson.supported_currencies,
    notes = atmNotesJson.notes,
    location_categories = atmLocationCategoriesJsonV400.location_categories,
    minimum_withdrawal = atmMinimumWithdrawalExample.value,
    branch_identification = atmBranchIdentificationExample.value,
    site_identification = siteIdentification.value,
    site_name = atmSiteNameExample.value,
    cash_withdrawal_national_fee = cashWithdrawalNationalFeeExample.value,
    cash_withdrawal_international_fee = cashWithdrawalInternationalFeeExample.value,
    balance_inquiry_fee = balanceInquiryFeeExample.value
  )

  lazy val atmsJsonV400 = AtmsJsonV400(List(atmJsonV400))

  lazy val productFeeValueJsonV400 =  ProductFeeValueJsonV400(
    currency = currencyExample.value,
    amount = 10.12,
    frequency = frequencyExample.value,
    `type` = typeExample.value
  )

  lazy val productFeeJsonV400 = ProductFeeJsonV400(
    product_fee_id = Some(productFeeIdExample.value),
    name = nameExample.value,
    is_active = true,
    more_info = moreInfoExample.value,
    value = productFeeValueJsonV400 
  )
  
  lazy val productFeeResponseJsonV400 = ProductFeeResponseJsonV400(
    bank_id = bankIdExample.value,
    product_code = productCodeExample.value,
    product_fee_id = productFeeIdExample.value,
    name = nameExample.value,
    is_active = true,
    more_info = moreInfoExample.value,
    value = productFeeValueJsonV400
  )
  
  lazy val productFeesResponseJsonV400 = ProductFeesResponseJsonV400(List(productFeeResponseJsonV400))
  
  
  lazy val productJsonV400 = ProductJsonV400(
    bank_id = bankIdExample.value,
    product_code = productCodeExample.value,
    parent_product_code = parentProductCodeExample.value,
    name = productNameExample.value,
    more_info_url = moreInfoUrlExample.value,
    terms_and_conditions_url = termsAndConditionsUrlExample.value,
    description = descriptionExample.value,
    meta = metaJson,
    attributes = Some(List(productAttributeResponseJson)),
    fees = Some(List(productFeeJsonV400))
  )

  lazy val productsJsonV400 = ProductsJsonV400(products = List(productJsonV400.copy(attributes = None, fees = None)))

  lazy val putProductJsonV400 = PutProductJsonV400(
    parent_product_code = parentProductCodeExample.value,
    name = productNameExample.value,
    more_info_url = moreInfoUrlExample.value,
    terms_and_conditions_url = termsAndConditionsUrlExample.value,
    description = descriptionExample.value,
    meta = metaJson,
  )
  lazy val putProductJsonV500 = PutProductJsonV500(
    parent_product_code = parentProductCodeExample.value,
    name = productNameExample.value,
    more_info_url = Some(moreInfoUrlExample.value),
    terms_and_conditions_url = Some(termsAndConditionsUrlExample.value),
    description = Some(descriptionExample.value),
    meta = Some(metaJson)
  )

  lazy val createMessageJsonV400 = CreateMessageJsonV400(
    message = messageExample.value,
    transport = transportExample.value,
    from_department = fromDepartmentExample.value,
    from_person = fromPersonExample.value,
  )

  lazy val customerMessageJsonV400 = CustomerMessageJsonV400(
    id = customerMessageId.value,
    date = DateWithDayExampleObject,
    transport = transportExample.value,
    message = messageExample.value,
    from_department = fromDepartmentExample.value,
    from_person = fromPersonExample.value,
  )

  lazy val customerMessagesJsonV400 = CustomerMessagesJsonV400(
    messages = List(customerMessageJsonV400)
  )

  lazy val requestRootJsonClass = PractiseEndpoint.RequestRootJsonClass(name = nameExample.value, age=ageExample.value.toLong, Nil)
  
  lazy val entitlementJsonV400 = EntitlementJsonV400(
    entitlement_id = entitlementIdExample.value,
    role_name = roleNameExample.value,
    bank_id = bankIdExample.value,
    user_id = userIdExample.value,
  )

  lazy val entitlementsJsonV400 =  EntitlementsJsonV400(
    list = List(entitlementJsonV400)
  )
  
  lazy val accountNotificationWebhookPostJson = AccountNotificationWebhookPostJson(
    url = "https://localhost.openbankproject.com",
    http_method = "POST",
    http_protocol = "HTTP/1.1"
  )

  lazy val systemAccountNotificationWebhookJson =  SystemAccountNotificationWebhookJson(
    webhook_id = "fc23a7e2-7dd2-4bdf-a0b4-ae31232a4762",
    trigger_name = ApiTrigger.onCreateTransaction.toString(),
    url = "https://localhost.openbankproject.com",
    http_method = "POST",
    http_protocol = "HTTP/1.1",
    created_by_user_id = ExampleValue.userIdExample.value
  )

  lazy val bankAccountNotificationWebhookJson =  BankAccountNotificationWebhookJson(
    webhook_id = "fc23a7e2-7dd2-4bdf-a0b4-ae31232a4762",
    bank_id = bankIdExample.value,
    trigger_name = ApiTrigger.onCreateTransaction.toString(),
    url = "https://localhost.openbankproject.com",
    http_method = "POST",
    http_protocol = "HTTP/1.1",
    created_by_user_id = ExampleValue.userIdExample.value
  )

  lazy val userAuthContextJsonV500 = UserAuthContextJsonV500(
    user_auth_context_id = "613c83ea-80f9-4560-8404-b9cd4ec42a7f",
    user_id = ExampleValue.userIdExample.value,
    key = "CUSTOMER_NUMBER",
    value = "78987432",
    time_stamp = parseDate(timeStampExample.value).getOrElse(sys.error("timeStampExample.value is not validate date format.")),
    consumer_id = consumerIdExample.value
  )

  lazy val userAuthContextUpdateJsonV500 = UserAuthContextUpdateJsonV500(
    user_auth_context_update_id = "613c83ea-80f9-4560-8404-b9cd4ec42a7f",
    user_id = ExampleValue.userIdExample.value,
    key = "CUSTOMER_NUMBER",
    value = "78987432",
    status = UserAuthContextUpdateStatus.INITIATED.toString,
    consumer_id = consumerIdExample.value
  )
  
  lazy val consentRequestResponseJson = ConsentRequestResponseJson(
    consent_request_id = consentRequestIdExample.value,
    payload = json.parse(consentRequestPayloadExample.value), 
    consumer_id = consumerIdExample.value
    )
  
  lazy val vrpConsentRequestResponseJson = ConsentRequestResponseJson(
    consent_request_id = consentRequestIdExample.value,
    payload = json.parse(vrpConsentRequestPayloadExample.value), 
    consumer_id = consumerIdExample.value
  )
  
  lazy val consentAccountAccessJson = ConsentAccountAccessJson(
    bank_id = bankIdExample.value,
    account_id = accountIdExample.value,
    view_id = viewIdExample.value,
    helper_info = Some(helperInfoJson)
  )
  
  lazy val consentJsonV500 = ConsentJsonV500(
    consent_id = "9d429899-24f5-42c8-8565-943ffa6a7945",
    jwt = jwtExample.value,
    status = ConsentStatus.INITIATED.toString,
    consent_request_id = Some(consentRequestIdExample.value),
    account_access= Some(consentAccountAccessJson)
  )
  lazy val consentJsonV510 = ConsentJsonV510(
    consent_id = "9d429899-24f5-42c8-8565-943ffa6a7945",
    jwt = jwtExample.value,
    status = ConsentStatus.INITIATED.toString,
    consent_request_id = Some(consentRequestIdExample.value),
    scopes = Some(List(roleJsonV510)),
    consumer_id= consumerIdExample.value
  )
  
  lazy val postConsentRequestJsonV500 = PostConsentRequestJsonV500(
    everything = false,
    bank_id = None,
    account_access = List(AccountAccessV500(
      account_routing = accountRoutingJsonV121,
      view_id = viewIdExample.value
      )),
    entitlements = Some(List(PostConsentEntitlementJsonV310(bankIdExample.value, "CanGetCustomer"))),
    consumer_id = Some(consumerIdExample.value),
    phone_number = Some(mobileNumberExample.value),
    email =  Some(emailExample.value),
    valid_from = Some(new Date()),
    time_to_live = Some(3600)
    )

  lazy val createPhysicalCardJsonV500 = CreatePhysicalCardJsonV500(
    card_number = bankCardNumberExample.value,
    card_type = cardTypeExample.value,
    name_on_card = nameOnCardExample.value,
    issue_number = issueNumberExample.value,
    serial_number = serialNumberExample.value,
    valid_from_date = DateWithDayExampleObject,
    expires_date = DateWithDayExampleObject,
    enabled = true,
    technology = technologyExample.value,
    networks = networksExample.value.split("[,;]").toList,
    allows = List(CardAction.CREDIT.toString.toLowerCase, CardAction.DEBIT.toString.toLowerCase),
    account_id =accountIdExample.value,
    replacement = Some(replacementJSON),
    pin_reset = List(pinResetJSON, pinResetJSON1),
    collected = Some(DateWithDayExampleObject),
    posted = Some(DateWithDayExampleObject),
    customer_id = customerIdExample.value,
    brand = brandExample.value
  )

  lazy val physicalCardJsonV500 = PhysicalCardJsonV500(
    card_id = cardIdExample.value,
    bank_id = bankIdExample.value,
    card_number = bankCardNumberExample.value,
    card_type = cardTypeExample.value,
    name_on_card = nameOnCardExample.value,
    issue_number = issueNumberExample.value,
    serial_number = serialNumberExample.value,
    valid_from_date = DateWithDayExampleObject,
    expires_date = DateWithDayExampleObject,
    enabled = true,
    cancelled = true,
    on_hot_list = true,
    technology = technologyExample.value,
    networks = networksExample.value.split("[,;]").toList,
    allows = List(CardAction.CREDIT.toString.toLowerCase, CardAction.DEBIT.toString.toLowerCase),
    account = accountJSON,
    replacement = replacementJSON,
    pin_reset = List(pinResetJSON),
    collected = DateWithDayExampleObject,
    posted = DateWithDayExampleObject,
    customer_id = customerIdExample.value,
    cvv = cvvExample.value,
    brand = brandExample.value
  )

  lazy val physicalCardWithAttributesJsonV500 = PhysicalCardWithAttributesJsonV500(
    card_id = cardIdExample.value,
    bank_id = bankIdExample.value,
    card_number = bankCardNumberExample.value,
    card_type = cardTypeExample.value,
    name_on_card = nameOnCardExample.value,
    issue_number = issueNumberExample.value,
    serial_number = serialNumberExample.value,
    valid_from_date = DateWithDayExampleObject,
    expires_date = DateWithDayExampleObject,
    enabled = true,
    cancelled = true,
    on_hot_list = true,
    technology = technologyExample.value,
    networks = networksExample.value.split("[,;]").toList,
    allows = List(CardAction.CREDIT.toString.toLowerCase, CardAction.DEBIT.toString.toLowerCase),
    account = accountBasicV310,
    replacement = replacementJSON,
    pin_reset = List(pinResetJSON),
    collected = DateWithDayExampleObject,
    posted = DateWithDayExampleObject,
    customer_id = customerIdExample.value,
    card_attributes = List(cardAttributeCommons),
    brand = brandExample.value
  )

  lazy val createCustomerAccountLinkJson =  CreateCustomerAccountLinkJson(
    customer_id = customerIdExample.value,
    bank_id = bankIdExample.value,
    account_id = accountIdExample.value,
    relationship_type= relationshipTypeExample.value
  )
  lazy val updateCustomerAccountLinkJson = UpdateCustomerAccountLinkJson(
    relationship_type= relationshipTypeExample.value
  )

  lazy val customerAccountLinkJson =  CustomerAccountLinkJson(
    customer_account_link_id = customerAccountLinkIdExample.value,
    customer_id = customerIdExample.value,
    bank_id = bankIdExample.value,
    account_id = accountIdExample.value,
    relationship_type= relationshipTypeExample.value
  )

  lazy val customerAccountLinksJson =  CustomerAccountLinksJson(
    List(customerAccountLinkJson)
  )
  
  lazy val inboundStatusMessage = InboundStatusMessage(
    source = sourceExample.value,
    status = statusExample.value,
    errorCode = errorCodeExample.value,
    text = textExample.value,
    duration = Some (BigDecimal(durationExample.value))
  )
  
  lazy val adapterInfoJsonV500 = AdapterInfoJsonV500(
    name = nameExample.value,
    version = versionExample.value,
    git_commit = gitCommitExample.value,
    date = dateExample.value,
    total_duration = BigDecimal(durationExample.value),
    backend_messages= List(inboundStatusMessage),
  )


  lazy val atmJsonV510 = AtmJsonV510(
    id = Some(atmIdExample.value),
    bank_id = bankIdExample.value,
    name = atmNameExample.value,
    address = addressJsonV300,
    location = locationJson,
    meta = metaJson,
    monday = openingTimesV300,
    tuesday = openingTimesV300,
    wednesday = openingTimesV300,
    thursday = openingTimesV300,
    friday = openingTimesV300,
    saturday = openingTimesV300,
    sunday = openingTimesV300,
    is_accessible = isAccessibleExample.value,
    located_at = locatedAtExample.value,
    more_info = moreInfoExample.value,
    has_deposit_capability = hasDepositCapabilityExample.value,
    supported_languages = supportedLanguagesJson.supported_languages,
    services = atmServicesJson.services,
    accessibility_features = accessibilityFeaturesJson.accessibility_features,
    supported_currencies = supportedCurrenciesJson.supported_currencies,
    notes = atmNotesJson.notes,
    location_categories = atmLocationCategoriesJsonV400.location_categories,
    minimum_withdrawal = atmMinimumWithdrawalExample.value,
    branch_identification = atmBranchIdentificationExample.value,
    site_identification = siteIdentification.value,
    site_name = atmSiteNameExample.value,
    cash_withdrawal_national_fee = cashWithdrawalNationalFeeExample.value,
    cash_withdrawal_international_fee = cashWithdrawalInternationalFeeExample.value,
    balance_inquiry_fee = balanceInquiryFeeExample.value,
    atm_type = atmTypeExample.value,
    phone = phoneExample.value,

    attributes = Some(List(atmAttributeResponseJsonV510))
  )

  lazy val userAttributeJsonV510 = UserAttributeJsonV510(
    name = userAttributeNameExample.value,
    `type` = userAttributeTypeExample.value,
    value = userAttributeValueExample.value
  )

  lazy val userAttributeResponseJsonV510 = UserAttributeResponseJsonV510(
    user_attribute_id = userAttributeIdExample.value,
    name = userAttributeNameExample.value,
    `type` = userAttributeTypeExample.value,
    value = userAttributeValueExample.value,
    is_personal = userAttributeIsPersonalExample.value.toBoolean,
    insert_date = new Date()
  )
  
  lazy val postAtmJsonV510 = PostAtmJsonV510(
      id = Some(atmIdExample.value),
      bank_id = bankIdExample.value,
      name = atmNameExample.value,
      address = addressJsonV300,
      location = locationJson,
      meta = metaJson,
      monday = openingTimesV300,
      tuesday = openingTimesV300,
      wednesday = openingTimesV300,
      thursday = openingTimesV300,
      friday = openingTimesV300,
      saturday = openingTimesV300,
      sunday = openingTimesV300,
      is_accessible = isAccessibleExample.value,
      located_at = locatedAtExample.value,
      more_info = moreInfoExample.value,
      has_deposit_capability = hasDepositCapabilityExample.value,
      supported_languages = supportedLanguagesJson.supported_languages,
      services = atmServicesJson.services,
      accessibility_features = accessibilityFeaturesJson.accessibility_features,
      supported_currencies = supportedCurrenciesJson.supported_currencies,
      notes = atmNotesJson.notes,
      location_categories = atmLocationCategoriesJsonV400.location_categories,
      minimum_withdrawal = atmMinimumWithdrawalExample.value,
      branch_identification = atmBranchIdentificationExample.value,
      site_identification = siteIdentification.value,
      site_name = atmSiteNameExample.value,
      cash_withdrawal_national_fee = cashWithdrawalNationalFeeExample.value,
      cash_withdrawal_international_fee = cashWithdrawalInternationalFeeExample.value,
      balance_inquiry_fee = balanceInquiryFeeExample.value,
      atm_type = atmTypeExample.value,
      phone = phoneExample.value,
  )
  
  lazy val postCounterpartyLimitV510 = PostCounterpartyLimitV510(
    currency = currencyExample.value,
    max_single_amount = maxSingleAmountExample.value,
    max_monthly_amount = maxMonthlyAmountExample.value,
    max_number_of_monthly_transactions = maxNumberOfMonthlyTransactionsExample.value.toInt,
    max_yearly_amount = maxYearlyAmountExample.value,
    max_number_of_yearly_transactions = maxNumberOfYearlyTransactionsExample.value.toInt,
    max_total_amount = maxTotalAmountExample.value,
    max_number_of_transactions = maxNumberOfTransactionsExample.value.toInt
  )
  
  lazy val counterpartyLimitV510 = CounterpartyLimitV510(
    counterparty_limit_id = counterpartyLimitIdExample.value,
    bank_id = bankIdExample.value,
    account_id = accountIdExample.value,
    view_id = viewIdExample.value,
    counterparty_id = counterpartyIdExample.value,
    currency = currencyExample.value,
    max_single_amount = maxSingleAmountExample.value,
    max_monthly_amount = maxMonthlyAmountExample.value,
    max_number_of_monthly_transactions = maxNumberOfMonthlyTransactionsExample.value.toInt,
    max_yearly_amount = maxYearlyAmountExample.value,
    max_number_of_yearly_transactions = maxNumberOfYearlyTransactionsExample.value.toInt,
    max_total_amount = maxTotalAmountExample.value,
    max_number_of_transactions = maxNumberOfTransactionsExample.value.toInt
  )

  lazy val counterpartyLimitStatus =  CounterpartyLimitStatus(
    currency_status = currencyExample.value,
    max_monthly_amount_status = maxSingleAmountExample.value,
    max_number_of_monthly_transactions_status = maxNumberOfMonthlyTransactionsExample.value.toInt,
    max_yearly_amount_status = maxYearlyAmountExample.value,
    max_number_of_yearly_transactions_status = maxNumberOfYearlyTransactionsExample.value.toInt,
    max_total_amount_status = maxTotalAmountExample.value,
    max_number_of_transactions_status = maxNumberOfTransactionsExample.value.toInt
  )

  lazy val counterpartyLimitStatusV510 = CounterpartyLimitStatusV510(
    counterparty_limit_id = counterpartyLimitIdExample.value,
    bank_id = bankIdExample.value,
    account_id = accountIdExample.value,
    view_id = viewIdExample.value,
    counterparty_id = counterpartyIdExample.value,
    currency = currencyExample.value,
    max_single_amount = maxSingleAmountExample.value,
    max_monthly_amount = maxMonthlyAmountExample.value,
    max_number_of_monthly_transactions = maxNumberOfMonthlyTransactionsExample.value.toInt,
    max_yearly_amount = maxYearlyAmountExample.value,
    max_number_of_yearly_transactions = maxNumberOfYearlyTransactionsExample.value.toInt,
    max_total_amount = maxTotalAmountExample.value,
    max_number_of_transactions = maxNumberOfTransactionsExample.value.toInt,
    status = counterpartyLimitStatus
  )
  
  lazy val atmsJsonV510 = AtmsJsonV510(
    atms = List(atmJsonV510)
  )
  
  lazy val postAccountAccessJsonV510 = PostAccountAccessJsonV510(userIdExample.value,viewIdExample.value)

  lazy val consentRequestFromAccountJson = ConsentRequestFromAccountJson (
    bank_routing = bankRoutingJsonV121,
    account_routing = accountRoutingJsonV121,
    branch_routing = branchRoutingJsonV141
  )

  lazy val consentRequestToAccountJson = ConsentRequestToAccountJson (
    counterparty_name = counterpartyNameExample.value,
    bank_routing = bankRoutingJsonV121,
    account_routing = accountRoutingJsonV121,
    branch_routing = branchRoutingJsonV141,
    limit = postCounterpartyLimitV510
  )

  lazy val postVRPConsentRequestJsonV510 = PostVRPConsentRequestJsonV510(
    from_account = consentRequestFromAccountJson,
    to_account = consentRequestToAccountJson,
    email = Some(emailExample.value),
    phone_number = Some(mobileNumberExample.value),
    valid_from = Some(new Date()),
    time_to_live = Some(3600)
  )

  lazy val consumerLogoUrlJson = ConsumerLogoUrlJson(
    "http://localhost:8888"
  )
  lazy val consumerCertificateJson = ConsumerCertificateJson(
    "QmFnIEF0dHJpYnV0ZXMNCiAgICBsb2NhbEtleUlEOiBFMSA3RiBCMyBCOCBEQiA4QyA2NCBGNiA4QyA1NSAzNCA3QSAyNiBCRSBEMCBCNCBENCBBMyBGRCA2NiANCnN1YmplY3Q9QyA9IE1ELCBPID0gTUFJQiwgQ04gPSBNQUlCIFByaXNhY2FydSBTZXJnaXUgKFRlc3QpDQoNCmlzc3Vlcj1DID0gTUQsIE8gPSBCTk0sIE9VID0gRFRJLCBDTiA9IEJOTSBDQSAodGVzdCksIGVtYWlsQWRkcmVzcyA9IGFkbWluQGJubS5tZA0KDQotLS0tLUJFR0lOIENFUlRJRklDQVRFLS0tLS0NCk1JSUdoVENDQkcyZ0F3SUJBZ0lDQkRvd0RRWUpLb1pJaHZjTkFRRUZCUUF3WGpFTE1Ba0dBMVVFQmhNQ1RVUXgNCkREQUtCZ05WQkFvTUEwSk9UVEVNTUFvR0ExVUVDd3dEUkZSSk1SWXdGQVlEVlFRRERBMUNUazBnUTBFZ0tIUmwNCmMzUXBNUnN3R1FZSktvWklodmNOQVFrQkZneGhaRzFwYmtCaWJtMHViV1F3SGhjTk1qUXdOREU0TVRFME5qUXgNCldoY05Nall3TkRFNE1URTBOalF4V2pCRE1Rc3dDUVlEVlFRR0V3Sk5SREVOTUFzR0ExVUVDZ3dFVFVGSlFqRWwNCk1DTUdBMVVFQXd3Y1RVRkpRaUJRY21sellXTmhjblVnVTJWeVoybDFJQ2hVWlhOMEtUQ0NBU0l3RFFZSktvWkkNCmh2Y05BUUVCQlFBRGdnRVBBRENDQVFvQ2dnRUJBTFdYMzlFSmZLNEg5MDZKSVpMbHRxTU56amxDd3NyMm0rZjMNCjVYdHZ4SVY1akEvUWlZSDdDVjBQK0E1U2grKytaNldUb1NnQStQemYwdTdWYWRVbWtyWEZBV0lzOXlPemduUjQNCmZ5TVVSNXR4UWJYdmZYcXVJUS9XQ0ZnRHBIU3I4eWN0UHlsOGdsUjFidVF0UmlTdEdMT0RnalhsTmhTMlhTYTMNCmFwVGhUVHAya3o1dEoyWjBXRnlxa1ZVM1FJNkdNVGU5eWhDdnVZQkI1QWJuUUU4bXVPb2NNaEJkRFREY2ZGdW0NCk5paUozelhLMXZzKzEzNW5sZEMxOXozWnBuaVBSeER2WGthR00wc0xiNnk5T1NIOUdmYTZHcXJnendTTmpubEkNCnZCeWFlK1dtbG16TzlBZXVKNVRaUFhPdzNwcFdpTWdTOVlZOWp1UUtFQUFBQUFBQTQ4c0NBd0VBQWFPQ0FtWXcNCmdnSmlNQkVHQ1dDR1NBR0crRUlCQVFRRUF3SUZvREFwQmdOVkhTVUVJakFnQmdnckJnRUZCUWNEQWdZSUt3WUINCkJRVUhBd1FHQ2lzR0FRUUJnamNVQWdJd0hRWURWUjBPQkJZRUZGR2ptcXM4OXUyMXcvZmNHVlgrb0pNZSsvWTYNCk1JR1FCZ05WSFNNRWdZZ3dnWVdBRkh1ckdvcWhWYVFUVkJwRVlObmNnRUl5Vkd3dG9XS2tZREJlTVFzd0NRWUQNClZRUUdFd0pOUkRFTU1Bb0dBMVVFQ2d3RFFrNU5NUXd3Q2dZRFZRUUxEQU5FVkVreEZqQVVCZ05WQkFNTURVSk8NClRTQkRRU0FvZEdWemRDa3hHekFaQmdrcWhraUc5dzBCQ1FFV0RHRmtiV2x1UUdKdWJTNXRaSUlKQUpuU0UxdVoNCkU1MU5NQlFHQTFVZEVnUU5NQXVCQ1VOQlFHSnViUzV0WkRBMkJnbGdoa2dCaHZoQ0FRUUVLUlluYUhSMGNEb3YNCkwzQnJhUzVpYm0wdWJXUXZjR3RwTDNCMVlpOWpjbXd2WTJGamNtd3VZM0pzTURZR0NXQ0dTQUdHK0VJQkF3UXANCkZpZG9kSFJ3T2k4dmNHdHBMbUp1YlM1dFpDOXdhMmt2Y0hWaUwyTnliQzlqWVdOeWJDNWpjbXd3T0FZRFZSMGYNCkJERXdMekF0b0N1Z0tZWW5hSFIwY0RvdkwzQnJhUzVpYm0wdWJXUXZjR3RwTDNCMVlpOWpjbXd2WTJGamNtd3UNClkzSnNNRU1HQ0NzR0FRVUZCd0VCQkRjd05UQXpCZ2dyQmdFRkJRY3dBb1luYUhSMGNEb3ZMM2QzZHk1aWJtMHUNCmJXUXZjSFZpTDJOaFkyVnlkQzlqWVdObGNuUXVZM0owTUZBR0NXQ0dTQUdHK0VJQkRRUkRGa0ZMWlhrZ1VHRnANCmNpQkhaVzVsY21GMFpXUWdZbmtnVlc1cFEzSjVjSFFnZGk0d0xqWXVPQzQySUdadmNpQlFTME5USXpFeUxXWnANCmJHVWdjM1J2Y21GblpUQUpCZ05WSFJNRUFqQUFNQTRHQTFVZER3RUIvd1FFQXdJR1FEQU5CZ2txaGtpRzl3MEINCkFRVUZBQU9DQWdFQUpTU0ZhRWZOOWVna2wyYVFEc3QvVEtWWmxSbFdWZWkrVmZwMnM1ZXpWNG9ibnZRUXI5QkcNCmZrNklqaU8zbGZHTjQyTkVZSTV6SGh3SDl2WTRiMjM2ZkdMZWltbmZDc2lGb0FyTEtGUDR6Y0dvS0ZJR2ZBNDINCnQzSmxIcENvbmNpMmxqUzg4MzN2c1k5M2xGSzFTa2NvUjBMT0s0NzdaNlBWMjVtdjVjdmhCN1ZkNWs4SWpLU3MNCllwWkpaSi9STWZNT3dPQUtqeDFhWDNxQUhhNVhTOUNINEJaMEl4SnBYcWZpMm5GUFVNRy8yU0JmSTN4dDhsM1UNClJtVy9qZVRoRG5tL0Vsb05sb3pObzdRS3AvbysyUVBFZDBUWkFBdUljQWFiM09waUptOWlrUlh3c21mNkFmS0INCnIwQmtHcTFiTi9RQk1DMDM4RHA4S1pKZmdmaTYxYnBiVUNFdDRsVWY0R252TW9FdjZnbTh1czE2VTI1d0Y0SUwNCnd5cmFBZHJUVHhaWEVydGY2c3pWY2JBRUY0QmdFM0hCVmF2V2FxbDZac1FFRFJoTGVtWVJwMHhleUtwYXI4d3INClhqN1oycmJteWpFci9ES1hMdHF2UlFIQVVrVDBEQXRST2R4NmpsNUtGSFVvbTM2QUZmeU5UcjJ6a0p2MkZWTlENCmc0TnJMRnk0WldidE84ZDc2M2NoMEpjaWYzZUdadnFmQnVETUs3Q25jUWluamxVcTg1cFpzeGlFUW56VTJOdGgNClRFUzBqZjZ6ZS9ibHpVaUsrRXlyeWpEeWNaYlk3RHlwWWVlTlJJbk9zVUVjZmtFT3BVL3dFTG83dnpNaGY1b2MNCmdjcUFKSzdOQWlEQzVHR0Iyb296ZzNSTTJBbGdPT1ZpRFZwRzRMaUxPenpqVStqaXlyclY3OGs9DQotLS0tLUVORCBDRVJUSUZJQ0FURS0tLS0tDQo="
  )
  lazy val consumerNameJson = ConsumerNameJson(
    "App name"
  )
  
  lazy val consumersJsonV510 = ConsumersJsonV510(
    List(consumerJsonV510)
  )

  lazy val agentIdJson = AgentCashWithdrawalJson(
    bankIdExample.value,
    agentNumberExample.value
  )
  
  lazy val transactionRequestBodyAgentJsonV400 = TransactionRequestBodyAgentJsonV400(
    to = agentIdJson,
    value = amountOfMoneyJsonV121,
    description =  descriptionExample.value,
    charge_policy = chargePolicyExample.value,
    future_date = Some(futureDateExample.value)
  )
  
  lazy val postAgentJsonV510  = PostAgentJsonV510(
    legal_name = legalNameExample.value,
    mobile_phone_number = mobilePhoneNumberExample.value,
    agent_number = agentNumberExample.value,
    currency = currencyExample.value
  )
  
  lazy val putAgentJsonV510  = PutAgentJsonV510(
    is_pending_agent = false,
    is_confirmed_agent = true
  )
  
  lazy val agentJsonV510  = AgentJsonV510(
    agent_id = agentIdExample.value,
    bank_id = bankIdExample.value,
    legal_name = legalNameExample.value,
    mobile_phone_number = mobilePhoneNumberExample.value,
    agent_number = agentNumberExample.value,
    currency = currencyExample.value,
    is_confirmed_agent = false,
    is_pending_agent = true
  )
  
  lazy val minimalAgentJsonV510 = MinimalAgentJsonV510(
    agent_id = agentIdExample.value,
    legal_name = legalNameExample.value,
    agent_number = agentNumberExample.value
  )

  lazy val minimalAgentsJsonV510 = MinimalAgentsJsonV510(
    agents = List(minimalAgentJsonV510)
  )

  lazy val regulatedEntityAttributeRequestJsonV510 = RegulatedEntityAttributeRequestJsonV510(
    name = regulatedEntityAttributeNameExample.value,
    attribute_type = regulatedEntityAttributeTypeExample.value,
    value = regulatedEntityAttributeValueExample.value,
    is_active = Some(isActiveExample.value.toBoolean)
  )

  lazy val regulatedEntityAttributeResponseJsonV510 = RegulatedEntityAttributeResponseJsonV510(
    regulated_entity_id = entityIdExample.value,
    regulated_entity_attribute_id = regulatedEntityAttributeIdExample.value,
    name = nameExample.value,
    attribute_type = typeExample.value,
    value = valueExample.value,
    is_active = Some(activeExample.value.toBoolean)
  )

  lazy val regulatedEntityAttributesJsonV510 = RegulatedEntityAttributesJsonV510(
    List(regulatedEntityAttributeResponseJsonV510)
  )
  
  lazy val bankAccountBalanceRequestJsonV510 = BankAccountBalanceRequestJsonV510(
    balance_type = balanceTypeExample.value,
    balance_amount = balanceAmountExample.value
  )

  lazy val bankAccountBalanceResponseJsonV510 = BankAccountBalanceResponseJsonV510(
    bank_id = bankIdExample.value,
    account_id = accountIdExample.value,
    balance_id = balanceIdExample.value,
    balance_type = balanceTypeExample.value,
    balance_amount = balanceAmountExample.value
  )

  lazy val bankAccountBalancesJsonV510 = BankAccountBalancesJsonV510(
    balances = List(bankAccountBalanceResponseJsonV510)
  )

  lazy val createViewPermissionJson = CreateViewPermissionJson(
    permission_name = CAN_GRANT_ACCESS_TO_VIEWS,
    extra_data = Some(List(SYSTEM_ACCOUNTANT_VIEW_ID, SYSTEM_AUDITOR_VIEW_ID))
  )


  lazy val cardanoPaymentJsonV600 = CardanoPaymentJsonV600(
    address = "addr_test1qpv3se9ghq87ud29l0a8asy8nlqwd765e5zt4rc2z4mktqulwagn832cuzcjknfyxwzxz2p2kumx6n58tskugny6mrqs7fd12",
    amount = CardanoAmountJsonV600(
      quantity = 1000000,
      unit = "lovelace"
    ),
    assets = Some(List(CardanoAssetJsonV600(
      policy_id = "policy1234567890abcdef",
      asset_name = "4f47435241",
      quantity = 10
    )))
  )

  // Example for Send ADA with Token only (no ADA amount)
  lazy val cardanoPaymentTokenOnlyJsonV510 = CardanoPaymentJsonV600(
    address = "addr_test1qpv3se9ghq87ud29l0a8asy8nlqwd765e5zt4rc2z4mktqulwagn832cuzcjknfyxwzxz2p2kumx6n58tskugny6mrqs7fd12",
    amount = CardanoAmountJsonV600(
      quantity = 0,
      unit = "lovelace"
    ),
    assets = Some(List(CardanoAssetJsonV600(
      policy_id = "policy1234567890abcdef",
      asset_name = "4f47435241",
      quantity = 10
    )))
  )

  lazy val cardanoMetadataStringJsonV600 = CardanoMetadataStringJsonV600(
    string = "Hello Cardano"
  )

  lazy val transactionRequestBodyCardanoJsonV600 = TransactionRequestBodyCardanoJsonV600(
    to =  cardanoPaymentJsonV600,
    value = amountOfMoneyJsonV121,
    passphrase = "password1234!",
    description = descriptionExample.value,
    metadata = Some(Map("202507022319" -> cardanoMetadataStringJsonV600))
  )

  lazy val transactionRequestBodyEthereumJsonV600 = TransactionRequestBodyEthereumJsonV600(
    to = "0x70997970C51812dc3A010C7d01b50e0d17dc79C8",
    value = AmountOfMoneyJsonV121("ETH", "0.01"),
    description = descriptionExample.value
  )
  lazy val transactionRequestBodyEthSendRawTransactionJsonV600 = TransactionRequestBodyEthSendRawTransactionJsonV600(
    params = "0xf86b018203e882520894627306090abab3a6e1400e9345bc60c78a8bef57880de0b6b3a764000080820ff6a0d0367709eee090a6ebd74c63db7329372db1966e76d28ce219d1e105c47bcba7a0042d52f7d2436ad96e8714bf0309adaf870ad6fb68cfe53ce958792b3da36c12",
    description = descriptionExample.value
  )
  
  //The common error or success format.
  //Just some helper format to use in Json 
  case class NotSupportedYet()
  
  lazy val notSupportedYet = NotSupportedYet()

  lazy val allFields: Seq[AnyRef] ={
    lazy val allFieldsThisFile = ReflectUtils.getValues(this, List(nameOf(allFields)))
                            .filter(it => it != null && it.isInstanceOf[AnyRef])
                            .map(_.asInstanceOf[AnyRef])
    allFieldsThisFile //++ JSONFactoryCustom300.allFields ++ SandboxData.allFields 
  }

}
