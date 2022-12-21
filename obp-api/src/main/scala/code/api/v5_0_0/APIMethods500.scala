package code.api.v5_0_0

import java.util.concurrent.ThreadLocalRandom

import code.accountattribute.AccountAttributeX
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil._
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.NewStyle.HttpCode
import code.api.util.NewStyle.function.extractQueryParams
import code.api.util._
import code.api.v2_1_0.JSONFactory210
import code.api.v3_0_0.JSONFactory300
import code.api.v3_1_0._
import code.api.v4_0_0.JSONFactory400.createCustomersMinimalJson
import code.api.v4_0_0.{JSONFactory400, PutProductJsonV400}
import code.api.v5_0_0.JSONFactory500.{createPhysicalCardJson, createViewJsonV500, createViewsJsonV500, createViewsIdsJsonV500}
import code.bankconnectors.Connector
import code.consent.{ConsentRequests, Consents}
import code.entitlement.Entitlement
import code.metrics.APIMetrics
import code.model._
import code.model.dataAccess.BankAccountCreation
import code.transactionrequests.TransactionRequests.TransactionRequestTypes.{apply => _}
import code.util.Helper
import code.util.Helper.booleanToFuture
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.enums.StrongCustomerAuthentication
import com.openbankproject.commons.model._
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.{Empty, Full}
import net.liftweb.http.Req
import net.liftweb.http.rest.RestHelper
import net.liftweb.json
import net.liftweb.json.{Extraction, compactRender, prettyRender}
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.{Helpers, Props}
import java.util.concurrent.ThreadLocalRandom

import code.accountattribute.AccountAttributeX
import code.util.Helper.booleanToFuture

import scala.collection.immutable.{List, Nil}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.util.Random

trait APIMethods500 {
  self: RestHelper =>

  val Implementations5_0_0 = new Implementations500()

  protected trait TestHead {
    /**
     * Test to see if the request is a GET and expecting JSON in the response.
     * The path and the Req instance are extracted.
     */
    def unapply(r: Req): Option[(List[String], Req)] =
      if (r.requestType.head_? && testResponse_?(r))
        Some(r.path.partPath -> r) else None

    def testResponse_?(r: Req): Boolean
  }

  lazy val JsonHead = new TestHead with JsonTest
  
  class Implementations500 {

    val implementedInApiVersion = ApiVersion.v5_0_0

    private val staticResourceDocs = ArrayBuffer[ResourceDoc]()
    def resourceDocs = staticResourceDocs 

    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(staticResourceDocs, apiRelations)


    staticResourceDocs += ResourceDoc(
      getBank,
      implementedInApiVersion,
      nameOf(getBank),
      "GET",
      "/banks/BANK_ID",
      "Get Bank",
      """Get the bank specified by BANK_ID
        |Returns information about a single bank specified by BANK_ID including:
        |
        |* Bank code and full name of bank
        |* Logo URL
        |* Website""",
      EmptyBody,
      bankJson500,
      List(UnknownError, BankNotFound),
      apiTagBank :: apiTagPSD2AIS :: apiTagPsd2 :: apiTagNewStyle :: Nil
    )

    lazy val getBank : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: Nil JsonGet _ => {
        cc =>
          for {
            (bank, callContext) <- NewStyle.function.getBank(bankId, cc.callContext)
            (attributes, callContext) <- NewStyle.function.getBankAttributesByBank(bankId, callContext)
          } yield
            (JSONFactory500.createBankJSON500(bank, attributes), HttpCode.`200`(callContext))
      }
    }
    
    staticResourceDocs += ResourceDoc(
      createBank,
      implementedInApiVersion,
      "createBank",
      "POST",
      "/banks",
      "Create Bank",
      s"""Create a new bank (Authenticated access).
         |
         |The user creating this will be automatically assigned the Role CanCreateEntitlementAtOneBank.
         |Thus the User can manage the bank they create and assign Roles to other Users.
         |
         |Only SANDBOX mode
         |The settlement accounts are created specified by the bank in the POST body.
         |Name and account id are created in accordance to the next rules:
         |  - Incoming account (name: Default incoming settlement account, Account ID: OBP_DEFAULT_INCOMING_ACCOUNT_ID, currency: EUR)
         |  - Outgoing account (name: Default outgoing settlement account, Account ID: OBP_DEFAULT_OUTGOING_ACCOUNT_ID, currency: EUR)
         |
         |""",
      postBankJson500,
      bankJson500,
      List(
        InvalidJsonFormat,
        $UserNotLoggedIn,
        InsufficientAuthorisationToCreateBank,
        UnknownError
      ),
      List(apiTagBank, apiTagNewStyle),
      Some(List(canCreateBank))
    )

    lazy val createBank: OBPEndpoint = {
      case "banks" :: Nil JsonPost json -> _ => {
        cc =>
          val failMsg = s"$InvalidJsonFormat The Json body should be the $PostBankJson500 "
          for {
            bank <- NewStyle.function.tryons(failMsg, 400, cc.callContext) {
              json.extract[PostBankJson500]
            }
            _ <- Helper.booleanToFuture(failMsg = ErrorMessages.InvalidConsumerCredentials, cc=cc.callContext) {
              cc.callContext.map(_.consumer.isDefined == true).isDefined
            }
            _ <- Helper.booleanToFuture(failMsg = s"$InvalidJsonFormat Min length of BANK_ID should be greater than 3 characters.", cc=cc.callContext) {
              bank.id.forall(_.length > 3)
            }
            _ <- Helper.booleanToFuture(failMsg = s"$InvalidJsonFormat BANK_ID can not contain space characters", cc=cc.callContext) {
              !bank.id.contains(" ")
            }
            _ <- Helper.booleanToFuture(failMsg = s"$InvalidJsonFormat BANK_ID can not contain `::::` characters", cc=cc.callContext) {
              !`checkIfContains::::`(bank.id.getOrElse(""))
            }
            (banks, callContext) <- NewStyle.function.getBanks(cc.callContext)
            _ <- Helper.booleanToFuture(failMsg = ErrorMessages.bankIdAlreadyExists, cc=cc.callContext) {
              !banks.exists { b => Some(b.bankId.value) == bank.id }
            }
            (success, callContext) <- NewStyle.function.createOrUpdateBank(
              bank.id.getOrElse(APIUtil.generateUUID()),
              bank.full_name.getOrElse(""),
              bank.bank_code,
              bank.logo.getOrElse(""),
              bank.website.getOrElse(""),
              bank.bank_routings.getOrElse(Nil).find(_.scheme == "BIC").map(_.address).getOrElse(""),
              "",
              bank.bank_routings.getOrElse(Nil).filterNot(_.scheme == "BIC").headOption.map(_.scheme).getOrElse(""),
              bank.bank_routings.getOrElse(Nil).filterNot(_.scheme == "BIC").headOption.map(_.address).getOrElse(""),
              callContext
            )
            entitlements <- NewStyle.function.getEntitlementsByUserId(cc.userId, callContext)
            entitlementsByBank = entitlements.filter(_.bankId==bank.id.getOrElse(""))
            _ <- entitlementsByBank.filter(_.roleName == CanCreateEntitlementAtOneBank.toString()).size > 0 match {
              case true =>
                // Already has entitlement
                Future()
              case false =>
                Future(Entitlement.entitlement.vend.addEntitlement(bank.id.getOrElse(""), cc.userId, CanCreateEntitlementAtOneBank.toString()))
            }
            _ <- entitlementsByBank.filter(_.roleName == CanReadDynamicResourceDocsAtOneBank.toString()).size > 0 match {
              case true =>
                // Already has entitlement
                Future()
              case false =>
                Future(Entitlement.entitlement.vend.addEntitlement(bank.id.getOrElse(""), cc.userId, CanReadDynamicResourceDocsAtOneBank.toString()))
            }
          } yield {
            (JSONFactory500.createBankJSON500(success), HttpCode.`201`(callContext))
          }
      }
    }    
    staticResourceDocs += ResourceDoc(
      updateBank,
      implementedInApiVersion,
      "updateBank",
      "PUT",
      "/banks",
      "Update Bank",
      s"""Update an existing bank (Authenticated access).
         |
         |""",
      postBankJson500,
      bankJson500,
      List(
        InvalidJsonFormat,
        $UserNotLoggedIn,
        BankNotFound,
        updateBankError,
        UnknownError
      ),
      List(apiTagBank, apiTagNewStyle),
      Some(List(canCreateBank))
    )

    lazy val updateBank: OBPEndpoint = {
      case "banks" :: Nil JsonPut json -> _ => {
        cc =>
          val failMsg = s"$InvalidJsonFormat The Json body should be the $PostBankJson500 "
          for {
            bank <- NewStyle.function.tryons(failMsg, 400, cc.callContext) {
              json.extract[PostBankJson500]
            }
            _ <- Helper.booleanToFuture(failMsg = ErrorMessages.InvalidConsumerCredentials, cc=cc.callContext) {
              cc.callContext.map(_.consumer.isDefined == true).isDefined
            }
            _ <- Helper.booleanToFuture(failMsg = s"$InvalidJsonFormat Min length of BANK_ID should be greater than 3 characters.", cc=cc.callContext) {
              bank.id.forall(_.length > 3)
            }
            _ <- Helper.booleanToFuture(failMsg = s"$InvalidJsonFormat BANK_ID can not contain space characters", cc=cc.callContext) {
              !bank.id.contains(" ")
            }
            bankId <- NewStyle.function.tryons(ErrorMessages.updateBankError, 400,  cc.callContext) {
              bank.id.get
            }
            (_, callContext) <- NewStyle.function.getBank(BankId(bankId), cc.callContext)
            (success, callContext) <- NewStyle.function.createOrUpdateBank(
              bankId,
              bank.full_name.getOrElse(""),
              bank.bank_code,
              bank.logo.getOrElse(""),
              bank.website.getOrElse(""),
              bank.bank_routings.getOrElse(Nil).find(_.scheme == "BIC").map(_.address).getOrElse(""),
              "",
              bank.bank_routings.getOrElse(Nil).filterNot(_.scheme == "BIC").headOption.map(_.scheme).getOrElse(""),
              bank.bank_routings.getOrElse(Nil).filterNot(_.scheme == "BIC").headOption.map(_.address).getOrElse(""),
              callContext
            )
          } yield {
            (JSONFactory500.createBankJSON500(success), HttpCode.`200`(callContext))
          }
      }
    }


