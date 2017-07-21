package code.accountholder



import code.model._
import net.liftweb.util.{Props, SimpleInjector}
import code.remotedata.{RemotedataAccountHolders}
import net.liftweb.common.Box


object AccountHolders extends SimpleInjector {

  val accountHolders = new Inject(buildOne _) {}

  def buildOne: AccountHolders =
    Props.getBool("use_akka", false) match {
      case false  => MapperAccountHolders
      case true => RemotedataAccountHolders     // We will use Akka as a middleware
    }

}

trait AccountHolders {
  def getAccountHolders(bankId: BankId, accountId: AccountId): Set[User]
  def createAccountHolder(userId: Long, bankId: String, accountId: String): Boolean
  def getOrCreateAccountHolder(user: User, bankAccountUID :BankIdAccountId): Box[MapperAccountHolders] //There is no AccountHolder trait, database structure different with view
  def bulkDeleteAllAccountHolders(): Box[Boolean]
}

class RemotedataAccountHoldersCaseClasses {
  case class createAccountHolder(userId: Long, bankId: String, accountId: String)
  case class getAccountHolders(bankId: BankId, accountId: AccountId)
  case class getOrCreateAccountHolder(user: User, bankAccountUID :BankIdAccountId)
  case class bulkDeleteAllAccountHolders()
}

object RemotedataAccountHoldersCaseClasses extends RemotedataAccountHoldersCaseClasses
