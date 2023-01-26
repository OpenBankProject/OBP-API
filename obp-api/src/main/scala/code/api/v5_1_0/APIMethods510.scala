package code.api.v5_1_0


import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.{apiCollectionJson400, apiCollectionsJson400, apiInfoJson400, postApiCollectionJson400, revokedConsentJsonV310}
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil._
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages.{$UserNotLoggedIn, BankNotFound, ConsentNotFound, InvalidJsonFormat, UnknownError, UserNotFoundByUserId, UserNotLoggedIn, _}
import code.api.util.{ApiRole, NewStyle}
import code.api.util.NewStyle.HttpCode
import code.api.v3_1_0.ConsentJsonV310
import code.api.v3_1_0.JSONFactory310.createBadLoginStatusJson
import code.api.v4_0_0.{JSONFactory400, PostApiCollectionJson400}
import code.consent.Consents
import code.loginattempts.LoginAttempt
import code.transactionrequests.TransactionRequests.TransactionRequestTypes.{apply => _}
import code.userlocks.UserLocksProvider
import code.users.Users
import code.util.Helper
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.BankId
import com.openbankproject.commons.util.{ApiVersion, ScannedApiVersion}
import net.liftweb.common.Full
import net.liftweb.http.rest.RestHelper

import scala.collection.immutable.{List, Nil}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future


trait APIMethods510 {
  self: RestHelper =>

  val Implementations5_1_0 = new Implementations510()

  class Implementations510 {

    val implementedInApiVersion: ScannedApiVersion = ApiVersion.v5_1_0

    private val staticResourceDocs = ArrayBuffer[ResourceDoc]()
    def resourceDocs = staticResourceDocs 

    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(staticResourceDocs, apiRelations)



    staticResourceDocs += ResourceDoc(
      root,
      implementedInApiVersion,
      "root",
      "GET",
      "/root",
      "Get API Info (root)",
      """Returns information about:
        |
        |* API version
        |* Hosted by information
        |* Hosted at information
        |* Energy source information
        |* Git Commit""",
      EmptyBody,
      apiInfoJson400,
      List(UnknownError, "no connector set"),
      apiTagApi :: apiTagNewStyle :: Nil)

    lazy val root : OBPEndpoint = {
      case (Nil | "root" :: Nil) JsonGet _ => {
        cc => Future {
          JSONFactory510.getApiInfoJSON(implementedInApiVersion) -> HttpCode.`200`(cc.callContext)
        }
      }
    }
    
    staticResourceDocs += ResourceDoc(
      getAllApiCollections,
      implementedInApiVersion,
      nameOf(getAllApiCollections),
      "GET",
      "/management/api-collections",
      "Get All API Collections",
      s"""Get All API Collections.
         |
         |${authenticationRequiredMessage(true)}
         |""".stripMargin,
      EmptyBody,
      apiCollectionsJson400,
      List(
        UserHasMissingRoles,
        UnknownError
      ),
      List(apiTagApiCollection, apiTagNewStyle),
      Some(canGetAllApiCollections :: Nil)
    )

