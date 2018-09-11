package code.accountholder



import code.api.util.APIUtil
import code.model._
import net.liftweb.util.{Props, SimpleInjector}
import code.remotedata.RemotedataAccountHolders
import net.liftweb.common.Box


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
  def getOrCreateAccountHolder(user: User, bankAccountUID :BankIdAccountId): Box[MapperAccountHolders] //There is no AccountHolder trait, database structure different with view
  def bulkDeleteAllAccountHolders(): Box[Boolean]
}

class RemotedataAccountHoldersCaseClasses {
  case class getAccountHolders(bankId: BankId, accountId: AccountId)
  case class getAccountsHeld(bankId: BankId, user: User)
  case class getOrCreateAccountHolder(user: User, bankAccountUID :BankIdAccountId)
  case class bulkDeleteAllAccountHolders()
}

object RemotedataAccountHoldersCaseClasses extends RemotedataAccountHoldersCaseClasses
