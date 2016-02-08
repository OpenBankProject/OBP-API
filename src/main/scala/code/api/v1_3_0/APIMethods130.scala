package code.api.v1_3_0

import code.api.util.APIUtil
import net.liftweb.http.rest.RestHelper
import net.liftweb.http.{JsonResponse, Req}
import net.liftweb.common.{Full, Failure, Box}
import code.model.{BankId, PhysicalCard, User}
import code.bankconnectors.Connector
import net.liftweb.json.Extraction
import APIUtil._
import net.liftweb.json.JsonAST.JValue

import scala.collection.mutable.ArrayBuffer

import scala.collection.immutable.Nil

// Makes JValue assignment to Nil work
import net.liftweb.json.JsonDSL._


trait APIMethods130 {
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  self: RestHelper =>

  val Implementations1_3_0 = new Object(){

    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val emptyObjectJson : JValue = Nil
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
      emptyObjectJson :: Nil,
      true,
      true,
      Nil)

    lazy val getCards : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "cards" :: Nil JsonGet _ => {
        user => {
          val cardsJson = user match {
            case Full(u) => {
              val cards = Connector.connector.vend.getPhysicalCards(u)
              JSONFactory1_3_0.createPhysicalCardsJSON(cards, u)
            }
            case _ => PhysicalCardsJSON(Nil)
          }

          Full(successJsonResponse(Extraction.decompose(cardsJson)))
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
      emptyObjectJson :: Nil,
      false,
      false,
      Nil)


    def getCardsForBank : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "cards" :: Nil JsonGet _ => {
        user => {
          val cardsJson = user match {
            case Full(u) => {
              val cards = Connector.connector.vend.getPhysicalCardsForBank(bankId, u)
              JSONFactory1_3_0.createPhysicalCardsJSON(cards, u)
            }
            case _ => PhysicalCardsJSON(Nil)
          }

          Full(successJsonResponse(Extraction.decompose(cardsJson)))
        }
      }
    }

  }

}