    resourceDocs += ResourceDoc(
      createAccount,
      implementedInApiVersion,
      "createAccount",
      "PUT",
      "/banks/BANK_ID/accounts/ACCOUNT_ID",
      "Create Account",
      """Create Account at bank specified by BANK_ID with Id specified by ACCOUNT_ID.
        |
        |The User can create an Account for themself  - or -  the User that has the USER_ID specified in the POST body.
        |
        |If the PUT body USER_ID *is* specified, the logged in user must have the Role canCreateAccount. Once created, the Account will be owned by the User specified by USER_ID.
        |
        |If the PUT body USER_ID is *not* specified, the account will be owned by the logged in User.
        |
        |The 'product_code' field SHOULD be a product_code from Product.
        |If the 'product_code' matches a product_code from Product, account attributes will be created that match the Product Attributes.
        |
        |Note: The Amount MUST be zero.""".stripMargin,
      createAccountRequestJsonV500,
      createAccountResponseJsonV310,
      List(
        InvalidJsonFormat,
        BankNotFound,
        UserNotLoggedIn,
        InvalidUserId,
        InvalidAccountIdFormat,
        InvalidBankIdFormat,
        UserNotFoundById,
        UserHasMissingRoles,
        InvalidAccountBalanceAmount,
        InvalidAccountInitialBalance,
        InitialBalanceMustBeZero,
        InvalidAccountBalanceCurrency,
        AccountIdAlreadyExists,
        UnknownError
      ),
      List(apiTagAccount,apiTagOnboarding, apiTagNewStyle),
      Some(List(canCreateAccount))
    )


