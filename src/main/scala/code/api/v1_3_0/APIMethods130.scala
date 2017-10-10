package code.api.v1_3_0

import code.api.util.{APIUtil, ErrorMessages}
import net.liftweb.http.rest.RestHelper
import net.liftweb.http.{JsonResponse, Req}
import net.liftweb.common.{Box, Failure, Full}
import code.model.{Bank, BankId, PhysicalCard, User}
import code.bankconnectors.Connector
import net.liftweb.json.Extraction
import APIUtil._
import scala.collection.mutable.ArrayBuffer
import scala.collection.immutable.Nil
import code.api.util.ErrorMessages._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON._

trait APIMethods130 {
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  self: RestHelper =>

  val Implementations1_3_0 = new Object(){

    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val emptyObjectJson = EmptyClassJson()
    val apiVersion : String = "1_3_0"


    resourceDocs += ResourceDoc(
      getCards,
      apiVersion,
      "getCards",
      "GET",
      "/cards",
      "Get cards for the current user",
      "Returns data about all the physical cards a user has been issued. These could be debit cards, credit cards, etc.",
      emptyObjectJson,
      physicalCardsJSON,
      List(UserNotLoggedIn, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCard, apiTagUser))

    lazy val getCards : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "cards" :: Nil JsonGet _ => {
        user => {
            for {
              u <- user ?~! ErrorMessages.UserNotLoggedIn 
              cards <- Connector.connector.vend.getPhysicalCards(u)
            } yield {
              val cardsJson = JSONFactory1_3_0.createPhysicalCardsJSON(cards, u)
              successJsonResponse(Extraction.decompose(cardsJson))
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
      emptyObjectJson,
      physicalCardsJSON,
      List(UserNotLoggedIn,BankNotFound, UnknownError),
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCard))


    lazy val getCardsForBank : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "cards" :: Nil JsonGet _ => {
        user => {
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            bank <- Bank(bankId) ?~! {ErrorMessages.BankNotFound}
            cards <- Connector.connector.vend.getPhysicalCardsForBank(bank, u)
          } yield {
             val cardsJson = JSONFactory1_3_0.createPhysicalCardsJSON(cards, u)
            successJsonResponse(Extraction.decompose(cardsJson))
          }
        }
      }
    }

  }

}
