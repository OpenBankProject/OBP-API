/**
  * Open Bank Project - API
  * Copyright (C) 2011-2016, TESOBE Ltd
  **
  *This program is free software: you can redistribute it and/or modify
  *it under the terms of the GNU Affero General Public License as published by
  *the Free Software Foundation, either version 3 of the License, or
  *(at your option) any later version.
  **
  *This program is distributed in the hope that it will be useful,
  *but WITHOUT ANY WARRANTY; without even the implied warranty of
  *MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  *GNU Affero General Public License for more details.
  **
  *You should have received a copy of the GNU Affero General Public License
*along with this program.  If not, see <http://www.gnu.org/licenses/>.
  **
 *Email: contact@tesobe.com
*TESOBE Ltd
*Osloerstrasse 16/17
*Berlin 13359, Germany
  **
 *This product includes software developed at
  *TESOBE (http://www.tesobe.com/)
  * by
  *Simon Redfern : simon AT tesobe DOT com
  *Stefan Bethge : stefan AT tesobe DOT com
  *Everett Sochowski : everett AT tesobe DOT com
  *Ayoub Benali: ayoub AT tesobe DOT com
  *
 */

package code.api.util

import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.{Date, UUID}

import code.api.Constant._
import code.api.DirectLogin
import code.api.OAuthHandshake._
import code.api.v1_2.ErrorMessage
import code.bankconnectors._
import code.consumer.Consumers
import code.customer.Customer
import code.entitlement.Entitlement
import code.metrics.{APIMetrics, ConnMetrics}
import code.model._
import code.sanitycheck.SanityCheck
import code.util.Helper.{MdcLoggable, SILENCE_IS_GOLDEN}
import dispatch.url
import net.liftweb.common.{Empty, _}
import net.liftweb.http._
import net.liftweb.http.js.JE.JsRaw
import net.liftweb.http.js.JsExp
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.{Extraction, parse}
import net.liftweb.util.Helpers._
import net.liftweb.util.Props

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.xml.{Elem, XML}

object ErrorMessages {
import code.api.util.APIUtil._


  // Notes to developers. Please:
  // 1) Follow (the existing) grouping of messages
  // 2) Stick to existing terminology e.g. use "invalid" or "incorrect" rather than "wrong"
  // 3) Before adding a new message, check that you can't use one that already exists.
  // 4) Use Proper Names for OBP Resources.
  // 5) Don't use abbreviations.

  // Infrastructure / config level messages (OBP-00XXX)
  val HostnameNotSpecified = "OBP-00001: Hostname not specified. Could not get hostname from Props. Please edit your props file. Here are some example settings: hostname=http://127.0.0.1:8080 or hostname=https://www.example.com"
  val DataImportDisabled  = "OBP-00002: Data import is disabled for this API instance."
  val TransactionDisabled = "OBP-00003: Transaction Requests is disabled in this API instance."

  @deprecated("This is too generic","25-06-2017")
  val ServerAddDataError = "OBP-00004: Server error: could not add message" // Do not use this

  val PublicViewsNotAllowedOnThisInstance = "OBP-00005: Public views not allowed on this instance. Please set allow_public_views = true in props files. "


  val RemoteDataSecretMatchError = "OBP-00006: Remote data secret cannot be matched!" // (was OBP-20021)
  val RemoteDataSecretObtainError = "OBP-00007: Remote data secret cannot be obtained!" // (was OBP-20022)



  // General messages (OBP-10XXX)
  val InvalidJsonFormat = "OBP-10001: Incorrect json format."
  val InvalidNumber = "OBP-10002: Invalid Number. Could not convert value to a number."
  val InvalidISOCurrencyCode = "OBP-10003: Invalid Currency Value. It should be three letters ISO Currency Code. "
  val FXCurrencyCodeCombinationsNotSupported = "OBP-10004: ISO Currency code combination not supported for FX. Please modify the FROM_CURRENCY_CODE or TO_CURRENCY_CODE. "
  val InvalidDateFormat = "OBP-10005: Invalid Date Format. Could not convert value to a Date."
  val InvalidInputJsonFormat = "OBP-10006: Invalid input JSON format." // Why do we need this as well as InvalidJsonFormat?
  val IncorrectRoleName = "OBP-10007: Incorrect Role name: "

  // General Sort and Paging
  val FilterSortDirectionError = "OBP-10023: obp_sort_direction parameter can only take two values: DESC or ASC!" // was OBP-20023
  val FilterOffersetError = "OBP-10024: wrong value for obp_offset parameter. Please send a positive integer (=>0)!" // was OBP-20024
  val FilterLimitError = "OBP-10025: wrong value for obp_limit parameter. Please send a positive integer (=>1)!" // was OBP-20025
  val FilterDateFormatError = s"OBP-10026: Failed to parse date string. Please use this format ${defaultFilterFormat.toPattern} or that one ${fallBackFilterFormat.toPattern}!" // OBP-20026



  // Authentication / Authorisation / User messages (OBP-20XXX)
  val UserNotLoggedIn = "OBP-20001: User not logged in. Authentication is required!"
  val DirectLoginMissingParameters = "OBP-20002: These DirectLogin parameters are missing: "
  val DirectLoginInvalidToken = "OBP-20003: This DirectLogin token is invalid or expired: "
  val InvalidLoginCredentials = "OBP-20004: Invalid login credentials. Check username/password."
  val UserNotFoundById = "OBP-20005: User not found. Please specify a valid value for USER_ID."
  val UserHasMissingRoles = "OBP-20006: User is missing one or more roles: "
  val UserNotFoundByEmail = "OBP-20007: User not found by email."

  val InvalidConsumerKey = "OBP-20008: Invalid Consumer Key."
  val InvalidConsumerCredentials = "OBP-20009: Invalid consumer credentials"
 
  val InvalidValueLength = "OBP-20010: Value too long"
  val InvalidValueCharacters = "OBP-20011: Value contains invalid characters"

  val InvalidDirectLoginParameters = "OBP-20012: Invalid direct login parameters"

  val UsernameHasBeenLocked = "OBP-20013: The account has been locked, please contact administrator !"

  val InvalidConsumerId = "OBP-20014: Invalid Consumer ID. Please specify a valid value for CONSUMER_ID."
  
  val UserNoPermissionUpdateConsumer = "OBP-20015: Only the developer that created the consumer key should be able to edit it, please login with the right user."

  val UnexpectedErrorDuringLogin = "OBP-20016: An unexpected login error occurred. Please try again."

