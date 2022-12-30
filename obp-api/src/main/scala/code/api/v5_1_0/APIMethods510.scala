package code.api.v5_1_0


import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.{apiCollectionJson400, apiCollectionsJson400, postApiCollectionJson400, revokedConsentJsonV310}
import code.api.util.APIUtil._
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages.{$UserNotLoggedIn, BankNotFound, ConsentNotFound, InvalidJsonFormat, UnknownError, UserNotFoundByUserId, UserNotLoggedIn, _}
import code.api.util.NewStyle
import code.api.util.NewStyle.HttpCode
import code.api.v3_1_0.ConsentJsonV310
import code.api.v4_0_0.{JSONFactory400, PostApiCollectionJson400}
import code.consent.Consents
import code.transactionrequests.TransactionRequests.TransactionRequestTypes.{apply => _}
import code.util.Helper
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.BankId
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.Full
import net.liftweb.http.rest.RestHelper

import scala.collection.immutable.{List, Nil}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future


trait APIMethods510 {
  self: RestHelper =>

  val Implementations5_1_0 = new Implementations510()

  class Implementations510 {

    val implementedInApiVersion = ApiVersion.v5_1_0

    private val staticResourceDocs = ArrayBuffer[ResourceDoc]()
    def resourceDocs = staticResourceDocs 

    val apiRelations = ArrayBuffer[ApiRelation]()
    val codeContext = CodeContext(staticResourceDocs, apiRelations)


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




  }
}

object APIMethods510 extends RestHelper with APIMethods510 {
  lazy val newStyleEndpoints: List[(String, String)] = Implementations5_1_0.resourceDocs.map {
    rd => (rd.partialFunctionName, rd.implementedInApiVersion.toString())
  }.toList
}

