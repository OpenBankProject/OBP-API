package code.api.v1_4_0

import code.api.APIFailure
import code.customerinfo.CustomerInfo
import code.model.{BankId, User}
import net.liftweb.common.Box
import net.liftweb.http.{JsonResponse, Req}
import net.liftweb.http.rest.RestHelper
import code.api.util.APIUtil._
import net.liftweb.json.Extraction

trait APIMethods140 {
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  self: RestHelper =>


  val Implementations1_4_0 = new Object(){
    lazy val getCustomerInfo : PartialFunction[Req, Box[User] => Box[JsonResponse]] = {
      case "banks" :: BankId(bankId) :: "customer" :: Nil JsonGet _ => {
        user => {
          for {
            u <- user ?~! "User must be logged in to retrieve customer info"
            info <- CustomerInfo.customerInfoProvider.vend.getInfo(bankId, u) ~> APIFailure("No customer info found", 404)
          } yield {
            val json = JSONFactory1_4_0.createCustomerInfoJson(info)
            successJsonResponse(Extraction.decompose(json))
          }
        }
      }
    }
  }

}