  val UserNoPermissionAccessView = "OBP-20017: Current user does not have access to the view. Please specify a valid value for VIEW_ID."


  val InvalidInternalRedirectUrl = "OBP-20018: Login failed, invalid internal redirectUrl."



  val UserNotFoundByUsername = "OBP-20027: User not found by username."




  // Resource related messages (OBP-30XXX)
  val BankNotFound = "OBP-30001: Bank not found. Please specify a valid value for BANK_ID."
  val CustomerNotFound = "OBP-30002: Customer not found. Please specify a valid value for CUSTOMER_NUMBER."
  val CustomerNotFoundByCustomerId = "OBP-30002: Customer not found. Please specify a valid value for CUSTOMER_ID."

  val AccountNotFound = "OBP-30003: Account not found. Please specify a valid value for ACCOUNT_ID."
  val CounterpartyNotFound = "OBP-30004: Counterparty not found. The BANK_ID / ACCOUNT_ID specified does not exist on this server."

  val ViewNotFound = "OBP-30005: View not found for Account. Please specify a valid value for VIEW_ID"

  val CustomerNumberAlreadyExists = "OBP-30006: Customer Number already exists. Please specify a different value for BANK_ID or CUSTOMER_NUMBER."
  val CustomerAlreadyExistsForUser = "OBP-30007: The User is already linked to a Customer at the bank specified by BANK_ID"
  val CustomerDoNotExistsForUser = "OBP-30008: User is not linked to a Customer at the bank specified by BANK_ID"
  val AtmNotFoundByAtmId = "OBP-30009: ATM not found. Please specify a valid value for ATM_ID."
  val BranchNotFoundByBranchId = "OBP-300010: Branch not found. Please specify a valid value for BRANCH_ID."
  val ProductNotFoundByProductCode = "OBP-30011: Product not found. Please specify a valid value for PRODUCT_CODE."
  val CounterpartyNotFoundByIban = "OBP-30012: Counterparty not found. Please specify a valid value for IBAN."
  val CounterpartyBeneficiaryPermit = "OBP-30013: The account can not send money to the Counterparty. Please set the Counterparty 'isBeneficiary' true first"
  val CounterpartyAlreadyExists = "OBP-30014: Counterparty already exists. Please specify a different value for BANK_ID or ACCOUNT_ID or VIEW_ID or NAME."
  val CreateBranchInsertError = "OBP-30015: Could not insert the Branch"
  val CreateBranchUpdateError = "OBP-30016: Could not update the Branch"
  val CounterpartyNotFoundByCounterpartyId = "OBP-30017: Counterparty not found. Please specify a valid value for COUNTERPARTY_ID."
  val BankAccountNotFound = "OBP-30018: Bank Account not found. Please specify valid values for BANK_ID and ACCOUNT_ID. "
  val ConsumerNotFoundByConsumerId = "OBP-30019: Consumer not found. Please specify a valid value for CONSUMER_ID."
  
  val CreateBankInsertError = "OBP-30020: Could not create the Bank"
  val CreateBankUpdateError = "OBP-30021: Could not update the Bank"
  val ViewNoPermission = "OBP-30022: The current view does not have the permission: "
  val UpdateConsumerError = "OBP-30023: Cannot update Consumer "
  val CreateConsumerError = "OBP-30024: Could not create customer "
  val CreateUserCustomerLinksError = "OBP-30025: Could not create user_customer_links "
  val ConsumerKeyAlreadyExists = "OBP-30026: Consumer Key already exists. Please specify a different value."
  val NoExistingAccountHolders = "OBP-30027: Account Holders not found. The BANK_ID / ACCOUNT_ID specified for account holder does not exist on this server. "
  

  // Meetings
  val MeetingsNotSupported = "OBP-30101: Meetings are not supported on this server."
  val MeetingApiKeyNotConfigured = "OBP-30102: Meeting provider API Key is not configured."
  val MeetingApiSecretNotConfigured = "OBP-30103: Meeting provider Secret is not configured."
  val MeetingNotFound = "OBP-30104: Meeting not found."


  val InvalidAccountBalanceCurrency = "OBP-30105: Invalid Balance Currency."
  val InvalidAccountBalanceAmount = "OBP-30106: Invalid Balance Amount."

  val InvalidUserId = "OBP-30107: Invalid User Id."
  val InvalidAccountType = "OBP-30108: Invalid Account Type."
  val InitialBalanceMustBeZero = "OBP-30109: Initial Balance of Account must be Zero (0)."
  val InvalidAccountIdFormat = "OBP-30110: Invalid Account Id. The ACCOUNT_ID should only contain 0-9/a-z/A-Z/'-'/'.'/'_', the length should be smaller than 255."
  val InvalidBankIdFormat = "OBP-30111: Invalid Bank Id. The BANK_ID should only contain 0-9/a-z/A-Z/'-'/'.'/'_', the length should be smaller than 255."
  val InvalidAccountInitialBalance = "OBP-30112: Invalid Number. Initial balance must be a number, e.g 1000.00"


  val EntitlementIsBankRole = "OBP-30205: This entitlement is a Bank Role. Please set bank_id to a valid bank id."
  val EntitlementIsSystemRole = "OBP-30206: This entitlement is a System Role. Please set bank_id to empty string."


  val InvalidStrongPasswordFormat = "OBP-30207: Invalid Password Format. Your password should EITHER be at least 10 characters long and contain mixed numbers and both upper and lower case letters and at least one special character, OR be longer than 16 characters."

  val AccountIdAlreadyExsits = "OBP-30208: Account_ID already exists at the Bank."


  val InsufficientAuthorisationToCreateBranch  = "OBP-30009: Insufficient authorisation to Create Branch. You do not have the role CanCreateBranch." // was OBP-20019
  val InsufficientAuthorisationToCreateBank  = "OBP-30010: Insufficient authorisation to Create Bank. You do not have the role CanCreateBank." // was OBP-20020

  // General Resource related messages above here


