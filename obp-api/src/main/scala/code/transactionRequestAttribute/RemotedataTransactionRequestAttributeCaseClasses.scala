package code.transactionRequestAttribute

import com.openbankproject.commons.model.enums.TransactionRequestAttributeType
import com.openbankproject.commons.model.{BankId, TransactionRequestAttributeTrait, TransactionRequestId, ViewId}

import scala.collection.immutable.List

class RemotedataTransactionRequestAttributeCaseClasses {

  case class getTransactionRequestAttributesFromProvider(transactionRequestId: TransactionRequestId)

  case class getTransactionRequestAttributes(bankId: BankId,
                                             transactionRequestId: TransactionRequestId)

  case class getTransactionRequestAttributesCanBeSeenOnView(bankId: BankId,
                                                            transactionRequestId: TransactionRequestId,
                                                            viewId: ViewId)

  case class getTransactionRequestAttributeById(transactionRequestAttributeId: String)

  case class getTransactionRequestIdsByAttributeNameValues(bankId: BankId, params: Map[String, List[String]])

  case class createOrUpdateTransactionRequestAttribute(bankId: BankId,
                                                       transactionRequestId: TransactionRequestId,
                                                       transactionRequestAttributeId: Option[String],
                                                       name: String,
                                                       attributeType: TransactionRequestAttributeType.Value,
                                                       value: String)

  case class createTransactionRequestAttributes(bankId: BankId,
                                                transactionRequestId: TransactionRequestId,
                                                transactionRequestAttributes: List[TransactionRequestAttributeTrait])

  case class deleteTransactionRequestAttribute(transactionRequestAttributeId: String)

}

object RemotedataTransactionRequestAttributeCaseClasses extends RemotedataTransactionRequestAttributeCaseClasses