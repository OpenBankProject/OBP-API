package code.api.v5_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil._
import code.api.util.ApiRole.{CanCreateUserAuthContextUpdate, canCreateUserAuthContext, canGetUserAuthContext}
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.{ApiRole, NewStyle}
import code.api.util.NewStyle.HttpCode
import code.api.v3_1_0.{PostUserAuthContextJson, PostUserAuthContextUpdateJsonV310}
import code.transactionrequests.TransactionRequests.TransactionRequestTypes.{apply => _}
import code.util.Helper
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{BankId, UserAuthContextUpdateStatus}
import com.openbankproject.commons.model.enums.StrongCustomerAuthentication
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.{Full}
import net.liftweb.http.{Req}
import net.liftweb.http.rest.RestHelper

import scala.collection.immutable.{List, Nil}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

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
      emptyObjectJson,
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

  }
}

object APIMethods500 extends RestHelper with APIMethods500 {
  lazy val newStyleEndpoints: List[(String, String)] = Implementations5_0_0.resourceDocs.map {
    rd => (rd.partialFunctionName, rd.implementedInApiVersion.toString())
  }.toList
}

