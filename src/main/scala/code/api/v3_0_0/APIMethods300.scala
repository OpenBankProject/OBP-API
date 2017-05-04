package code.api.v3_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil._
import code.api.util.ErrorMessages._
import code.api.v2_0_0.JSONFactory200
import code.model.{BankId, ViewId, _}
import net.liftweb.common.Box
import net.liftweb.http.rest.RestHelper
import net.liftweb.http.{JsonResponse, Req}
import net.liftweb.json.Extraction
import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
import code.api.v3_0_0.JSONFactory300._


trait APIMethods300 {
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  self: RestHelper =>

  // helper methods begin here
  val Implementations3_0_0 = new Object() {

    val apiVersion: String = "3_0_0"
    
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(resourceDocs, apiRelations)
  
    resourceDocs += ResourceDoc(
      getCoreTransactionsForBankAccount,
      apiVersion,
      "getCoreTransactionsForBankAccount",
      "GET",
      "/my/banks/BANK_ID/accounts/ACCOUNT_ID/transactions",
      "Get Transactions for Account (Core)",
      """Returns transactions list (Core info) of the account specified by ACCOUNT_ID.
        |
        |Authentication is required.
        |
        |Possible custom headers for pagination:
        |
        |* obp_sort_by=CRITERIA ==> default value: "completed" field
        |* obp_sort_direction=ASC/DESC ==> default value: DESC
        |* obp_limit=NUMBER ==> default value: 50
        |* obp_offset=NUMBER ==> default value: 0
        |* obp_from_date=DATE => default value: date of the oldest transaction registered (format below)
        |* obp_to_date=DATE => default value: date of the newest transaction registered (format below)
        |
        |**Date format parameter**: "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" (2014-07-01T00:00:00.000Z) ==> time zone is UTC.""",
      emptyObjectJson,
      moderatedCoreAccountJSON,
      List(
        FilterSortDirectionError,
        FilterOffersetError,
        FilterLimitError ,
        FilterDateFormatError,
        UserNotLoggedIn, 
        BankAccountNotFound,
        ViewNotFound, 
        UnKnownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccount, apiTagTransaction)
    )
  
    lazy val getCoreTransactionsForBankAccount : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "my" :: "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "transactions" :: Nil JsonGet json => {
        user =>
          for {
            //Note: error handling and messages for getTransactionParams are in the sub method
            params <- getTransactionParams(json)
            u <- user ?~ UserNotLoggedIn
            bankAccount <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            // Assume owner view was requested
            view <- View.fromUrl(ViewId("owner"), bankAccount) ?~! ViewNotFound
            transactions <- bankAccount.getModeratedTransactions(user, view, params : _*)
          } yield {
            val json = createCoreTransactionsJSON(transactions)
            successJsonResponse(Extraction.decompose(json))
          }
      }
    }
  
  
    resourceDocs += ResourceDoc(
      getTransactionsForBankAccount,
      apiVersion,
      "getTransactionsForBankAccount",
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/VIEW_ID/transactions",
      "Get Transactions for Account (Full)",
      """Returns transactions list of the account specified by ACCOUNT_ID and [moderated](#1_2_1-getViewsForBankAccount) by the view (VIEW_ID).
        |
        |Authentication via OAuth is required if the view is not public.
        |
        |Possible custom headers for pagination:
        |
        |* obp_sort_by=CRITERIA ==> default value: "completed" field
        |* obp_sort_direction=ASC/DESC ==> default value: DESC
        |* obp_limit=NUMBER ==> default value: 50
        |* obp_offset=NUMBER ==> default value: 0
        |* obp_from_date=DATE => default value: date of the oldest transaction registered (format below)
        |* obp_to_date=DATE => default value: date of the newest transaction registered (format below)
        |
        |**Date format parameter**: "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" (2014-07-01T00:00:00.000Z) ==> time zone is UTC.""",
      emptyObjectJson,
      transactionsJSON,
      List(
        FilterSortDirectionError,
        FilterOffersetError,
        FilterLimitError ,
        FilterDateFormatError,
        UserNotLoggedIn, 
        BankAccountNotFound, 
        ViewNotFound, 
        UnKnownError
      ),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagAccount, apiTagTransaction)
    )
  
    lazy val getTransactionsForBankAccount: PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: ViewId(viewId) :: "transactions" :: Nil JsonGet json => {
        user =>
          for {
            //Note: error handling and messages for getTransactionParams are in the sub method
            params <- getTransactionParams(json)
            u <- user ?~! UserNotLoggedIn
            bankAccount <- BankAccount(bankId, accountId) ?~! BankAccountNotFound
            view <- View.fromUrl(viewId, bankAccount) ?~! ViewNotFound
            transactions <- bankAccount.getModeratedTransactions(user, view, params: _*)
          } yield {
            val json = createTransactionsJson(transactions)
            successJsonResponse(Extraction.decompose(json))
          }
      }
    }
  }
}

object APIMethods300 {
}
