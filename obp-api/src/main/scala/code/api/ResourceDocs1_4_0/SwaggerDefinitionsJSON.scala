package code.api.ResourceDocs1_4_0

import java.util.Date

import code.api.Constant
import code.api.UKOpenBanking.v2_0_0.JSONFactory_UKOpenBanking_200
import code.api.UKOpenBanking.v2_0_0.JSONFactory_UKOpenBanking_200.{Account, AccountBalancesUKV200, AccountInner, AccountList, Accounts, BalanceJsonUKV200, BalanceUKOpenBankingJson, BankTransactionCodeJson, CreditLineJson, DataJsonUKV200, Links, MetaBisJson, MetaInnerJson, TransactionCodeJson, TransactionInnerJson, TransactionsInnerJson, TransactionsJsonUKV200}
import code.api.berlin.group.v1.JSONFactory_BERLIN_GROUP_1.{AccountBalance, AccountBalances, AmountOfMoneyV1, Balances, ClosingBookedBody, CoreAccountJsonV1, CoreAccountsJsonV1, ExpectedBody, TransactionJsonV1, Transactions, TransactionsJsonV1, ViewAccount}
import code.api.util.APIUtil.{defaultJValue, _}
import code.api.util.ApiRole._
import code.api.util.Glossary.GlossaryItem
import code.api.util.{APIUtil, ApiTrigger}
import code.api.v2_2_0.JSONFactory220.{AdapterImplementationJson, MessageDocJson, MessageDocsJson}
import code.api.v3_0_0.JSONFactory300.createBranchJsonV300
import code.api.v3_0_0.custom.JSONFactoryCustom300
import code.api.v3_0_0.{LobbyJsonV330, _}
import code.api.v3_1_0.{BadLoginStatusJson, ContactDetailsJson, InviteeJson, ObpApiLoopbackJson, _}
import code.branches.Branches.{Branch, DriveUpString, LobbyString}
import code.sandbox.SandboxData
import code.transactionrequests.TransactionRequests.TransactionRequestTypes._
import code.api.builder.JsonFactory_APIBuilder
import code.context.UserAuthContextUpdateStatus
import com.openbankproject.commons.model
import com.openbankproject.commons.model.PinResetReason.{FORGOT, GOOD_SECURITY_PRACTICE}
import com.openbankproject.commons.model.{GeoTag, _}
import net.liftweb.json

import scala.collection.immutable.List

/**
  * This object prepare all the JSON case classes for Swagger .
  * For now, just support all the endpoints for V220.
  * Because different versions, has different case classes.
  * It is hard to mapping all these case class dynamicaly for now.
  * May be it can be fixed later.
  *
  */
object SwaggerDefinitionsJSON {



  val license =  License(
    id = "id",
    name ="String"
  )

  val routing = Routing(
    scheme ="String",
    address ="String"
  )

  val branchId = BranchId(value = "String")

  // from code.model, not from normal version JSON Factory
  ///////////////////////////////////////////////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////

  val amountOfMoney = AmountOfMoney(
    currency = "EUR",
    amount = "100"
  )

  val accountRouting =  AccountRouting(
    scheme = "accountNumber",
    address = "123456"
  )

  val coreAccount = CoreAccount(
    id ="123",
    label=" work",
    bankId="123123",
    accountType="330",
    accountRoutings= List(accountRouting)
  )


  val accountHeld = AccountHeld(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    bankId = "gh.29.uk",
    number = "String",
    accountRoutings = List(accountRouting)
  )

  val createViewJson = CreateViewJson(
    name = "_test",
    description = "This view is for family",
    metadata_view ="_test",
    is_public = true,
    which_alias_to_use = "family",
    hide_metadata_if_alias_used = false,
    allowed_actions = List(
      "can_see_transaction_this_bank_account",
      "can_see_transaction_other_bank_account",
      "can_see_transaction_metadata",
      "can_see_transaction_label",
      "can_see_transaction_amount",
      "can_see_transaction_type",
      "can_see_transaction_currency",
      "can_see_transaction_start_date",
      "can_see_transaction_finish_date",
      "can_see_transaction_balance",
      "can_see_comments",
      "can_see_narrative",
      "can_see_tags",
      "can_see_images",
      "can_see_bank_account_owners",
      "can_see_bank_account_type",
      "can_see_bank_account_balance",
      "can_see_bank_account_currency",
      "can_see_bank_account_label",
      "can_see_bank_account_national_identifier",
      "can_see_bank_account_swift_bic",
      "can_see_bank_account_iban",
      "can_see_bank_account_number",
      "can_see_bank_account_bank_name",
      "can_see_other_account_national_identifier",
      "can_see_other_account_swift_bic",
      "can_see_other_account_iban",
      "can_see_other_account_bank_name",
      "can_see_other_account_number",
      "can_see_other_account_metadata",
      "can_see_other_account_kind",
      "can_see_more_info",
      "can_see_url",
      "can_see_image_url",
      "can_see_open_corporates_url",
      "can_see_corporate_location",
      "can_see_physical_location",
      "can_see_public_alias",
      "can_see_private_alias",
      "can_add_more_info",
      "can_add_url",
      "can_add_image_url",
      "can_add_open_corporates_url",
      "can_add_corporate_location",
      "can_add_physical_location",
      "can_add_public_alias",
      "can_add_private_alias",
      "can_delete_corporate_location",
      "can_delete_physical_location",
      "can_edit_narrative",
      "can_add_comment",
      "can_delete_comment",
      "can_add_tag",
      "can_delete_tag",
      "can_add_image",
      "can_delete_image",
      "can_add_where_tag",
      "can_see_where_tag",
      "can_delete_where_tag",
      "can_create_counterparty",
      //V300 New
      "can_see_bank_routing_scheme",
      "can_see_bank_routing_address",
      "can_see_bank_account_routing_scheme",
      "can_see_bank_account_routing_address",
      "can_see_other_bank_routing_scheme",
      "can_see_other_bank_routing_address",
      "can_see_other_account_routing_scheme",
      "can_see_other_account_routing_address",
      //v310
      "can_query_available_funds"
    )
  )

  val updateViewJSON = UpdateViewJSON(
    description = "this is for family",
    is_public = true,
    metadata_view = "owner",
    which_alias_to_use = "family",
    hide_metadata_if_alias_used = true,
    allowed_actions = List(
      "can_see_transaction_this_bank_account",
      "can_see_transaction_other_bank_account",
      "can_see_transaction_metadata",
      "can_see_transaction_label",
      "can_see_transaction_amount",
      "can_see_transaction_type",
      "can_see_transaction_currency",
      "can_see_transaction_start_date",
      "can_see_transaction_finish_date",
      "can_see_transaction_balance",
      "can_see_comments",
      "can_see_narrative", "can_see_tags",
      "can_see_images",
      "can_see_bank_account_owners",
      "can_see_bank_account_type",
      "can_see_bank_account_balance",
      "can_see_bank_account_currency",
      "can_see_bank_account_label",
      "can_see_bank_account_national_identifier",
      "can_see_bank_account_swift_bic",
      "can_see_bank_account_iban",
      "can_see_bank_account_number",
      "can_see_bank_account_bank_name",
      "can_see_other_account_national_identifier",
      "can_see_other_account_swift_bic",
      "can_see_other_account_iban",
      "can_see_other_account_bank_name",
      "can_see_other_account_number",
      "can_see_other_account_metadata",
      "can_see_other_account_kind",
      "can_see_more_info",
      "can_see_url",
      "can_see_image_url",
      "can_see_open_corporates_url",
      "can_see_corporate_location",
      "can_see_physical_location",
      "can_see_public_alias",
      "can_see_private_alias",
      "can_add_more_info",
      "can_add_url",
      "can_add_image_url",
      "can_add_open_corporates_url",
      "can_add_corporate_location",
      "can_add_physical_location",
      "can_add_public_alias",
      "can_add_private_alias",
      "can_delete_corporate_location",
      "can_delete_physical_location",
      "can_edit_narrative",
      "can_add_comment",
      "can_delete_comment",
      "can_add_tag",
      "can_delete_tag",
      "can_add_image",
      "can_delete_image",
      "can_add_where_tag",
      "can_see_where_tag",
      "can_delete_where_tag",
      "can_create_counterparty",
      //V300 New
      "can_see_bank_routing_scheme",
      "can_see_bank_routing_address",
      "can_see_bank_account_routing_scheme",
      "can_see_bank_account_routing_address",
      "can_see_other_bank_routing_scheme",
      "can_see_other_bank_routing_address",
      "can_see_other_account_routing_scheme",
      "can_see_other_account_routing_address",
      //v310
      "can_query_available_funds"
    )
  )

  val transactionTypeId = TransactionTypeId(value = "123")

  val bankId = BankId(value = "gh.uk.9j")

  val transactionRequestId = TransactionRequestId(value = "123")

  val counterpartyId = CounterpartyId(value = "123")

  val accountId = model.AccountId(value = "123")

  val viewId = ViewId(value = "owner")


  // from code.TransactionTypes.TransactionType, not from normal version Factory
  ///////////////////////////////////////////////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////
  import code.TransactionTypes.TransactionType._

  val transactionType = TransactionType(
    id = transactionTypeId,
    bankId = bankId,
    shortCode = "80080",
    summary = "SANSANDBOX_TAN",
    description = "This is the sandbox mode, charging litter money.",
    charge = amountOfMoney
  )


  // code.transactionrequests.TransactionRequests
  ///////////////////////////////////////////////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////
  import code.transactionrequests.TransactionRequests._

  val transactionRequestCharge = TransactionRequestCharge(
    summary = "String",
    value = amountOfMoney
  )

  val transactionRequestChallenge = TransactionRequestChallenge(
    id= "String",
    allowed_attempts= 4,
    challenge_type= "String"
  )

  val transactionRequestAccount = TransactionRequestAccount(
    bank_id= "String",
    account_id= "String"
  )

  val transactionRequestCounterpartyId = TransactionRequestCounterpartyId (counterparty_id = "String")

  val transactionRequestIban =  TransactionRequestIban (iban = "String")

  val transactionRequestBody = TransactionRequestBody(
    to = transactionRequestAccount,
    value= amountOfMoney,
    description= "String"
  )


  val fromAccountTransfer = FromAccountTransfer(
    mobile_phone_number = "String",
    nickname = "String"
  )

  val toAccountTransferToPhone = ToAccountTransferToPhone(
    mobile_phone_number = "String"
  )

  val toAccountTransferToAtmKycDocument = ToAccountTransferToAtmKycDocument(
    `type` = "String",
    number = "String",
  )

  val toAccountTransferToAtm = ToAccountTransferToAtm(
    legal_name = "String",
    date_of_birth = "20181230",
    mobile_phone_number = "String",
    kyc_document = toAccountTransferToAtmKycDocument
  )

  val toAccountTransferToAccountAccount = ToAccountTransferToAccountAccount(
    number = "String",
    iban = "String"
  )

  val toAccountTransferToAccount = ToAccountTransferToAccount(
    name = "String",
    bank_code = "String",
    branch_number = "String",
    account = toAccountTransferToAccountAccount
  )

  val amountOfMoneyJsonV121 = AmountOfMoneyJsonV121(
    currency = "EUR",
    amount = "10"
  )

