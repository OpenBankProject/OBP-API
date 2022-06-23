package code.api.v5_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil._
import code.api.util.ApiRole.{CanCreateUserAuthContextUpdate, canCreateUserAuthContext, canGetUserAuthContext}
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.{APIUtil, ApiRole, Consent, NewStyle}
import code.api.util.NewStyle.HttpCode
import code.api.v3_1_0.{ConsentJsonV310, PostConsentBodyCommonJson, PostConsentEmailJsonV310, PostConsentPhoneJsonV310, PostUserAuthContextJson, PostUserAuthContextUpdateJsonV310}
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
import net.liftweb.http.rest.RestHelper
import net.liftweb.json
import net.liftweb.json.compactRender
import net.liftweb.util.Props

import scala.collection.immutable.{List, Nil}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.util.Random

trait APIMethods500 {
  self: RestHelper =>

  val Implementations5_0_0 = new Implementations500()

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
            (userAuthContext, callContext) <- NewStyle.function.createUserAuthContext(user, postedData.key, postedData.value, callContext)
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
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            _ <- Helper.booleanToFuture(ConsentAllowedScaMethods, cc=callContext){
              List(StrongCustomerAuthentication.SMS.toString(), StrongCustomerAuthentication.EMAIL.toString()).exists(_ == scaMethod)
            }
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostUserAuthContextJson "
            postedData <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PostUserAuthContextJson]
            }
            (userAuthContextUpdate, callContext) <- NewStyle.function.validateUserAuthContextUpdateRequest(bankId.value, user.userId, postedData.key, postedData.value, scaMethod, callContext)
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
      "Answer Auth Context Update Challenge",
      s"""
         |Answer Auth Context Update Challenge.
         |""",
      postUserAuthContextUpdateJsonV310,
      userAuthContextUpdateJsonV500,
      List(
        UserNotLoggedIn,
        BankNotFound,
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
                    userAuthContextUpdate.key,
                    userAuthContextUpdate.value,
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
      "/my/consents/request",
      "Create Consent Request",
      s"""""",
      postConsentRequestJsonV310,
      PostConsentRequestResponseJson("9d429899-24f5-42c8-8565-943ffa6a7945"),
      List(InvalidJsonFormat, ConsentMaxTTL, UnknownError),
      apiTagConsent :: apiTagPSD2AIS :: apiTagPsd2 :: apiTagNewStyle :: Nil
      )
  
    lazy val createConsentRequest : OBPEndpoint = {
      case "my" :: "consents" :: "request" :: Nil JsonPost json -> _  =>  {
        cc =>
          for {
            (_, callContext) <- applicationAccess(cc)
            _ <- passesPsd2Aisp(callContext)
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostConsentBodyCommonJson "
            consentJson: PostConsentBodyCommonJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.extract[PostConsentBodyCommonJson]
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
            (PostConsentRequestResponseJson(createdConsentRequest.consentRequestId), HttpCode.`201`(callContext))
          }
      }
    }  

    staticResourceDocs += ResourceDoc(
      getConsentRequest,
      implementedInApiVersion,
      nameOf(getConsentRequest),
      "GET",
      "/my/consents/requests/CONSENT_REQUEST_ID",
      "Get Consent Request",
      s"""""",
      EmptyBody,
      PostConsentRequestResponseJson("9d429899-24f5-42c8-8565-943ffa6a7945"),
      List(ConsentRequestNotFound,UnknownError),
      apiTagConsent :: apiTagPSD2AIS :: apiTagPsd2 :: apiTagNewStyle :: Nil
      )

    lazy val getConsentRequest : OBPEndpoint = {
      case "my" :: "consents" :: "requests" :: consentRequestId ::  Nil  JsonGet _  =>  {
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
            (GetConsentRequestResponseJson(
              consent_request_id = createdConsentRequest.consentRequestId,
              payload = createdConsentRequest.payload,
              consumer_id = createdConsentRequest.consumerId
              ), 
              HttpCode.`201`(callContext)
            )
          }
      }
    }
  
    staticResourceDocs += ResourceDoc(
      getConsentByConsentRequestId,
      implementedInApiVersion,
      nameOf(getConsentByConsentRequestId),
      "GET",
      "/banks/BANK_ID/consents/consent-requests/CONSENT_REQUEST_ID",
      "Get Consent By Consent Request Id",
      s"""
         |
         |This endpoint gets the Consent By consent request id.
         |
         |${authenticationRequiredMessage(true)}
         |
      """.stripMargin,
      EmptyBody,
      consentJsonV310,
      List(
        $UserNotLoggedIn,
        $BankNotFound,
        UnknownError
        ),
      List(apiTagConsent, apiTagPSD2AIS, apiTagPsd2, apiTagNewStyle))
  
    lazy val getConsentByConsentRequestId: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "consents" :: "consent-requests" :: consentRequestId :: Nil JsonGet _ => {
        cc =>
          for {
            consent<- Future { Consents.consentProvider.vend.getConsentByConsentRequestId(consentRequestId).head}
          } yield {
            (
              ConsentJsonV310(
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
  
    resourceDocs += ResourceDoc(
      createConsentByConsentRequestIdEmail,
      implementedInApiVersion,
      nameOf(createConsentByConsentRequestIdEmail),
      "POST",
      "/banks/BANK_ID/consents/consent-requests/CONSENT_REQUEST_ID/EMAIL",
      "Create Consent By Request Id(EMAIL)",
      s"""
         |
         |This endpoint starts the process of creating a Consent by consent request id.
         |
         |""",
      EmptyBody,
      consentJsonV310,
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
  
    resourceDocs += ResourceDoc(
      createConsentByConsentRequestIdSms,
      implementedInApiVersion,
      nameOf(createConsentByConsentRequestIdSms),
      "POST",
      "/banks/BANK_ID/consents/consent-requests/CONSENT_REQUEST_ID/SMS",
      "Create Consent By Request Id (SMS)",
      s"""
         |
         |This endpoint starts the process of creating a Consent.
         |
         |""",
      EmptyBody,
      consentJsonV310,
      List(
        UserNotLoggedIn,
        BankNotFound,
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
      case "banks" :: BankId(bankId) :: "consents" :: "consent-requests":: consentRequestId :: scaMethod :: Nil JsonPost _ -> _  => {
        cc =>
          for {
            (Full(user), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            createdConsentRequest <- Future(ConsentRequests.consentRequestProvider.vend.getConsentRequestById(
              consentRequestId
              )) map {
              i => unboxFullOrFail(i,callContext, ConsentRequestNotFound)
            }
            _ <- Helper.booleanToFuture(ConsentAllowedScaMethods, cc=callContext){
              List(StrongCustomerAuthentication.SMS.toString(), StrongCustomerAuthentication.EMAIL.toString()).exists(_ == scaMethod)
            }
            failMsg = s"$InvalidJsonFormat The Json body should be the $PostConsentBodyCommonJson "
            consentJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
              json.parse(createdConsentRequest.payload).extract[PostConsentBodyCommonJson].copy(consent_request_id = Some(createdConsentRequest.consentRequestId))
            }
            maxTimeToLive = APIUtil.getPropsAsIntValue(nameOfProperty="consents.max_time_to_live", defaultValue=3600)
            _ <- Helper.booleanToFuture(s"$ConsentMaxTTL ($maxTimeToLive)", cc=callContext){
              consentJson.time_to_live match {
                case Some(ttl) => ttl <= maxTimeToLive
                case _ => true
              }
            }
            requestedEntitlements = consentJson.entitlements
            myEntitlements <- Entitlement.entitlement.vend.getEntitlementsByUserIdFuture(user.userId)
            _ <- Helper.booleanToFuture(RolesAllowedInConsent, cc=callContext){
              requestedEntitlements.forall(
                re => myEntitlements.getOrElse(Nil).exists(
                  e => e.roleName == re.role_name && e.bankId == re.bank_id
                  )
                )
            }
            requestedViews = consentJson.views
            (_, assignedViews) <- Future(Views.views.vend.privateViewsUserCanAccess(user))
            _ <- Helper.booleanToFuture(ViewsAllowedInConsent, cc=callContext){
              requestedViews.forall(
                rv => assignedViews.exists{
                  e =>
                    e.view_id == rv.view_id &&
                      e.bank_id == rv.bank_id &&
                      e.account_id == rv.account_id
                }
                )
            }
            (consumerId, applicationText) <- consentJson.consumer_id match {
              case Some(id) => NewStyle.function.checkConsumerByConsumerId(id, callContext) map {
                c => (Some(c.consumerId.get), c.description)
              }
              case None => Future(None, "Any application")
            }
          
            _ <- Helper.booleanToFuture(ConsentRequestNotFound, cc=callContext){
              consentJson.consent_request_id match {
                case Some(id) => //If it is existing in Json, we need to check it from database.
                  ConsentRequests.consentRequestProvider.vend.getConsentRequestById(id).isDefined
                case None => //If it is not, just pass
                  true
              }
            }
          
            challengeAnswer = Props.mode match {
              case Props.RunModes.Test => Consent.challengeAnswerAtTestEnvironment
              case _ => Random.nextInt(99999999).toString()
            }
            createdConsent <- Future(Consents.consentProvider.vend.createObpConsent(user, challengeAnswer, consentJson.consent_request_id)) map {
              i => connectorEmptyResponse(i, callContext)
            }
            consentJWT =
            Consent.createConsentJWT(
              user,
              consentJson,
              createdConsent.secret,
              createdConsent.consentId,
              consumerId,
              consentJson.valid_from,
              consentJson.time_to_live.getOrElse(3600)
              )
            _ <- Future(Consents.consentProvider.vend.setJsonWebToken(createdConsent.consentId, consentJWT)) map {
              i => connectorEmptyResponse(i, callContext)
            }
            challengeText = s"Your consent challenge : ${challengeAnswer}, Application: $applicationText"
            _ <- scaMethod match {
              case v if v == StrongCustomerAuthentication.EMAIL.toString => // Send the email
                for{
                  failMsg <- Future {s"$InvalidJsonFormat The Json body should be the $PostConsentEmailJsonV310"}
                  postConsentEmailJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
                    json.parse(createdConsentRequest.payload).extract[PostConsentEmailJsonV310]
                  }
                  (Full(status), callContext) <- Connector.connector.vend.sendCustomerNotification(
                    StrongCustomerAuthentication.EMAIL,
                    postConsentEmailJson.email,
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
                  postConsentPhoneJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
                    json.parse(createdConsentRequest.payload).extract[PostConsentPhoneJsonV310]
                  }
                  phoneNumber = postConsentPhoneJson.phone_number
                  (Full(status), callContext) <- Connector.connector.vend.sendCustomerNotification(
                    StrongCustomerAuthentication.SMS,
                    phoneNumber,
                    None,
                    challengeText,
                    callContext
                    )
                } yield Future{status}
              case _ =>Future{"Success"}
            }
          } yield {
            (ConsentJsonV310(createdConsent.consentId, consentJWT, createdConsent.status, Some(createdConsent.consentRequestId)), HttpCode.`201`(callContext))
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

