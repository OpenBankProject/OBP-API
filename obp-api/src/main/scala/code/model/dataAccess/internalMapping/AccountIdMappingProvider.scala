package code.model.dataAccess.internalMapping

import com.openbankproject.commons.model.{BankId, AccountId}
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector


object AccountIdMappingProvider extends SimpleInjector {

  val accountIdMappingProvider = new Inject(buildOne _) {}

  def buildOne: AccountIdMappingProvider = MappedAccountIdMappingProvider

}

trait AccountIdMappingProvider {

  def getOrCreateAccountId(accountPlainTextReference: String): Box[AccountId]

  def getAccountPlainTextReference(accountId: AccountId): Box[String]

}