    lazy val createAccount : OBPEndpoint = {
      // Create a new account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: Nil JsonPut json -> _ => {
        cc =>{
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            (account, callContext) <- Connector.connector.vend.checkBankAccountExists(bankId, accountId, callContext)
            _ <- Helper.booleanToFuture(AccountIdAlreadyExists, cc=callContext){
              account.isEmpty
            }
            failMsg = s"$InvalidJsonFormat The Json body should be the ${prettyRender(Extraction.decompose(createAccountRequestJsonV310))} "
            createAccountJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[CreateAccountRequestJsonV500]
            }
            loggedInUserId = u.userId
            userIdAccountOwner = createAccountJson.user_id match {
              case Some(userId) => userId
              case _ => loggedInUserId
            }
            _ <- Helper.booleanToFuture(InvalidAccountIdFormat, cc=callContext){
              isValidID(accountId.value)
            }
            _ <- Helper.booleanToFuture(InvalidBankIdFormat, cc=callContext){
              isValidID(accountId.value)
            }
            (postedOrLoggedInUser,callContext) <- NewStyle.function.findByUserId(userIdAccountOwner, callContext)
            // User can create account for self or an account for another user if they have CanCreateAccount role
            _ <- Helper.booleanToFuture(InvalidAccountIdFormat, cc=callContext){
              isValidID(accountId.value)
            }
            _ <- {userIdAccountOwner == loggedInUserId} match {
              case true => Future.successful(Full(Unit))
              case false =>
                NewStyle.function.hasEntitlement(
                  bankId.value, 
                  loggedInUserId,
                  canCreateAccount, 
                  callContext, 
                  s"${UserHasMissingRoles} $canCreateAccount or create account for self"
                )
            }
            initialBalanceAsString = createAccountJson.balance.map(_.amount).getOrElse("0")
            accountType = createAccountJson.product_code
            accountLabel = createAccountJson.label
            initialBalanceAsNumber <- NewStyle.function.tryons(InvalidAccountInitialBalance, 400, callContext) {
              BigDecimal(initialBalanceAsString)
            }
            _ <-  Helper.booleanToFuture(InitialBalanceMustBeZero, cc=callContext){0 == initialBalanceAsNumber}
            _ <-  Helper.booleanToFuture(InvalidISOCurrencyCode, cc=callContext){isValidCurrencyISOCode(createAccountJson.balance.map(_.currency).getOrElse("EUR"))}
            currency = createAccountJson.balance.map(_.currency).getOrElse("EUR")
            (_, callContext ) <- NewStyle.function.getBank(bankId, callContext)
            _ <- Helper.booleanToFuture(s"$InvalidAccountRoutings Duplication detected in account routings, please specify only one value per routing scheme", 400, cc=callContext){
              createAccountJson.account_routings.getOrElse(Nil).map(_.scheme).distinct.size == createAccountJson.account_routings.getOrElse(Nil).size
            }
            alreadyExistAccountRoutings <- Future.sequence(createAccountJson.account_routings.getOrElse(Nil).map(accountRouting =>
              NewStyle.function.getAccountRouting(Some(bankId), accountRouting.scheme, accountRouting.address, callContext).map(_ => Some(accountRouting)).fallbackTo(Future.successful(None))
            ))
            alreadyExistingAccountRouting = alreadyExistAccountRoutings.collect {
              case Some(accountRouting) => s"bankId: $bankId, scheme: ${accountRouting.scheme}, address: ${accountRouting.address}"
            }
            _ <- Helper.booleanToFuture(s"$AccountRoutingAlreadyExist (${alreadyExistingAccountRouting.mkString("; ")})", cc=callContext) {
              alreadyExistingAccountRouting.isEmpty
            }
            (bankAccount,callContext) <- NewStyle.function.createBankAccount(
              bankId,
              accountId,
              accountType,
              accountLabel,
              currency,
              initialBalanceAsNumber,
              postedOrLoggedInUser.name,
              createAccountJson.branch_id.getOrElse(""),
              createAccountJson.account_routings.getOrElse(Nil).map(r => AccountRouting(r.scheme, r.address)),
              callContext
            )
            (productAttributes, callContext) <- NewStyle.function.getProductAttributesByBankAndCode(bankId, ProductCode(accountType), callContext)
            (accountAttributes, callContext) <- NewStyle.function.createAccountAttributes(
              bankId,
              accountId,
              ProductCode(accountType),
              productAttributes,
              None,
              callContext: Option[CallContext]
            )
          } yield {
            //1 Create or Update the `Owner` for the new account
            //2 Add permission to the user
            //3 Set the user as the account holder
            BankAccountCreation.setAccountHolderAndRefreshUserAccountAccess(bankId, accountId, postedOrLoggedInUser, callContext)
            (JSONFactory310.createAccountJSON(userIdAccountOwner, bankAccount, accountAttributes), HttpCode.`201`(callContext))
          }
        }
      }
    }
    

    staticResourceDocs += ResourceDoc(
      createUserAuthContext,
      implementedInApiVersion,
      nameOf(createUserAuthContext),
      "POST",
      "/users/USER_ID/auth-context",
      "Create User Auth Context",
      s"""Create User Auth Context. These key value pairs will be propagated over connector to adapter. Normally used for mapping OBP user and 
         | Bank User/Customer. 
         |${authenticationRequiredMessage(true)}
         |""",
      postUserAuthContextJson,
      userAuthContextJsonV500,
      List(
        UserNotLoggedIn,
        InvalidJsonFormat,
        CreateUserAuthContextError,
        UnknownError
      ),
      List(apiTagUser, apiTagNewStyle),
      Some(List(canCreateUserAuthContext)))
    lazy val createUserAuthContext : OBPEndpoint = {
      case "users" :: userId ::"auth-context" :: Nil JsonPost  json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, canCreateUserAuthContext, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostUserAuthContextJson "
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PostUserAuthContextJson]
            }
            (user, callContext) <- NewStyle.function.findByUserId(userId, callContext)
            (userAuthContext, callContext) <- NewStyle.function.createUserAuthContext(user, postedData.key.trim, postedData.value.trim, callContext)
          } yield {
            (JSONFactory500.createUserAuthContextJson(userAuthContext), HttpCode.`201`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      getUserAuthContexts,
      implementedInApiVersion,
      nameOf(getUserAuthContexts),
      "GET",
      "/users/USER_ID/auth-context",
      "Get User Auth Contexts",
      s"""Get User Auth Contexts for a User.
         |
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      userAuthContextJsonV500,
      List(
        UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagUser, apiTagNewStyle),
      Some(canGetUserAuthContext :: Nil)
    )
    lazy val getUserAuthContexts : OBPEndpoint = {
      case "users" :: userId :: "auth-context" ::  Nil  JsonGet _ => {
        cc =>
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            _ <- NewStyle.function.hasEntitlement("", u.userId, canGetUserAuthContext, callContext)
            (_, callContext) <- NewStyle.function.findByUserId(userId, callContext)
            (userAuthContexts, callContext) <- NewStyle.function.getUserAuthContexts(userId, callContext)
          } yield {
            (JSONFactory500.createUserAuthContextsJson(userAuthContexts), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      createUserAuthContextUpdateRequest,
      implementedInApiVersion,
      nameOf(createUserAuthContextUpdateRequest),
      "POST",
      "/banks/BANK_ID/users/current/auth-context-updates/SCA_METHOD",
      "Create User Auth Context Update Request",
      s"""Create User Auth Context Update Request.
         |${authenticationRequiredMessage(true)}
         |
         |A One Time Password (OTP) (AKA security challenge) is sent Out of Band (OOB) to the User via the transport defined in SCA_METHOD
         |SCA_METHOD is typically "SMS" or "EMAIL". "EMAIL" is used for testing purposes.
         |
         |""",
      postUserAuthContextJson,
      userAuthContextUpdateJsonV500,
      List(
        UserNotLoggedIn,
        $BankNotFound,
        InvalidJsonFormat,
        CreateUserAuthContextError,
        UnknownError
      ),
      List(apiTagUser, apiTagNewStyle),
      None
    )

    lazy val createUserAuthContextUpdateRequest : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "users" :: "current" ::"auth-context-updates" :: scaMethod :: Nil JsonPost  json -> _ => {
        cc =>
          for {
            (Full(user), callContext) <- authenticatedAccess(cc)
            _ <- Helper.booleanToFuture(failMsg = ConsumerHasMissingRoles + CanCreateUserAuthContextUpdate, cc=callContext) {
              checkScope(bankId.value, getConsumerPrimaryKey(callContext), ApiRole.canCreateUserAuthContextUpdate)
            }
            _ <- Helper.booleanToFuture(ConsentAllowedScaMethods, cc=callContext){
              List(StrongCustomerAuthentication.SMS.toString(), StrongCustomerAuthentication.EMAIL.toString()).exists(_ == scaMethod)
            }
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostUserAuthContextJson "
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PostUserAuthContextJson]
            }
            (userAuthContextUpdate, callContext) <- NewStyle.function.validateUserAuthContextUpdateRequest(bankId.value, user.userId, postedData.key.trim, postedData.value.trim, scaMethod, callContext)
          } yield {

            (JSONFactory500.createUserAuthContextUpdateJson(userAuthContextUpdate), HttpCode.`201`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      answerUserAuthContextUpdateChallenge,
      implementedInApiVersion,
      nameOf(answerUserAuthContextUpdateChallenge),
      "POST",
      "/banks/BANK_ID/users/current/auth-context-updates/AUTH_CONTEXT_UPDATE_ID/challenge",
      "Answer User Auth Context Update Challenge",
      s"""
         |Answer User Auth Context Update Challenge.
         |""",
      postUserAuthContextUpdateJsonV310,
      userAuthContextUpdateJsonV500,
      List(
        UserNotLoggedIn,
        $BankNotFound,
        InvalidJsonFormat,
        InvalidConnectorResponse,
        UnknownError
      ),
      apiTagUser :: apiTagNewStyle :: Nil)

    lazy val answerUserAuthContextUpdateChallenge : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "users" :: "current" ::"auth-context-updates"  :: authContextUpdateId :: "challenge" :: Nil JsonPost json -> _  => {
        cc =>
          for {
            (_, callContext) <- authenticatedAccess(cc)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostUserAuthContextUpdateJsonV310 "
            postUserAuthContextUpdateJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PostUserAuthContextUpdateJsonV310]
            }
            (userAuthContextUpdate, callContext) <- NewStyle.function.checkAnswer(authContextUpdateId, postUserAuthContextUpdateJson.answer, callContext)
            (user, callContext) <- NewStyle.function.getUserByUserId(userAuthContextUpdate.userId, callContext)
            (_, callContext) <-
              userAuthContextUpdate.status match {
                case status if status == UserAuthContextUpdateStatus.ACCEPTED.toString =>
                  NewStyle.function.createUserAuthContext(
                    user,
                    userAuthContextUpdate.key.trim,
                    userAuthContextUpdate.value.trim,
                    callContext).map(x => (Some(x._1), x._2))
                case _ =>
                  Future((None, callContext))
              }
            (_, callContext) <-
              userAuthContextUpdate.key match {
                case "CUSTOMER_NUMBER" =>
                  NewStyle.function.getOCreateUserCustomerLink(
                    bankId,
                    userAuthContextUpdate.value, // Customer number
                    user.userId,
                    callContext
                  )
                case _ =>
                  Future((None, callContext))
              }
          } yield {
            (JSONFactory500.createUserAuthContextUpdateJson(userAuthContextUpdate), HttpCode.`200`(callContext))
          }
      }
    }
    
    staticResourceDocs += ResourceDoc(
      createConsentRequest,
      implementedInApiVersion,
      nameOf(createConsentRequest),
      "POST",
      "/consumer/consent-requests",
      "Create Consent Request",
      s"""
         |Client Authentication (mandatory)
         |
         |It is used when applications request an access token to access their own resources, not on behalf of a user.
         |
         |The client needs to authenticate themselves for this request.
         |In case of public client we use client_id and private kew to obtain access token, otherwise we use client_id and client_secret.
         |The obtained access token is used in the HTTP Bearer auth header of our request.
         |
         |Example:
         |Authorization: Bearer eXtneO-THbQtn3zvK_kQtXXfvOZyZFdBCItlPDbR2Bk.dOWqtXCtFX-tqGTVR0YrIjvAolPIVg7GZ-jz83y6nA0
         |
         |""".stripMargin,
      postConsentRequestJsonV500,
      consentRequestResponseJson,
      List(
        $BankNotFound,
        InvalidJsonFormat,
        ConsentMaxTTL,
        UnknownError
        ),
      apiTagConsent :: apiTagPSD2AIS :: apiTagPsd2 :: apiTagNewStyle :: Nil
      )
  
    lazy val createConsentRequest : OBPEndpoint = {
      case  "consumer" :: "consent-requests" :: Nil JsonPost json -> _  =>  {
        cc =>
          for {
            (_, callContext) <- applicationAccess(cc)
            _ <- passesPsd2Aisp(callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostConsentBodyCommonJson "
            consentJson: PostConsentRequestJsonV500 <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PostConsentRequestJsonV500]
            }
            maxTimeToLive = APIUtil.getPropsAsIntValue(nameOfProperty="consents.max_time_to_live", defaultValue=3600)
            _ <- Helper.booleanToFuture(s"$ConsentMaxTTL ($maxTimeToLive)", cc=callContext){
              consentJson.time_to_live match {
                case Some(ttl) => ttl <= maxTimeToLive
                case _ => true
              }
            }
            createdConsentRequest <- Future(ConsentRequests.consentRequestProvider.vend.createConsentRequest(
              callContext.flatMap(_.consumer),
              Some(compactRender(json))
              )) map {
              i => connectorEmptyResponse(i, callContext)
            }
          } yield {
            (
              ConsentRequestResponseJson(
                createdConsentRequest.consentRequestId,
                net.liftweb.json.parse(createdConsentRequest.payload),
                createdConsentRequest.consumerId,
                ), 
              HttpCode.`201`(callContext)
            )
          }
      }
    }  

    staticResourceDocs += ResourceDoc(
      getConsentRequest,
      implementedInApiVersion,
      nameOf(getConsentRequest),
      "GET",
      "/consumer/consent-requests/CONSENT_REQUEST_ID",
      "Get Consent Request",
      s"""""",
      EmptyBody,
      consentRequestResponseJson,
      List(
        $BankNotFound,
        ConsentRequestNotFound,
        UnknownError
        ),
      apiTagConsent :: apiTagPSD2AIS :: apiTagPsd2 :: apiTagNewStyle :: Nil
      )

    lazy val getConsentRequest : OBPEndpoint = {
      case "consumer" :: "consent-requests" :: consentRequestId ::  Nil  JsonGet _  =>  {
        cc =>
          for {
            (_, callContext) <- applicationAccess(cc)
            _ <- passesPsd2Aisp(callContext)
            createdConsentRequest <- Future(ConsentRequests.consentRequestProvider.vend.getConsentRequestById(
              consentRequestId
              )) map {
              i => unboxFullOrFail(i,callContext, ConsentRequestNotFound)
            }
          } yield {
            (ConsentRequestResponseJson(
              consent_request_id = createdConsentRequest.consentRequestId,
              payload = json.parse(createdConsentRequest.payload),
              consumer_id = createdConsentRequest.consumerId
              ), 
              HttpCode.`200`(callContext)
            )
          }
      }
    }
  
    staticResourceDocs += ResourceDoc(
      getConsentByConsentRequestId,
      implementedInApiVersion,
      nameOf(getConsentByConsentRequestId),
      "GET",
      "/consumer/consent-requests/CONSENT_REQUEST_ID/consents",
      "Get Consent By Consent Request Id",
      s"""
         |
         |This endpoint gets the Consent By consent request id.
         |
         |${authenticationRequiredMessage(true)}
         |
      """.stripMargin,
      EmptyBody,
      consentJsonV500,
      List(
        $UserNotLoggedIn,
        UnknownError
        ),
      List(apiTagConsent, apiTagPSD2AIS, apiTagPsd2, apiTagNewStyle))
    lazy val getConsentByConsentRequestId: OBPEndpoint = {
      case "consumer" :: "consent-requests" :: consentRequestId :: "consents" :: Nil  JsonGet _  => {
        cc =>
          for {
            (_, callContext) <- applicationAccess(cc)
            consent<- Future { Consents.consentProvider.vend.getConsentByConsentRequestId(consentRequestId)} map {
              unboxFullOrFail(_, callContext, ConsentRequestNotFound)
            }
          } yield {
            (
              ConsentJsonV500(
              consent.consentId, 
              consent.jsonWebToken, 
              consent.status, 
              Some(consent.consentRequestId)
              ), 
              HttpCode.`200`(cc)
            )
          }
      }
    }
  
    staticResourceDocs += ResourceDoc(
      createConsentByConsentRequestIdEmail,
      implementedInApiVersion,
      nameOf(createConsentByConsentRequestIdEmail),
      "POST",
      "/consumer/consent-requests/CONSENT_REQUEST_ID/EMAIL/consents",
      "Create Consent By CONSENT_REQUEST_ID (EMAIL)",
      s"""
         |
         |This endpoint continues the process of creating a Consent. It starts the SCA flow which changes the status of the consent from INITIATED to ACCEPTED or REJECTED.
         |Please note that the Consent cannot elevate the privileges logged in user already have.
         |
         |""",
      EmptyBody,
      consentJsonV500,
      List(
        UserNotLoggedIn,
        BankNotFound,
        InvalidJsonFormat,
        ConsentAllowedScaMethods,
        RolesAllowedInConsent,
        ViewsAllowedInConsent,
        ConsumerNotFoundByConsumerId,
        ConsumerIsDisabled,
        InvalidConnectorResponse,
        UnknownError
        ),
      apiTagConsent :: apiTagPSD2AIS :: apiTagPsd2 :: apiTagNewStyle :: Nil)
    staticResourceDocs += ResourceDoc(
      createConsentByConsentRequestIdSms,
      implementedInApiVersion,
      nameOf(createConsentByConsentRequestIdSms),
      "POST",
      "/consumer/consent-requests/CONSENT_REQUEST_ID/SMS/consents",
      "Create Consent By CONSENT_REQUEST_ID (SMS)",
      s"""
         |
         |This endpoint continues the process of creating a Consent. It starts the SCA flow which changes the status of the consent from INITIATED to ACCEPTED or REJECTED.
         |Please note that the Consent cannot elevate the privileges logged in user already have. 
         |
         |""",
      EmptyBody,
      consentJsonV500,
      List(
        UserNotLoggedIn,
        $BankNotFound,
        InvalidJsonFormat,
        ConsentRequestIsInvalid,
        ConsentAllowedScaMethods,
        RolesAllowedInConsent,
        ViewsAllowedInConsent,
        ConsumerNotFoundByConsumerId,
        ConsumerIsDisabled,
        MissingPropsValueAtThisInstance,
        SmsServerNotResponding,
        InvalidConnectorResponse,
        UnknownError
        ),
      apiTagConsent :: apiTagPSD2AIS :: apiTagPsd2 ::apiTagNewStyle :: Nil)
    
    lazy val createConsentByConsentRequestIdEmail = createConsentByConsentRequestId
    lazy val createConsentByConsentRequestIdSms = createConsentByConsentRequestId
    
    lazy val createConsentByConsentRequestId : OBPEndpoint = {
      case "consumer" :: "consent-requests":: consentRequestId :: scaMethod :: "consents" :: Nil JsonPost _ -> _  => {
        cc =>
          for {
            (Full(user), callContext) <- authenticatedAccess(cc)
            createdConsentRequest <- Future(ConsentRequests.consentRequestProvider.vend.getConsentRequestById(
              consentRequestId
              )) map {
              i => unboxFullOrFail(i,callContext, ConsentRequestNotFound)
            }
            _ <- Helper.booleanToFuture(ConsentRequestIsInvalid, cc=callContext){
              Consents.consentProvider.vend.getConsentByConsentRequestId(consentRequestId).isEmpty
            }
            _ <- Helper.booleanToFuture(ConsentAllowedScaMethods, cc=callContext){
              List(StrongCustomerAuthentication.SMS.toString(), StrongCustomerAuthentication.EMAIL.toString()).exists(_ == scaMethod)
            }
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostConsentBodyCommonJson "
            consentRequestJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.parse(createdConsentRequest.payload).extract[PostConsentRequestJsonV500]
            }
            maxTimeToLive = APIUtil.getPropsAsIntValue(nameOfProperty="consents.max_time_to_live", defaultValue=3600)
            _ <- Helper.booleanToFuture(s"$ConsentMaxTTL ($maxTimeToLive)", cc=callContext){
              consentRequestJson.time_to_live match {
                case Some(ttl) => ttl <= maxTimeToLive
                case _ => true
              }
            }
            requestedEntitlements = consentRequestJson.entitlements.getOrElse(Nil)
            myEntitlements <- Entitlement.entitlement.vend.getEntitlementsByUserIdFuture(user.userId)
            _ <- Helper.booleanToFuture(RolesAllowedInConsent, cc=callContext){
              requestedEntitlements.forall(
                re => myEntitlements.getOrElse(Nil).exists(
                  e => e.roleName == re.role_name && e.bankId == re.bank_id
                  )
                )
            }

            postConsentViewJsons <- Future.sequence(
              consentRequestJson.account_access.map(
                access => 
                  NewStyle.function.getBankAccountByRouting(None,access.account_routing.scheme, access.account_routing.address, cc.callContext)
                    .map(result =>PostConsentViewJsonV310(
                      result._1.bankId.value,
                      result._1.accountId.value,
                      access.view_id
                    ))
                )
              )
  
            (_, assignedViews) <- Future(Views.views.vend.privateViewsUserCanAccess(user))
            _ <- Helper.booleanToFuture(ViewsAllowedInConsent, cc=callContext){
              postConsentViewJsons.forall(
                rv => assignedViews.exists{
                  e =>
                    e.view_id == rv.view_id &&
                      e.bank_id == rv.bank_id &&
                      e.account_id == rv.account_id
                }
                )
            }
            // Use consumer specified at the payload of consent request in preference to the field ConsumerId of consent request
            // i.e. ConsentRequest.Payload.consumer_id in preference to ConsentRequest.ConsumerId
            calculatedConsumerId = consentRequestJson.consumer_id.orElse(Some(createdConsentRequest.consumerId))
            (consumerId, applicationText) <- calculatedConsumerId match {
              case Some(id) => NewStyle.function.checkConsumerByConsumerId(id, callContext) map {
                c => (Some(c.consumerId.get), c.description)
              }
              case None => Future(None, "Any application")
            }
  
            challengeAnswer = Props.mode match {
              case Props.RunModes.Test => Consent.challengeAnswerAtTestEnvironment
              case _ => SecureRandomUtil.numeric()
            }
            createdConsent <- Future(Consents.consentProvider.vend.createObpConsent(user, challengeAnswer, Some(consentRequestId))) map {
              i => connectorEmptyResponse(i, callContext)
            }

            postConsentBodyCommonJson = PostConsentBodyCommonJson(
              everything = consentRequestJson.everything,
              views = postConsentViewJsons,
              entitlements = consentRequestJson.entitlements.getOrElse(Nil),
              consumer_id = consentRequestJson.consumer_id,
              consent_request_id = Some(consentRequestId),
              valid_from = consentRequestJson.valid_from,
              time_to_live = consentRequestJson.time_to_live,
            ) 
            
            consentJWT = Consent.createConsentJWT(
              user,
              postConsentBodyCommonJson,
              createdConsent.secret,
              createdConsent.consentId,
              consumerId,
              postConsentBodyCommonJson.valid_from,
              postConsentBodyCommonJson.time_to_live.getOrElse(3600)
              )
            _ <- Future(Consents.consentProvider.vend.setJsonWebToken(createdConsent.consentId, consentJWT)) map {
              i => connectorEmptyResponse(i, callContext)
            }
            challengeText = s"Your consent challenge : ${challengeAnswer}, Application: $applicationText"
            _ <- scaMethod match {
              case v if v == StrongCustomerAuthentication.EMAIL.toString => // Send the email
                for{
                  failMsg <- Future {s"$InvalidJsonFormat The Json body should be the $PostConsentEmailJsonV310"}
                  consentScaEmail <- NewStyle.function.tryons(failMsg, 400, callContext) {
                    consentRequestJson.email.head
                  }
                  (Full(status), callContext) <- Connector.connector.vend.sendCustomerNotification(
                    StrongCustomerAuthentication.EMAIL,
                    consentScaEmail,
                    Some("OBP Consent Challenge"),
                    challengeText,
                    callContext
                    )
                } yield Future{status}
              case v if v == StrongCustomerAuthentication.SMS.toString => // Not implemented
                for {
                  failMsg <- Future {
                    s"$InvalidJsonFormat The Json body should be the $PostConsentPhoneJsonV310"
                  }
                  consentScaPhoneNumber <- NewStyle.function.tryons(failMsg, 400, callContext) {
                    consentRequestJson.phone_number.head
                  }
                  (Full(status), callContext) <- Connector.connector.vend.sendCustomerNotification(
                    StrongCustomerAuthentication.SMS,
                    consentScaPhoneNumber,
                    None,
                    challengeText,
                    callContext
                    )
                } yield Future{status}
              case _ =>Future{"Success"}
            }
          } yield {
            (ConsentJsonV500(createdConsent.consentId, consentJWT, createdConsent.status, Some(createdConsent.consentRequestId)), HttpCode.`201`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      headAtms,
      implementedInApiVersion,
      nameOf(headAtms),
      "HEAD",
      "/banks/BANK_ID/atms",
      "Head Bank ATMS",
      s"""Head Bank ATMS.""",
      EmptyBody,
      atmsJsonV400,
      List(
        $BankNotFound,
        UnknownError
      ),
      List(apiTagATM, apiTagNewStyle)
    )
    lazy val headAtms : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "atms" :: Nil JsonHead _ => {
        cc =>
          for {
            (_, callContext) <- getAtmsIsPublic match {
              case false => authenticatedAccess(cc)
              case true => anonymousAccess(cc)
            }
          } yield {
            ("", HttpCode.`200`(callContext))
          }
      }
    }



    staticResourceDocs += ResourceDoc(
      createCustomer,
      implementedInApiVersion,
      nameOf(createCustomer),
      "POST",
      "/banks/BANK_ID/customers",
      "Create Customer",
      s"""
         |The Customer resource stores the customer number (which is set by the backend), legal name, email, phone number, their date of birth, relationship status, education attained, a url for a profile image, KYC status etc.
         |Dates need to be in the format 2013-01-21T23:08:00Z
         |
         |Note: If you need to set a specific customer number, use the Update Customer Number endpoint after this call.
         |
         |${authenticationRequiredMessage(true)}
         |""",
      postCustomerJsonV500,
      customerJsonV310,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        InvalidJsonFormat,
        CustomerNumberAlreadyExists,
        UserNotFoundById,
        CustomerAlreadyExistsForUser,
        CreateConsumerError,
        UnknownError
      ),
      List(apiTagCustomer, apiTagPerson, apiTagNewStyle),
      Some(List(canCreateCustomer,canCreateCustomerAtAnyBank))
    )
    lazy val createCustomer : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostCustomerJsonV310 ", 400, cc.callContext) {
              json.extract[PostCustomerJsonV500]
            }
            _ <- Helper.booleanToFuture(failMsg =  InvalidJsonContent + s" The field dependants(${postedData.dependants.getOrElse(0)}) not equal the length(${postedData.dob_of_dependants.getOrElse(Nil).length }) of dob_of_dependants array", 400, cc.callContext) {
              postedData.dependants.getOrElse(0) == postedData.dob_of_dependants.getOrElse(Nil).length
            }
            customerNumber = postedData.customer_number.getOrElse(Random.nextInt(Integer.MAX_VALUE).toString)

            _ <- Helper.booleanToFuture(failMsg = s"$InvalidJsonFormat customer_number can not contain `::::` characters", cc=cc.callContext) {
              !`checkIfContains::::` (customerNumber)
            }
            (_, callContext) <- NewStyle.function.checkCustomerNumberAvailable(bankId, customerNumber, cc.callContext)
            (customer, callContext) <- NewStyle.function.createCustomerC2(
              bankId,
              postedData.legal_name,
              customerNumber,
              postedData.mobile_phone_number,
              postedData.email.getOrElse(""),
              CustomerFaceImage(
                postedData.face_image.map(_.date).getOrElse(null), 
                postedData.face_image.map(_.url).getOrElse("")
              ),
              postedData.date_of_birth.getOrElse(null),
              postedData.relationship_status.getOrElse(""),
              postedData.dependants.getOrElse(0),
              postedData.dob_of_dependants.getOrElse(Nil),
              postedData.highest_education_attained.getOrElse(""),
              postedData.employment_status.getOrElse(""),
              postedData.kyc_status.getOrElse(false),
              postedData.last_ok_date.getOrElse(null),
              postedData.credit_rating.map(i => CreditRating(i.rating, i.source)),
              postedData.credit_limit.map(i => CreditLimit(i.currency, i.amount)),
              postedData.title.getOrElse(""),
              postedData.branch_id.getOrElse(""),
              postedData.name_suffix.getOrElse(""),
              callContext,
            )
          } yield {
            (JSONFactory310.createCustomerJson(customer), HttpCode.`201`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      getCustomerOverview,
      implementedInApiVersion,
      nameOf(getCustomerOverview),
      "POST",
      "/banks/BANK_ID/customers/customer-number-query/overview",
      "Get Customer Overview",
      s"""Gets the Customer Overview specified by customer_number and bank_code.
         |
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      postCustomerOverviewJsonV500,
      customerOverviewJsonV500,
      List(
        UserNotLoggedIn,
        UserCustomerLinksNotFoundForUser,
        UnknownError
      ),
      List(apiTagCustomer, apiTagKyc ,apiTagNewStyle),
      Some(List(canGetCustomerOverview))
    )

    lazy val getCustomerOverview : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: "customer-number-query" :: "overview" ::  Nil JsonPost  json -> req => {
        cc =>
          for {
            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostCustomerOverviewJsonV500 ", 400, cc.callContext) {
              json.extract[PostCustomerOverviewJsonV500]
            }
            (customer, callContext) <- NewStyle.function.getCustomerByCustomerNumber(postedData.customer_number, bankId, cc.callContext)
            (customerAttributes, callContext) <- NewStyle.function.getCustomerAttributes(
              bankId,
              CustomerId(customer.customerId),
              callContext: Option[CallContext])
            accountIds <- AccountAttributeX.accountAttributeProvider.vend
              .getAccountIdsByParams(bankId, List("customer_number" -> List(postedData.customer_number)).toMap)
            (accounts: List[BankAccount], callContext) <- NewStyle.function.getBankAccounts(accountIds.toList.flatten.map(i => BankIdAccountId(bankId, AccountId(i))), callContext)
            (accountAttributes, callContext) <- NewStyle.function.getAccountAttributesForAccounts(
              bankId,
              accounts,
              callContext: Option[CallContext])
          } yield {
            (JSONFactory500.createCustomerWithAttributesJson(customer, customerAttributes, accountAttributes), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getCustomerOverviewFlat,
      implementedInApiVersion,
      nameOf(getCustomerOverviewFlat),
      "POST",
      "/banks/BANK_ID/customers/customer-number-query/overview-flat",
      "Get Customer Overview Flat",
      s"""Gets the Customer Overview Flat specified by customer_number and bank_code.
         |
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      postCustomerOverviewJsonV500,
      customerOverviewFlatJsonV500,
      List(
        UserNotLoggedIn,
        UserCustomerLinksNotFoundForUser,
        UnknownError
      ),
      List(apiTagCustomer, apiTagKyc ,apiTagNewStyle),
      Some(List(canGetCustomerOverviewFlat))
    )

    lazy val getCustomerOverviewFlat : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: "customer-number-query" :: "overview-flat" ::  Nil JsonPost  json -> req => {
        cc =>
          for {
            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostCustomerOverviewJsonV500 ", 400, cc.callContext) {
              json.extract[PostCustomerOverviewJsonV500]
            }
            (customer, callContext) <- NewStyle.function.getCustomerByCustomerNumber(postedData.customer_number, bankId, cc.callContext)
            (customerAttributes, callContext) <- NewStyle.function.getCustomerAttributes(
              bankId,
              CustomerId(customer.customerId),
              callContext: Option[CallContext])
            accountIds <- AccountAttributeX.accountAttributeProvider.vend
              .getAccountIdsByParams(bankId, List("customer_number" -> List(postedData.customer_number)).toMap)
            (accounts: List[BankAccount], callContext) <- NewStyle.function.getBankAccounts(accountIds.toList.flatten.map(i => BankIdAccountId(bankId, AccountId(i))), callContext)
            (accountAttributes, callContext) <- NewStyle.function.getAccountAttributesForAccounts(
              bankId,
              accounts,
              callContext: Option[CallContext])
          } yield {
            (JSONFactory500.createCustomerOverviewFlatJson(customer, customerAttributes, accountAttributes), HttpCode.`200`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      getMyCustomersAtAnyBank,
      implementedInApiVersion,
      nameOf(getMyCustomersAtAnyBank),
      "GET",
      "/my/customers",
      "Get My Customers",
      """Gets all Customers that are linked to me.
        |
        |Authentication via OAuth is required.""",
      EmptyBody,
      customerJsonV210,
      List(
        $UserNotLoggedIn,
        UserCustomerLinksNotFoundForUser,
        UnknownError
      ),
      List(apiTagCustomer, apiTagUser))

    lazy val getMyCustomersAtAnyBank : OBPEndpoint = {
      case "my" :: "customers" :: Nil JsonGet _ => {
        cc => {
          for {
            (Full(u), callContext) <- SS.user
            (customers, callContext) <- Connector.connector.vend.getCustomersByUserId(u.userId, callContext) map {
              connectorEmptyResponse(_, callContext)
            }
          } yield {
            (JSONFactory210.createCustomersJson(customers), HttpCode.`200`(callContext))
          }
        }
      }
    }

    staticResourceDocs += ResourceDoc(
      getMyCustomersAtBank,
      implementedInApiVersion,
      nameOf(getMyCustomersAtBank),
      "GET",
      "/banks/BANK_ID/my/customers",
      "Get My Customers at Bank",
      s"""Returns a list of Customers at the Bank that are linked to the currently authenticated User.
         |
         |
         |${authenticationRequiredMessage(true)}""".stripMargin,
      EmptyBody,
      customerJSONs,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UserCustomerLinksNotFoundForUser,
        UnknownError
      ),
      List(apiTagCustomer, apiTagNewStyle)
    )

    lazy val getMyCustomersAtBank : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "my" :: "customers" :: Nil JsonGet _ => {
        cc => {
          for {
            (Full(u), callContext) <- SS.user
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (customers, callContext) <- Connector.connector.vend.getCustomersByUserId(u.userId, callContext) map {
              connectorEmptyResponse(_, callContext)
            }
          } yield {
            // Filter so we only see the ones for the bank in question
            val bankCustomers = customers.filter(_.bankId==bankId.value)
            val json = JSONFactory210.createCustomersJson(bankCustomers)
            (json, HttpCode.`200`(callContext))
          }
        }
      }
    }


    staticResourceDocs += ResourceDoc(
      getCustomersAtOneBank,
      implementedInApiVersion,
      nameOf(getCustomersAtOneBank),
      "GET",
      "/banks/BANK_ID/customers",
      "Get Customers at Bank",
      s"""Get Customers at Bank.
         |
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      customersJsonV300,
      List(
        UserNotLoggedIn,
        UserCustomerLinksNotFoundForUser,
        UnknownError
      ),
      List(apiTagCustomer, apiTagUser, apiTagNewStyle),
      Some(List(canGetCustomers))
    )

    lazy val getCustomersAtOneBank : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: Nil JsonGet _ => {
        cc => {
          for {
            (requestParams, callContext) <- extractQueryParams(cc.url, List("limit","offset","sort_direction"), cc.callContext)
            customers <- NewStyle.function.getCustomers(bankId, callContext, requestParams)
          } yield {
            (JSONFactory300.createCustomersJson(customers.sortBy(_.bankId)), HttpCode.`200`(callContext))
          }
        }
      }
    }

    staticResourceDocs += ResourceDoc(
      getCustomersMinimalAtOneBank,
      implementedInApiVersion,
      nameOf(getCustomersMinimalAtOneBank),
      "GET",
      "/banks/BANK_ID/customers-minimal",
      "Get Customers Minimal at Bank",
      s"""Get Customers Minimal at Bank.
         |
         |
         |
         |""",
      EmptyBody,
      customersMinimalJsonV300,
      List(
        UserCustomerLinksNotFoundForUser,
        UnknownError
      ),
      List(apiTagCustomer, apiTagUser, apiTagNewStyle),
      Some(List(canGetCustomersMinimal))
    )
    lazy val getCustomersMinimalAtOneBank : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers-minimal" :: Nil JsonGet _ => {
        cc => {
          for {
            (requestParams, callContext) <- extractQueryParams(cc.url, List("limit","offset","sort_direction"), cc.callContext)
            customers <- NewStyle.function.getCustomers(bankId, callContext, requestParams)
          } yield {
            (createCustomersMinimalJson(customers.sortBy(_.bankId)), HttpCode.`200`(callContext))
          }
        }
      }
    }


    staticResourceDocs += ResourceDoc(
      createProduct,
      implementedInApiVersion,
      nameOf(createProduct),
      "PUT",
      "/banks/BANK_ID/products/PRODUCT_CODE",
      "Create Product",
      s"""Create or Update Product for the Bank.
         |
         |
         |Typical Super Family values / Asset classes are:
         |
         |Debt
         |Equity
         |FX
         |Commodity
         |Derivative
         |
         |$productHiearchyAndCollectionNote
         |
         |
         |${authenticationRequiredMessage(true) }
         |
         |
         |""",
      putProductJsonV500,
      productJsonV400.copy(attributes = None, fees = None),
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagProduct, apiTagNewStyle),
      Some(List(canCreateProduct, canCreateProductAtAnyBank))
    )
    lazy val createProduct: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "products" :: ProductCode(productCode) :: Nil JsonPut json -> _ => {
        cc =>
          for {
            (Full(u), callContext) <- SS.user
            _ <- NewStyle.function.hasAtLeastOneEntitlement(failMsg = createProductEntitlementsRequiredText)(bankId.value, u.userId, createProductEntitlements, callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PutProductJsonV400 "
            product <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PutProductJsonV500]
            }
            parentProductCode <- product.parent_product_code.trim.nonEmpty match {
              case false =>
                Future(Empty)
              case true =>
                Future(Connector.connector.vend.getProduct(bankId, ProductCode(product.parent_product_code))) map {
                  getFullBoxOrFail(_, callContext, ParentProductNotFoundByProductCode + " {" + product.parent_product_code + "}", 400)
                }
            }
            success <- Future(Connector.connector.vend.createOrUpdateProduct(
              bankId = bankId.value,
              code = productCode.value,
              parentProductCode = parentProductCode.map(_.code.value).toOption,
              name = product.name,
              category = null,
              family = null,
              superFamily = null,
              moreInfoUrl = product.more_info_url.getOrElse(""),
              termsAndConditionsUrl = product.terms_and_conditions_url.getOrElse(""),
              details = null,
              description = product.description.getOrElse(""),
              metaLicenceId = product.meta.map(_.license.id).getOrElse(""),
              metaLicenceName = product.meta.map(_.license.name).getOrElse("")
            )) map {
              connectorEmptyResponse(_, callContext)
            }
          } yield {
            (JSONFactory400.createProductJson(success), HttpCode.`201`(callContext))
          }
      }
    }
    
    

    staticResourceDocs += ResourceDoc(
      addCardForBank,
      implementedInApiVersion,
      nameOf(addCardForBank),
      "POST",
      "/management/banks/BANK_ID/cards",
      "Create Card",
      s"""Create Card at bank specified by BANK_ID .
         |
         |${authenticationRequiredMessage(true)}
         |""",
      createPhysicalCardJsonV500,
      physicalCardJsonV500,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UserHasMissingRoles,
        AllowedValuesAre,
        UnknownError
      ),
      List(apiTagCard, apiTagNewStyle),
      Some(List(canCreateCardsForBank)))
    lazy val addCardForBank: OBPEndpoint = {
      case "management" :: "banks" :: BankId(bankId) :: "cards" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            (Full(u), _,callContext) <- SS.userBank

            failMsg = s"$InvalidJsonFormat The Json body should be the $CreatePhysicalCardJsonV500 "
            postJson <- NewStyle.function.tryons(failMsg, 400, callContext) {json.extract[CreatePhysicalCardJsonV500]}

            _ <- postJson.allows match {
              case List() => Future {true}
              case _ => Helper.booleanToFuture(AllowedValuesAre + CardAction.availableValues.mkString(", "), cc=callContext)(postJson.allows.forall(a => CardAction.availableValues.contains(a)))
            }

            failMsg = AllowedValuesAre + CardReplacementReason.availableValues.mkString(", ")
            cardReplacementReason <- NewStyle.function.tryons(failMsg, 400, callContext) {
              postJson.replacement match {
                case Some(value) => CardReplacementReason.valueOf(value.reason_requested)
                case None => CardReplacementReason.valueOf(CardReplacementReason.FIRST.toString)
              }
            }

            _<-Helper.booleanToFuture(s"${maximumLimitExceeded.replace("10000", "10")} Current issue_number is ${postJson.issue_number}", cc=callContext)(postJson.issue_number.length<= 10)

            (_, callContext)<- NewStyle.function.getBankAccount(bankId, AccountId(postJson.account_id), callContext)

            (_, callContext)<- NewStyle.function.getCustomerByCustomerId(postJson.customer_id, callContext)

            replacement = postJson.replacement match {
              case Some(replacement) =>
                Some(CardReplacementInfo(requestedDate = replacement.requested_date, cardReplacementReason))
              case None => None
            }
            collected = postJson.collected match {
              case Some(collected) => Some(CardCollectionInfo(collected))
              case None => None
            }
            posted = postJson.posted match {
              case Some(posted) => Option(CardPostedInfo(posted))
              case None => None
            }
            
            cvv = ThreadLocalRandom.current().nextLong(100, 999)
            
            (card, callContext) <- NewStyle.function.createPhysicalCard(
              bankCardNumber=postJson.card_number,
              nameOnCard=postJson.name_on_card,
              cardType = postJson.card_type,
              issueNumber=postJson.issue_number,
              serialNumber=postJson.serial_number,
              validFrom=postJson.valid_from_date,
              expires=postJson.expires_date,
              enabled=postJson.enabled,
              cancelled=false,
              onHotList=false,
              technology=postJson.technology,
              networks= postJson.networks,
              allows= postJson.allows,
              accountId= postJson.account_id,
              bankId=bankId.value,
              replacement = replacement,
              pinResets= postJson.pin_reset.map(e => PinResetInfo(e.requested_date, PinResetReason.valueOf(e.reason_requested.toUpperCase))),
              collected = collected,
              posted = posted,
              customerId = postJson.customer_id,
              cvv = cvv.toString,
              brand = postJson.brand,
              callContext
            )
          } yield { 
            //NOTE: OBP do not store the 3 digits cvv, only the hash, so we copy it here.
            (createPhysicalCardJson(card, u).copy(cvv=cvv.toString), HttpCode.`201`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      getViewsForBankAccount,
      implementedInApiVersion,
      nameOf(getViewsForBankAccount),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/views",
      "Get Views for Account",
      s"""#Views
         |
         |
         |Views in Open Bank Project provide a mechanism for fine grained access control and delegation to Accounts and Transactions. Account holders use the 'owner' view by default. Delegated access is made through other views for example 'accountants', 'share-holders' or 'tagging-application'. Views can be created via the API and each view has a list of entitlements.
         |
         |Views on accounts and transactions filter the underlying data to redact certain fields for certain users. For instance the balance on an account may be hidden from the public. The way to know what is possible on a view is determined in the following JSON.
         |
         |**Data:** When a view moderates a set of data, some fields my contain the value `null` rather than the original value. This indicates either that the user is not allowed to see the original data or the field is empty.
         |
         |There is currently one exception to this rule; the 'holder' field in the JSON contains always a value which is either an alias or the real name - indicated by the 'is_alias' field.
         |
         |**Action:** When a user performs an action like trying to post a comment (with POST API call), if he is not allowed, the body response will contain an error message.
         |
         |**Metadata:**
         |Transaction metadata (like images, tags, comments, etc.) will appears *ONLY* on the view where they have been created e.g. comments posted to the public view only appear on the public view.
         |
         |The other account metadata fields (like image_URL, more_info, etc.) are unique through all the views. Example, if a user edits the 'more_info' field in the 'team' view, then the view 'authorities' will show the new value (if it is allowed to do it).
         |
         |# All
         |*Optional*
         |
         |Returns the list of the views created for account ACCOUNT_ID at BANK_ID.
         |
         |${authenticationRequiredMessage(true)} and the user needs to have access to the owner view.""",
      EmptyBody,
      viewsJsonV500,
      List(
        $UserNotLoggedIn,
        $BankAccountNotFound,
        UnknownError
      ),
      List(apiTagView, apiTagAccount, apiTagNewStyle))

    lazy val getViewsForBankAccount : OBPEndpoint = {
      //get the available views on an bank account
      case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: "views" :: Nil JsonGet req => {
        cc =>
          val res =
            for {
              _ <- Helper.booleanToFuture(failMsg = UserNoOwnerView +"userId : " + cc.userId + ". account : " + accountId, cc=cc.callContext){
                cc.loggedInUser.hasOwnerViewAccess(BankIdAccountId(bankId, accountId))
              }
            } yield {
              for {
                views <- Full(Views.views.vend.availableViewsForAccount(BankIdAccountId(bankId, accountId)))
              } yield {
                (createViewsJsonV500(views), HttpCode.`200`(cc.callContext))
              }
            }
          res map { fullBoxOrException(_) } map { unboxFull(_) }
      }
    }

    staticResourceDocs += ResourceDoc(
      deleteSystemView,
      implementedInApiVersion,
      "deleteSystemView",
      "DELETE",
      "/system-views/VIEW_ID",
      "Delete System View",
      "Deletes the system view specified by VIEW_ID",
      EmptyBody,
      EmptyBody,
      List(
        UserNotLoggedIn,
        BankAccountNotFound,
        UnknownError,
        "user does not have owner access"
      ),
      List(apiTagSystemView, apiTagNewStyle),
      Some(List(canDeleteSystemView))
    )

    lazy val deleteSystemView: OBPEndpoint = {
      case "system-views" :: viewId :: Nil JsonDelete req => {
        cc =>
          for {
            _ <- NewStyle.function.systemView(ViewId(viewId), cc.callContext)
            view <- NewStyle.function.deleteSystemView(ViewId(viewId), cc.callContext)
          } yield {
            (Full(view),  HttpCode.`200`(cc.callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getMetricsAtBank,
      implementedInApiVersion,
      nameOf(getMetricsAtBank),
      "GET",
      "/management/metrics/banks/BANK_ID",
      "Get Metrics at Bank",
      s"""Get the all metrics at the Bank specified by BANK_ID
         |
         |require CanReadMetrics role
         |
         |Filters Part 1.*filtering* (no wilde cards etc.) parameters to GET /management/metrics
         |
         |Should be able to filter on the following metrics fields
         |
         |eg: /management/metrics?from_date=$DateWithMsExampleString&to_date=$DateWithMsExampleString&limit=50&offset=2
         |
         |1 from_date (defaults to one week before current date): eg:from_date=$DateWithMsExampleString
         |
         |2 to_date (defaults to current date) eg:to_date=$DateWithMsExampleString
         |
         |3 limit (for pagination: defaults to 50)  eg:limit=200
         |
         |4 offset (for pagination: zero index, defaults to 0) eg: offset=10
         |
         |5 sort_by (defaults to date field) eg: sort_by=date
         |  possible values:
         |    "url",
         |    "date",
         |    "user_name",
         |    "app_name",
         |    "developer_email",
         |    "implemented_by_partial_function",
         |    "implemented_in_version",
         |    "consumer_id",
         |    "verb"
         |
         |6 direction (defaults to date desc) eg: direction=desc
         |
         |eg: /management/metrics?from_date=$DateWithMsExampleString&to_date=$DateWithMsExampleString&limit=10000&offset=0&anon=false&app_name=TeatApp&implemented_in_version=v2.1.0&verb=POST&user_id=c7b6cb47-cb96-4441-8801-35b57456753a&user_name=susan.uk.29@example.com&consumer_id=78
         |
         |Other filters:
         |
         |7 consumer_id  (if null ignore)
         |
         |8 user_id (if null ignore)
         |
         |9 anon (if null ignore) only support two value : true (return where user_id is null.) or false (return where user_id is not null.)
         |
         |10 url (if null ignore), note: can not contain '&'.
         |
         |11 app_name (if null ignore)
         |
         |12 implemented_by_partial_function (if null ignore),
         |
         |13 implemented_in_version (if null ignore)
         |
         |14 verb (if null ignore)
         |
         |15 correlation_id (if null ignore)
         |
         |16 duration (if null ignore) non digit chars will be silently omitted
         |
      """.stripMargin,
      EmptyBody,
      metricsJson,
      List(
        $UserNotLoggedIn,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagMetric, apiTagApi, apiTagNewStyle),
      Some(List(canGetMetricsAtOneBank)))

    lazy val getMetricsAtBank : OBPEndpoint = {
      case "management" :: "metrics" :: "banks" :: bankId :: Nil JsonGet _ => {
        cc => {
          for {
            httpParams <- NewStyle.function.extractHttpParamsFromUrl(cc.url)
            (obpQueryParams, callContext) <- createQueriesByHttpParamsFuture(httpParams, cc.callContext)
            metrics <- Future(APIMetrics.apiMetrics.vend.getAllMetrics(obpQueryParams ::: List(OBPBankId(bankId))))
          } yield {
            (JSONFactory210.createMetricsJson(metrics), HttpCode.`200`(callContext))
          }
        }
      }
    }

    staticResourceDocs += ResourceDoc(
      getSystemView,
      implementedInApiVersion,
      "getSystemView",
      "GET",
      "/system-views/VIEW_ID",
      "Get System View",
      s"""Get System View
         |
         |${authenticationRequiredMessage(true)}
         |
      """.stripMargin,
      EmptyBody,
      viewJsonV500,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UnknownError
      ),
      List(apiTagSystemView, apiTagNewStyle),
      Some(List(canGetSystemView))
    )

    lazy val getSystemView: OBPEndpoint = {
      case "system-views" :: viewId :: Nil JsonGet _ => {
        cc =>
          for {
            view <- NewStyle.function.systemView(ViewId(viewId), cc.callContext)
          } yield {
            (createViewJsonV500(view), HttpCode.`200`(cc.callContext))
          }
      }
    }
    
    staticResourceDocs += ResourceDoc(
      getSystemViewsIds,
      implementedInApiVersion,
      nameOf(getSystemViewsIds),
      "GET",
      "/system-views-ids",
      "Get Ids of System Views",
      s"""Get Ids of System Views
         |
         |${authenticationRequiredMessage(true)}
         |
      """.stripMargin,
      EmptyBody,
      viewIdsJsonV500,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UnknownError
      ),
      List(apiTagSystemView, apiTagNewStyle),
      Some(List(canGetSystemView))
    )

    lazy val getSystemViewsIds: OBPEndpoint = {
      case "system-views-ids" :: Nil JsonGet _ => {
        cc =>
          for {
            views <- NewStyle.function.systemViews()
          } yield {
            (createViewsIdsJsonV500(views), HttpCode.`200`(cc.callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      createSystemView,
      implementedInApiVersion,
      nameOf(createSystemView),
      "POST",
      "/system-views",
      "Create System View",
      s"""Create a system view
         |
         | ${authenticationRequiredMessage(true)} and the user needs to have access to the $canCreateSystemView entitlement.
         | The 'alias' field in the JSON can take one of two values:
         |
         | * _public_: to use the public alias if there is one specified for the other account.
         | * _private_: to use the public alias if there is one specified for the other account.
         |
         | * _''(empty string)_: to use no alias; the view shows the real name of the other account.
         |
         | The 'hide_metadata_if_alias_used' field in the JSON can take boolean values. If it is set to `true` and there is an alias on the other account then the other accounts' metadata (like more_info, url, image_url, open_corporates_url, etc.) will be hidden. Otherwise the metadata will be shown.
         |
         | The 'allowed_actions' field is a list containing the name of the actions allowed on this view, all the actions contained will be set to `true` on the view creation, the rest will be set to `false`.
         | 
         | Please note that system views cannot be public. In case you try to set it you will get the error $SystemViewCannotBePublicError
         | """,
      createSystemViewJsonV500,
      viewJsonV500,
      List(
        $UserNotLoggedIn,
        InvalidJsonFormat,
        UnknownError
      ),
      List(apiTagSystemView, apiTagNewStyle),
      Some(List(canCreateSystemView))
    )

    lazy val createSystemView : OBPEndpoint = {
      //creates a system view
      case "system-views" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            createViewJson <- NewStyle.function.tryons(failMsg = s"$InvalidJsonFormat The Json body should be the $CreateViewJson ", 400, cc.callContext) {
              json.extract[CreateViewJsonV500]
            }
            _ <- Helper.booleanToFuture(SystemViewCannotBePublicError, failCode=400, cc=cc.callContext) {
              createViewJson.is_public == false
            }
            view <- NewStyle.function.createSystemView(createViewJson.toCreateViewJson, cc.callContext)
          } yield {
            (createViewJsonV500(view),  HttpCode.`201`(cc.callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      updateSystemView,
      implementedInApiVersion,
      nameOf(updateSystemView),
      "PUT",
      "/system-views/VIEW_ID",
      "Update System View",
      s"""Update an existing view on a bank account
         |
         |${authenticationRequiredMessage(true)} and the user needs to have access to the owner view.
         |
         |The json sent is the same as during view creation (above), with one difference: the 'name' field
         |of a view is not editable (it is only set when a view is created)""",
      updateSystemViewJson500,
      viewJsonV500,
      List(
        InvalidJsonFormat,
        $UserNotLoggedIn,
        BankAccountNotFound,
        UnknownError
      ),
      List(apiTagSystemView, apiTagNewStyle),
      Some(List(canUpdateSystemView))
    )

    lazy val updateSystemView : OBPEndpoint = {
      //updates a view on a bank account
      case "system-views" :: viewId :: Nil JsonPut json -> _ => {
        cc =>
          for {
            updateJson <- Future { tryo{json.extract[UpdateViewJsonV500]} } map {
              val msg = s"$InvalidJsonFormat The Json body should be the $UpdateViewJSON "
              x => unboxFullOrFail(x, cc.callContext, msg)
            }
            _ <- Helper.booleanToFuture(SystemViewCannotBePublicError, failCode=400, cc=cc.callContext) {
              updateJson.is_public == false
            }
            _ <- NewStyle.function.systemView(ViewId(viewId), cc.callContext)
            updatedView <- NewStyle.function.updateSystemView(ViewId(viewId), updateJson.toUpdateViewJson, cc.callContext)
          } yield {
            (JSONFactory310.createViewJSON(updatedView), HttpCode.`200`(cc.callContext))
          }
      }
    }
    
    staticResourceDocs += ResourceDoc(
      createCustomerAccountLink,
      implementedInApiVersion,
      nameOf(createCustomerAccountLink),
      "POST",
      "/banks/BANK_ID/customer-account-links",
      "Create Customer Account Link",
      s"""Link a Customer to a Account
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      createCustomerAccountLinkJson,
      customerAccountLinkJson,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        BankAccountNotFound,
        InvalidJsonFormat,
        CustomerNotFoundByCustomerId,
        UserHasMissingRoles,
        AccountAlreadyExistsForCustomer,
        CreateCustomerAccountLinkError,
        UnknownError
      ),
      List(apiTagCustomer, apiTagAccount),
      Some(List(canCreateCustomerAccountLink)))
    lazy val createCustomerAccountLink : OBPEndpoint = {
      case "banks" :: BankId(bankId):: "customer-account-links" :: Nil JsonPost json -> _ => {
        cc =>
          for {
            (_, _,callContext) <- SS.userBank
            
            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $CreateCustomerAccountLinkJson ", 400, callContext) {
              json.extract[CreateCustomerAccountLinkJson]
            }
            (customer, callContext) <- NewStyle.function.getCustomerByCustomerId(postedData.customer_id, callContext)
            _ <- booleanToFuture(s"Bank of the customer specified by the CUSTOMER_ID(${customer.bankId}) has to matches BANK_ID(${bankId.value}) in URL", 400, callContext) {
              customer.bankId == bankId.value
            }
            (_, callContext) <- NewStyle.function.getBankAccount(bankId, AccountId(postedData.account_id), callContext)
            _ <- booleanToFuture("Field customer_id is not defined in the posted json!", 400, callContext) {
              postedData.customer_id.nonEmpty
            }
            (customerAccountLinkExists, callContext) <- Connector.connector.vend.getCustomerAccountLink(postedData.customer_id, postedData.account_id, callContext)
            _ <- booleanToFuture(AccountAlreadyExistsForCustomer, 400, callContext) {
              customerAccountLinkExists.isEmpty
            }
            (customerAccountLink, callContext) <- NewStyle.function.createCustomerAccountLink(postedData.customer_id, postedData.bank_id, postedData.account_id, postedData.relationship_type, callContext)
          } yield {
            (JSONFactory500.createCustomerAccountLinkJson(customerAccountLink), HttpCode.`201`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getCustomerAccountLinksByCustomerId,
      implementedInApiVersion,
      nameOf(getCustomerAccountLinksByCustomerId),
      "GET",
      "/banks/BANK_ID/customers/CUSTOMER_ID/customer-account-links",
      "Get Customer Account Links by CUSTOMER_ID",
      s""" Get Customer Account Links by CUSTOMER_ID
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      customerAccountLinksJson,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        CustomerNotFoundByCustomerId,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagCustomer, apiTagNewStyle),
      Some(List(canGetCustomerAccountLinks)))
    lazy val getCustomerAccountLinksByCustomerId : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customers" :: customerId :: "customer-account-links" :: Nil JsonGet _ => {
        cc =>
          for {
            (customer, callContext) <- NewStyle.function.getCustomerByCustomerId(customerId, cc.callContext)
            _ <- booleanToFuture(s"Bank of the customer specified by the CUSTOMER_ID(${customer.bankId}) has to matches BANK_ID(${bankId.value}) in URL", 400, callContext) {
              customer.bankId == bankId.value
            }
            (customerAccountLinks, callContext) <-  NewStyle.function.getCustomerAccountLinksByCustomerId(customerId, callContext)
          } yield {
            (JSONFactory500.createCustomerAccountLinksJon(customerAccountLinks), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      getCustomerAccountLinksByBankIdAccountId,
      implementedInApiVersion,
      nameOf(getCustomerAccountLinksByBankIdAccountId),
      "GET",
      "/banks/BANK_ID/accounts/ACCOUNT_ID/customer-account-links",
      "Get Customer Account Links by ACCOUNT_ID",
      s""" Get Customer Account Links by ACCOUNT_ID
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      customerAccountLinksJson,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        BankAccountNotFound,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagCustomer, apiTagNewStyle),
      Some(List(canGetCustomerAccountLinks)))
    lazy val getCustomerAccountLinksByBankIdAccountId : OBPEndpoint = {
      case "banks" :: bankId :: "accounts" :: accountId :: "customer-account-links" :: Nil JsonGet _ => {
        cc =>
          for {
            (_, _,callContext) <- SS.userBank
            (customerAccountLinks, callContext) <-  NewStyle.function.getCustomerAccountLinksByBankIdAccountId(bankId, accountId, callContext)
          } yield {
            (JSONFactory500.createCustomerAccountLinksJon(customerAccountLinks), HttpCode.`200`(callContext))
          }
      }
    }
    
    staticResourceDocs += ResourceDoc(
      getCustomerAccountLinkById,
      implementedInApiVersion,
      nameOf(getCustomerAccountLinkById),
      "GET",
      "/banks/BANK_ID/customer-account-links/CUSTOMER_ACCOUNT_LINK_ID",
      "Get Customer Account Link by Id",
      s""" Get Customer Account Link by CUSTOMER_ACCOUNT_LINK_ID
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      customerAccountLinkJson,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagCustomer, apiTagNewStyle),
      Some(List(canGetCustomerAccountLink)))
    lazy val getCustomerAccountLinkById : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customer-account-links" :: customerAccountLinkId :: Nil JsonGet _ => {
        cc =>
          for {
            (_, _,callContext) <- SS.userBank
            (customerAccountLink, callContext) <-  NewStyle.function.getCustomerAccountLinkById(customerAccountLinkId, callContext)
          } yield {
            (JSONFactory500.createCustomerAccountLinkJson(customerAccountLink), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      updateCustomerAccountLinkById,
      implementedInApiVersion,
      nameOf(updateCustomerAccountLinkById),
      "PUT",
      "/banks/BANK_ID/customer-account-links/CUSTOMER_ACCOUNT_LINK_ID",
      "Update Customer Account Link by Id",
      s""" Update Customer Account Link by CUSTOMER_ACCOUNT_LINK_ID
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      updateCustomerAccountLinkJson,
      customerAccountLinkJson,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagCustomer, apiTagNewStyle),
      Some(List(canUpdateCustomerAccountLink)))
    lazy val updateCustomerAccountLinkById : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customer-account-links" :: customerAccountLinkId :: Nil JsonPut json -> _ => {
        cc =>
          for {
            (Full(u), _,callContext) <- SS.userBank
            postedData <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $UpdateCustomerAccountLinkJson ", 400, callContext) {
              json.extract[UpdateCustomerAccountLinkJson]
            }
            (_, callContext) <-  NewStyle.function.getCustomerAccountLinkById(customerAccountLinkId, callContext)

            (customerAccountLink, callContext) <-  NewStyle.function.updateCustomerAccountLinkById(customerAccountLinkId, postedData.relationship_type, callContext)
          } yield {
            (JSONFactory500.createCustomerAccountLinkJson(customerAccountLink), HttpCode.`200`(callContext))
          }
      }
    }
    
    staticResourceDocs += ResourceDoc(
      deleteCustomerAccountLinkById,
      implementedInApiVersion,
      nameOf(deleteCustomerAccountLinkById),
      "DELETE",
      "/banks/BANK_ID/customer-account-links/CUSTOMER_ACCOUNT_LINK_ID",
      "Delete Customer Account Link",
      s""" Delete Customer Account Link by CUSTOMER_ACCOUNT_LINK_ID
         |
         |${authenticationRequiredMessage(true)}
         |
         |""",
      EmptyBody,
      EmptyBody,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagCustomer, apiTagNewStyle),
      Some(List(canDeleteCustomerAccountLink)))
    lazy val deleteCustomerAccountLinkById : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "customer-account-links" :: customerAccountLinkId :: Nil JsonDelete _ => {
        cc =>
          for {
            (Full(u), _,callContext) <- SS.userBank
            (_, callContext) <-  NewStyle.function.getCustomerAccountLinkById(customerAccountLinkId, callContext)
            (deleted, callContext) <-  NewStyle.function.deleteCustomerAccountLinkById(customerAccountLinkId, callContext)
          } yield {
            (Full(deleted), HttpCode.`204`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getAdapterInfo,
      implementedInApiVersion,
      nameOf(getAdapterInfo),
      "GET",
      "/adapter",
      "Get Adapter Info",
      s"""Get basic information about the Adapter.
         |
         |${authenticationRequiredMessage(false)}
         |
      """.stripMargin,
      EmptyBody,
      adapterInfoJsonV500,
      List($UserNotLoggedIn, UserHasMissingRoles, UnknownError),
      List(apiTagApi, apiTagNewStyle),
      Some(List(canGetAdapterInfo))
    )
    lazy val getAdapterInfo: OBPEndpoint = {
      case "adapter" :: Nil JsonGet _ => {
        cc =>
          for {
            (_, callContext) <- SS.user
            (adapterInfo,_) <- NewStyle.function.getAdapterInfo(callContext)
          } yield {
            (JSONFactory500.createAdapterInfoJson(adapterInfo,cc.startTime.getOrElse(Helpers.now).getTime), HttpCode.`200`(callContext))
          }
      }
    }
  }
}

object APIMethods500 extends RestHelper with APIMethods500 {
  lazy val newStyleEndpoints: List[(String, String)] = Implementations5_0_0.resourceDocs.map {
    rd => (rd.partialFunctionName, rd.implementedInApiVersion.toString())
  }.toList
}

