package code.transactionRequestAttribute

import com.openbankproject.commons.model.enums.TransactionRequestAttributeType
import com.openbankproject.commons.model.{BankId, TransactionRequestAttributeJsonV400, TransactionRequestAttributeTrait, TransactionRequestId, ViewId}
import net.liftweb.common.{Box, Logger}
import code.util.Helper.MdcLoggable

import scala.collection.immutable.List
import scala.concurrent.Future

trait TransactionRequestAttributeProvider extends MdcLoggable {

  def getTransactionRequestAttributesFromProvider(transactionRequestId: TransactionRequestId): Future[Box[List[TransactionRequestAttributeTrait]]]

  def getTransactionRequestAttributes(bankId: BankId,
                                      transactionRequestId: TransactionRequestId): Future[Box[List[TransactionRequestAttributeTrait]]]

  def getTransactionRequestAttributesCanBeSeenOnView(bankId: BankId,
                                                     transactionRequestId: TransactionRequestId,
                                                     viewId: ViewId): Future[Box[List[TransactionRequestAttributeTrait]]]

  def getTransactionRequestAttributeById(transactionRequestAttributeId: String): Future[Box[TransactionRequestAttributeTrait]]

  def getTransactionRequestIdsByAttributeNameValues(bankId: BankId, params: Map[String, List[String]], isPersonal: Boolean): Future[Box[List[String]]]

  def getByAttributeNameValues(bankId: BankId, params: Map[String, List[String]], isPersonal: Boolean): Future[Box[List[TransactionRequestAttributeTrait]]]

  def createOrUpdateTransactionRequestAttribute(bankId: BankId,
                                                transactionRequestId: TransactionRequestId,
                                                transactionRequestAttributeId: Option[String],
                                                name: String,
                                                attributeType: TransactionRequestAttributeType.Value,
                                                value: String): Future[Box[TransactionRequestAttributeTrait]]

  def createTransactionRequestAttributes(bankId: BankId,
                                         transactionRequestId: TransactionRequestId,
                                         transactionRequestAttributes: List[TransactionRequestAttributeJsonV400],
                                         isPersonal: Boolean): Future[Box[List[TransactionRequestAttributeTrait]]]

  def deleteTransactionRequestAttribute(transactionRequestAttributeId: String): Future[Box[Boolean]]

}
