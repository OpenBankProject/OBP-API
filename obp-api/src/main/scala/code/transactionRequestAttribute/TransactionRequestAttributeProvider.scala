package code.transactionRequestAttribute

import com.openbankproject.commons.model.enums.TransactionRequestAttributeType
import com.openbankproject.commons.model.{BankId, TransactionRequestAttribute, TransactionRequestId, ViewId}
import net.liftweb.common.{Box, Logger}

import scala.collection.immutable.List
import scala.concurrent.Future

trait TransactionRequestAttributeProvider {

  private val logger = Logger(classOf[TransactionRequestAttributeProvider])

  def getTransactionRequestAttributesFromProvider(transactionRequestId: TransactionRequestId): Future[Box[List[TransactionRequestAttribute]]]

  def getTransactionRequestAttributes(bankId: BankId,
                                      transactionRequestId: TransactionRequestId): Future[Box[List[TransactionRequestAttribute]]]

  def getTransactionRequestAttributesCanBeSeenOnView(bankId: BankId,
                                                     transactionRequestId: TransactionRequestId,
                                                     viewId: ViewId): Future[Box[List[TransactionRequestAttribute]]]

  def getTransactionRequestAttributeById(transactionRequestAttributeId: String): Future[Box[TransactionRequestAttribute]]

  def getTransactionRequestIdsByAttributeNameValues(bankId: BankId, params: Map[String, List[String]]): Future[Box[List[String]]]

  def createOrUpdateTransactionRequestAttribute(bankId: BankId,
                                                transactionRequestId: TransactionRequestId,
                                                transactionRequestAttributeId: Option[String],
                                                name: String,
                                                attributeType: TransactionRequestAttributeType.Value,
                                                value: String): Future[Box[TransactionRequestAttribute]]

  def createTransactionRequestAttributes(bankId: BankId,
                                         transactionRequestId: TransactionRequestId,
                                         transactionRequestAttributes: List[TransactionRequestAttribute]): Future[Box[List[TransactionRequestAttribute]]]

  def deleteTransactionRequestAttribute(transactionRequestAttributeId: String): Future[Box[Boolean]]

}