  // Transaction Request related messages (OBP-40XXX)
  val InvalidTransactionRequestType = "OBP-40001: Invalid value for TRANSACTION_REQUEST_TYPE"
  val InsufficientAuthorisationToCreateTransactionRequest  = "OBP-40002: Insufficient authorisation to create TransactionRequest. The Transaction Request could not be created because you don't have access to the owner view of the from account or you don't have access to canCreateAnyTransactionRequest."
  val InvalidTransactionRequestCurrency = "OBP-40003: Transaction Request Currency must be the same as From Account Currency."
  val InvalidTransactionRequestId = "OBP-40004: Transaction Request Id not found."
  val InsufficientAuthorisationToCreateTransactionType  = "OBP-40005: Insufficient authorisation to Create Transaction Type offered by the bank. The Request could not be created because you don't have access to CanCreateTransactionType."
  val CreateTransactionTypeInsertError  = "OBP-40006: Could not insert Transaction Type: Non unique BANK_ID / SHORT_CODE"
  val CreateTransactionTypeUpdateError  = "OBP-40007: Could not update Transaction Type: Non unique BANK_ID / SHORT_CODE"
  val NotPositiveAmount = "OBP-40008: Can't send a payment with a value of 0 or less."
  val TransactionRequestTypeHasChanged = "OBP-40009: The TRANSACTION_REQUEST_TYPE has changed."
  val InvalidTransactionRequesChallengeId = "OBP-40010: Invalid Challenge Id. Please specify a valid value for CHALLENGE_ID."
  val TransactionRequestStatusNotInitiated = "OBP-40011: Transaction Request Status is not INITIATED."
  val CounterpartyNotFoundOtherAccountProvider = "OBP-40012: Please set up the otherAccountRoutingScheme and otherBankRoutingScheme fields of the Counterparty to 'OBP'"
  val InvalidChargePolicy = "OBP-40013: Invalid Charge Policy. Please specify a valid value for Charge_Policy: SHARED, SENDER or RECEIVER. "
  val AllowedAttemptsUsedUp = "OBP-40014: Sorry, you've used up your allowed attempts. "
  val InvalidChallengeType = "OBP-40015: Invalid Challenge Type. Please specify a valid value for CHALLENGE_TYPE, when you create the transaction request."



  // Exceptions (OBP-50XXX)
  val UnknownError = "OBP-50000: Unknown Error."
  val FutureTimeoutException = "OBP-50001: Future Timeout Exception."
  val KafkaMessageClassCastException = "OBP-50002: Kafka Response Message Class Cast Exception."
  val AdapterOrCoreBankingSystemException = "OBP-50003: Adapter Or Core Banking System Exception. Failed to get a valid response from the south side Adapter or Core Banking System."


  // Connector Data Exceptions (OBP-502XX)
  val ConnectorEmptyResponse = "OBP-50200: Connector cannot return the data we requested." // was OBP-30200
  val InvalidConnectorResponseForGetBankAccounts = "OBP-50201: Connector did not return the set of accounts we requested."  // was OBP-30201
  val InvalidConnectorResponseForGetBankAccount = "OBP-50202: Connector did not return the account we requested."  // was OBP-30202
  val InvalidConnectorResponseForGetTransaction = "OBP-50203: Connector did not return the transaction we requested."  // was OBP-30203
  val InvalidConnectorResponseForGetTransactions = "OBP-50204: Connector did not return the set of transactions we requested."  // was OBP-30204





  //For Swagger, used reflect to  list all the varible names and values.
  // eg : val InvalidUserId = "OBP-30107: Invalid User Id."
  //   -->(InvalidUserId, "OBP-30107: Invalid User Id.")
  val allFields =
    for (
      v <- this.getClass.getDeclaredFields
      //add guard, ignore the SwaggerJSONsV220.this and allFieldsAndValues fields
      if (APIUtil.notExstingBaseClass(v.getName()))
    ) yield {
      v.setAccessible(true)
      v.getName() -> v.get(this)
    }
  
  //For Swagger, get varible name by value: 
  // eg: val InvalidUserId = "OBP-30107: Invalid User Id."
  //  getFildNameByValue("OBP-30107: Invalid User Id.") return InvalidUserId
  def getFildNameByValue(value: String) = {
    val strings = for (e <- allFields if (e._2 == (value))) yield e._1
    strings.head
  }

}




object APIUtil extends MdcLoggable {

  implicit val formats = net.liftweb.json.DefaultFormats
  implicit def errorToJson(error: ErrorMessage): JValue = Extraction.decompose(error)
  val headers = ("Access-Control-Allow-Origin","*") :: Nil
  val defaultJValue = Extraction.decompose(Nil)(APIUtil.formats)
  val exampleDateString: String = "22/08/2013"
  val simpleDateFormat: SimpleDateFormat = new SimpleDateFormat("dd/mm/yyyy")
  val exampleDate = simpleDateFormat.parse(exampleDateString)
  val emptyObjectJson = EmptyClassJson()
  val defaultFilterFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
  val fallBackFilterFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
  import code.api.util.ErrorMessages._
  
  def httpMethod : String =
    S.request match {
      case Full(r) => r.request.method
      case _ => "GET"
    }

  def isThereDirectLoginHeader : Boolean = {
    S.request match {
      case Full(a) =>  a.header("Authorization") match {
        case Full(parameters) => parameters.contains("DirectLogin")
        case _ => false
      }
      case _ => false
    }
  }

  def isThereGatewayHeader : Boolean = {
    S.request match {
      case Full(a) =>  a.header("Authorization") match {
        case Full(parameters) => parameters.contains("Gateway")
        case _ => false
      }
      case _ => false
    }
  }

  def isThereAnOAuthHeader : Boolean = {
    S.request match {
      case Full(a) =>  a.header("Authorization") match {
        case Full(parameters) => parameters.contains("OAuth")
        case _ => false
      }
      case _ => false
    }
  }

  def registeredApplication(consumerKey: String): Boolean = {
    Consumers.consumers.vend.getConsumerByConsumerKey(consumerKey) match {
      case Full(application) => application.isActive
      case _ => false
    }
  }