    lazy val getAllApiCollections: OBPEndpoint = {
      case "management" :: "api-collections" :: Nil JsonGet _ => {
        cc =>
          for {
            (apiCollections, callContext) <- NewStyle.function.getAllApiCollections(cc.callContext)
          } yield {
            (JSONFactory400.createApiCollectionsJsonV400(apiCollections), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      revokeConsentAtBank,
      implementedInApiVersion,
      nameOf(revokeConsentAtBank),
      "GET",
      "/banks/BANK_ID/consents/CONSENT_ID/revoke",
      "Revoke Consent at Bank",
      s"""
         |Revoke Consent specified by CONSENT_ID
         |
         |There are a few reasons you might need to revoke an application’s access to a user’s account:
         |  - The user explicitly wishes to revoke the application’s access
         |  - You as the service provider have determined an application is compromised or malicious, and want to disable it
         |  - etc.
         ||
         |OBP as a resource server stores access tokens in a database, then it is relatively easy to revoke some token that belongs to a particular user.
         |The status of the token is changed to "REVOKED" so the next time the revoked client makes a request, their token will fail to validate.
         |
         |${authenticationRequiredMessage(true)}
         |
      """.stripMargin,
      EmptyBody,
      revokedConsentJsonV310,
      List(
        UserNotLoggedIn,
        BankNotFound,
        UnknownError
      ),
      List(apiTagConsent, apiTagPSD2AIS, apiTagPsd2, apiTagNewStyle), 
      Some(List(canRevokeConsentAtBank))
    )

    lazy val revokeConsentAtBank: OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "consents" :: consentId :: "revoke" :: Nil JsonGet _ => {
        cc =>
          for {
            (Full(user), callContext) <- authenticatedAccess(cc)
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
            consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId)) map {
              unboxFullOrFail(_, callContext, ConsentNotFound)
            }
            _ <- Helper.booleanToFuture(failMsg = ConsentNotFound, cc=callContext) {
              consent.mUserId == user.userId
            }
            consent <- Future(Consents.consentProvider.vend.revoke(consentId)) map {
              i => connectorEmptyResponse(i, callContext)
            }
          } yield {
            (ConsentJsonV310(consent.consentId, consent.jsonWebToken, consent.status), HttpCode.`200`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      updateMyApiCollection,
      implementedInApiVersion,
      nameOf(updateMyApiCollection),
      "PUT",
      "/my/api-collections/API_COLLECTION_ID",
      "Update My Api Collection By API_COLLECTION_ID",
      s"""Update Api Collection for logged in user.
         |
         |${authenticationRequiredMessage(true)}
         |""".stripMargin,
      postApiCollectionJson400,
      apiCollectionJson400,
      List(
        $UserNotLoggedIn,
        InvalidJsonFormat,
        UserNotFoundByUserId,
        UnknownError
      ),
      List(apiTagApiCollection, apiTagNewStyle)
    )

    lazy val updateMyApiCollection: OBPEndpoint = {
      case "my" :: "api-collections" :: apiCollectionId :: Nil JsonPut json -> _ => {
        cc =>
          for {
            putJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $PostApiCollectionJson400", 400, cc.callContext) {
              json.extract[PostApiCollectionJson400]
            }
            (_, callContext) <- NewStyle.function.getApiCollectionById(apiCollectionId, cc.callContext)
            (apiCollection, callContext) <- NewStyle.function.updateApiCollection(
              apiCollectionId,
              putJson.api_collection_name,
              putJson.is_sharable,
              putJson.description.getOrElse(""),
              callContext
            )
          } yield {
            (JSONFactory400.createApiCollectionJsonV400(apiCollection), HttpCode.`200`(callContext))
          }
      }
    }


    staticResourceDocs += ResourceDoc(
      getUserByUsername,
      implementedInApiVersion,
      nameOf(getUserByUsername),
      "GET",
      "/users/provider/PROVIDER/username/USERNAME",
      "Get User by USERNAME",
      s"""Get user by PROVIDER and USERNAME
         |
         |${authenticationRequiredMessage(true)}
         |
         |CanGetAnyUser entitlement is required,
         |
      """.stripMargin,
      EmptyBody,
      userJsonV400,
      List($UserNotLoggedIn, UserHasMissingRoles, UserNotFoundByProviderAndUsername, UnknownError),
      List(apiTagUser, apiTagNewStyle),
      Some(List(canGetAnyUser))
    )
    
    lazy val getUserByUsername: OBPEndpoint = {
      case "users" :: "provider" :: provider :: "username" :: username :: Nil JsonGet _ => {
        cc =>
          for {
            user <- Users.users.vend.getUserByProviderAndUsernameFuture(provider, username) map {
              x => unboxFullOrFail(x, cc.callContext, UserNotFoundByProviderAndUsername, 404)
            }
            entitlements <- NewStyle.function.getEntitlementsByUserId(user.userId, cc.callContext)
            isLocked = LoginAttempt.userIsLocked(user.provider, user.name)
          } yield {
            (JSONFactory400.createUserInfoJSON(user, entitlements, None, isLocked), HttpCode.`200`(cc.callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      getBadLoginStatus,
      implementedInApiVersion,
      nameOf(getBadLoginStatus),
      "GET",
      "/users/PROVIDER/USERNAME/lock-status",
      "Get User Lock Status",
      s"""
         |Get User Login Status.
         |${authenticationRequiredMessage(true)}
         |
         |""".stripMargin,
      EmptyBody,
      badLoginStatusJson,
      List(UserNotLoggedIn, UserNotFoundByProviderAndUsername, UserHasMissingRoles, UnknownError),
      List(apiTagUser, apiTagNewStyle),
      Some(List(canReadUserLockedStatus))
    )
    lazy val getBadLoginStatus: OBPEndpoint = {
      //get private accounts for all banks
      case "users" ::provider :: username :: "lock-status" :: Nil JsonGet req => {
        cc =>
          for {
            (Full(u), callContext) <- SS.user
            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canReadUserLockedStatus, callContext)
            badLoginStatus <- Future {
              LoginAttempt.getBadLoginStatus(provider, username)
            } map {
              unboxFullOrFail(_, callContext, s"$UserNotFoundByProviderAndUsername provider($provider), username($username)", 404)
            }
          } yield {
            (createBadLoginStatusJson(badLoginStatus), HttpCode.`200`(callContext))
          }
      }
    }

    resourceDocs += ResourceDoc(
      unlockUser,
      implementedInApiVersion,
      nameOf(unlockUser),
      "PUT",
      "/users/PROVIDER/USERNAME/lock-status",
      "Unlock the user",
      s"""
         |Unlock a User.
         |
         |(Perhaps the user was locked due to multiple failed login attempts)
         |
         |${authenticationRequiredMessage(true)}
         |
         |""".stripMargin,
      EmptyBody,
      badLoginStatusJson,
      List(UserNotLoggedIn, UserNotFoundByProviderAndUsername, UserHasMissingRoles, UnknownError),
      List(apiTagUser, apiTagNewStyle),
      Some(List(canUnlockUser)))
    lazy val unlockUser: OBPEndpoint = {
      //get private accounts for all banks
      case "users" ::  provider :: username :: "lock-status" :: Nil JsonPut req => {
        cc =>
          for {
            (Full(u), callContext) <- SS.user
            _ <- NewStyle.function.hasEntitlement("", u.userId, ApiRole.canUnlockUser, callContext)
            _ <- Future {
              LoginAttempt.resetBadLoginAttempts(provider, username)
            }
            _ <- Future {
              UserLocksProvider.unlockUser(provider, username)
            }
            badLoginStatus <- Future {
              LoginAttempt.getBadLoginStatus(provider, username)
            } map {
              unboxFullOrFail(_, callContext, s"$UserNotFoundByProviderAndUsername provider($provider), username($username)", 404)
            }
          } yield {
            (createBadLoginStatusJson(badLoginStatus), HttpCode.`200`(callContext))
          }
      }
    }

    staticResourceDocs += ResourceDoc(
      lockUser,
      implementedInApiVersion,
      nameOf(lockUser),
      "POST",
      "/users/PROVIDER/USERNAME/locks",
      "Lock the user",
      s"""
         |Lock a User.
         |
         |${authenticationRequiredMessage(true)}
         |
         |""".stripMargin,
      EmptyBody,
      userLockStatusJson,
      List($UserNotLoggedIn, UserNotFoundByProviderAndUsername, UserHasMissingRoles, UnknownError),
      List(apiTagUser, apiTagNewStyle),
      Some(List(canLockUser)))
    lazy val lockUser: OBPEndpoint = {
      case "users" :: provider :: username :: "locks" :: Nil JsonPost req => {
        cc =>
          for {
            (Full(u), callContext) <- SS.user
            userLocks <- Future {
              UserLocksProvider.lockUser(provider, username)
            } map {
              unboxFullOrFail(_, callContext, s"$UserNotFoundByProviderAndUsername provider($provider), username($username)", 404)
            }
          } yield {
            (JSONFactory400.createUserLockStatusJson(userLocks), HttpCode.`200`(callContext))
          }
      }
    }

  }
}

object APIMethods510 extends RestHelper with APIMethods510 {
  lazy val newStyleEndpoints: List[(String, String)] = Implementations5_1_0.resourceDocs.map {
    rd => (rd.partialFunctionName, rd.implementedInApiVersion.toString())
  }.toList
}

