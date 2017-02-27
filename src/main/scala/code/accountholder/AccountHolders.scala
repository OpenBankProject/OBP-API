package code.accountholder



import code.model.{AccountId, BankId, User}
import net.liftweb.util.SimpleInjector
import code.remotedata.Remotedata


object AccountHolders extends SimpleInjector {

  val accountHolders = new Inject(buildOne _) {}

  //def buildOne: AccountHolders = MapperAccountHolders
  def buildOne: AccountHolders = Remotedata

}

trait AccountHolders {
  def getAccountHolders(bankId: BankId, accountId: AccountId): Set[User]
  def createAccountHolder(userId: Long, bankId: String, accountId: String, source: String = "MappedAccountHolder"): Boolean
  def bulkDeleteAllAccountHolders(): Boolean
}

class AccountHoldersCaseClasses {
  case class createAccountHolder(userId: Long, bankId: String, accountId: String, source: String = "MappedAccountHolder")
  case class getAccountHolders(bankId: BankId, accountId: AccountId)
  case class bulkDeleteAllAccountHolders()
}

object RemoteAccountHoldersCaseClasses extends AccountHoldersCaseClasses