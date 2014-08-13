package code.api.v1_3_0

import net.liftweb.http.rest.RestHelper
import net.liftweb.http.{JsonResponse, Req}
import net.liftweb.common.{Full, Failure, Box}
import code.model.{PhysicalCard, User}
import code.bankconnectors.Connector
import net.liftweb.json.Extraction
import code.util.APIUtil._

trait APIMethods130 {
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  self: RestHelper =>

  val Implementations1_3_0 = new Object(){

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

    def getCardsForBank : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: bankId :: "cards" :: Nil JsonGet _ => {
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
