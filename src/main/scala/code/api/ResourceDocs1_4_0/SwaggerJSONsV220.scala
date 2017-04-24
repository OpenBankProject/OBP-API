package code.api.ResourceDocs1_4_0

import code.api.util.APIUtil
import code.api.util.APIUtil._
import code.model.CreateViewJSON
import net.liftweb.json.Extraction
/**
  * Created by zhanghongwei on 07/04/2017.
  * This object prepare all the JSON case classes for Swagger .
  * For now, just support all the endpoints for V220.
  * Because different versions, has different case classes.
  * It is hard to mapping all these case class dynamicly for now.
  * May be it can be fixed later.
  *
  */
object SwaggerJSONsV220 {
  
  val basicViewJSON = code.api.v2_0_0.BasicViewJSON(
    id = "1",
    short_name = "HHH",
    is_public = true
  )
  
  val basicAccountJSON =
    code.api.v2_0_0.BasicAccountJSON(
      id = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
      label = "NoneLabel",
      bank_id = "gh.29.uk",
      views_available = List(basicViewJSON)
    )
  
  val coreAccountJSON = code.api.v2_0_0.CoreAccountJSON(
    id = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
    label = "NoneLabel",
    bank_id = "gh.29.uk",
    _links = defaultJValue
  )
  
  
  val basicAccountsJSON =
    code.api.v2_0_0.BasicAccountsJSON(
      accounts = List(basicAccountJSON)
    )
  val accountRoutingJSON =
    code.api.v1_2_1.AccountRoutingJSON(
      scheme = "swftcode",
      address = "UKTF3049auf"
    )
  
  val amountOfMoneyJSON =
    code.api.v1_2_1.AmountOfMoneyJSON(
      currency = "EUR",
      amount = "10"
    )
  
  val userJSONV121 =
    code.api.v1_2_1.UserJSON(
      id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
      provider = "OBP",
      display_name = "OBP"
    )
  val moderatedCoreAccountJSON =
    code.api.v2_0_0.JSONFactory200.ModeratedCoreAccountJSON(
      id = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
      label = "NoneLabel",
      number = "123",
      owners = List(userJSONV121),
      `type` = "OBP",
      balance = amountOfMoneyJSON,
      IBAN = "GR1301720530005053000582373",
      swift_bic = "UKTF3049auf",
      bank_id = "gh.29.uk",
      account_routing = accountRoutingJSON
    )
  
