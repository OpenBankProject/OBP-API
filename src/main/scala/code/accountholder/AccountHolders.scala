package code.accountholder



import code.model.{AccountId, BankId, User}
import net.liftweb.util.SimpleInjector
import code.remotedata.RemotedataAccountHolders
import net.liftweb.common.Box


object AccountHolders extends SimpleInjector {

  val accountHolders = new Inject(buildOne _) {}

  //def buildOne: AccountHolders = MapperAccountHolders
  def buildOne: AccountHolders = RemotedataAccountHolders

}

trait AccountHolders {
  def getAccountHolders(bankId: BankId, accountId: AccountId): Set[User]
  def createAccountHolder(userId: Long, bankId: String, accountId: String, source: String = "MappedAccountHolder"): Boolean
  def bulkDeleteAllAccountHolders(): Box[Boolean]
}

class RemotedataAccountHoldersCaseClasses {
  case class createAccountHolder(userId: Long, bankId: String, accountId: String, source: String = "MappedAccountHolder")
  case class getAccountHolders(bankId: BankId, accountId: AccountId)
  case class bulkDeleteAllAccountHolders()
}

object RemotedataAccountHoldersCaseClasses extends RemotedataAccountHoldersCaseClasses