  val transactionRequestTransferToPhone = TransactionRequestTransferToPhone(
    value = amountOfMoneyJsonV121,
    description = "String",
    message = "String",
    from = fromAccountTransfer,
    to = toAccountTransferToPhone
  )

  val transactionRequestTransferToAtm = TransactionRequestTransferToAtm(
    value = amountOfMoneyJsonV121,
    description = "String",
    message = "String",
    from = fromAccountTransfer,
    to = toAccountTransferToAtm
  )

  val transactionRequestTransferToAccount = TransactionRequestTransferToAccount(
    value = amountOfMoneyJsonV121,
    description = "String",
    transfer_type = "String",
    future_date = "20181230",
    to = toAccountTransferToAccount
  )


  val transactionRequestBodyAllTypes = TransactionRequestBodyAllTypes (
    to_sandbox_tan = Some(transactionRequestAccount),
    to_sepa = Some(transactionRequestIban),
    to_counterparty = Some(transactionRequestCounterpartyId),
    to_transfer_to_phone = Some(transactionRequestTransferToPhone),
    to_transfer_to_atm = Some(transactionRequestTransferToAtm),
    to_transfer_to_account = Some(transactionRequestTransferToAccount),
    value = amountOfMoney,
    description = "String"
  )

  val transactionRequest = TransactionRequest(
    id= transactionRequestId,
    `type`= "String",
    from= transactionRequestAccount,
    body= transactionRequestBodyAllTypes,
    transaction_ids= "String",
    status= "String",
    start_date= DateWithDayExampleObject,
    end_date= DateWithDayExampleObject,
    challenge= transactionRequestChallenge,
    charge= transactionRequestCharge,
    charge_policy= "String",
    counterparty_id= counterpartyId,
    name= "String",
    this_bank_id= bankId,
    this_account_id= accountId,
    this_view_id= viewId,
    other_account_routing_scheme= "String",
    other_account_routing_address= "String",
    other_bank_routing_scheme= "String",
    other_bank_routing_address= "String",
    is_beneficiary= true,
    future_date = Some("20881230")
  )

  val adapterImplementationJson = AdapterImplementationJson("CORE",3)

  val messageDocJson = MessageDocJson(
    process = "getAccounts",
    message_format = "KafkaV2017",
    inbound_topic = Some("from.obp.api.1.to.adapter.mf.caseclass.OutboundGetAccounts"),
    outbound_topic = Some("to.obp.api.1.caseclass.OutboundGetAccounts"),
    description = "get Banks",
    example_outbound_message = defaultJValue,
    example_inbound_message = defaultJValue,
    outboundAvroSchema = Some(defaultJValue),
    inboundAvroSchema = Some(defaultJValue),
    adapter_implementation = adapterImplementationJson
  )

  val messageDocsJson = MessageDocsJson(message_docs = List(messageDocJson))

  //V121 - code.api.v1_2_1
  ///////////////////////////////////////////////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////
  import code.api.v1_2_1._

  val makePaymentJson = MakePaymentJson(
    bank_id = "gh.29.uk",
    account_id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    amount = "10"
  )

  val transactionIdJson = TransactionIdJson(
    transaction_id = "123"
  )

  val hostedBy = HostedBy(
    organisation = "String",
    email = "String",
    phone = "String",
    organisation_website = "String"
  )

  val rateLimiting = RateLimiting(true, "REDIS", true, true)

  val apiInfoJSON = APIInfoJSON(
    version = "String",
    version_status = "String",
    git_commit = "String",
    connector = "String",
    hosted_by = hostedBy
  )

  /*  val aggregateMetricsJSON = AggregateMetricJSON(
    total_api_calls = 591,
    average_duration = {"_1":["avg"],"_2":[["164.4940778341793570"]]},
    minimum_duration = {"_1":["min"],"_2":[["0"]]},
    maximum_duration = {"_1":["max"],"_2":[["2847"]]}
  )*/

  val errorMessage = ErrorMessage(
    code = 500,
    message = "Internal Server Error"
  )

  val postTransactionImageJSON = PostTransactionImageJSON(
    label = "String",
    URL = "String"
  )
  val postTransactionCommentJSON = PostTransactionCommentJSON(
    value = "String"
  )
  val postTransactionTagJSON = PostTransactionTagJSON(
    value = "String"
  )
  val aliasJSON = AliasJSON(
    alias = "String"
  )
  val moreInfoJSON = MoreInfoJSON(
    more_info = "String"
  )
  val urlJSON = UrlJSON(
    URL = "String"
  )
  val imageUrlJSON = ImageUrlJSON(
    image_URL = "String"
  )
  val openCorporateUrlJSON = OpenCorporateUrlJSON(
    open_corporates_URL = "String"
  )

  val accountRoutingJsonV121 = AccountRoutingJsonV121(
    scheme = "AccountNumber",
    address = "4930396"
  )

  val accountRuleJsonV300 = AccountRuleJsonV300(
    scheme = "OVERDRAFT",
    value = "10"
  )
  val userJSONV121 = UserJSONV121(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    provider = "OBP",
    display_name = "OBP"
  )

