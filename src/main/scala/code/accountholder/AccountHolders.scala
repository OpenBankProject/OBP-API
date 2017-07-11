package code.accountholder



import code.consumer.ConsumersProvider
import code.model.{AccountId, BankId, MappedConsumersProvider, User}
import net.liftweb.util.{Props, SimpleInjector}
import code.remotedata.{RemotedataAccountHolders, RemotedataConsumers}
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
  def bulkDeleteAllAccountHolders(): Box[Boolean]
}

class RemotedataAccountHoldersCaseClasses {
  case class createAccountHolder(userId: Long, bankId: String, accountId: String)
  case class getAccountHolders(bankId: BankId, accountId: AccountId)
  case class bulkDeleteAllAccountHolders()
}

object RemotedataAccountHoldersCaseClasses extends RemotedataAccountHoldersCaseClasses
