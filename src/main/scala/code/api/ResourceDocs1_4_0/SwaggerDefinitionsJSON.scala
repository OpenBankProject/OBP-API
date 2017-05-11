package code.api.ResourceDocs1_4_0

import code.api.util.APIUtil
import code.api.util.APIUtil.defaultJValue
import code.api.util.APIUtil._

/**
  * Created by zhanghongwei on 07/04/2017.
  * This object prepare all the JSON case classes for Swagger .
  * For now, just support all the endpoints for V220.
  * Because different versions, has different case classes.
  * It is hard to mapping all these case class dynamicly for now.
  * May be it can be fixed later.
  *
  */
object SwaggerDefinitionsJSON {
  
  
  // from code.model, not from normal version JSON Factory
  ///////////////////////////////////////////////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////
  import code.model._
  
  val amountOfMoney = AmountOfMoney(
    currency = "EUR",
    amount = "100"
  )
  
  val createViewJSON = CreateViewJSON(
    name = "test",
    description = "good",
    is_public = true,
    which_alias_to_use = "good",
    hide_metadata_if_alias_used = true,
    allowed_actions = List("good")
  )
  
  val updateViewJSON = UpdateViewJSON(
    description = "good",
    is_public = true,
    which_alias_to_use = "good",
    hide_metadata_if_alias_used = true,
    allowed_actions = List("good")
  )
  
  val transactionTypeId = TransactionTypeId(value = "123")
  
  val bankId = BankId(value = "gh.uk.9j")
  
  val transactionRequestId = TransactionRequestId(value = "123")
  
  val counterpartyId = CounterpartyId(value = "123")
  
  val accountId = AccountId(value = "123")
  
  val viewId = ViewId(value = "owner")
  
  
  // from code.TransactionTypes.TransactionType, not from normal version Factory
  ///////////////////////////////////////////////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////
  import code.TransactionTypes.TransactionType._
  