  def logAPICall(date: TimeSpan, duration: Long, rd: Option[ResourceDoc]) = {
    if(Props.getBool("write_metrics", false)) {
      val user =
        if (isThereAnOAuthHeader) {
          getUser match {
            case Full(u) => Full(u)
            case _ => Empty
          }
        } else if (Props.getBool("allow_direct_login", true) && isThereDirectLoginHeader) {
          DirectLogin.getUser match {
            case Full(u) => Full(u)
            case _ => Empty
          }
        } else {
            Empty
        }

      val consumer =
        if (isThereAnOAuthHeader) {
          getConsumer match {
            case Full(c) => Full(c)
            case _ => Empty
          }
        } else if (Props.getBool("allow_direct_login", true) && isThereDirectLoginHeader) {
          DirectLogin.getConsumer match {
            case Full(c) => Full(c)
            case _ => Empty
          }
        } else {
          Empty
        }

      // TODO This should use Elastic Search or Kafka not an RDBMS
      val u: User = user.orNull
      val userId = if (u != null) u.userId else "null"
      val userName = if (u != null) u.name else "null"

      val c: Consumer = consumer.orNull
      //The consumerId, not key
      val consumerId = if (u != null) c.id.toString() else "null"
      var appName = if (u != null) c.name.toString() else "null"
      var developerEmail = if (u != null) c.developerEmail.toString() else "null"
      val implementedByPartialFunction = rd match {
        case Some(r) => r.apiFunction
        case _       => ""
      }
      //name of version where the call is implemented) -- S.request.get.view
      val implementedInVersion = S.request.get.view
      //(GET, POST etc.) --S.request.get.requestType.method
      val verb = S.request.get.requestType.method
      val url = S.uriAndQueryString.getOrElse("")

      //execute saveMetric in future, as we do not need to know result of operation
      import scala.concurrent.ExecutionContext.Implicits.global
      Future {
        APIMetrics.apiMetrics.vend.saveMetric(
          userId,
          url,
          date,
          duration: Long,
          userName,
          appName,
          developerEmail,
          consumerId,
          implementedByPartialFunction,
          implementedInVersion, verb
        )
      }

    }
  }


  /*
  Return the git commit. If we can't for some reason (not a git root etc) then log and return ""
   */
  def gitCommit : String = {
    val commit = try {
      val properties = new java.util.Properties()
      logger.debug("Before getResourceAsStream git.properties")
      properties.load(getClass().getClassLoader().getResourceAsStream("git.properties"))
      logger.debug("Before get Property git.commit.id")
      properties.getProperty("git.commit.id", "")
    } catch {
      case e : Throwable => {
               logger.warn("gitCommit says: Could not return git commit. Does resources/git.properties exist?")
               logger.error(s"Exception in gitCommit: $e")
        "" // Return empty string
      }
    }
    commit
  }
  
//  https://httpstatuses.com/ the introduction for all http-codes
  
  /**
    * 204 NO CONTENT
    * The server has successfully fulfilled the request and that there is no additional content to send in the response payload body.
    */
  def noContentJsonResponse : JsonResponse =
    JsonResponse(JsRaw(""), headers, Nil, 204)
  
  /**
    * 200 OK
    * The request has succeeded.
    */
  def successJsonResponse(json: JsExp, httpCode : Int = 200) : JsonResponse =
    JsonResponse(json, headers, Nil, httpCode)
  
  /**
    * 201 CREATED
    * The request has been fulfilled and has resulted in one or more new resources being created.
    */
  def createdJsonResponse(json: JsExp, httpCode : Int = 201) : JsonResponse =
    JsonResponse(json, headers, Nil, httpCode)

  def successJsonResponseFromCaseClass(cc: Any, httpCode : Int = 200) : JsonResponse =
    JsonResponse(Extraction.decompose(cc), headers, Nil, httpCode)
  
  /**
    * 202 ACCEPTED
    * The request has been accepted for processing, but the processing has not been completed.
    * The request might or might not eventually be acted upon, as it might be disallowed when processing actually takes place.
    */
  def acceptedJsonResponse(json: JsExp, httpCode : Int = 202) : JsonResponse =
    JsonResponse(json, headers, Nil, httpCode)
  
  /**
    * 400 BAD REQUEST
    * The server cannot or will not process the request due to something that is perceived to be a client error
    * (e.g., malformed request syntax, invalid request message framing, or deceptive request routing).
    */
  def errorJsonResponse(message : String = "error", httpCode : Int = 400) : JsonResponse =
    JsonResponse(Extraction.decompose(ErrorMessage(message)), headers, Nil, httpCode)
  
  /**
    * 501 NOT IMPLEMENTED
    * The server does not support the functionality required to fulfill the request.
    */
  def notImplementedJsonResponse(message : String = "Not Implemented", httpCode : Int = 501) : JsonResponse =
    JsonResponse(Extraction.decompose(ErrorMessage(message)), headers, Nil, httpCode)


  def oauthHeaderRequiredJsonResponse : JsonResponse =
    JsonResponse(Extraction.decompose(ErrorMessage("Authentication via OAuth is required")), headers, Nil, 400)

  /** check the currency ISO code from the ISOCurrencyCodes.xml file */
  def isValidCurrencyISOCode(currencyCode: String): Boolean = {
    //just for initialization the Elem variable
    var xml: Elem = <html/>
    LiftRules.getResource("/media/xml/ISOCurrencyCodes.xml").map{ url =>
      val input: InputStream = url.openStream()
      xml = XML.load(input)
    }
    val stringArray = (xml \ "Currency" \ "CurrencyCode").map(_.text).mkString(" ").split("\\s+")
    stringArray.contains(currencyCode)
  }

  /** Check the id values from GUI, such as ACCOUNT_ID, BANK_ID ...  */
  def isValidID(id :String):Boolean= {
    val regex = """^([A-Za-z0-9\-_.]+)$""".r
    id match {
      case regex(e) if(e.length<256) => true
      case _ => false
    }
  }

  /** enforce the password. 
    * The rules : 
    * 1) length is >16 characters without validations
    * 2) or Min 10 characters with mixed numbers + letters + upper+lower case + at least one special character. 
    * */
  def isValidStrongPassword(password: String): Boolean = {
    /**
      * (?=.*\d)                    //should contain at least one digit
      * (?=.*[a-z])                 //should contain at least one lower case
      * (?=.*[A-Z])                 //should contain at least one upper case
      * (?=.*[!"#$%&'\(\)*+,-./:;<=>?@\\[\\\\]^_\\`{|}~])              //should contain at least one special character
      * ([A-Za-z0-9!"#$%&'\(\)*+,-./:;<=>?@\\[\\\\]^_\\`{|}~]{10,16})  //should contain 10 to 16 valid characters
      **/
    val regex =
      """^(?=.*\d)(?=.*[a-z])(?=.*[A-Z])(?=.*[!"#$%&'\(\)*+,-./:;<=>?@\\[\\\\]^_\\`{|}~])([A-Za-z0-9!"#$%&'\(\)*+,-./:;<=>?@\\[\\\\]^_\\`{|}~]{10,16})$""".r
    password match {
      case password if (password.length > 16) => true
      case regex(password) => true
      case _ => false
    }
  }
  


  /** These three functions check rather than assert. I.e. they are silent if OK and return an error message if not.
    * They do not throw an exception on failure thus they are not assertions
    */

