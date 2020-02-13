package code.transactionattribute

/* For TransactionAttribute */

import code.api.util.APIUtil
import code.remotedata.RemotedataTransactionAttribute
import com.openbankproject.commons.model.enums.TransactionAttributeType
import com.openbankproject.commons.model.{BankId, TransactionAttribute, TransactionId}
import net.liftweb.common.{Box, Logger}
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future

object TransactionAttributeX extends SimpleInjector {

  val transactionAttributeProvider = new Inject(buildOne _) {}

  def buildOne: TransactionAttributeProvider =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
      case false  => MappedTransactionAttributeProvider
      case true => RemotedataTransactionAttribute     // We will use Akka as a middleware
    }

  // Helper to get the count out of an option
  def countOfTransactionAttribute(listOpt: Option[List[TransactionAttribute]]): Int = {
    val count = listOpt match {
      case Some(list) => list.size
      case None => 0
    }
    count
  }


}

trait TransactionAttributeProvider {

  private val logger = Logger(classOf[TransactionAttributeProvider])

  def getTransactionAttributesFromProvider(transactionId: TransactionId): Future[Box[List[TransactionAttribute]]]
  def getTransactionAttributes(bankId: BankId,
                                    transactionId: TransactionId): Future[Box[List[TransactionAttribute]]]

  def getTransactionAttributeById(transactionAttributeId: String): Future[Box[TransactionAttribute]]

  def createOrUpdateTransactionAttribute(bankId: BankId,
                                     transactionId: TransactionId,
                                     transactionAttributeId: Option[String],
                                     name: String,
                                     attributeType: TransactionAttributeType.Value,
                                     value: String): Future[Box[TransactionAttribute]]

  def createTransactionAttributes(bankId: BankId,
                              transactionId: TransactionId,
                              transactionAttributes: List[TransactionAttribute]): Future[Box[List[TransactionAttribute]]]
  
  def deleteTransactionAttribute(transactionAttributeId: String): Future[Box[Boolean]]
  // End of Trait
}

class RemotedataTransactionAttributeCaseClasses {
  case class getTransactionAttributesFromProvider(transactionId: TransactionId)
  case class getTransactionAttributes(bankId: BankId,
                                           transactionId: TransactionId)

  case class getTransactionAttributeById(transactionAttributeId: String)

  case class createOrUpdateTransactionAttribute(bankId: BankId,
                                            transactionId: TransactionId,
                                            transactionAttributeId: Option[String],
                                            name: String,
                                            attributeType: TransactionAttributeType.Value,
                                            value: String)
  
  case class createTransactionAttributes(bankId: BankId,
                                     transactionId: TransactionId,
                                     transactionAttributes: List[TransactionAttribute])

  case class deleteTransactionAttribute(transactionAttributeId: String)
}

object RemotedataTransactionAttributeCaseClasses extends RemotedataTransactionAttributeCaseClasses