  val transactionType = TransactionType(
    id = transactionTypeId,
    bankId = bankId,
    shortCode = "80080",
    summary = "good",
    description = "good",
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
  
  val transactionRequestBody = TransactionRequestBody(
    to = transactionRequestAccount,
    value= amountOfMoney,
    description= "String"
  )
  
  val transactionRequest = TransactionRequest(
    id= transactionRequestId,
    `type`= "String",
    from= transactionRequestAccount,
    details= defaultJValue, // Note= This is unstructured! (allows multiple "to" accounts etc.)
    body= transactionRequestBody, // Note= This is structured with one "to" account etc.
    transaction_ids= "String",
    status= "String",
    start_date= exampleDate,
    end_date= exampleDate,
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
    is_beneficiary= true
  )
  
  // from code.bankconnectors, not from normal version Factory
  ///////////////////////////////////////////////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////
  import code.bankconnectors._
  
  val messageDocJson = MessageDocJson(
    process = "getBanks",
    message_format = "KafkaV2017",
    description = "get Banks",
    example_outbound_message = defaultJValue,
    example_inbound_message = defaultJValue
  )
  
  val messageDocsJson = MessageDocsJson(messageDocs = List(messageDocJson))
  
  //V121 - code.api.v1_2_1
  ///////////////////////////////////////////////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////
  import code.api.v1_2_1._
  
  val makePaymentJson = MakePaymentJson(
    bank_id = "String",
    account_id = "String",
    amount = "String"
  )
  
  val transactionIdJson = TransactionIdJson(
    transaction_id = "123"
  )
  
  val hostedBy = HostedBy(
    organisation = "String",
    email = "String",
    phone = "String"
  )
  val akka = Akka(
    remote_data_secret_matched = Option(true)
  )
  
  val apiInfoJSON = APIInfoJSON(
    version = "String",
    version_status = "String",
    git_commit = "String",
    connector = "String",
    hosted_by = hostedBy,
    akka = akka
  )
  
  val errorMessage = ErrorMessage(
    error = "String"
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
  
  val accountRoutingJSON = AccountRoutingJsonV121(
    scheme = "IBAN",
    address = "DE89 3704 0044 0532 0130 00"
  )
  
  val amountOfMoneyJsonV121 = AmountOfMoneyJsonV121(
    currency = "EUR",
    amount = "10"
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
  
  val bankRoutingJSON = BankRoutingJsonV121(
    scheme = "Bank_ID",
    address = "gh.29.uk"
  )
  
  val bankJSON = BankJSON(
    id = "gh.29.uk",
    short_name = "short_name ",
    full_name = "full_name",
    logo = "logo",
    website = "www.openbankproject.com",
    bank_routing = bankRoutingJSON
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
    account_routing = accountRoutingJSON
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
    date = exampleDate,
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
    description = "GOOD",
    posted = exampleDate,
    completed = exampleDate,
    new_balance = amountOfMoneyJsonV121,
    value = amountOfMoneyJsonV121
  )
  
  val transactionImageJSON = TransactionImageJSON(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    label = "NONE",
    URL = "www.openbankproject.com",
    date = exampleDate,
    user = userJSONV121
  )
  
  val transactionImagesJSON = TransactionImagesJSON(
    images = List(transactionImageJSON)
  )
  
  val transactionCommentJSON = TransactionCommentJSON(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    value = "OBP",
    date = exampleDate,
    user = userJSONV121
  )
  
  val transactionTagJSON = TransactionTagJSON(
    id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    value = "OBP",
    date = exampleDate,
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
    views = List("good")
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
    requested_date = exampleDate,
    reason_requested = "forgot"
  )
  
  val replacementJSON = ReplacementJSON(
    requested_date = exampleDate,
    reason_requested = "Good Point"
  )
  
  val physicalCardJSON = PhysicalCardJSON(
    bank_card_number = "String",
    name_on_card = "String",
    issue_number = "String",
    serial_number = "String",
    valid_from_date = exampleDate,
    expires_date = exampleDate,
    enabled = true,
    cancelled = true,
    on_hot_list = true,
    technology = "String",
    networks = List("String"),
    allows = List("String"),
    account = accountJSON,
    replacement = replacementJSON,
    pin_reset = List(pinResetJSON),
    collected = exampleDate,
    posted = exampleDate
  )
  
  val physicalCardsJSON = PhysicalCardsJSON(
    cards = List(physicalCardJSON)
  )
  
  val postPhysicalCardJSON = PostPhysicalCardJSON(
    bank_card_number = "String",
    name_on_card = "String",
    issue_number = "String",
    serial_number = "String",
    valid_from_date = exampleDate,
    expires_date = exampleDate,
    enabled = true,
    cancelled = true,
    on_hot_list = true,
    technology = "String",
    networks = List("String"),
    allows = List("credit"),
    account_id = "String",
    replacement = replacementJSON,
    pin_reset = List(pinResetJSON),
    collected = exampleDate,
    posted = exampleDate
  )
  
  //V140 -- code.api.v1_4_0.JSONFactory1_4_0
  ///////////////////////////////////////////////////////////////////////////
  ///////////////////////////////////////////////////////////////////////////
  import code.api.v1_4_0.JSONFactory1_4_0._
  
  val customerFaceImageJson = CustomerFaceImageJson(
    url = "www.openbankproject",
    date = exampleDate
  )
  
  val locationJson = LocationJson(
    latitude = 11.45,
    longitude = 11.45
  )
  
  val transactionRequestChargeJsonV140 = TransactionRequestChargeJsonV140(
    summary = "Good",
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
    challenge_type = "SANDBOX_TAN"
  )
  
  val driveUpJson = DriveUpJson(
    hours = "5"
  )
  val licenseJson = LicenseJson(
    id = "5",
    name = "TESOBE"
  )
  val metaJson = MetaJson(
    license = licenseJson
  )
  val lobbyJson = LobbyJson(
    hours = "5"
  )
  val addressJson = AddressJson(
    line_1 = "Berlin",
    line_2 = "Berlin",
    line_3 = "Berlin",
    city = "Berlin",
    state = "Berlin",
    postcode = "123",
    country = "Germany"
  )
  val challengeAnswerJSON = ChallengeAnswerJSON(
    id = "b20dd004-93e3-494f-8773-69e3ff8c205e",
    answer = "good"
  )
  
  val postCustomerJson = PostCustomerJson(
    customer_number = "String",
    legal_name = "String",
    mobile_phone_number = "String",
    email = "String",
    face_image = customerFaceImageJson,
    date_of_birth = exampleDate,
    relationship_status = "String",
    dependants = 1,
    dob_of_dependants = List(exampleDate),
    highest_education_attained = "String",
    employment_status = "String",
    kyc_status = true,
    last_ok_date = exampleDate
  )
  
  val customerJsonV140 = CustomerJsonV140(
    customer_id = "String",
    customer_number = "String",
    legal_name = "String",
    mobile_phone_number = "String",
    email = "String",
    face_image = customerFaceImageJson,
    date_of_birth = exampleDate,
    relationship_status = "String",
    dependants = 10,
    dob_of_dependants = List(exampleDate),
    highest_education_attained = "String",
    employment_status = "String",
    kyc_status = true,
    last_ok_date = exampleDate
  )
  
  val customersJsonV140 = CustomersJsonV140(
    customers = List(customerJsonV140)
  )
  
  val customerMessageJson = CustomerMessageJson(
    id = "String",
    date = exampleDate,
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
    scheme = "String",
    address = "String"
  )
  
  val branchJson = BranchJson(
    id = "String",
    name = "String",
    address = addressJson,
    location = locationJson,
    lobby = lobbyJson,
    drive_up = driveUpJson,
    meta = metaJson,
    branch_routing = branchRoutingJSON
  )
  
  val branchesJson = BranchesJson(branches = List(branchJson))
  
  val atmJson = AtmJson(
    id = "String",
    name = "String",
    address = addressJson,
    location = locationJson,
    meta = metaJson
  )
  
  val atmsJson = AtmsJson(atms = List(atmJson))
  
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
    id = "String",
    bank_id = "String",
    customer_name = "String",
    customer_number = "String",
    category = "String",
    detail = "String",
    channel = "String",
    scheduled_date = exampleDate,
    actual_date = exampleDate,
    result = "String"
  )
  
  val crmEventsJson = CrmEventsJson(crm_events = List(crmEventJson))
  
  val implementedByJson = ImplementedByJson(
    version = "1_4_0",
    function = "getBranches"
  )
  // Used to describe the OBP API calls for documentation and API discovery purposes
  val resourceDocJson = ResourceDocJson(
    operation_id = "String",
    request_verb = "String",
    request_url = "String",
    summary = "String",
    description = "String",
    example_request_body = successMessage, //TODO maybe need fix
    success_response_body = successMessage,
    error_response_bodies = List("OBP-10001= Incorrect json format."),
    implemented_by = implementedByJson,
    is_core = true,
    is_psd2 = true,
    is_obwg = true,
    tags = List("String")
  )
  
  val resourceDocsJson = ResourceDocsJson(resource_docs = List(resourceDocJson))
  
  val transactionRequestBodyJsonV140 = TransactionRequestBodyJsonV140(
    to = transactionRequestAccountJsonV140,
    value = amountOfMoneyJsonV121,
    description = "String",
    challenge_type = "String"
  )
  val transactionRequestJSON = TransactionRequestJsonV140(
    id = "String",
    `type` = "String",
    from = transactionRequestAccountJsonV140,
    body = transactionRequestBodyJsonV140,
    transaction_ids = "String",
    status = "String",
    start_date = exampleDate,
    end_date = exampleDate,
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
    account_routing = accountRoutingJSON
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
    issue_date = exampleDate,
    issue_place = "PlaceholderString",
    expiry_date = exampleDate
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
    date = exampleDate,
    relates_to_kyc_document_id = "PlaceholderString",
    relates_to_kyc_check_id = "PlaceholderString"
  )
  val kycMediasJSON = KycMediasJSON(medias = List(kycMediaJSON))
  
  
  val kycCheckJSON = KycCheckJSON(
    bank_id = "PlaceholderString",
    customer_id = "PlaceholderString",
    id = "PlaceholderString",
    customer_number = "PlaceholderString",
    date = exampleDate,
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
    date = exampleDate
  )
  var kycStatusesJSON = KycStatusesJSON(statuses = List(kycStatusJSON))
  
  var socialMediaJSON = SocialMediaJSON(
    customer_number = "PlaceholderString",
    `type` = "PlaceholderString",
    handle = "PlaceholderString",
    date_added = exampleDate,
    date_activated = exampleDate
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
  
  val userJSONV200 = UserJSONV200(
    user_id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
    email = "robert.x.0.gh@example.com",
    provider_id = "OBP",
    provider = "OBP",
    username = "robert.x.0.gh",
    entitlements = entitlementJSONs
  )
  
  val coreTransactionDetailsJSON = CoreTransactionDetailsJSON(
    `type` = "AC",
    description = "OBP",
    posted = exampleDate,
    completed = exampleDate,
    new_balance = amountOfMoneyJsonV121,
    value = amountOfMoneyJsonV121
  )
  
  val coreAccountHolderJSON = CoreAccountHolderJSON(
    name = "ZACK"
  )
  
  val createEntitlementJSON = CreateEntitlementJSON(
    bank_id = "String",
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
    summary = "Good",
    value = amountOfMoneyJsonV121
  )
  
  val transactionRequestWithChargeJson = TransactionRequestWithChargeJson(
    id = "82f92531-9c63-4246-abfc-96c20ec46188",
    `type` = "SANDBOX_TAN",
    from = transactionRequestAccountJsonV140,
    details = defaultJValue,
    transaction_ids = "666666-9c63-4246-abfc-96c20ec46188",
    status = "COMPLETED",
    start_date = exampleDate,
    end_date = exampleDate,
    challenge = challengeJsonV140,
    charge = transactionRequestChargeJsonV200
  )
  
  val transactionRequestBodyJsonV200 = TransactionRequestBodyJsonV200(
    to = transactionRequestAccountJsonV140,
    value = amountOfMoneyJsonV121,
    description = "Good"
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
    bank_id = "String",
    present = meetingPresentJSON,
    keys = meetingKeysJSON,
    when = exampleDate
  )
  
  val meetingsJson = MeetingsJson(
    meetings = List(meetingJson)
  )
  
  
  val userCustomerLinkJson = UserCustomerLinkJson(
    user_customer_link_id = "String",
    customer_id = "String",
    user_id = "String",
    date_inserted = exampleDate,
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
    customer_number = "String",
    `type` = "String",
    number = "String",
    issue_date = exampleDate,
    issue_place = "String",
    expiry_date = exampleDate
  )
  
  val postKycMediaJSON = PostKycMediaJSON(
    customer_number = "String",
    `type` = "String",
    url = "String",
    date = exampleDate,
    relates_to_kyc_document_id = "String",
    relates_to_kyc_check_id = "String"
  )
  
  val postKycCheckJSON = PostKycCheckJSON(
    customer_number = "1239879",
    date = exampleDate,
    how = "online_meeting",
    staff_user_id = "67876",
    staff_name = "Simon",
    satisfied = true,
    comments = "String"
  )
  
  val postKycStatusJSON = PostKycStatusJSON(
    customer_number = "String",
    ok = true,
    date = exampleDate
  )
  
  val createCustomerJson = CreateCustomerJson(
    user_id = "String",
    customer_number = "String",
    legal_name = "String",
    mobile_phone_number = "String",
    email = "String",
    face_image = customerFaceImageJson,
    date_of_birth = exampleDate,
    relationship_status = "String",
    dependants = 1,
    dob_of_dependants = List(exampleDate),
    highest_education_attained = "String",
    employment_status = "String",
    kyc_status = true,
    last_ok_date = exampleDate
  )
  
  val transactionRequestJsonV200 = TransactionRequestJsonV200(
    id = "String",
    `type` = "String",
    from = transactionRequestAccountJsonV140,
    body = transactionRequestBodyJsonV200,
    transaction_ids = "String",
    status = "String",
    start_date = exampleDate,
    end_date = exampleDate,
    challenge = challengeJsonV140
  )
  
  val transactionRequestWithChargesJson = TransactionRequestWithChargesJson(
    transaction_requests_with_charges = List(transactionRequestWithChargeJson)
  )
  
  val usersJSONV200 = UsersJSONV200(
    users = List(userJSONV200)
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
    date = exampleDate,
    user_name = "OBP",
    app_name = "SOFI",
    developer_email = "contact@tesobe.ocm",
    implemented_by_partial_function = "getBanks",
    implemented_in_version = "v210",
    consumer_id = "123",
    verb = "get"
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
    "SHARED"
  )
  
  val transactionRequestBodySEPAJSON = TransactionRequestBodySEPAJSON(
    amountOfMoneyJsonV121,
    ibanJson,
    "This is a SEPA Transaction Request",
    "SHARED"
  )
  
  val customerCreditRatingJSON = CustomerCreditRatingJSON(
    rating = "OBP",
    source = "OBP"
  )
  
  val customerJsonV210 = CustomerJsonV210(
    customer_id = "123",
    customer_number = "123",
    legal_name = "legal_name",
    mobile_phone_number = "123",
    email = "contact@tesobe.com",
    face_image = customerFaceImageJson,
    date_of_birth = exampleDate,
    relationship_status = "123",
    dependants = 123,
    dob_of_dependants = List(exampleDate),
    credit_rating = Option(customerCreditRatingJSON),
    credit_limit = Option(amountOfMoneyJsonV121),
    highest_education_attained = "123",
    employment_status = "123",
    kyc_status = true,
    last_ok_date = exampleDate
  )
  
  val userJSONV210 = UserJSONV210(
    id = "123",
    provider = "OBP",
    username = "OBP"
  )
  
  val locationJSON =
    LocationJSONV210(
      latitude = 11.45,
      longitude = 11.45,
      date = exampleDate,
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
      date_of_birth = exampleDate,
      relationship_status = "Single",
      dependants = 5,
      dob_of_dependants = List(exampleDate),
      credit_rating = customerCreditRatingJSON,
      credit_limit = amountOfMoneyJsonV121,
      highest_education_attained = "Bachelor’s Degree",
      employment_status = "Employed",
      kyc_status = true,
      last_ok_date = exampleDate
    )
  
  val transactionRequestWithChargeJSON210 = TransactionRequestWithChargeJSON210(
    id = "4050046c-63b3-4868-8a22-14b4181d33a6",
    `type` = "SANDBOX_TAN",
    from = transactionRequestAccountJsonV140,
    details = defaultJValue,
    transaction_ids = List("902ba3bb-dedd-45e7-9319-2fd3f2cd98a1"),
    status = "COMPLETED",
    start_date = exampleDate,
    end_date = exampleDate,
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
    created = exampleDate
  )
  
  val consumersJson = ConsumersJson(
    list = List(consumerJSON)
  )
  
  val putEnabledJSON = PutEnabledJSON(
    enabled = false
  )
  
  val productJsonV210 = ProductJsonV210(
    code = "123",
    name = "Good",
    category = "OBP",
    family = "Mother",
    super_family = "GOOD",
    more_info_url = "www.openbankproject.com",
    details = "good ides",
    description = "Good boy",
    meta = metaJson
  )
  
  val productsJsonV210 = ProductsJsonV210(products = List(productJsonV210))
  
  val postCounterpartyJSON = PostCounterpartyJSON(
    name = "GOOD",
    other_account_routing_scheme = "IBAN",
    other_account_routing_address = "7987987-2348987-234234",
    other_bank_routing_scheme = "BIC",
    other_bank_routing_address = "123456",
    other_branch_routing_scheme = "OBP",
    other_branch_routing_address = "Berlin",
    is_beneficiary = true
  )
  
  val metricsJson = MetricsJson(
    metrics = List(metricJson)
  )
  
  val branchJsonPut = BranchJsonPut("gh.29.fi", "OBP",
    addressJson,
    locationJson,
    metaJson,
    lobbyJson,
    driveUpJson
  )
  
  val branchJsonPost = BranchJsonPost("123", "gh.29.fi", "OBP",
    addressJson,
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
  
  val fXRateJSON = FXRateJSON(
    from_currency_code = "EUR",
    to_currency_code = "GBP",
    conversion_value = 1.001,
    inverse_conversion_value = 0.998,
    effective_date = exampleDate
  )
  
  val counterpartyJsonV220 = CounterpartyJsonV220(
    name = "b2dd6c2c-7ebd-4014-9c73-b7d28cc71fe1",
    created_by_user_id = "49e1e147-64c1-4823-ad9f-89efcd02a9fa",
    this_bank_id = "gh.29.uk",
    this_account_id = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
    this_view_id = "owner",
    counterparty_id = "1d65db7c-a7b2-4839-af41-958276ab7790",
    other_bank_routing_scheme = "test",
    other_bank_routing_address = "test",
    other_branch_routing_scheme = "OBP",
    other_branch_routing_address = "Berlin",
    other_account_routing_scheme = "IBAN",
    other_account_routing_address = "DE89 3704 0044 0532 0130 00",
    is_beneficiary = true
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
  
  val branchJSONV220 = BranchJSONV220(
    id = "123",
    bank_id = "gh.29.uk",
    name = "OBP",
    address = addressJson,
    location = locationJson,
    meta = metaJson,
    lobby = lobbyJson,
    drive_up = driveUpJson,
    branch_routing = branchRoutingJSON
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
    log_level = "Debug"
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
    obp_api_request_id = "12345",
    date = exampleDate,
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
    can_see_where_tag = true,
    //V300 new 
    can_see_bank_routing_scheme = true,
    can_see_bank_routing_address = true,
    can_see_bank_account_routing_scheme = true,
    can_see_bank_account_routing_address = true,
    can_see_other_bank_routing_scheme = true,
    can_see_other_bank_routing_address = true,
    can_see_other_account_routing_scheme = true,
    can_see_other_account_routing_address = true 
  )
  
  val viewsJsonV300 =  ViewsJsonV300(
    views = List(viewJsonV300)
  )
  
  //The commont error or success format.
  //Just some helper format to use in Json 
  case class NoSupportYet()
  
  val noSupportYet = NoSupportYet()
  
  val allFields =
    for (
      v <- this.getClass.getDeclaredFields
      //add guard, ignore the SwaggerJSONsV220.this and allFieldsAndValues fields
      if (APIUtil.notExstingBaseClass(v.getName()))
    )
      yield {
        v.setAccessible(true)
        v.get(this)
      }
}
