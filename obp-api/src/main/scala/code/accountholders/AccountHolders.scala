package code.accountholders

import code.api.util.APIUtil
import com.openbankproject.commons.model.{AccountId, BankId, BankIdAccountId, User}
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector


object AccountHolders extends SimpleInjector {

  val accountHolders = new Inject(buildOne _) {}

  def buildOne: AccountHolders = MapperAccountHolders

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