  /** only  A-Z, a-z and max length <= 512  */
  def checkMediumAlpha(value:String): String ={
    val valueLength = value.length
    val regex = """^([A-Za-z]+)$""".r
    value match {
      case regex(e) if(valueLength <= 512) => SILENCE_IS_GOLDEN
      case regex(e) if(valueLength > 512) => ErrorMessages.InvalidValueLength
      case _ => ErrorMessages.InvalidValueCharacters
    }
  }

  /** only  A-Z, a-z, 0-9 and max length <= 512  */
  def checkMediumAlphaNumeric(value:String): String ={
    val valueLength = value.length
    val regex = """^([A-Za-z0-9]+)$""".r
    value match {
      case regex(e) if(valueLength <= 512) => SILENCE_IS_GOLDEN
      case regex(e) if(valueLength > 512) => ErrorMessages.InvalidValueLength
      case _ => ErrorMessages.InvalidValueCharacters
    }
  }

  /** only  A-Z, a-z, 0-9, all allowed characters for password and max length <= 512  */
  def checkMediumPassword(value:String): String ={
    val valueLength = value.length
    val regex = """^([A-Za-z0-9!"#$%&'\(\)*+,-./:;<=>?@\\[\\\\]^_\\`{|}~]+)$""".r
    value match {
      case regex(e) if(valueLength <= 512) => SILENCE_IS_GOLDEN
      case regex(e) if(valueLength > 512) => ErrorMessages.InvalidValueLength
      case _ => ErrorMessages.InvalidValueCharacters
    }
  }

  /** only  A-Z, a-z, 0-9, -, _, ., @, and max length <= 512  */
  def checkMediumString(value:String): String ={
    val valueLength = value.length
    val regex = """^([A-Za-z0-9\-._@]+)$""".r
    value match {
      case regex(e) if(valueLength <= 512) => SILENCE_IS_GOLDEN
      case regex(e) if(valueLength > 512) => ErrorMessages.InvalidValueLength
      case _ => ErrorMessages.InvalidValueCharacters
    }
  }
  
  def stringOrNull(text : String) =
    if(text == null || text.isEmpty)
      null
    else
      text
  
  def stringOptionOrNull(text : Option[String]) =
    text match {
      case Some(t) => stringOrNull(t)
      case _ => null
    }

  //started -- Filtering and Paging revelent methods////////////////////////////
  object DateParser {
    /**
      * first tries to parse dates using this pattern "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" (2012-07-01T00:00:00.000Z) ==> time zone is UTC
      * in case of failure (for backward compatibility reason), try "yyyy-MM-dd'T'HH:mm:ss.SSSZ" (2012-07-01T00:00:00.000+0000) ==> time zone has to be specified
      */
    def parse(date: String): Box[Date] = {
      val parsedDate = tryo{
        defaultFilterFormat.parse(date)
      }
      
      lazy val fallBackParsedDate = tryo{
        fallBackFilterFormat.parse(date)
      }
      
      if(parsedDate.isDefined){
        Full(parsedDate.get)
      }
      else if(fallBackParsedDate.isDefined){
        Full(fallBackParsedDate.get)
      }
      else{
        Failure(FilterDateFormatError)
      }
    }
  }
  
   def getSortDirection(req: Req): Box[OBPOrder] = {
    req.header("obp_sort_direction") match {
      case Full(v) => {
        if(v.toLowerCase == "desc" || v.toLowerCase == "asc"){
          Full(OBPOrder(Some(v.toLowerCase)))
        }
        else{
          Failure(FilterSortDirectionError)
        }
      }
      case _ => Full(OBPOrder(None))
    }
  }
  
   def getFromDate(req: Req): Box[OBPFromDate] = {
    val date: Box[Date] = req.header("obp_from_date") match {
      case Full(d) => {
        DateParser.parse(d)
      }
      case _ => {
        Full(new Date(0))
      }
    }
    
    date.map(OBPFromDate(_))
  }
  
   def getToDate(req: Req): Box[OBPToDate] = {
    val date: Box[Date] = req.header("obp_to_date") match {
      case Full(d) => {
        DateParser.parse(d)
      }
      case _ => Full(new Date())
    }
    
    date.map(OBPToDate(_))
  }
  
   def getOffset(req: Req): Box[OBPOffset] = {
    getPaginationParam(req, "obp_offset", 0, 0, FilterOffersetError).map(OBPOffset(_))
  }
  
   def getLimit(req: Req): Box[OBPLimit] = {
    getPaginationParam(req, "obp_limit", 50, 1, FilterLimitError).map(OBPLimit(_))
  }
  
   def getPaginationParam(req: Req, paramName: String, defaultValue: Int, minimumValue: Int, errorMsg: String): Box[Int]= {
    req.header(paramName) match {
      case Full(v) => {
        tryo{
          v.toInt
        } match {
          case Full(value) => {
            if(value >= minimumValue){
              Full(value)
            }
            else{
              Failure(errorMsg)
            }
          }
          case _ => Failure(errorMsg)
        }
      }
      case _ => Full(defaultValue)
    }
  }
  
  def getTransactionParams(req: Req): Box[List[OBPQueryParam]] = {
    for{
      sortDirection <- getSortDirection(req)
      fromDate <- getFromDate(req)
      toDate <- getToDate(req)
      limit <- getLimit(req)
      offset <- getOffset(req)
    }yield{
      /**
        * sortBy is currently disabled as it would open up a security hole:
        *
        * sortBy as currently implemented will take in a parameter that searches on the mongo field names. The issue here
        * is that it will sort on the true value, and not the moderated output. So if a view is supposed to return an alias name
        * rather than the true value, but someone uses sortBy on the other bank account name/holder, not only will the returned data
        * have the wrong order, but information about the true account holder name will be exposed due to its position in the sorted order
        *
        * This applies to all fields that can have their data concealed... which in theory will eventually be most/all
        *
        */
      //val sortBy = json.header("obp_sort_by")
      val sortBy = None
      val ordering = OBPOrdering(sortBy, sortDirection)
      limit :: offset :: ordering :: fromDate :: toDate :: Nil
    }
  }
  //ended -- Filtering and Paging revelent methods  ////////////////////////////

  
  /** Import this object's methods to add signing operators to dispatch.Request */
  object OAuth {
    import javax.crypto

    import dispatch.{Req => Request}
    import org.apache.http.protocol.HTTP.UTF_8

    import scala.collection.Map
    import scala.collection.immutable.{TreeMap, Map => IMap}