  val viewJSON =
    code.api.v1_2_1.ViewJSON(
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
  
  val viewsJSON =
    code.api.v1_2_1.ViewsJSON(
      views = List(viewJSON)
    )
  
  val moderatedAccountJSON =
    code.api.v1_2_1.ModeratedAccountJSON(
      id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
      label = "NoneLabel",
      number = "123",
      owners = List(userJSONV121),
      `type` = "OBP",
      balance = amountOfMoneyJSON,
      IBAN = "GR1301720530005053000582373",
      swift_bic = "UKTF3049auf",
      views_available = List(viewJSON),
      bank_id = "gh.29.uk",
      account_routing = accountRoutingJSON
    )
  
  val entitlementJSON =
    code.api.v2_0_0.EntitlementJSON(
      entitlement_id = "6fb17583-1e49-4435-bb74-a14fe0996723",
      role_name = "CanQueryOtherUser",
      bank_id = "gh.29.uk"
    )
  val entitlementJSONs =
    code.api.v2_0_0.EntitlementJSONs(
      list = List(entitlementJSON)
    )
  
  val userJSONV200 =
    code.api.v2_0_0.JSONFactory200.UserJSON(
      user_id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
      email = "robert.x.0.gh@example.com",
      provider_id = "OBP",
      provider = "OBP",
      username = "robert.x.0.gh",
      entitlements = entitlementJSONs
    )
  
  val bankRoutingJSON =
    code.api.v1_2_1.BankRoutingJSON(
      scheme = "Bank_ID",
      address = "gh.29.uk"
    )
  
  val bankJSON =
    code.api.v1_2_1.BankJSON(
      id = "gh.29.uk",
      short_name = "short_name ",
      full_name = "full_name",
      logo = "logo",
      website = "www.openbankproject.com",
      bank_routing = bankRoutingJSON
    )
  
  val banksJSON =
    code.api.v1_2_1.BanksJSON(
      banks = List(bankJSON)
    )
  
  val customerFaceImageJson =
    code.api.v1_4_0.JSONFactory1_4_0.CustomerFaceImageJson(
      url = "www.openbankproject",
      date = exampleDate
    )
  
  val customerCreditRatingJSON = code.api.v2_1_0.CustomerCreditRatingJSON(
    rating = "OBP",
    source = "OBP"
  )
  
  val customerJson =
    code.api.v2_1_0.CustomerJson(
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
      credit_limit = Option(amountOfMoneyJSON),
      highest_education_attained = "123",
      employment_status = "123",
      kyc_status = true,
      last_ok_date = exampleDate
    )
  
  val accountHolderJSON =
    code.api.v1_2_1.AccountHolderJSON(
      name = "OBP",
      is_alias = true
    )
  
  val minimalBankJSON =
    code.api.v1_2_1.MinimalBankJSON(
      national_identifier = "OBP",
      name = "OBP"
    )
  
  val locationJSON =
    code.api.v1_2_1.LocationJSON(
      latitude = 11.45,
      longitude = 11.45,
      date = exampleDate,
      user = userJSONV121
    )
  
  val thisAccountJSON =
    code.api.v1_2_1.ThisAccountJSON(
      id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
      holders = List(accountHolderJSON),
      number = "123",
      kind = "AC",
      IBAN = "UK1234AD",
      swift_bic = "UK1234AD",
      bank = minimalBankJSON
    )
  
  val otherAccountMetadataJSON =
    code.api.v1_2_1.OtherAccountMetadataJSON(
      public_alias = "NONE",
      private_alias = "NONE",
      more_info = "www.openbankproject.com",
      URL = "www.openbankproject.com",
      image_URL = "www.openbankproject.com",
      open_corporates_URL = "www.openbankproject.com",
      corporate_location = locationJSON,
      physical_location = locationJSON
    )
  
  val otherAccountJSON =
    code.api.v1_2_1.OtherAccountJSON(
      id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
      holder = accountHolderJSON,
      number = "123",
      kind = "3456",
      IBAN = "UK234DB",
      swift_bic = "UK12321DB",
      bank = minimalBankJSON,
      metadata = otherAccountMetadataJSON
    )
  
  val transactionDetailsJSON =
    code.api.v1_2_1.TransactionDetailsJSON(
      `type` = "AC",
      description = "GOOD",
      posted = exampleDate,
      completed = exampleDate,
      new_balance = amountOfMoneyJSON,
      value = amountOfMoneyJSON
    )
  
  val transactionImageJSON =
    code.api.v1_2_1.TransactionImageJSON(
      id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
      label = "NONE",
      URL = "www.openbankproject.com",
      date = exampleDate,
      user = userJSONV121
    )
  
  val transactionImagesJSON =
    code.api.v1_2_1.TransactionImagesJSON(
      images = List(transactionImageJSON)
    )
  
  val transactionCommentJSON =
    code.api.v1_2_1.TransactionCommentJSON(
      id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
      value = "OBP",
      date = exampleDate,
      user = userJSONV121
    )
  
  val transactionTagJSON =
    code.api.v1_2_1.TransactionTagJSON(
      id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
      value = "OBP",
      date = exampleDate,
      user = userJSONV121
    )
  
  val transactionTagsJSON =
    code.api.v1_2_1.TransactionTagsJSON(
      tags = List(transactionTagJSON)
    )
  
  val transactionMetadataJSON =
    code.api.v1_2_1.TransactionMetadataJSON(
      narrative = "NONE",
      comments = List(transactionCommentJSON),
      tags = List(transactionTagJSON),
      images = List(transactionImageJSON),
      where = locationJSON
    )
  
  val transactionJSON =
    code.api.v1_2_1.TransactionJSON(
      id = "5995d6a2-01b3-423c-a173-5481df49bdaf",
      this_account = thisAccountJSON,
      other_account = otherAccountJSON,
      details = transactionDetailsJSON,
      metadata = transactionMetadataJSON
    )
  
  val transactionsJSON =
    code.api.v1_2_1.TransactionsJSON(
      transactions = List(transactionJSON)
    )
  
  val coreTransactionDetailsJSON =
    code.api.v2_0_0.JSONFactory200.CoreTransactionDetailsJSON(
      `type` = "AC",
      description = "OBP",
      posted = exampleDate,
      completed = exampleDate,
      new_balance = amountOfMoneyJSON,
      value = amountOfMoneyJSON
    )
  
  val coreAccountHolderJSON =
    code.api.v2_0_0.JSONFactory200.CoreAccountHolderJSON(
      name = "ZACK"
    )
  
  
  val coreCounterpartyJSON =
    code.api.v2_0_0.JSONFactory200.CoreCounterpartyJSON(
      id = "123",
      holder = coreAccountHolderJSON,
      number = "1234",
      kind = "AV",
      IBAN = "UK12344DB",
      swift_bic = "UK12344DB",
      bank = minimalBankJSON
    )
  
  val coreTransactionJSON =
    code.api.v2_0_0.JSONFactory200.CoreTransactionJSON(
      id = "123",
      account = thisAccountJSON,
      counterparty = coreCounterpartyJSON,
      details = coreTransactionDetailsJSON
    )
  
  val coreTransactionsJSON =
    code.api.v2_0_0.JSONFactory200.CoreTransactionsJSON(
      transactions = List(coreTransactionJSON)
    )
  
  val transactionRequestChargeJSON =
    code.api.v2_0_0.TransactionRequestChargeJSON(
      summary = "Good",
      value = amountOfMoneyJSON
    )
  
  val transactionRequestTypeJSON =
    code.api.v1_4_0.JSONFactory1_4_0.TransactionRequestTypeJSON(
      value = "10",
      charge = transactionRequestChargeJSON
    )
  
  val transactionRequestTypeJSONs =
    code.api.v1_4_0.JSONFactory1_4_0.TransactionRequestTypeJSONs(
      transaction_request_types = List(transactionRequestTypeJSON)
    )
  
  val transactionRequestAccountJSON =
    code.api.v1_4_0.JSONFactory1_4_0.TransactionRequestAccountJSON(
      bank_id = "gh.29.uk",
      account_id = "8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0"
    )
  
  val challengeJSON =
    code.api.v1_4_0.JSONFactory1_4_0.ChallengeJSON(
      id = "be1a183d-b301-4b83-b855-5eeffdd3526f",
      allowed_attempts = 3,
      challenge_type = "SANDBOX_TAN"
    )
  
  val transactionRequestWithChargeJSON210 =
    code.api.v2_1_0.TransactionRequestWithChargeJSON210(
      id = "4050046c-63b3-4868-8a22-14b4181d33a6",
      `type` = "SANDBOX_TAN",
      from = transactionRequestAccountJSON,
      details = defaultJValue,
      transaction_ids = List("902ba3bb-dedd-45e7-9319-2fd3f2cd98a1"),
      status = "COMPLETED",
      start_date = exampleDate,
      end_date = exampleDate,
      challenge = challengeJSON,
      charge = transactionRequestChargeJSON
    )
  
  val transactionRequestWithChargeJSON =
    code.api.v2_0_0.TransactionRequestWithChargeJSON(
      id = "82f92531-9c63-4246-abfc-96c20ec46188",
      `type` = "SANDBOX_TAN",
      from = transactionRequestAccountJSON,
      details = defaultJValue,
      transaction_ids = "666666-9c63-4246-abfc-96c20ec46188",
      status = "COMPLETED",
      start_date = exampleDate,
      end_date = exampleDate,
      challenge = challengeJSON,
      charge = transactionRequestChargeJSON
    )
  
  val counterpartyJSON =
    code.api.v2_2_0.CounterpartyJSON(
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
      other_account_routing_address = "829b116f-027c-4508-a537-6b15ed6fbaaa",
      is_beneficiary = true
    )
  
  val counterpartiesJSON =
    code.api.v2_2_0.CounterpartiesJSON(
      counterparties = List(counterpartyJSON)
    )
  
  val successMessage =
    code.api.v1_2_1.SuccessMessage(
      success = "Success"
    )
  
  
  val viewJSONV220 =
    code.api.v2_2_0.ViewJSONV220(
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
  
    val viewsJSONV220 =
      code.api.v2_2_0.ViewsJSONV220(
        views = List(viewJSONV220)
      )
  
  val otherAccountsJSON =
    code.api.v1_2_1.OtherAccountsJSON(
      other_accounts = List(otherAccountJSON)
    )
  
  val transactionNarrativeJSON =
    code.api.v1_2_1.TransactionNarrativeJSON(
      narrative = "narative"
    )
  
  val transactionCommentsJSON =
    code.api.v1_2_1.TransactionCommentsJSON(
      comments = List(transactionCommentJSON)
    )
  
  val transactionWhereJSON =
    code.api.v1_2_1.TransactionWhereJSON(
      where = locationJSON
    )
  
  val fXRateJSON =
    code.api.v2_2_0.FXRateJSON(
      from_currency_code = "EUR",
      to_currency_code = "GBP",
      conversion_value = 1.001,
      inverse_conversion_value = 0.998,
      effective_date = exampleDate
    )
  
  val permissionJSON =
    code.api.v1_2_1.PermissionJSON(
      user = userJSONV121,
      views = List(viewJSON)
    )
  
  val permissionsJSON =
    code.api.v1_2_1.PermissionsJSON(
      permissions = List(permissionJSON)
    )
  
  val TransactionRequestWithChargeJSONs210 =
    code.api.v2_1_0.TransactionRequestWithChargeJSONs210(
      transaction_requests_with_charges = List(
        transactionRequestWithChargeJSON210
      )
    )
  
  val transactionRequestBodyJSON =
    code.api.v2_0_0.TransactionRequestBodyJSON(
      to = transactionRequestAccountJSON,
      value = amountOfMoneyJSON,
      description = "Good"
    )
  
  val challengeAnswerJSON =
    code.api.v1_4_0.JSONFactory1_4_0.ChallengeAnswerJSON(
      id = "b20dd004-93e3-494f-8773-69e3ff8c205e",
      answer = "good"
    )
  
  val updateAccountJSON =
    code.api.v1_2_1.UpdateAccountJSON(
      id = "123123",
      label = "label",
      bank_id = "gh.29.uk"
    )
  
  val createViewJSON =
    code.model.CreateViewJSON(
      name = "test",
      description = "good",
      is_public = true,
      which_alias_to_use = "good",
      hide_metadata_if_alias_used = true,
      allowed_actions = List("good")
    )
  
  val updateViewJSON =
    code.model.UpdateViewJSON(
      description = "good",
      is_public = true,
      which_alias_to_use = "good",
      hide_metadata_if_alias_used = true,
      allowed_actions = List("good")
    )
  
  val viewIdsJson =
    code.api.v1_2_1.ViewIdsJson(
      views = List("good")
    )
  
  val postCustomerJson =
    code.api.v2_1_0.PostCustomerJson(
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
      credit_limit = amountOfMoneyJSON,
      highest_education_attained = "Bachelor’s Degree",
      employment_status = "Employed",
      kyc_status = true,
      last_ok_date = exampleDate
    )
  
  val allFieldsAndValues =
    for (
      v <- this.getClass.getDeclaredFields
      //add guard, ignore the SwaggerJSONsV220.this and allFieldsAndValues fields
      if (APIUtil.notExstingBaseClass(v.getName()))
    )
      yield {
        v.setAccessible(true)
        v.get(this).getClass.getSimpleName -> v.get(this)
      }
}
