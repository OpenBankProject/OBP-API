package code.payments

import code.api.APIFailure
import code.bankconnectors.Connector
import code.model.{User, BankAccount}
import net.liftweb.common.Box
import net.liftweb.json._

case class TransferMethod(
  permalink : String,
  description : String,
  body : JObject
)

object TransferMethods {

  /**
   * Gets transfer methods for @account visible to @user
   * @param account
   * @param user
   */
  def apply(account : BankAccount, user : User) : Box[Set[TransferMethod]] = {
    for {
      canSee <- user.canInitiateTransactions(account) ~> APIFailure(
        "You do not have permissions to view the transfer methods for this account", 401)
    } yield Connector.connector.vend.getTransferMethods(account.bankPermalink, account.permalink)
  }

}
