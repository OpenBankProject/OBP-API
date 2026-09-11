package code.transaction.internalMapping

import com.openbankproject.commons.model.TransactionId
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector


object TransactionIdMappingProvider extends SimpleInjector {

  val transactionIdMappingProvider = new Inject(() => buildOne) {}

  def buildOne: TransactionIdMappingProvider = DoobieTransactionIdMappingProvider

}

trait TransactionIdMappingProvider {

  def getOrCreateTransactionId(transactionPlainTextReference: String): Box[TransactionId]

  def getTransactionPlainTextReference(transactionId: TransactionId): Box[String]

}
