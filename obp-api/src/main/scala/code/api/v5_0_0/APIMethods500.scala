package code.api.v5_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil._
import code.api.util.ApiRole.{CanCreateUserAuthContextUpdate, canCreateUserAuthContext, canGetCustomers, canGetCustomersMinimal, canGetUserAuthContext}
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.{APIUtil, ApiRole, Consent, NewStyle}
import code.api.util.NewStyle.HttpCode
import code.api.util.NewStyle.function.extractQueryParams
import code.api.v2_1_0.JSONFactory210
import code.api.v3_0_0.JSONFactory300
import code.api.v3_1_0.{PostConsentBodyCommonJson, PostConsentEmailJsonV310, PostConsentEntitlementJsonV310, PostConsentPhoneJsonV310, PostConsentViewJsonV310, PostUserAuthContextJson, PostUserAuthContextUpdateJsonV310}
import code.api.v4_0_0.JSONFactory400.createCustomersMinimalJson
import code.bankconnectors.Connector
import code.consent.{ConsentRequests, Consents}
import code.entitlement.Entitlement
import code.transactionrequests.TransactionRequests.TransactionRequestTypes.{apply => _}
import code.util.Helper
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{BankId, UserAuthContextUpdateStatus}
import com.openbankproject.commons.model.enums.StrongCustomerAuthentication
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.Full
import net.liftweb.http.Req
import net.liftweb.http.rest.RestHelper
import net.liftweb.json
import net.liftweb.json.{compactRender}
import net.liftweb.util.Props

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
      s"""""",
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
      "Create Consent By Request Id(EMAIL)",
      s"""
         |
         |This endpoint starts the process of creating a Consent by consent request id.
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
      "Create Consent By Request Id (SMS)",
      s"""
         |
         |This endpoint starts the process of creating a Consent.
         |
         |""",
      EmptyBody,
      consentJsonV500,
      List(
        UserNotLoggedIn,
        $BankNotFound,
        InvalidJsonFormat,
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
            _ <- Helper.booleanToFuture(ConsentRequestAlreadyUsed, cc=callContext){
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
            (consumerId, applicationText) <- consentRequestJson.consumer_id match {
              case Some(id) => NewStyle.function.checkConsumerByConsumerId(id, callContext) map {
                c => (Some(c.consumerId.get), c.description)
              }
              case None => Future(None, "Any application")
            }
  
            challengeAnswer = Props.mode match {
              case Props.RunModes.Test => Consent.challengeAnswerAtTestEnvironment
              case _ => Random.nextInt(99999999).toString()
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
      getMyCustomersAtAnyBank,
      implementedInApiVersion,
      nameOf(getMyCustomersAtAnyBank),
      "GET",
      "/my/customers",
      "Get My Customers",
      """Gets all Customers that are linked to me.
        |
        |Authentication via OAuth is required.""",
      emptyObjectJson,
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
      emptyObjectJson,
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
      emptyObjectJson,
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
            requestParams <- extractQueryParams(cc.url, List("limit","offset","sort_direction"), cc.callContext)
            customers <- NewStyle.function.getCustomers(bankId, cc.callContext, requestParams)
          } yield {
            (JSONFactory300.createCustomersJson(customers.sortBy(_.bankId)), HttpCode.`200`(cc.callContext))
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
      emptyObjectJson,
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
            requestParams <- extractQueryParams(cc.url, List("limit","offset","sort_direction"), cc.callContext)
            customers <- NewStyle.function.getCustomers(bankId, cc.callContext, requestParams)
          } yield {
            (createCustomersMinimalJson(customers.sortBy(_.bankId)), HttpCode.`200`(cc.callContext))
          }
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