    case class ReqData (
                      url: String,
                      method: String,
                      body: String,
                      body_encoding: String,
                      headers: Map[String, String],
                      query_params: Map[String,String],
                      form_params: Map[String,String]
                     )

    case class Consumer(key: String, secret: String)
    case class Token(value: String, secret: String)
    object Token {
      def apply[T <: Any](m: Map[String, T]): Option[Token] = List("oauth_token", "oauth_token_secret").flatMap(m.get) match {
        case value :: secret :: Nil => Some(Token(value.toString, secret.toString))
        case _ => None
      }
    }

    /** @return oauth parameter map including signature */
    def sign(method: String, url: String, user_params: Map[String, Any], consumer: Consumer, token: Option[Token], verifier: Option[String], callback: Option[String]) = {
      val oauth_params = IMap(
        "oauth_consumer_key" -> consumer.key,
        "oauth_signature_method" -> "HMAC-SHA1",
        "oauth_timestamp" -> (System.currentTimeMillis / 1000).toString,
        "oauth_nonce" -> System.nanoTime.toString,
        "oauth_version" -> "1.0"
      ) ++ token.map { "oauth_token" -> _.value } ++
        verifier.map { "oauth_verifier" -> _ } ++
        callback.map { "oauth_callback" -> _ }

      val encoded_ordered_params = (
        new TreeMap[String, String] ++ (user_params ++ oauth_params map %%)
      ) map { case (k, v) => k + "=" + v } mkString "&"

      val message =
        %%(method.toUpperCase :: url :: encoded_ordered_params :: Nil)

      val SHA1 = "HmacSHA1"
      val key_str = %%(consumer.secret :: (token map { _.secret } getOrElse "") :: Nil)
      val key = new crypto.spec.SecretKeySpec(bytes(key_str), SHA1)
      val sig = {
        val mac = crypto.Mac.getInstance(SHA1)
        mac.init(key)
        base64Encode(mac.doFinal(bytes(message)))
      }
      oauth_params + ("oauth_signature" -> sig)
    }

    /** Out-of-band callback code */
    val oob = "oob"

    /** Map with oauth_callback set to the given url */
    def callback(url: String) = IMap("oauth_callback" -> url)

    //normalize to OAuth percent encoding
    private def %% (str: String): String = {
      val remaps = ("+", "%20") :: ("%7E", "~") :: ("*", "%2A") :: Nil
      (encode_%(str) /: remaps) { case (str, (a, b)) => str.replace(a,b) }
    }
    private def %% (s: Seq[String]): String = s map %% mkString "&"
    private def %% (t: (String, Any)): (String, String) = (%%(t._1), %%(t._2.toString))

    private def bytes(str: String) = str.getBytes(UTF_8)

    /** Add OAuth operators to dispatch.Request */
    implicit def Request2RequestSigner(r: Request) = new RequestSigner(r)

    /** @return %-encoded string for use in URLs */
    def encode_% (s: String) = java.net.URLEncoder.encode(s, org.apache.http.protocol.HTTP.UTF_8)

    /** @return %-decoded string e.g. from query string or form body */
    def decode_% (s: String) = java.net.URLDecoder.decode(s, org.apache.http.protocol.HTTP.UTF_8)

    class RequestSigner(rb: Request) {
      private val r = rb.toRequest
      @deprecated("use <@ (consumer, callback) to pass the callback in the header for a request-token request")
      def <@ (consumer: Consumer): Request = sign(consumer, None, None, None)
      /** sign a request with a callback, e.g. a request-token request */
      def <@ (consumer: Consumer, callback: String): Request = sign(consumer, None, None, Some(callback))
      /** sign a request with a consumer, token, and verifier, e.g. access-token request */
      def <@ (consumer: Consumer, token: Token, verifier: String): Request =
        sign(consumer, Some(token), Some(verifier), None)
      /** sign a request with a consumer and a token, e.g. an OAuth-signed API request */
      def <@ (consumer: Consumer, token: Token): Request = sign(consumer, Some(token), None, None)
      def <@ (consumerAndToken: Option[(Consumer,Token)]): Request = {
        consumerAndToken match {
          case Some(cAndt) => sign(cAndt._1, Some(cAndt._2), None, None)
          case _ => rb
        }
      }

      /** Sign request by reading Post (<<) and query string parameters */
      private def sign(consumer: Consumer, token: Option[Token], verifier: Option[String], callback: Option[String]) = {

        val oauth_url = r.getUrl.split('?')(0)
        val query_params = r.getQueryParams.asScala.groupBy(_.getName).mapValues(_.map(_.getValue)).map {
            case (k, v) => k -> v.toString
          }
        val form_params = r.getFormParams.asScala.groupBy(_.getName).mapValues(_.map(_.getValue)).map {
            case (k, v) => k -> v.toString
          }
        val body_encoding = r.getBodyEncoding
        var body = new String()
        if (r.getByteData != null )
          body = new String(r.getByteData)
        val oauth_params = OAuth.sign(r.getMethod, oauth_url,
                                      query_params ++ form_params,
                                      consumer, token, verifier, callback)

        def createRequest( reqData: ReqData ): Request = {
          val rb = url(reqData.url)
            .setMethod(reqData.method)
            .setBodyEncoding(reqData.body_encoding)
            .setBody(reqData.body) <:< reqData.headers
          if (reqData.query_params.nonEmpty)
            rb <<? reqData.query_params
          rb
        }

        createRequest( ReqData(
          oauth_url,
          r.getMethod,
          body,
          body_encoding,
          IMap("Authorization" -> ("OAuth " + oauth_params.map {
            case (k, v) => encode_%(k) + "=\"%s\"".format(encode_%(v.toString))
          }.mkString(",") )),
          query_params,
          form_params
        ))
      }
    }
  }

  /*
  Used to document API calls / resources.

  TODO Can we extract apiVersion, apiFunction, requestVerb and requestUrl from partialFunction?

   */

  // Used to tag Resource Docs
  case class ResourceDocTag(tag: String)

