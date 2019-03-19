package code.management

import code.api.{OBPRestHelper}
import code.api.util.APIUtil._
import code.model._
import net.liftweb.common.{Full, Failure}
import net.liftweb.http.js.JE.JsRaw
import net.liftweb.http.rest.RestHelper
import code.util.Helper.MdcLoggable
/*
object AccountsAPI extends OBPRestHelper with MdcLoggable {
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  self: RestHelper =>

  val MODULE = "internal"
  val version = "v1.0"
  val versionStatus = "DEPRECIATED"
  val prefix = (MODULE / version ).oPrefix(_)

  oauthServe(prefix {
    //deletes a bank account
    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: Nil JsonDelete req => {
      cc =>
        for {
          u <- cc.user ?~ "user not found"
          account <- BankAccount(bankId, accountId) ?~ "Account not found"
        } yield {
          account.remove(u) match {
            case Full(_) => successJsonResponse(JsRaw("""{"success":"Success"}"""), 204)
            case Failure(x, _, _) => errorJsonResponse("{'Error': '"+ x + "'}", 500)
            case _ => errorJsonResponse("{'Error': 'could not delete Account'}", 500)
          }
        }
    }
  })
}
*/