  val viewJSONV121 = ViewJSONV121(
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

  val createViewJsonV121 = CreateViewJsonV121(
    name = "_test",
    description = "This view is for family",
    is_public = true,
    which_alias_to_use = "family",
    hide_metadata_if_alias_used = false,
    allowed_actions = List(
      "can_see_transaction_this_bank_account",
      "can_see_transaction_other_bank_account",
      "can_see_transaction_metadata",
      "can_see_transaction_label",
      "can_see_transaction_amount",
      "can_see_transaction_type",
      "can_see_transaction_currency",
      "can_see_transaction_start_date",
      "can_see_transaction_finish_date",
      "can_see_transaction_balance",
      "can_see_comments",
      "can_see_narrative",
      "can_see_tags",
      "can_see_images",
      "can_see_bank_account_owners",
      "can_see_bank_account_type",
      "can_see_bank_account_balance",
      "can_see_bank_account_currency",
      "can_see_bank_account_label",
      "can_see_bank_account_national_identifier",
      "can_see_bank_account_swift_bic",
      "can_see_bank_account_iban",
      "can_see_bank_account_number",
      "can_see_bank_account_bank_name",
      "can_see_other_account_national_identifier",
      "can_see_other_account_swift_bic",
      "can_see_other_account_iban",
      "can_see_other_account_bank_name",
      "can_see_other_account_number",
      "can_see_other_account_metadata",
      "can_see_other_account_kind",
      "can_see_more_info",
      "can_see_url",
      "can_see_image_url",
      "can_see_open_corporates_url",
      "can_see_corporate_location",
      "can_see_physical_location",
      "can_see_public_alias",
      "can_see_private_alias",
      "can_add_more_info",
      "can_add_url",
      "can_add_image_url",
      "can_add_open_corporates_url",
      "can_add_corporate_location",
      "can_add_physical_location",
      "can_add_public_alias",
      "can_add_private_alias",
      "can_delete_corporate_location",
      "can_delete_physical_location",
      "can_edit_narrative",
      "can_add_comment",
      "can_delete_comment",
      "can_add_tag",
      "can_delete_tag",
      "can_add_image",
      "can_delete_image",
      "can_add_where_tag",
      "can_see_where_tag",
      "can_delete_where_tag",
      "can_create_counterparty",
      //V300 New
      "can_see_bank_routing_scheme",
      "can_see_bank_routing_address",
      "can_see_bank_account_routing_scheme",
      "can_see_bank_account_routing_address",
      "can_see_other_bank_routing_scheme",
      "can_see_other_bank_routing_address",
      "can_see_other_account_routing_scheme",
      "can_see_other_account_routing_address"
    )
  )

  val updateViewJsonV121 = UpdateViewJsonV121(
    description = "This view is for family",
    is_public = true,
    which_alias_to_use = "family",
    hide_metadata_if_alias_used = false,
    allowed_actions = List(
      "can_see_transaction_this_bank_account",
      "can_see_transaction_other_bank_account",
      "can_see_transaction_metadata",
      "can_see_transaction_label",
      "can_see_transaction_amount",
      "can_see_transaction_type",
      "can_see_transaction_currency",
      "can_see_transaction_start_date",
      "can_see_transaction_finish_date",
      "can_see_transaction_balance",
      "can_see_comments",
      "can_see_narrative",
      "can_see_tags",
      "can_see_images",
      "can_see_bank_account_owners",
      "can_see_bank_account_type",
      "can_see_bank_account_balance",
      "can_see_bank_account_currency",
      "can_see_bank_account_label",
      "can_see_bank_account_national_identifier",
      "can_see_bank_account_swift_bic",
      "can_see_bank_account_iban",
      "can_see_bank_account_number",
      "can_see_bank_account_bank_name",
      "can_see_other_account_national_identifier",
      "can_see_other_account_swift_bic",
      "can_see_other_account_iban",
      "can_see_other_account_bank_name",
      "can_see_other_account_number",
      "can_see_other_account_metadata",
      "can_see_other_account_kind",
      "can_see_more_info",
      "can_see_url",
      "can_see_image_url",
      "can_see_open_corporates_url",
      "can_see_corporate_location",
      "can_see_physical_location",
      "can_see_public_alias",
      "can_see_private_alias",
      "can_add_more_info",
      "can_add_url",
      "can_add_image_url",
      "can_add_open_corporates_url",
      "can_add_corporate_location",
      "can_add_physical_location",
      "can_add_public_alias",
      "can_add_private_alias",
      "can_delete_corporate_location",
      "can_delete_physical_location",
      "can_edit_narrative",
      "can_add_comment",
      "can_delete_comment",
      "can_add_tag",
      "can_delete_tag",
      "can_add_image",
      "can_delete_image",
      "can_add_where_tag",
      "can_see_where_tag",
      "can_delete_where_tag",
      "can_create_counterparty",
      //V300 New
      "can_see_bank_routing_scheme",
      "can_see_bank_routing_address",
      "can_see_bank_account_routing_scheme",
      "can_see_bank_account_routing_address",
      "can_see_other_bank_routing_scheme",
      "can_see_other_bank_routing_address",
      "can_see_other_account_routing_scheme",
      "can_see_other_account_routing_address"
    )
  )
  val viewsJSONV121 = ViewsJSONV121(
    views = List(viewJSONV121)
  )

  val accountJSON = AccountJSON(
    id = "123",
    label = "OBP",
    views_available = List(viewJSONV121),
    bank_id = "gh.uk.db"
  )

  val accountsJSON = AccountsJSON(
    accounts = List(accountJSON)
  )

  val bankRoutingJsonV121 = BankRoutingJsonV121(
    scheme = "Bank_ID",
    address = "gh.29.uk"
  )

  val bankJSON = BankJSON(
    id = "gh.29.uk",
    short_name = "short_name ",
    full_name = "full_name",
    logo = "logo",
    website = "www.openbankproject.com",
    bank_routing = bankRoutingJsonV121
  )

  val banksJSON = BanksJSON(
    banks = List(bankJSON)
  )

  val accountHolderJSON = AccountHolderJSON(
    name = "OBP",
    is_alias = true
  )

  val minimalBankJSON = MinimalBankJSON(
    national_identifier = "OBP",
    name = "OBP"
  )

  val moderatedAccountJSON = ModeratedAccountJSON(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    label = "NoneLabel",
    number = "123",
    owners = List(userJSONV121),
    `type` = "OBP",
    balance = amountOfMoneyJsonV121,
    IBAN = "DE89 3704 0044 0532 0130 00",
    swift_bic = "OKOYFIHH",
    views_available = List(viewJSONV121),
    bank_id = "gh.29.uk",
    account_routing = accountRoutingJsonV121
  )

  val thisAccountJSON = ThisAccountJSON(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    holders = List(accountHolderJSON),
    number = "123",
    kind = "AC",
    IBAN = "UK1234AD",
    swift_bic = "UK1234AD",
    bank = minimalBankJSON
  )

  val locationJSONV121 = LocationJSONV121(
    latitude = 1.231,
    longitude = 1.231,
    date = DateWithDayExampleObject,
    user = userJSONV121
  )

  val otherAccountMetadataJSON = OtherAccountMetadataJSON(
    public_alias = "NONE",
    private_alias = "NONE",
    more_info = "www.openbankproject.com",
    URL = "www.openbankproject.com",
    image_URL = "www.openbankproject.com",
    open_corporates_URL = "www.openbankproject.com",
    corporate_location = locationJSONV121,
    physical_location = locationJSONV121
  )

  val otherAccountJSON = OtherAccountJSON(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    holder = accountHolderJSON,
    number = "123",
    kind = "3456",
    IBAN = "UK234DB",
    swift_bic = "UK12321DB",
    bank = minimalBankJSON,
    metadata = otherAccountMetadataJSON
  )

  val transactionDetailsJSON = TransactionDetailsJSON(
    `type` = "AC",
    description = "this is for family",
    posted = DateWithDayExampleObject,
    completed = DateWithDayExampleObject,
    new_balance = amountOfMoneyJsonV121,
    value = amountOfMoneyJsonV121
  )

  val transactionImageJSON = TransactionImageJSON(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    label = "NONE",
    URL = "www.openbankproject.com",
    date = DateWithDayExampleObject,
    user = userJSONV121
  )

  val transactionImagesJSON = TransactionImagesJSON(
    images = List(transactionImageJSON)
  )

  val transactionCommentJSON = TransactionCommentJSON(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    value = "OBP",
    date = DateWithDayExampleObject,
    user = userJSONV121
  )

  val transactionTagJSON = TransactionTagJSON(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    value = "OBP",
    date = DateWithDayExampleObject,
    user = userJSONV121
  )

  val transactionTagsJSON = TransactionTagsJSON(
    tags = List(transactionTagJSON)
  )

  val transactionMetadataJSON = TransactionMetadataJSON(
    narrative = "NONE",
    comments = List(transactionCommentJSON),
    tags = List(transactionTagJSON),
    images = List(transactionImageJSON),
    where = locationJSONV121
  )

  val transactionJSON = TransactionJSON(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    this_account = thisAccountJSON,
    other_account = otherAccountJSON,
    details = transactionDetailsJSON,
    metadata = transactionMetadataJSON
  )

  val transactionsJSON = TransactionsJSON(
    transactions = List(transactionJSON)
  )

  val successMessage = SuccessMessage(
    success = "Success"
  )

  val otherAccountsJSON = OtherAccountsJSON(
    other_accounts = List(otherAccountJSON)
  )

  val transactionNarrativeJSON = TransactionNarrativeJSON(
    narrative = "narative"
  )

  val transactionCommentsJSON = TransactionCommentsJSON(
    comments = List(transactionCommentJSON)
  )

  val transactionWhereJSON = TransactionWhereJSON(
    where = locationJSONV121
  )

  val permissionJSON = PermissionJSON(
    user = userJSONV121,
    views = List(viewJSONV121)
  )

  val permissionsJSON = PermissionsJSON(
    permissions = List(permissionJSON)
  )

  val updateAccountJSON = UpdateAccountJSON(
    id = "123123",
    label = "label",
    bank_id = "gh.29.uk"
  )

  val viewIdsJson = ViewIdsJson(
    views = List("_family" ,"_work")
  )

  val locationPlainJSON = LocationPlainJSON(
    latitude = 1.532,
    longitude = 1.535
  )

  val postTransactionWhereJSON = PostTransactionWhereJSON(
    where = locationPlainJSON
  )

  val corporateLocationJSON = CorporateLocationJSON(
    corporate_location = locationPlainJSON
  )
  val physicalLocationJSON = PhysicalLocationJSON(
    physical_location = locationPlainJSON
  )

  //V130 -- code.api.v1_3_0
  ///////////////////////////////////////////////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////
  import code.api.v1_3_0._

  val pinResetJSON = PinResetJSON(
    requested_date = DateWithDayExampleObject,
    reason_requested = FORGOT.toString
  )
  val pinResetJSON1 = PinResetJSON(
    requested_date = DateWithDayExampleObject,
    reason_requested = GOOD_SECURITY_PRACTICE.toString
  )

  val replacementJSON = ReplacementJSON(
    requested_date = DateWithDayExampleObject,
    reason_requested = CardReplacementReason.RENEW.toString
  )

  val physicalCardJSON = PhysicalCardJSON(
    bank_id = "gh.29.uk",
    bank_card_number = "String",
    name_on_card = "String",
    issue_number = "String",
    serial_number = "String",
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

  val physicalCardsJSON = PhysicalCardsJSON(
    cards = List(physicalCardJSON)
  )

  val postPhysicalCardJSON = PostPhysicalCardJSON(
    bank_card_number = "String",
    name_on_card = "String",
    issue_number = "String",
    serial_number = "String",
    valid_from_date = DateWithDayExampleObject,
    expires_date = DateWithDayExampleObject,
    enabled = true,
    technology = "String",
    networks = List("network1", "network2"),
    allows = List("credit", "debit"),
    account_id = "String",
    replacement = replacementJSON,
    pin_reset = List(pinResetJSON, pinResetJSON1),
    collected = DateWithDayExampleObject,
    posted = DateWithDayExampleObject
  )

  //V140 -- code.api.v1_4_0.JSONFactory1_4_0
  ///////////////////////////////////////////////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////
  import code.api.v1_4_0.JSONFactory1_4_0._

  val transactionRequestBodyJson = TransactionRequestBodyJson (
    to = transactionRequestAccount,
    value = amountOfMoney,
    description = "String"
  )

  val transactionRequestJson = TransactionRequestJson(
    id = transactionRequestId,
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
    charge_policy = "String",
    counterparty_id = counterpartyId,
    name = "String",
    this_bank_id = bankId,
    this_account_id = accountId,
    this_view_id = viewId,
    other_account_routing_scheme = "String",
    other_account_routing_address = "String",
    other_bank_routing_scheme = "String",
    other_bank_routing_address = "String",
    is_beneficiary = true
  )

  val customerFaceImageJson = CustomerFaceImageJson(
    url = "www.openbankproject",
    date = DateWithDayExampleObject
  )

  val locationJson = LocationJsonV140(
    latitude = 11.45,
    longitude = 11.45
  )

  val transactionRequestChargeJsonV140 = TransactionRequestChargeJsonV140(
    summary = "The bank fixed charge",
    value = amountOfMoneyJsonV121 //amountOfMoneyJSON
  )

  val transactionRequestTypeJsonV140 = TransactionRequestTypeJsonV140(
    value = "10",
    charge = transactionRequestChargeJsonV140
  )

  val transactionRequestTypesJsonV140 = TransactionRequestTypesJsonV140(
    transaction_request_types = List(transactionRequestTypeJsonV140)
  )

  val transactionRequestAccountJsonV140 = TransactionRequestAccountJsonV140(
    bank_id = "gh.29.uk",
    account_id = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0"
  )

  val challengeJsonV140 = ChallengeJsonV140(
    id = "be1a183d-b301-4b83-b855-5eeffdd3526f",
    allowed_attempts = 3,
    challenge_type = SANDBOX_TAN.toString
  )

  val driveUpJson = DriveUpStringJson(
    hours = "5"
  )
  val licenseJson = LicenseJsonV140(
    id = "5",
    name = "TESOBE"
  )
  val metaJson = MetaJsonV140(
    license = licenseJson
  )
  val lobbyJson = LobbyStringJson(
    hours = "5"
  )
  val addressJsonV140 = AddressJsonV140(
    line_1 = "Osloer Straße 16/17",
    line_2 = "Wedding",
    line_3 = "",
    city = "Berlin",
    state = "Berlin Brandenburg",
    postcode = "13359",
    country = "DE"
  )
  val challengeAnswerJSON = ChallengeAnswerJSON(
    id = "This is challenge.id, you can get it from `Create Transaction Request.` response, only is useful if status ==`INITIATED` there.",
    answer = "123"
  )

  val postCustomerJson = PostCustomerJson(
    customer_number = "String",
    legal_name = "String",
    mobile_phone_number = "String",
    email = "String",
    face_image = customerFaceImageJson,
    date_of_birth = DateWithDayExampleObject,
    relationship_status = "String",
    dependants = 1,
    dob_of_dependants = List(DateWithDayExampleObject),
    highest_education_attained = "String",
    employment_status = "String",
    kyc_status = true,
    last_ok_date = DateWithDayExampleObject
  )

  val customerJsonV140 = CustomerJsonV140(
    customer_id = "String",
    customer_number = "String",
    legal_name = "String",
    mobile_phone_number = "String",
    email = "String",
    face_image = customerFaceImageJson,
    date_of_birth = DateWithDayExampleObject,
    relationship_status = "String",
    dependants = 10,
    dob_of_dependants = List(DateWithDayExampleObject),
    highest_education_attained = "String",
    employment_status = "String",
    kyc_status = true,
    last_ok_date = DateWithDayExampleObject
  )

  val customersJsonV140 = CustomersJsonV140(
    customers = List(customerJsonV140)
  )

  val customerMessageJson = CustomerMessageJson(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    date = DateWithDayExampleObject,
    message = "String",
    from_department = "String",
    from_person = "String"
  )

  val customerMessagesJson = CustomerMessagesJson(
    messages = List(customerMessageJson)
  )

  val addCustomerMessageJson = AddCustomerMessageJson(
    message = "String",
    from_department = "String",
    from_person = "String"
  )

  val branchRoutingJSON = BranchRoutingJsonV141(
    scheme = "BranchNumber",
    address = "678"
  )

  val branchJson = BranchJson(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    name = "String",
    address = addressJsonV140,
    location = locationJson,
    lobby = lobbyJson,
    drive_up = driveUpJson,
    meta = metaJson,
    branch_routing = branchRoutingJSON
  )






  val branchesJson = BranchesJson(branches = List(branchJson))




  // Internal data examples (none JSON format).
  // Use transform... to convert these to our various json formats for different API versions

  val meta: Meta =  Meta(license = License (id = "PDDL", name = "Open Data Commons Public Domain Dedication and License "))  // Note the meta  is V140
  val openingTimesV300 =OpeningTimesV300(
    opening_time = "10:00",
    closing_time = "18:00")
  val openingTimes = OpeningTimes(
    openingTime = "10:00",
    closingTime = "18:00"
  )

  val address : Address = Address(
    line1 = "No 1 the Road",
    line2 = "The Place",
    line3 = "The Hill",
    city = "Berlin",
    county = Some("String"),
    state = "Brandenburg",
    postCode = "13359",
    countryCode = "DE"
  )

  val lobby: Lobby = Lobby(
    monday = List(openingTimes),
    tuesday = List(openingTimes),
    wednesday = List(openingTimes),
    thursday = List(openingTimes),
    friday = List(openingTimes),
    saturday = List(openingTimes),
    sunday = List(openingTimes)
  )


  val driveUp: DriveUp = DriveUp(
    monday = openingTimes,
    tuesday = openingTimes,
    wednesday = openingTimes,
    thursday = openingTimes,
    friday = openingTimes,
    saturday = openingTimes,
    sunday = openingTimes
  )

  val branchRouting = Routing("OBP", "123abc")

  val basicResourceUser = BasicResourceUser(
    userId= "String", // Should come from Resource User Id
    provider= " String",
    username= " String"
  )

  val location : Location = Location (
    10.0,
    10.0,
    Some(DateWithDayExampleObject),
    Some(basicResourceUser))

  val lobbyString = LobbyString (
    hours ="String "
  )

  val driveUpString = DriveUpString (
    hours ="String "
  )

  val branch: Branch = Branch(
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


  val lobbyJsonV330 = LobbyJsonV330(
    monday = List(openingTimesV300),
    tuesday = List(openingTimesV300),
    wednesday = List(openingTimesV300),
    thursday =  List(openingTimesV300),
    friday =  List(openingTimesV300),
    saturday =  List(openingTimesV300),
    sunday =  List(openingTimesV300)
  )

  val driveUpJsonV330 = DriveUpJsonV330(
    monday = openingTimesV300,
    tuesday = openingTimesV300,
    wednesday = openingTimesV300,
    thursday =  openingTimesV300,
    friday =  openingTimesV300,
    saturday =  openingTimesV300,
    sunday =  openingTimesV300
  )


  val branchJsonV300: BranchJsonV300 = createBranchJsonV300 (branch)
  val branchesJsonV300 = BranchesJsonV300(branches = List(branchJsonV300))

  val postBranchJsonV300 = PostBranchJsonV300(
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



  val atmJson = AtmJson(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    name = "String",
    address = addressJsonV140,
    location = locationJson,
    meta = metaJson
  )

  val atmsJson = AtmsJson(atms = List(atmJson))



  val addressJsonV300 = AddressJsonV300(
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

  val customerAddressJsonV310 = CustomerAddressJsonV310(
    customer_address_id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    customer_id = "36f8a9e6-c2b1-407a-8bd0-421b7119307e",
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
  val customerAddressesJsonV310 = CustomerAddressesJsonV310(List(customerAddressJsonV310))

  val postCustomerAddressJsonV310 = PostCustomerAddressJsonV310(
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
  
  val atmJsonV300 = AtmJsonV300(
    id = "atm-id-123",
    bank_id = "bank-id-123",
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

  val productJson = ProductJson(
    code = "String",
    name = "String",
    category = "String",
    family = "String",
    super_family = "String",
    more_info_url = "String",
    meta = metaJson
  )

  val productsJson = ProductsJson(products = List(productJson))


  val crmEventJson = CrmEventJson(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    bank_id = "gh.29.uk",
    customer_name = "String",
    customer_number = "String",
    category = "String",
    detail = "String",
    channel = "String",
    scheduled_date = DateWithDayExampleObject,
    actual_date = DateWithDayExampleObject,
    result = "String"
  )

  val crmEventsJson = CrmEventsJson(crm_events = List(crmEventJson))

  val implementedByJson = ImplementedByJson(
    version = "1_4_0",
    function = "getBranches"
  )
  // Used to describe the OBP API calls for documentation and API discovery purposes
  val canCreateCustomerSwagger = CanCreateCustomer()
  val resourceDocJson = ResourceDocJson(
    operation_id = "String",
    request_verb = "String",
    request_url = "String",
    summary = "String",
    description = "HTML String",
    description_markdown = "Mark_down String",
    example_request_body = successMessage, //TODO maybe need fix
    success_response_body = successMessage,
    error_response_bodies = List("OBP-10001= Incorrect json format."),
    implemented_by = implementedByJson,
    is_core = true,
    is_psd2 = true,
    is_obwg = true,
    tags = List("String"),
    typed_request_body = json.parse("""{"request": { "type" :"string" }}"""),
    typed_success_response_body = json.parse("""{"response": { "type" :"string" }}"""),
    roles = Some(List(canCreateCustomerSwagger)),
    is_featured = false,
    special_instructions = "",
    specified_url = ""
  )

  val resourceDocsJson = ResourceDocsJson(resource_docs = List(resourceDocJson))

  val transactionRequestBodyJsonV140 = TransactionRequestBodyJsonV140(
    to = transactionRequestAccountJsonV140,
    value = amountOfMoneyJsonV121,
    description = "String",
    challenge_type = "String"
  )
  val transactionRequestJSON = TransactionRequestJsonV140(
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

  val basicViewJSON = BasicViewJson(
    id = "1",
    short_name = "HHH",
    is_public = true
  )

  val basicAccountJSON = BasicAccountJSON(
    id = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
    label = "NoneLabel",
    bank_id = "gh.29.uk",
    views_available = List(basicViewJSON)
  )

  val coreAccountJSON = CoreAccountJSON(
    id = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
    label = "NoneLabel",
    bank_id = "gh.29.uk",
    _links = defaultJValue
  )

  val moderatedCoreAccountJSON = ModeratedCoreAccountJSON(
    id = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
    label = "NoneLabel",
    number = "123",
    owners = List(userJSONV121),
    `type` = "OBP",
    balance = amountOfMoneyJsonV121,
    IBAN = "GR1301720530005053000582373",
    swift_bic = "UKTF3049auf",
    bank_id = "gh.29.uk",
    account_routing = accountRoutingJsonV121
  )

  val basicAccountsJSON = BasicAccountsJSON(
    accounts = List(basicAccountJSON)
  )
  val coreAccountsJSON = CoreAccountsJSON(accounts = List(coreAccountJSON))

  val kycDocumentJSON = KycDocumentJSON(
    bank_id = "PlaceholderString",
    customer_id = "PlaceholderString",
    id = "PlaceholderString",
    customer_number = "PlaceholderString",
    `type` = "PlaceholderString",
    number = "PlaceholderString",
    issue_date = DateWithDayExampleObject,
    issue_place = "PlaceholderString",
    expiry_date = DateWithDayExampleObject
  )

  val kycDocumentsJSON = KycDocumentsJSON(
    documents = List(kycDocumentJSON)
  )
  val kycMediaJSON = KycMediaJSON(
    bank_id = "PlaceholderString",
    customer_id = "PlaceholderString",
    id = "PlaceholderString",
    customer_number = "PlaceholderString",
    `type` = "PlaceholderString",
    url = "PlaceholderString",
    date = DateWithDayExampleObject,
    relates_to_kyc_document_id = "PlaceholderString",
    relates_to_kyc_check_id = "PlaceholderString"
  )
  val kycMediasJSON = KycMediasJSON(medias = List(kycMediaJSON))


  val kycCheckJSON = KycCheckJSON(
    bank_id = "PlaceholderString",
    customer_id = "PlaceholderString",
    id = "PlaceholderString",
    customer_number = "PlaceholderString",
    date = DateWithDayExampleObject,
    how = "PlaceholderString",
    staff_user_id = "PlaceholderString",
    staff_name = "PlaceholderString",
    satisfied = true,
    comments = "PlaceholderString"
  )
  var kycChecksJSON = KycChecksJSON(checks = List(kycCheckJSON))

  var kycStatusJSON = KycStatusJSON(
    customer_id = "PlaceholderString",
    customer_number = "PlaceholderString",
    ok = true,
    date = DateWithDayExampleObject
  )
  var kycStatusesJSON = KycStatusesJSON(statuses = List(kycStatusJSON))

  var socialMediaJSON = SocialMediaJSON(
    customer_number = "PlaceholderString",
    `type` = "PlaceholderString",
    handle = "PlaceholderString",
    date_added = DateWithDayExampleObject,
    date_activated = DateWithDayExampleObject
  )
  var socialMediasJSON = SocialMediasJSON(checks = List(socialMediaJSON))

  val entitlementJSON =
    code.api.v2_0_0.EntitlementJSON(
      entitlement_id = "6fb17583-1e49-4435-bb74-a14fe0996723",
      role_name = "CanQueryOtherUser",
      bank_id = "gh.29.uk"
    )
  val entitlementJSONs = EntitlementJSONs(
    list = List(entitlementJSON)
  )

  val userJsonV200 = UserJsonV200(
    user_id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    email = "robert.x.0.gh@example.com",
    provider_id = "OBP",
    provider = "OBP",
    username = "robert.x.0.gh",
    entitlements = entitlementJSONs
  )

  val entitlementRequestJSON =
    code.api.v3_0_0.EntitlementRequestJSON(
      user = userJsonV200,
      entitlement_request_id = "6fb17583-1e49-4435-bb74-a14fe0996723",
      role_name = "CanQueryOtherUser",
      bank_id = "gh.29.uk",
      created = DateWithDayExampleObject
    )

  val entitlementRequestsJSON = EntitlementRequestsJSON(entitlement_requests = List(entitlementRequestJSON))


  val coreTransactionDetailsJSON = CoreTransactionDetailsJSON(
    `type` = "AC",
    description = "OBP",
    posted = DateWithDayExampleObject,
    completed = DateWithDayExampleObject,
    new_balance = amountOfMoneyJsonV121,
    value = amountOfMoneyJsonV121
  )

  val coreAccountHolderJSON = CoreAccountHolderJSON(
    name = "ZACK"
  )

  val createEntitlementJSON = CreateEntitlementJSON(
    bank_id = "gh.29.uk",
    role_name = "String"
  )

  val coreCounterpartyJSON = CoreCounterpartyJSON(
    id = "123",
    holder = coreAccountHolderJSON,
    number = "1234",
    kind = "AV",
    IBAN = "UK12344DB",
    swift_bic = "UK12344DB",
    bank = minimalBankJSON
  )

  val coreTransactionJSON = CoreTransactionJSON(
    id = "123",
    account = thisAccountJSON,
    counterparty = coreCounterpartyJSON,
    details = coreTransactionDetailsJSON
  )

  val coreTransactionsJSON = CoreTransactionsJSON(
    transactions = List(coreTransactionJSON)
  )

  val transactionRequestChargeJsonV200 = TransactionRequestChargeJsonV200(
    summary = "Rent the flat",
    value = amountOfMoneyJsonV121
  )

  val transactionRequestWithChargeJson = TransactionRequestWithChargeJson(
    id = "82f92531-9c63-4246-abfc-96c20ec46188",
    `type` = SANDBOX_TAN.toString,
    from = transactionRequestAccountJsonV140,
    details = transactionRequestBody,
    transaction_ids = "666666-9c63-4246-abfc-96c20ec46188",
    status = "COMPLETED",
    start_date = DateWithDayExampleObject,
    end_date = DateWithDayExampleObject,
    challenge = challengeJsonV140,
    charge = transactionRequestChargeJsonV200
  )

  val transactionRequestBodyJsonV200 = TransactionRequestBodyJsonV200(
    to = transactionRequestAccountJsonV140,
    value = amountOfMoneyJsonV121,
    description = "this is for work"
  )

  val transactionTypeJsonV200 = TransactionTypeJsonV200(
    id = transactionTypeId,
    bank_id = "PlaceholderString",
    short_code = "PlaceholderString",
    summary = "PlaceholderString",
    description = "PlaceholderString",
    charge = amountOfMoneyJsonV121
  )

  val transactionTypesJsonV200 = TransactionTypesJsonV200(
    transaction_types = List(transactionTypeJsonV200)
  )
  val linkJson = LinkJson(
    href = "String",
    rel = "String",
    method = "String"
  )

  val linksJson = LinksJson(
    _links = List(linkJson)
  )

  val resultAndLinksJson = ResultAndLinksJson(
    result = defaultJValue,
    links = linksJson
  )

  val createUserJson = CreateUserJson(
    email = "String",
    username = "String",
    password = "String",
    first_name = "String",
    last_name = "String"
  )

  val createUserJSONs = CreateUsersJson(
    users = List(createUserJson)
  )

  val createMeetingJson = CreateMeetingJson(
    provider_id = "String",
    purpose_id = "String"
  )

  val meetingKeysJSON = MeetingKeysJson(
    session_id = "String",
    staff_token = "String",
    customer_token = "String"
  )

  val meetingPresentJSON = MeetingPresentJson(
    staff_user_id = "String",
    customer_user_id = "String"
  )

  val meetingJson = MeetingJson(
    meeting_id = "String",
    provider_id = "String",
    purpose_id = "String",
    bank_id = "gh.29.uk",
    present = meetingPresentJSON,
    keys = meetingKeysJSON,
    when = DateWithDayExampleObject
  )

  val meetingsJson = MeetingsJson(
    meetings = List(meetingJson)
  )


  val userCustomerLinkJson = UserCustomerLinkJson(
    user_customer_link_id = "String",
    customer_id = "String",
    user_id = "String",
    date_inserted = DateWithDayExampleObject,
    is_active = true
  )

  val userCustomerLinksJson = UserCustomerLinksJson(
    l = List(userCustomerLinkJson)
  )

  val createUserCustomerLinkJson = CreateUserCustomerLinkJson(
    user_id = "String",
    customer_id = "String"
  )

  val createAccountJSON = CreateAccountJSON(
    user_id = "String",
    label = "String",
    `type` = "String",
    balance = amountOfMoneyJsonV121
  )

  val postKycDocumentJSON = PostKycDocumentJSON(
    customer_number = "1234",
    `type` = "passport",
    number = "12345",
    issue_date = DateWithDayExampleObject,
    issue_place = "Berlin",
    expiry_date = DateWithDayExampleObject
  )

  val postKycMediaJSON = PostKycMediaJSON(
    customer_number = "1239879",
    `type` = "image",
    url = "http://www.example.com/id-docs/123/image.png",
    date = DateWithDayExampleObject,
    relates_to_kyc_document_id = "123",
    relates_to_kyc_check_id = "123"
  )

  val postKycCheckJSON = PostKycCheckJSON(
    customer_number = "1239879",
    date = DateWithDayExampleObject,
    how = "online_meeting",
    staff_user_id = "67876",
    staff_name = "Simon",
    satisfied = true,
    comments = "String"
  )

  val postKycStatusJSON = PostKycStatusJSON(
    customer_number = "String",
    ok = true,
    date = DateWithDayExampleObject
  )

  val createCustomerJson = CreateCustomerJson(
    title = "String",
    branchId = "String",
    nameSuffix = "String",
    user_id = "String",
    customer_number = "String",
    legal_name = "String",
    mobile_phone_number = "String",
    email = "String",
    face_image = customerFaceImageJson,
    date_of_birth = DateWithDayExampleObject,
    relationship_status = "String",
    dependants = 1,
    dob_of_dependants = List(DateWithDayExampleObject),
    highest_education_attained = "String",
    employment_status = "String",
    kyc_status = true,
    last_ok_date = DateWithDayExampleObject
  )

  val transactionRequestJsonV200 = TransactionRequestJsonV200(
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

  val transactionRequestWithChargesJson = TransactionRequestWithChargesJson(
    transaction_requests_with_charges = List(transactionRequestWithChargeJson)
  )

  val usersJsonV200 = UsersJsonV200(
    users = List(userJsonV200)
  )

  val counterpartiesJSON = CounterpartiesJSON(
    counterparties = List(coreCounterpartyJSON)
  )

  //V210
  ///////////////////////////////////////////////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////
  import code.api.v2_1_0._

  val counterpartyIdJson = CounterpartyIdJson(
    counterparty_id = "123456"
  )
  val ibanJson = IbanJson(
    iban = "123"
  )

  val metricJson = MetricJson(
    user_id = "134",
    url = "www.openbankproject.com",
    date = DateWithDayExampleObject,
    user_name = "OBP",
    app_name = "SOFI",
    developer_email = "contact@tesobe.ocm",
    implemented_by_partial_function = "getBanks",
    implemented_in_version = "v210",
    consumer_id = "123",
    verb = "get",
    correlation_id = "v8ho6h5ivel3uq7a5zcnv0w1",
    duration = 39
  )

  val resourceUserJSON = ResourceUserJSON(
    user_id = "123",
    email = "contact@tesobe.com",
    provider_id = "obp",
    provider = "obp",
    username = "TESOBE"
  )

  val availableRoleJSON = AvailableRoleJSON(
    role = "CanCreateBranch",
    requires_bank_id = true
  )

  val transactionRequestTypeJSONV210 = TransactionRequestTypeJSONV210(
    transaction_request_type = "SandboxTan"
  )

  val transactionRequestTypesJSON = TransactionRequestTypesJSON(
    transaction_request_types = List(transactionRequestTypeJSONV210)
  )

  val transactionRequestBodyCounterpartyJSON = TransactionRequestBodyCounterpartyJSON(
    counterpartyIdJson,
    amountOfMoneyJsonV121,
    "A description for the transaction to the counterparty",
    "SHARED",
    Some("20881230")
  )

  val transactionRequestBodySEPAJSON = TransactionRequestBodySEPAJSON(
    amountOfMoneyJsonV121,
    ibanJson,
    "This is a SEPA Transaction Request",
    "SHARED",
    Some("20881230")
  )

  val customerCreditRatingJSON = CustomerCreditRatingJSON(
    rating = "OBP",
    source = "OBP"
  )

  val customerJsonV210 = CustomerJsonV210(
    bank_id = "bankid1234",
    customer_id = "123",
    customer_number = "123",
    legal_name = "legal_name",
    mobile_phone_number = "123",
    email = "contact@tesobe.com",
    face_image = customerFaceImageJson,
    date_of_birth = DateWithDayExampleObject,
    relationship_status = "123",
    dependants = 123,
    dob_of_dependants = List(DateWithDayExampleObject),
    credit_rating = Option(customerCreditRatingJSON),
    credit_limit = Option(amountOfMoneyJsonV121),
    highest_education_attained = "123",
    employment_status = "123",
    kyc_status = true,
    last_ok_date = DateWithDayExampleObject
  )
  
  val customerJSONs = CustomerJSONs(customers = List(customerJsonV210))

  val userJSONV210 = UserJSONV210(
    id = "123",
    provider = "OBP",
    username = "OBP"
  )

  val locationJsonV210 =
    LocationJsonV210(
      latitude = 11.45,
      longitude = 11.45,
      date = DateWithDayExampleObject,
      user = userJSONV210
    )

  val postCustomerJsonV210 =
    PostCustomerJsonV210(
      user_id = "user_id to attach this customer to e.g. 123213",
      customer_number = "new customer number 687687678",
      legal_name = "NONE",
      mobile_phone_number = "+44 07972 444 876",
      email = "person@example.com",
      face_image = customerFaceImageJson,
      date_of_birth = DateWithDayExampleObject,
      relationship_status = "Single",
      dependants = 5,
      dob_of_dependants = List(DateWithDayExampleObject),
      credit_rating = customerCreditRatingJSON,
      credit_limit = amountOfMoneyJsonV121,
      highest_education_attained = "Bachelor’s Degree",
      employment_status = "Employed",
      kyc_status = true,
      last_ok_date = DateWithDayExampleObject
    )

  val customerJsonV300 = CustomerJsonV300(
    bank_id = "bankid1234",
    customer_id = "123",
    customer_number = "123",
    legal_name = "legal_name",
    mobile_phone_number = "123",
    email = "contact@tesobe.com",
    face_image = customerFaceImageJson,
    date_of_birth = "19900101",
    relationship_status = "123",
    dependants = 123,
    dob_of_dependants = List("19900101"),
    credit_rating = Option(customerCreditRatingJSON),
    credit_limit = Option(amountOfMoneyJsonV121),
    highest_education_attained = "123",
    employment_status = "123",
    kyc_status = true,
    last_ok_date = DateWithDayExampleObject,
    title  = "Dr.",
    branchId = "12314",
    nameSuffix = "Sr"
  )
  
  val postCustomerJsonV310 =
    PostCustomerJsonV310(
      legal_name = "Tom Tom",
      mobile_phone_number = "+44 07972 444 876",
      email = "person@example.com",
      face_image = customerFaceImageJson,
      date_of_birth = DateWithDayExampleObject,
      relationship_status = "Single",
      dependants = 5,
      dob_of_dependants = List(DateWithDayExampleObject),
      credit_rating = customerCreditRatingJSON,
      credit_limit = amountOfMoneyJsonV121,
      highest_education_attained = "string",
      employment_status = "Employed",
      kyc_status = true,
      last_ok_date = DateWithDayExampleObject,
      title  = "Dr.",
      branchId = "12314",
      nameSuffix = "Sr"
    )
  
  val customerJsonV310 = CustomerJsonV310(
    bank_id = "bankid1234",
    customer_id = "123",
    customer_number = "123",
    legal_name = "legal_name",
    mobile_phone_number = "123",
    email = "contact@tesobe.com",
    face_image = customerFaceImageJson,
    date_of_birth = DateWithDayExampleObject,
    relationship_status = "123",
    dependants = 123,
    dob_of_dependants = List(DateWithDayExampleObject),
    credit_rating = Option(customerCreditRatingJSON),
    credit_limit = Option(amountOfMoneyJsonV121),
    highest_education_attained = "123",
    employment_status = "123",
    kyc_status = true,
    last_ok_date = DateWithDayExampleObject,
    title  = "Dr.",
    branchId = "12314",
    nameSuffix = "Sr"
  )

  val postCustomerNumberJsonV310 = PostCustomerNumberJsonV310(customer_number = "123")

  val taxResidenceV310 = TaxResidenceV310(domain = "Enter some domain", tax_number = "Enter some number", tax_residence_id = "902ba3bb-dedd-45e7-9319-2fd3f2cd98a1")
  val postTaxResidenceJsonV310 = PostTaxResidenceJsonV310(domain = "Enter some domain", tax_number = "Enter some number")
  val taxResidenceJsonV310 = TaxResidenceJsonV310(tax_residence = List(taxResidenceV310))


  val transactionRequestWithChargeJSON210 = TransactionRequestWithChargeJSON210(
    id = "4050046c-63b3-4868-8a22-14b4181d33a6",
    `type` = SANDBOX_TAN.toString,
    from = transactionRequestAccountJsonV140,
    details = transactionRequestBodyAllTypes,
    transaction_ids = List("902ba3bb-dedd-45e7-9319-2fd3f2cd98a1"),
    status = "COMPLETED",
    start_date = DateWithDayExampleObject,
    end_date = DateWithDayExampleObject,
    challenge = challengeJsonV140,
    charge = transactionRequestChargeJsonV200
  )

  val transactionRequestWithChargeJSONs210 =
    TransactionRequestWithChargeJSONs210(
      transaction_requests_with_charges = List(
        transactionRequestWithChargeJSON210
      )
    )

  val availableRolesJSON = AvailableRolesJSON(
    roles = List(availableRoleJSON)
  )

  val consumerJSON = ConsumerJSON(
    consumer_id = 1213,
    app_name = "SOFI",
    app_type = "Web",
    description = "Account Management",
    developer_email = "contact@tesobe.com",
    redirect_url = "www.openbankproject.com",
    created_by_user_id = "123213",
    created_by_user = resourceUserJSON,
    enabled = true,
    created = DateWithDayExampleObject
  )

  val consumersJson = ConsumersJson(
    list = List(consumerJSON)
  )

  val consumerJsonV310 = ConsumerJsonV310(
    consumer_id = "8e716299-4668-4efd-976a-67f57a9984ec",
    app_name = "SOFI",
    app_type = "Web",
    description = "Account Management",
    developer_email = "contact@tesobe.com",
    redirect_url = "www.openbankproject.com",
    created_by_user = resourceUserJSON,
    enabled = true,
    created = DateWithDayExampleObject
  )
  
  val consumersJson310 = ConsumersJsonV310(
    List(consumerJsonV310)
  )

  val putEnabledJSON = PutEnabledJSON(
    enabled = false
  )

  val productJsonV210 = ProductJsonV210(
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

  val productsJsonV210 = ProductsJsonV210(products = List(productJsonV210))




  val grandparentProductTreeJsonV310 = ProductTreeJsonV310(
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
  val parentProductTreeJsonV310 = ProductTreeJsonV310(
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
  val childProductTreeJsonV310 = ProductTreeJsonV310(
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
  
  
  val postCounterpartyBespokeJson = PostCounterpartyBespokeJson(
    key = "englishName",
    value = "english Name"
  )
  
  val postCounterpartyJSON = PostCounterpartyJSON(
    name = "CounterpartyName",
    description ="My landlord",
    other_account_routing_scheme = "accountNumber",
    other_account_routing_address = "7987987-2348987-234234",
    other_account_secondary_routing_scheme = "IBAN",
    other_account_secondary_routing_address = "DE89370400440532013000",
    other_bank_routing_scheme = "bankCode",
    other_bank_routing_address = "10",
    other_branch_routing_scheme = "branchNumber",
    other_branch_routing_address = "10010", 
    is_beneficiary = true,
    bespoke =  List(postCounterpartyBespokeJson)
  )

  val metricsJson = MetricsJson(
    metrics = List(metricJson)
  )

  val branchJsonPut = BranchJsonPutV210("gh.29.fi", "OBP",
    addressJsonV140,
    locationJson,
    metaJson,
    lobbyJson,
    driveUpJson
  )

  val branchJsonPost = BranchJsonPostV210("123", "gh.29.fi", "OBP",
    addressJsonV140,
    locationJson,
    metaJson,
    lobbyJson,
    driveUpJson
  )

  val consumerRedirectUrlJSON = ConsumerRedirectUrlJSON(
    "http://localhost:8888"
  )

  //V220
  ///////////////////////////////////////////////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////
  import code.api.v2_2_0._

  val viewJSONV220 = ViewJSONV220(
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

  val viewsJSONV220 = ViewsJSONV220(
    views = List(viewJSONV220)
  )

  val fXRateJSON = FXRateJsonV220(
    bank_id = "bankid434",
    from_currency_code = "EUR",
    to_currency_code = "GBP",
    conversion_value = 1.001,
    inverse_conversion_value = 0.998,
    effective_date = DateWithDayExampleObject
  )

  val counterpartyJsonV220 = CounterpartyJsonV220(
    name = postCounterpartyJSON.name,
    description = postCounterpartyJSON.description,
    created_by_user_id = "49e1e147-64c1-4823-ad9f-89efcd02a9fa",
    this_bank_id = "gh.29.uk",
    this_account_id = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
    this_view_id = "owner",
    counterparty_id = "1d65db7c-a7b2-4839-af41-958276ab7790",
    other_bank_routing_scheme = postCounterpartyJSON.other_bank_routing_scheme,
    other_bank_routing_address = postCounterpartyJSON.other_bank_routing_address,
    other_branch_routing_scheme = postCounterpartyJSON.other_branch_routing_scheme,
    other_branch_routing_address = postCounterpartyJSON.other_branch_routing_address,
    other_account_routing_scheme = postCounterpartyJSON.other_account_routing_scheme,
    other_account_routing_address = postCounterpartyJSON.other_account_routing_address,
    is_beneficiary = true,
    other_account_secondary_routing_scheme = "accountId",
    other_account_secondary_routing_address= "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
    bespoke = postCounterpartyJSON.bespoke
  )
  
  val counterpartyMetadataJson = CounterpartyMetadataJson(
    public_alias = "String",
    more_info = "String",
    url = "String",
    image_url = "String",
    open_corporates_url = "String",
    corporate_location = locationJsonV210,
    physical_location = locationJsonV210,
    private_alias ="String"
  )
  
  val counterpartyWithMetadataJson = CounterpartyWithMetadataJson(
    name = postCounterpartyJSON.name,
    description = postCounterpartyJSON.description,
    created_by_user_id = "49e1e147-64c1-4823-ad9f-89efcd02a9fa",
    this_bank_id = "gh.29.uk",
    this_account_id = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
    this_view_id = "owner",
    counterparty_id = "1d65db7c-a7b2-4839-af41-958276ab7790",
    other_bank_routing_scheme = postCounterpartyJSON.other_bank_routing_scheme,
    other_bank_routing_address = postCounterpartyJSON.other_bank_routing_address,
    other_branch_routing_scheme = postCounterpartyJSON.other_branch_routing_scheme,
    other_branch_routing_address = postCounterpartyJSON.other_branch_routing_address,
    other_account_routing_scheme = postCounterpartyJSON.other_account_routing_scheme,
    other_account_routing_address = postCounterpartyJSON.other_account_routing_address,
    is_beneficiary = true,
    other_account_secondary_routing_scheme = "accountId",
    other_account_secondary_routing_address= "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
    bespoke = postCounterpartyJSON.bespoke,
    metadata = counterpartyMetadataJson
  )

  val counterpartiesJsonV220 = CounterpartiesJsonV220(
    counterparties = List(counterpartyJsonV220)
  )

  val bankJSONV220 = BankJSONV220(
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

  val branchJsonV220 = BranchJsonV220(
    id = "123",
    bank_id = "gh.29.uk",
    name = "OBP",
    address = addressJsonV140,
    location = locationJson,
    meta = metaJson,
    lobby = lobbyJson,
    drive_up = driveUpJson,
    branch_routing = branchRoutingJSON
  )


  val atmJsonV220 = AtmJsonV220(
    id = "123",
    bank_id = "gh.29.uk",
    name = "OBP",
    address = addressJsonV140,
    location = locationJson,
    meta = metaJson
  )

  val productJsonV220 = ProductJsonV220(
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
  val postPutProductJsonV310 = PostPutProductJsonV310(
    bank_id = "bankid123",
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

  val putProductCollectionsV310 = PutProductCollectionsV310("A", List("B", "C", "D"))





  val fxJsonV220 = FXRateJsonV220(
    bank_id = "bankid123",
    from_currency_code = "EUR",
    to_currency_code = "USD",
    conversion_value = 1,
    inverse_conversion_value = 1,
    effective_date = DateWithDayExampleObject
  )



  val createAccountJSONV220 = CreateAccountJSONV220(
    user_id = "66214b8e-259e-44ad-8868-3eb47be70646",
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
  
  val cachedFunctionJSON = CachedFunctionJSON(
    function_name = "getBanks",
    ttl_in_seconds = 5
  )
  val portJSON = PortJSON(
    property = "default",
    value = "8080"
  )
  val akkaJSON = AkkaJSON(
    ports = List(portJSON),
    log_level = "Debug",
    remote_data_secret_matched = Some(true)
  )
  val metricsJSON = MetricsJSON(
    property = "String",
    value = "Mapper"
  )
  val warehouseJSON = WarehouseJSON(
    property = "String",
    value = "ElasticSearch"
  )
  val elasticSearchJSON = ElasticSearchJSON(
    metrics = List(metricsJSON),
    warehouse = List(warehouseJSON)
  )
  
  val configurationJSON = ConfigurationJSON(
    akka = akkaJSON,
    elastic_search = elasticSearchJSON,
    cache = List(cachedFunctionJSON)
  )
  
  val connectorMetricJson = ConnectorMetricJson(
    connector_name = "mapper",
    function_name = "getBanks",
    correlation_id = "12345",
    date = DateWithDayExampleObject,
    duration = 1000
  )
  
  val connectorMetricsJson = ConnectorMetricsJson(
    metrics = List(connectorMetricJson)
  )
  
  //V300
  ///////////////////////////////////////////////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////
  import code.api.v3_0_0._

  val viewJsonV300 =  ViewJsonV300(
    id = "1234",
    short_name = "short_name",
    description = "description",
    metadata_view = "owner",
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
    can_see_bank_account_credit_limit = true
  )
  
  val viewsJsonV300 =  ViewsJsonV300(
    views = List(viewJsonV300)
  )

  val coreAccountJsonV300 = CoreAccountJsonV300(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    label = "String",
    bank_id = "gh.29.uk",
    account_routings = List(accountRoutingJsonV121)
  )
  
  val viewBasic = ViewBasic(
    id = "123",
    short_name ="short_name",
    description = "description",
    is_public = false
  )
  
  val coreAccountJson = CoreAccountJson(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    label = "String",
    bank_id = "gh.29.uk",
    account_type = "330",
    account_routings = List(accountRoutingJsonV121),
    views = List(viewBasic)
  )
  
  val coreAccountsJsonV300 = CoreAccountsJsonV300(accounts = List(coreAccountJson))

  val balances = Balances("/v1/accounts/3dc3d5b3-7023-4848-9853-f5400a64e80f/balances")
  
  val transactions  = Transactions("/v1/accounts/3dc3d5b3-7023-4848-9853-f5400a64e80f/transactions")
  
  val coreAccountJson_v1 = CoreAccountJsonV1(
    id = "3dc3d5b3-7023-4848-9853-f5400a64e80f",
    iban = "DE2310010010123456789",
    currency = "EUR",
    accountType = "Girokonto",
    cashAccountType = "CurrentAccount",
    _links = List(
      balances,
      transactions
    ),
    name = "Main Account"
  )
  val coreAccountsJsonV1 = CoreAccountsJsonV1(List(coreAccountJson_v1))
  
  val amountOfMoneyV1 = AmountOfMoneyV1(
    currency = "String",
    content = "String"
  )

  val accountInnerJsonUKOpenBanking_v200 = AccountInner(
    SchemeName = "SortCodeAccountNumber",
    Identification = "80200110203345",
    Name = "Mr Kevin",
    SecondaryIdentification = Some("00021")
  )

  val accountJsonUKOpenBanking_v200 = Account(
    AccountId = "22289",
    Currency = "GBP",
    AccountType = "Personal",
    AccountSubType = "CurrentAccount",
    Nickname = "Bills",
    Account = accountInnerJsonUKOpenBanking_v200
  )

  val accountList = AccountList(List(accountJsonUKOpenBanking_v200))
  
  val links =  Links(Self = s"${Constant.HostName}/open-banking/v2.0/accounts/")
  
  val metaUK = JSONFactory_UKOpenBanking_200.MetaUK(1) 
  
  val accountsJsonUKOpenBanking_v200 = Accounts(
    Data = accountList,
    Links = links,
    Meta = metaUK
  )
  
  val closingBookedBody = ClosingBookedBody(
    amount = amountOfMoneyV1,
    date = "2017-10-25"
  )
  
  val expectedBody = ExpectedBody(
    amount  = amountOfMoneyV1,
    lastActionDateTime = DateWithDayExampleObject
  )
  
  val accountBalance = AccountBalance(
    closingBooked = closingBookedBody,
    expected = expectedBody
  )
  
  val accountBalances = AccountBalances(
    `balances` = List(accountBalance)
  )
  
  val transactionJsonV1 = TransactionJsonV1(
    transactionId = "String",
    creditorName = "String",
    creditorAccount = ibanJson,
    amount = amountOfMoneyV1,
    bookingDate = DateWithDayExampleObject,
    valueDate = DateWithDayExampleObject,
    remittanceInformationUnstructured = "String"
  )
  
  val viewAccount = ViewAccount(viewAccount = "/v1/accounts/3dc3d5b3-7023-4848-9853- f5400a64e80f")
  
  val transactionsJsonV1 = TransactionsJsonV1(
    transactions_booked = List(transactionJsonV1),
    transactions_pending =  List(transactionJsonV1),
    _links = List(viewAccount)
  )
  
  val accountIdJson = AccountIdJson(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf"
  )
  
  val accountsIdsJsonV300 = AccountsIdsJsonV300(accounts = List(accountIdJson))

  val adapterInfoJsonV300 = AdapterInfoJsonV300(
    name = "String",
    version = "String",
    git_commit = "String",
    date = "2013-01-21T23:08:00Z"
  )
  val rateLimitingInfoV310 = RateLimitingInfoV310(
    enabled = true,
    technology = "REDIS",
    service_available = true,
    is_active = true
  )
  
  val thisAccountJsonV300 = ThisAccountJsonV300(
    id ="String",
    bank_routing = bankRoutingJsonV121,
    account_routings = List(accountRoutingJsonV121),
    holders =  List(accountHolderJSON)
  )
  
  val otherAccountJsonV300 = OtherAccountJsonV300(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    holder = accountHolderJSON,
    bank_routing = bankRoutingJsonV121,
    account_routings = List(accountRoutingJsonV121),
    metadata = otherAccountMetadataJSON
  )
  
  val otherAccountsJsonV300 = OtherAccountsJsonV300(
    other_accounts = List(otherAccountJsonV300)
  )
  
  val transactionJsonV300 = TransactionJsonV300(
    id= "String",
    this_account = thisAccountJsonV300,
    other_account = otherAccountJsonV300,
    details = transactionDetailsJSON,
    metadata = transactionMetadataJSON
  )
  
  val transactionsJsonV300 = TransactionsJsonV300(
    transactions = List(transactionJsonV300)
  )
  
  val coreCounterpartyJsonV300 = CoreCounterpartyJsonV300(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    holder = accountHolderJSON,
    bank_routing = bankRoutingJsonV121,
    account_routings = List(accountRoutingJsonV121)
  )
  
  val coreTransactionJsonV300 = CoreTransactionJsonV300(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    this_account = thisAccountJsonV300,
    other_account = coreCounterpartyJsonV300,
    details = coreTransactionDetailsJSON
  )
  
  val coreCounterpartiesJsonV300 =  CoreCounterpartiesJsonV300(
    counterparties = List(coreCounterpartyJsonV300)
  )
  
  val coreTransactionsJsonV300 = CoreTransactionsJsonV300(
    transactions = List(coreTransactionJsonV300)
  )
  
  //ended -- Transaction relevant case classes /////
  
  //stated -- account relevant case classes /////
  
  
  val accountHeldJson  = AccountHeldJson(
    id = "12314",
    bank_id=  "123",
    number = "123",
    account_routings = List(accountRoutingJsonV121)
  )
  
  val coreAccountsHeldJsonV300 = CoreAccountsHeldJsonV300(
    accounts= List(accountHeldJson)
  )
  val moderatedAccountJsonV300 = ModeratedAccountJsonV300(
    id= "String",
    bank_id = "gh.29.uk",
    label = "String",
    number = "String",
    owners = List(userJSONV121),
    `type`= "String",
    balance =  amountOfMoneyJsonV121,
    views_available = List(viewJsonV300),
    account_routings = List(accountRoutingJsonV121)
  )
  
  val moderatedCoreAccountJsonV300 = ModeratedCoreAccountJsonV300(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    bank_id= "String",
    label= "String",
    number= "String",
    owners =  List(userJSONV121),
    `type`= "String",
    balance = amountOfMoneyJsonV121,
    account_routings = List(accountRoutingJsonV121),
    account_rules = List(accountRuleJsonV300)
  )
  
  val moderatedCoreAccountsJsonV300 = ModeratedCoreAccountsJsonV300(List(moderatedCoreAccountJsonV300))

  val aggregateMetricsJSONV300 = AggregateMetricJSON(
    count = 7076,
    average_response_time = 65.21,
    minimum_response_time = 1,
    maximum_response_time = 9039
  )
  
  //APIMethods_UKOpenBanking_200 
  
  val bankTransactionCodeJson = BankTransactionCodeJson(
    Code = "ReceivedCreditTransfer",
    SubCode = "DomesticCreditTransfer"
  )

  val balanceUKOpenBankingJson = BalanceUKOpenBankingJson(
    Amount = amountOfMoneyJsonV121,
    CreditDebitIndicator = "Credit",
    Type = "InterimBooked"
  )

  val transactionCodeJson = TransactionCodeJson(
    Code = "Transfer",
    Issuer = "AlphaBank"
  )
  
  val transactionInnerJson  = TransactionInnerJson(
    AccountId = accountId.value,
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

  val transactionsInnerJson =  TransactionsInnerJson(
    Transaction = List(transactionInnerJson)
  )

  val metaInnerJson  = MetaInnerJson(
    TotalPages = 1,
    FirstAvailableDateTime = DateWithDayExampleObject,
    LastAvailableDateTime = DateWithDayExampleObject
  )

  val transactionsJsonUKV200 = TransactionsJsonUKV200(
    Data = transactionsInnerJson,
    Links = links.copy(s"${Constant.HostName}/open-banking/v2.0/accounts/22289/transactions/"),
    Meta = metaInnerJson
  )
  
  val creditLineJson = CreditLineJson(
    Included = true,
    Amount = amountOfMoneyJsonV121,
    Type = "Pre-Agreed"
  )
  
  val balanceJsonUK200 = BalanceJsonUKV200(
    AccountId = "22289",
    Amount = amountOfMoneyJsonV121,
    CreditDebitIndicator = "Credit",
    Type = "InterimAvailable",
    DateTime = DateWithDayExampleObject,
    CreditLine = List(creditLineJson)
  )
  
  val dataJsonUK200 = DataJsonUKV200(
    Balance = List(balanceJsonUK200)
  )
  
  val metaBisJson =  MetaBisJson(
    TotalPages = 1
  )
  
  val accountBalancesUKV200 = AccountBalancesUKV200(
    Data = dataJsonUK200,
    Links = links.copy(s"${Constant.HostName}/open-banking/v2.0/accounts/22289/balances/"),
    Meta = metaBisJson
  )
  
  val createScopeJson =  CreateScopeJson(bank_id = "gh.29.uk", role_name = "CanGetEntitlementsForAnyUserAtOneBank")
   
  val scopeJson = ScopeJson(
    scope_id = "88625da4-a671-435e-9d24-e5b6e5cc404f", 
    role_name = "CanGetEntitlementsForAnyUserAtOneBank", 
    bank_id = "gh.29.uk"
  )
  val scopeJsons = ScopeJsons(List(scopeJson))
  
  
  
  //V310 
  
  val orderObjectJson = OrderObjectJson(
    order_id ="xjksajfkj",
    order_date = "07082013",
    number_of_checkbooks = "4",
    distribution_channel = "1201",
    status = "2",
    first_check_number = "5165276",
    shipping_code = "1"
  )
  
  val orderJson = OrderJson(orderObjectJson)
  
  val accountV310Json = AccountV310Json(
    bank_id = "10",
    account_id = "xjfsafjj" ,
    account_type  ="330",
    account_routings  = List(accountRoutingJsonV121),
    branch_routings = List(branchRoutingJSON)
  )
  
  val checkbookOrdersJson = CheckbookOrdersJson(
    account = accountV310Json ,
    orders = List(orderJson)
  )

  val checkFundsAvailableJson = CheckFundsAvailableJson(
    "yes",
    new Date(),
    "c4ykz59svsr9b7fmdxk8ezs7"
  )
  
  val cardObjectJson = CardObjectJson(
    card_type = "5",
    card_description= "good",
    use_type  ="3"
  )
  
  val creditCardOrderStatusResponseJson = CreditCardOrderStatusResponseJson(
    cards = List(cardObjectJson)
  )
  
  val creditLimitRequestJson = CreditLimitRequestJson(
    requested_current_rate_amount1 = "String",
    requested_current_rate_amount2 = "String",
    requested_current_valid_end_date = "String",
    current_credit_documentation = "String",
    temporary_requested_current_amount = "String",
    requested_temporary_valid_end_date = "String",
    temporary_credit_documentation = "String",
  )
  
  val creditLimitOrderResponseJson = CreditLimitOrderResponseJson(
    execution_time = "String",
    execution_date = "String",
    token = "String",
    short_reference = "String"
  )
  
  val creditLimitOrderJson = CreditLimitOrderJson(
    rank_amount_1 = "String",
    nominal_interest_1 = "String",
    rank_amount_2 = "String",
    nominal_interest_2 = "String"
  )
  
  val topApiJson = TopApiJson(
    count = 7076,
    Implemented_by_partial_function = "getBanks",
    implemented_in_version = "v1.2.1"
  )
  
  val topApisJson = TopApisJson(List(topApiJson))
  
  val topConsumerJson = TopConsumerJson(
    count = 7076,
    consumer_id = "12312312",
    app_name = "Api Explorer",
    developer_email = "tesobe@tesobe.com"
  )
  
  val topConsumersJson = TopConsumersJson(List(topConsumerJson))
  
  val glossaryItem = GlossaryItem(
    title = "Title ",
    description =
      """Description.
        |
        |Goes here..
      """
  )
  
  val glossaryDescriptionJsonV300 =  GlossaryDescriptionJsonV300 (markdown= "String", html = "String")

  val glossaryItemJsonV300 = GlossaryItemJsonV300(
    title = "String",
    description = glossaryDescriptionJsonV300
  )

  val glossaryItemsJsonV300 = GlossaryItemsJsonV300 (glossary_items = List(glossaryItemJsonV300))
  
  val badLoginStatusJson = BadLoginStatusJson(
    username = "tesobe",
    bad_attempts_since_last_success_or_reset = 0,
    last_failure_date = DateWithMsExampleObject
  )

  val callLimitPostJson = CallLimitPostJson(
    per_second_call_limit = "-1",
    per_minute_call_limit = "-1",
    per_hour_call_limit = "-1",
    per_day_call_limit ="-1",
    per_week_call_limit = "-1",
    per_month_call_limit = "-1"
  )
  
  val rateLimit = RateLimit(Some(-1),Some(-1))
  
  val redisCallLimitJson = RedisCallLimitJson(
    Some(rateLimit),
    Some(rateLimit),
    Some(rateLimit),
    Some(rateLimit),
    Some(rateLimit),
    Some(rateLimit)
  )
  
  val callLimitJson = CallLimitJson(
    per_second_call_limit = "-1",
    per_minute_call_limit = "-1",
    per_hour_call_limit = "-1",
    per_day_call_limit ="-1",
    per_week_call_limit = "-1",
    per_month_call_limit = "-1",
    Some(redisCallLimitJson)
  )

  val accountWebhookPostJson = AccountWebhookPostJson(
    account_id = "fc23a7e2-7dd2-4bdf-a0b4-ae31232a4762",
    trigger_name = ApiTrigger.onBalanceChange.toString(),
    url = "https://localhost.openbankproject.com",
    http_method = "POST",
    http_protocol = "HTTP/1.1",
    is_active = "true"
  )
  val accountWebhookPutJson = AccountWebhookPutJson(
    account_webhook_id = "fc23a7e2-7dd2-4bdf-a0b4-ae31232a4762",
    is_active = "true"
  )
  val accountWebhookJson =  AccountWebhookJson(
    account_webhook_id = "613c83ea-80f9-4560-8404-b9cd4ec42a7f",
    bank_id = "obp-x",
    account_id = "fc23a7e2-7dd2-4bdf-a0b4-ae31232a4762",
    trigger_name = ApiTrigger.onBalanceChange.toString(),
    url = "https://localhost.openbankproject.com",
    http_method = "POST",
    http_protocol = "HTTP/1.1",
    created_by_user_id = "b1fd9b29-659d-4838-a300-ea65b65b5fb6",
    is_active = true
  )

  val accountWebhooksJson = AccountWebhooksJson(List(accountWebhookJson))
  
  val postUserAuthContextJson = PostUserAuthContextJson(
    key = "CUSTOMER_NUMBER",
    value = "78987432"
  )
  
  val userAuthContextJson = UserAuthContextJson(
    user_auth_context_id = "613c83ea-80f9-4560-8404-b9cd4ec42a7f",
    user_id = "qieuriopwoir987203984729384ipeiu",
    key = "CUSTOMER_NUMBER",
    value = "78987432"
  )

  val userAuthContextUpdateJson = UserAuthContextUpdateJson(
    user_auth_context_update_id = "613c83ea-80f9-4560-8404-b9cd4ec42a7f",
    user_id = "qieuriopwoir987203984729384ipeiu",
    key = "CUSTOMER_NUMBER",
    value = "78987432",
    status = UserAuthContextUpdateStatus.INITIATED.toString
  )
  
  val userAuthContextsJson = UserAuthContextsJson(
    user_auth_contexts = List(userAuthContextJson)
  )
  
  val obpApiLoopbackJson = ObpApiLoopbackJson("kafka_vSept2018","f0acd4be14cdcb94be3433ec95c1ad65228812a0","10 ms")
  
  val refresUserJson = RefreshUserJson("10 ms")
  
  val productAttributeJson = ProductAttributeJson(
    name = "OVERDRAFT_START_DATE",
    `type` = "DATE_WITH_DAY",
    value = "2012-04-23"
  )
  val productAttributeResponseJson = ProductAttributeResponseWithoutBankIdJson(
    product_code = "saving1",
    product_attribute_id = "613c83ea-80f9-4560-8404-b9cd4ec42a7f",
    name = "OVERDRAFT_START_DATE",
    `type` = "DATE_WITH_DAY",
    value = "2012-04-23"
  )

  val accountAttributeJson = AccountAttributeJson(
    bank_id = "123",
    account_id = "456",
    name = "OVERDRAFT_START_DATE",
    `type` = "DATE_WITH_DAY",
    value = "2012-04-23"
  )  
  val accountAttributeResponseJson = AccountAttributeResponseJson(
    bank_id = "123",
    account_id = "456",
    product_code = "saving1",
    account_attribute_id = "613c83ea-80f9-4560-8404-b9cd4ec42a7f",
    name = "OVERDRAFT_START_DATE",
    `type` = "DATE_WITH_DAY",
    value = "2012-04-23"
  )

  val accountApplicationJson = AccountApplicationJson(
    product_code = "saveing1",
    user_id = Some("123"),
    customer_id = Some("123")
  )

  val accountApplicationResponseJson = AccountApplicationResponseJson (
    account_application_id = "gc23a7e2-7dd2-4bdf-a0b4-ae31232a4763",
    product_code = "saveing1",
    user = resourceUserJSON,
    customer = customerJsonV310,
    date_of_application = DateWithDayExampleObject,
    status = "REQUESTED"
  )
  val accountApplicationUpdateStatusJson = AccountApplicationUpdateStatusJson(
    status = "ACCEPTED"
  )

  val accountApplicationsJsonV310 = AccountApplicationsJsonV310(List(accountApplicationResponseJson))

  val productJsonV310 = ProductJsonV310(
    bank_id = "gh.29.uk",
    code = "product_code",
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
  val productsJsonV310 = ProductsJsonV310(products = List(productJsonV310))
  
  
  val productCollectionItemJsonV310 = ProductCollectionItemJsonV310(member_product_code = "A")
  val productCollectionJsonV310 = ProductCollectionJsonV310(
    collection_code = "C",
    product_code = "D", items = List(productCollectionItemJsonV310, productCollectionItemJsonV310.copy(member_product_code = "B"))
  )
  val productCollectionsJsonV310 = ProductCollectionsJsonV310(product_collection = List(productCollectionJsonV310))
  
  val productCollectionJsonTreeV310 = ProductCollectionJsonTreeV310(collection_code = "A", products = List(productJsonV310))
  
  val contactDetailsJson = ContactDetailsJson(
    name = "Simon ",
    mobile_phone = "+44 07972 444 876",
    email_address = "contact@tesobe.com"
  )
  
  val inviteeJson = InviteeJson(
    contactDetailsJson,
    "String, eg: Good"
  )
  
  val createMeetingJsonV310 = CreateMeetingJsonV310(
    provider_id = "String, eg: tokbox",
    purpose_id = "String, eg: onboarding",
    date = DateWithMsExampleObject,
    creator = contactDetailsJson,
    invitees = List(inviteeJson)
  )
  
  val meetingJsonV310 = MeetingJsonV310(
    meeting_id = "UUID-String",
    provider_id = "String, eg: tokbox",
    purpose_id = "String, eg: onboarding",
    bank_id = "gh.29.uk",
    present = meetingPresentJSON,
    keys = meetingKeysJSON,
    when = DateWithDayExampleObject,
    creator = contactDetailsJson,
    invitees = List(inviteeJson)
  )
  
  val meetingsJsonV310 = MeetingsJsonV310(List(meetingJsonV310))
  
  case class SeverJWK(kty: String = "RSA",
                      e: String = "AQAB",
                      use: String = "sig",
                      kid: String = "fr6-BxXH5gikFeZ2O6rGk0LUmJpukeswASN_TMW8U_s",
                      n: String = "hrB0OWqg6AeNU3WCnhheG18R5EbQtdNYGOaSeylTjkj2lZr0_vkhNVYvase-CroxO4HOT06InxTYwLnmJiyv2cZxReuoVjTlk--olGu-9MZooiFiqWez0JzndyKxQ27OiAjFsMh0P04kaUXeHKhXRfiU7K2FqBshR1UlnWe7iHLkq2p9rrGjxQc7ff0w-Uc0f-8PWg36Y2Od7s65493iVQwnI13egqMaSvgB1s8_dgm08noEjhr8C5m1aKmr5oipWEPNi-SBV2VNuiCLR1IEPuXq0tOwwZfv31t34KPO-2H2bbaWmzGJy9mMOGqoNrbXyGiUZoyeHRELaNtm1GilyQ")
  val severJWK = SeverJWK()
  
  val consentJsonV310 = ConsentJsonV310(
    consent_id = "9d429899-24f5-42c8-8565-943ffa6a7945",
    jwt = "eyJhbGciOiJIUzI1NiJ9.eyJlbnRpdGxlbWVudHMiOltdLCJjcmVhdGVkQnlVc2VySWQiOiJhYjY1MzlhOS1iMTA1LTQ0ODktYTg4My0wYWQ4ZDZjNjE2NTciLCJzdWIiOiIyMWUxYzhjYy1mOTE4LTRlYWMtYjhlMy01ZTVlZWM2YjNiNGIiLCJhdWQiOiJlanpuazUwNWQxMzJyeW9tbmhieDFxbXRvaHVyYnNiYjBraWphanNrIiwibmJmIjoxNTUzNTU0ODk5LCJpc3MiOiJodHRwczpcL1wvd3d3Lm9wZW5iYW5rcHJvamVjdC5jb20iLCJleHAiOjE1NTM1NTg0OTksImlhdCI6MTU1MzU1NDg5OSwianRpIjoiMDlmODhkNWYtZWNlNi00Mzk4LThlOTktNjYxMWZhMWNkYmQ1Iiwidmlld3MiOlt7ImFjY291bnRfaWQiOiJtYXJrb19wcml2aXRlXzAxIiwiYmFua19pZCI6ImdoLjI5LnVrLngiLCJ2aWV3X2lkIjoib3duZXIifSx7ImFjY291bnRfaWQiOiJtYXJrb19wcml2aXRlXzAyIiwiYmFua19pZCI6ImdoLjI5LnVrLngiLCJ2aWV3X2lkIjoib3duZXIifV19.8cc7cBEf2NyQvJoukBCmDLT7LXYcuzTcSYLqSpbxLp4",
    status = "INITIATED"
  )
  
  val consentsJsonV310 = ConsentsJsonV310(List(consentJsonV310))
  
  val oAuth2ServerJwksUrisJson = OAuth2ServerJwksUrisJson(List(OAuth2ServerJWKURIJson("https://www.googleapis.com/oauth2/v3/certs")))
  
  //The common error or success format.
  //Just some helper format to use in Json 
  case class NoSupportYet()
  
  val noSupportYet = NoSupportYet()
  
  val allFields ={
    val allFieldsThisFile = for (
      v <- this.getClass.getDeclaredFields
      //add guard, ignore the SwaggerJSONsV220.this and allFieldsAndValues fields
      if (APIUtil.notExstingBaseClass(v.getName()))
    )
      yield {
        v.setAccessible(true)
        v.get(this)
      }

    allFieldsThisFile ++ JSONFactoryCustom300.allFields ++ SandboxData.allFields //++ JsonFactory_APIBuilder.allFields
  }

}