  // Use the *singular* case. for both the variable name and string.
  // e.g. "This call is Payment related"
  val apiTagTransactionRequest = ResourceDocTag("TransactionRequest")
  val apiTagApiInfo = ResourceDocTag("APIInfo")
  val apiTagBank = ResourceDocTag("Bank")
  val apiTagAccount = ResourceDocTag("Account")
  val apiTagPublicData = ResourceDocTag("PublicData")
  val apiTagPrivateData = ResourceDocTag("PrivateData")
  val apiTagTransaction = ResourceDocTag("Transaction")
  val apiTagMetaData = ResourceDocTag("MetaData")
  val apiTagView = ResourceDocTag("View")
  val apiTagEntitlement = ResourceDocTag("Entitlement")
  val apiTagOwnerRequired = ResourceDocTag("OwnerViewRequired")
  val apiTagCounterparty = ResourceDocTag("Counterparty")
  val apiTagKyc = ResourceDocTag("KYC")
  val apiTagCustomer = ResourceDocTag("Customer")
  val apiTagOnboarding = ResourceDocTag("Onboarding")
  val apiTagUser = ResourceDocTag("User")
  val apiTagMeeting = ResourceDocTag("Meeting")
  val apiTagExperimental = ResourceDocTag("Experimental")
  val apiTagPerson = ResourceDocTag("Person")

  case class Catalogs(core: Boolean = false, psd2: Boolean = false, obwg: Boolean = false)

  val Core = true
  val PSD2 = true
  val OBWG = true
  val notCore = false
  val notPSD2 = false
  val notOBWG = false
  
  case class BaseErrorResponseBody(
    //code: String,//maybe used, for now, 400,204,200...are handled in RestHelper class
    //TODO, this should be a case class name, but for now, the InvalidNumber are just String, not the case class.
    name: String,
    detail: String
  ) 
  
  //check #511, https://github.com/OpenBankProject/OBP-API/issues/511
  // get rid of JValue, but in API-EXPLORER or other places, it need the Empty JValue "{}" 
  // So create the EmptyClassJson to set the empty JValue "{}"
  case class EmptyClassJson()
  
  // Used to document the API calls
  case class ResourceDoc(
    partialFunction : PartialFunction[Req, Box[User] => Box[JsonResponse]],
    apiVersion: String, // TODO: Constrain to certain strings?
    apiFunction: String, // The partial function that implements this resource. Could use it to link to the source code that implements the call
    requestVerb: String, // GET, POST etc. TODO: Constrain to GET, POST etc.
    requestUrl: String, // The URL (not including /obp/vX.X). Starts with / No trailing slash. TODO Constrain the string?
    summary: String, // A summary of the call (originally taken from code comment) SHOULD be under 120 chars to be inline with Swagger
    description: String, // Longer description (originally taken from github wiki)
    exampleRequestBody: scala.Product, // An example of the body required (maybe empty)
    successResponseBody: scala.Product, // A successful response body
    errorResponseBodies: List[String], // Possible error responses
    catalogs: Catalogs,
    tags: List[ResourceDocTag]
  )
  
  
  /**
    * 
    * This is the base class for all kafka outbound case class
    * action and messageFormat are mandatory 
    * The optionalFields can be any other new fields .
    */
  abstract class OutboundMessageBase(
    optionalFields: String*
  ) {
    def action: String
    def messageFormat: String
  }
  
  abstract class InboundMessageBase(
    optionalFields: String*
  ) {
    def errorCode: String
  }

  // Used to document the KafkaMessage calls
  case class MessageDoc(
    process: String,
    messageFormat: String,
    description: String,
    exampleOutboundMessage: JValue,
    exampleInboundMessage: JValue  
  )
  
  // Define relations between API end points. Used to create _links in the JSON and maybe later for API Explorer browsing
  case class ApiRelation(
    fromPF : PartialFunction[Req, Box[User] => Box[JsonResponse]],
    toPF : PartialFunction[Req, Box[User] => Box[JsonResponse]],
    rel : String
  )

  // Populated from Resource Doc and ApiRelation
  case class InternalApiLink(
    fromPF : PartialFunction[Req, Box[User] => Box[JsonResponse]],
    toPF : PartialFunction[Req, Box[User] => Box[JsonResponse]],
    rel : String,
    requestUrl: String
    )

  // Used to pass context of current API call to the function that generates links for related Api calls.
  case class DataContext(
    user : Box[User],
    bankId :  Option[BankId],
    accountId: Option[AccountId],
    viewId: Option[ViewId],
    counterpartyId: Option[CounterpartyId],
    transactionId: Option[TransactionId]
)

  case class CallerContext(
    caller : PartialFunction[Req, Box[User] => Box[JsonResponse]]
  )

  case class CodeContext(
    resourceDocsArrayBuffer : ArrayBuffer[ResourceDoc],
    relationsArrayBuffer : ArrayBuffer[ApiRelation]
  )



  case class ApiLink(
    rel: String,
    href: String
  )

  case class LinksJSON(
   _links: List[ApiLink]
 )

  case class ResultAndLinksJSON(
    result : JValue,
    _links: List[ApiLink]
  )


  def createResultAndLinksJSON(result : JValue, links : List[ApiLink] ) : ResultAndLinksJSON = {
    new ResultAndLinksJSON(
      result,
      links
    )
  }





/*
Returns a string showed to the developer
 */
  def authenticationRequiredMessage(authRequired: Boolean) : String =
  authRequired match {
      case true => "Authentication is Mandatory"
      case false => "Authentication is Optional"
    }



  def apiVersionWithV(apiVersion : String) : String = {
    // TODO Define a list of supported versions (put in Constant) and constrain the input
    // Append v and replace _ with .
    s"v${apiVersion.replaceAll("_",".")}"
  }

  def fullBaseUrl : String = {
    val crv = CurrentReq.value
    val apiPathZeroFromRequest = crv.path.partPath(0)
    if (apiPathZeroFromRequest != ApiPathZero) throw new Exception("Configured ApiPathZero is not the same as the actual.")

    val path = s"$HostName/$ApiPathZero"
    path
  }


// Modify URL replacing placeholders for Ids
  def contextModifiedUrl(url: String, context: DataContext) = {

  // Potentially replace BANK_ID
    val url2: String = context.bankId match {
      case Some(x) => url.replaceAll("BANK_ID", x.value)
      case _ => url
    }

    val url3: String = context.accountId match {
      // Take care *not* to change OTHER_ACCOUNT_ID HERE
      case Some(x) => url2.replaceAll("/ACCOUNT_ID", s"/${x.value}").replaceAll("COUNTERPARTY_ID", x.value)
      case _ => url2
    }

    val url4: String = context.viewId match {
      case Some(x) => url3.replaceAll("VIEW_ID", {x.value})
      case _ => url3
    }

    val url5: String = context.counterpartyId match {
      // Change OTHER_ACCOUNT_ID or COUNTERPARTY_ID
      case Some(x) => url4.replaceAll("OTHER_ACCOUNT_ID", x.value).replaceAll("COUNTERPARTY_ID", x.value)
      case _ => url4
    }

    val url6: String = context.transactionId match {
      case Some(x) => url5.replaceAll("TRANSACTION_ID", x.value)
      case _ => url5
    }

  // Add host, port, prefix, version.

  // not correct because call could be in other version
    val fullUrl = s"$fullBaseUrl$url6"

  fullUrl
  }


