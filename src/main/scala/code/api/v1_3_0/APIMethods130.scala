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
import code.api.ResourceDocs1_4_0.SwaggerJSONFactory._
import code.api.util.ErrorMessages._

trait APIMethods130 {
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  self: RestHelper =>

  val Implementations1_3_0 = new Object(){

    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val emptyObjectJson  = null
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
      emptyObjectJson,
      UserNotLoggedIn :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCustomer))

    lazy val getCards : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "cards" :: Nil JsonGet _ => {
        user => {
            for {
              u <- user ?~! ErrorMessages.UserNotLoggedIn
            } yield {
              val cardsJson = user match {
                case Full(u) => {
                  val cards: List[PhysicalCard] = Connector.connector.vend.getPhysicalCards(u)
                  JSONFactory1_3_0.createPhysicalCardsJSON(cards, u)
                }
                case _ => PhysicalCardsJSON(Nil)
              }

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
      emptyObjectJson,
      UserNotLoggedIn :: Nil,
      Catalogs(notCore, notPSD2, notOBWG),
      List(apiTagCustomer))


    lazy val getCardsForBank : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "cards" :: Nil JsonGet _ => {
        user => {
          for {
            u <- user ?~! ErrorMessages.UserNotLoggedIn
            bank <- Bank(bankId) ?~! {ErrorMessages.BankNotFound}
          } yield {
            val cardsJson = user match {
              case Full(u) => {
                val cards = Connector.connector.vend.getPhysicalCardsForBank(bank, u)
                JSONFactory1_3_0.createPhysicalCardsJSON(cards, u)
              }
              case _ => PhysicalCardsJSON(Nil)
            }

            successJsonResponse(Extraction.decompose(cardsJson))
          }
        }
      }
    }

  }

}
