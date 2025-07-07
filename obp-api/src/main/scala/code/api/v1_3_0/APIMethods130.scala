package code.api.v1_3_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._
import code.api.util.APIUtil._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.FutureUtil.EndpointContext
import code.api.util.NewStyle.HttpCode
import code.api.util.{ApiRole, NewStyle}
import code.api.v1_2_1.JSONFactory
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.BankId
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.Full
import net.liftweb.http.rest.RestHelper

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

trait APIMethods130 {
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  self: RestHelper =>

  val Implementations1_3_0 = new Object(){

    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiVersion = ApiVersion.v1_3_0 // was String "1_3_0"


    resourceDocs += ResourceDoc(
      root,
      apiVersion,
      "root",
      "GET",
      "/root",
      "Get API Info (root)",
      """Returns information about:
        |
        |* API version
        |* Hosted by information
        |* Git Commit""",
      EmptyBody,
      apiInfoJSON,
      List(UnknownError, MandatoryPropertyIsNotSet),
      apiTagApi :: Nil)

    lazy val root : OBPEndpoint = {
      case (Nil | "root" :: Nil) JsonGet _ => {
        cc =>
          implicit val ec = EndpointContext(Some(cc))
          for {
            _ <- Future() // Just start async call
          } yield {
            (JSONFactory.getApiInfoJSON(OBPAPI1_3_0.version, OBPAPI1_3_0.versionStatus), HttpCode.`200`(cc.callContext))
          }
      }
    }
    
    resourceDocs += ResourceDoc(
      getCards,
      apiVersion,
      "getCards",
      "GET",
      "/cards",
      "Get cards for the current user",
      "Returns data about all the physical cards a user has been issued. These could be debit cards, credit cards, etc.",
      EmptyBody,
      physicalCardsJSON,
      List(UserNotLoggedIn, UnknownError),
      List(apiTagCard))

    lazy val getCards : OBPEndpoint = {
      case "cards" :: Nil JsonGet _ => {
        cc => {
          implicit val ec = EndpointContext(Some(cc))
            for {
              (Full(u), callContext) <- authenticatedAccess(cc)
              (cards,callContext) <- NewStyle.function.getPhysicalCardsForUser(u, callContext)
            } yield {
              (JSONFactory1_3_0.createPhysicalCardsJSON(cards, u), HttpCode.`200`(callContext))
            }
          }
      }
    }


    resourceDocs += ResourceDoc(
      getCardsForBank,
      apiVersion,
      "getCardsForBank",
      "GET",
      "/banks/BANK_ID/cards",
      "Get cards for the specified bank",
      "",
      EmptyBody,
      physicalCardsJSON,
      List(UserNotLoggedIn,BankNotFound, UnknownError),
      List(apiTagCard))

    lazy val getCardsForBank : OBPEndpoint = {
      case "banks" :: BankId(bankId) :: "cards" :: Nil JsonGet _ => {
        cc => {
          implicit val ec = EndpointContext(Some(cc))
          for {
            (Full(u), callContext) <- authenticatedAccess(cc)
            httpParams <- NewStyle.function.extractHttpParamsFromUrl(cc.url)
            (obpQueryParams, callContext) <- createQueriesByHttpParamsFuture(httpParams, callContext)
            _ <- NewStyle.function.hasEntitlement(bankId.value, u.userId, ApiRole.canGetCardsForBank, callContext)
            (bank, callContext) <- NewStyle.function.getBank(bankId, callContext)
            (cards, callContext) <- NewStyle.function.getPhysicalCardsForBank(bank, u, obpQueryParams, callContext)
          } yield {
            (JSONFactory1_3_0.createPhysicalCardsJSON(cards, u), HttpCode.`200`(callContext))
          }
        }
      }
    }
  }
}
