package code.api.v1_3_0

import net.liftweb.http.rest.RestHelper
import net.liftweb.http.{JsonResponse, Req}
import net.liftweb.common.{Failure, Box}
import code.model.User

trait APIMethods130 {
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  self: RestHelper =>

  val Implementations1_3_0 = new Object(){

    lazy val getCards : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "cards" :: Nil JsonGet _ => {
        user => {
          Failure("TODO")
        }
      }
    }

    def getCardsForBank : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: bankId :: "cards" :: Nil JsonGet _ => {
        user => {
          Failure("TODO")
        }
      }
    }

  }

}