  def getApiLinkTemplates(callerContext: CallerContext,
                           codeContext: CodeContext
                         ) : List[InternalApiLink] = {



    // Relations of the API version where the caller is defined.
    val relations =  codeContext.relationsArrayBuffer.toList

    // Resource Docs
    // Note: This doesn't allow linking to calls in earlier versions of the API
    // TODO: Fix me
    val resourceDocs =  codeContext.resourceDocsArrayBuffer

    val pf = callerContext.caller

    val internalApiLinks: List[InternalApiLink] = for {
      relation <- relations.filter(r => r.fromPF == pf)
      toResourceDoc <- resourceDocs.find(rd => rd.partialFunction == relation.toPF)
    }
      yield new InternalApiLink(
        pf,
        toResourceDoc.partialFunction,
        relation.rel,
        // Add the vVersion to the documented url
        s"/${apiVersionWithV(toResourceDoc.apiVersion)}${toResourceDoc.requestUrl}"
      )
    internalApiLinks
  }



  // This is not currently including "templated" attribute
  def halLinkFragment (link: ApiLink) : String = {
    "\"" + link.rel +"\": { \"href\": \"" +link.href + "\" }"
  }


  // Since HAL links can't be represented via a case class, (they have dynamic attributes rather than a list) we need to generate them here.
  def buildHalLinks(links: List[ApiLink]): JValue = {

    val halLinksString = links match {
      case head :: tail => tail.foldLeft("{"){(r: String, c: ApiLink) => ( r + " " + halLinkFragment(c) + " ,"  ) } + halLinkFragment(head) + "}"
      case Nil => "{}"
    }
    parse(halLinksString)
  }


  // Returns API links (a list of them) that have placeholders (e.g. BANK_ID) replaced by values (e.g. ulster-bank)
  def getApiLinks(callerContext: CallerContext, codeContext: CodeContext, dataContext: DataContext) : List[ApiLink]  = {
    val templates = getApiLinkTemplates(callerContext, codeContext)
    // Replace place holders in the urls like BANK_ID with the current value e.g. 'ulster-bank' and return as ApiLinks for external consumption
    val links = templates.map(i => ApiLink(i.rel,
      contextModifiedUrl(i.requestUrl, dataContext) )
    )
    links
  }


  // Returns links formatted at objects.
  def getHalLinks(callerContext: CallerContext, codeContext: CodeContext, dataContext: DataContext) : JValue  = {
    val links = getApiLinks(callerContext, codeContext, dataContext)
    getHalLinksFromApiLinks(links)
  }



  def getHalLinksFromApiLinks(links: List[ApiLink]) : JValue = {
    val halLinksJson = buildHalLinks(links)
    halLinksJson
  }

  def isSuperAdmin(user_id: String) : Boolean = {
    val user_ids = Props.get("super_admin_user_ids", "super_admin_user_ids is not defined").split(",").map(_.trim).toList
    user_ids.filter(_ == user_id).length > 0
  }

  def hasEntitlement(bankId: String, userId: String, role: ApiRole): Boolean = {
    !Entitlement.entitlement.vend.getEntitlement(bankId, userId, role.toString).isEmpty
  }

  // Function checks does a user specified by a parameter userId has at least one role provided by a parameter roles at a bank specified by a parameter bankId
  // i.e. does user has assigned at least one role from the list
  def hasAtLeastOneEntitlement(bankId: String, userId: String, roles: List[ApiRole]): Boolean = {
    val list: List[Boolean] = for (role <- roles) yield {
      !Entitlement.entitlement.vend.getEntitlement(if (role.requiresBankId == true) bankId else "", userId, role.toString).isEmpty
    }
    list.exists(_ == true)
  }

  // Function checks does a user specified by a parameter userId has all roles provided by a parameter roles at a bank specified by a parameter bankId
  // i.e. does user has assigned all roles from the list
  // TODO Should we accept Option[BankId] for bankId  instead of String ?
  def hasAllEntitlements(bankId: String, userId: String, roles: List[ApiRole]): Boolean = {
    val list: List[Boolean] = for (role <- roles) yield {
      !Entitlement.entitlement.vend.getEntitlement(if (role.requiresBankId == true) bankId else "", userId, role.toString).isEmpty
    }
    list.forall(_ == true)
  }

  def getCustomers(ids: List[String]): List[Customer] = {
    val customers = {
      for {id <- ids
           c = Customer.customerProvider.vend.getCustomerByCustomerId(id)
           u <- c
      } yield {
        u
      }
    }
    customers
  }

  def getAutocompleteValue: String = {
    Props.get("autocomplete_at_login_form_enabled", "false") match {
      case "true"  => "on"
      case "false" => "off"
      case _       => "off"
    }
  }
  
  // check is there a "$" in the input value.
  // eg: MODULE$ is not the useful input.
  // eg2: allFieldsAndValues is just for SwaggerJSONsV220.allFieldsAndValues,it is not useful.
  def notExstingBaseClass(input: String): Boolean = {
    !input.contains("$") && !input.equalsIgnoreCase("allFieldsAndValues")
  }


  def saveConnectorMetric[R](blockOfCode: => R)(nameOfFunction: String = "")(implicit nameOfConnector: String): R = {
    val t0 = System.currentTimeMillis()
    val result = blockOfCode
    // call-by-name
    val t1 = System.currentTimeMillis()
    if (Props.getBool("write_metrics", false)){
      import scala.concurrent.ExecutionContext.Implicits.global
      Future {
        ConnMetrics.metrics.vend.saveConnectorMetric(nameOfConnector, nameOfFunction, "", now, t1 - t0)
      }
    }
    result
  }

  val localRemotedataSecret = UUID.randomUUID.toString

  def akkaSanityCheck (): Box[Boolean] = {
    val remotedataSecret = Props.getBool("remotedata.enable", false) match {
      case true => Props.get("remotedata.secret").openOrThrowException("Cannot obtain property remotedata.secret")
      case false => localRemotedataSecret
    }
    SanityCheck.sanityCheck.vend.remoteAkkaSanityCheck(remotedataSecret)

  }

}
