package code.accountholders

import code.api.util.APIUtil
import code.remotedata.RemotedataAccountHolders
import com.openbankproject.commons.model.{AccountId, BankId, BankIdAccountId, User}
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector


object AccountHolders extends SimpleInjector {

  val accountHolders = new Inject(buildOne _) {}

  def buildOne: AccountHolders =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
      case false  => MapperAccountHolders
      case true => RemotedataAccountHolders     // We will use Akka as a middleware
    }

}

trait AccountHolders {

  def getAccountHolders(bankId: BankId, accountId: AccountId): Set[User]
  def getAccountsHeld(bankId: BankId, user: User): Set[BankIdAccountId]

  /**
   * if source == None, we return all accountHeld for the user.
   * if set source == Some(null) or Some(""), we only return the OBP created (source == null) accountHeld for the user.
   * if set source == Some("UserAuthContext"), we only return the user auth context created accountHeld for the user.
   * @param source
   * @return
   */
  def getAccountsHeldByUser(user: User, source: Option[String] = None): Set[BankIdAccountId]
  def getOrCreateAccountHolder(user: User, bankAccountUID :BankIdAccountId, source: Option[String] = None): Box[MapperAccountHolders] //There is no AccountHolder trait, database structure different with view
  def deleteAccountHolder(user: User, bankAccountUID :BankIdAccountId): Box[Boolean] 
  def bulkDeleteAllAccountHolders(): Box[Boolean]
}

class RemotedataAccountHoldersCaseClasses {
  case class getAccountHolders(bankId: BankId, accountId: AccountId)
  case class getAccountsHeld(bankId: BankId, user: User)
  case class getAccountsHeldByUser(user: User, source: Option[String] = None)
  case class getOrCreateAccountHolder(user: User, bankAccountUID :BankIdAccountId, source: Option[String] = None)
  case class bulkDeleteAllAccountHolders()
  case class deleteAccountHolder(user: User, bankAccountUID :BankIdAccountId)
}

object RemotedataAccountHoldersCaseClasses extends RemotedataAccountHoldersCaseClasses




