package code.management
/**
 * Created by Stefan on 16.04.15.
 */

import code.api.{OBPRestHelper, APIFailure}
import code.api.util.APIUtil._
import code.model._
import code.model.dataAccess.Account
import code.util.Helper
import net.liftweb.common.{Box, Full, Loggable}
import net.liftweb.http._
import net.liftweb.http.js.JE.JsRaw
import net.liftweb.http.rest.RestHelper
import net.liftweb.util.Helpers._
import net.liftweb.util.Props

object AccountsAPI extends OBPRestHelper with Loggable {
  //needs to be a RestHelper to get access to JsonGet, JsonPost, etc.
  self: RestHelper =>

  val MODULE = "internal"
  val VERSION = "v1.0"
  val prefix = (MODULE / VERSION ).oPrefix(_)

  oauthServe(prefix {
    //deletes a bank account
    case "banks" :: BankId(bankId) :: "accounts" :: AccountId(accountId) :: Nil JsonDelete json => {
      user =>
        for {
          u <- user ?~ "user not found"
          account <- BankAccount(bankId, accountId) ?~ "Account not found"
        } yield {
          if(account.remove(u))
            successJsonResponse(JsRaw("{}"), 204)
          else
            errorJsonResponse("{'Error': 'could not delete Account'}", 500)
        }
    }
  })
}
