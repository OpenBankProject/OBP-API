package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.transactionRequestAttribute.{RemotedataTransactionRequestAttributeCaseClasses, TransactionRequestAttributeProvider}
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.TransactionRequestAttributeType
import net.liftweb.common.Box

import scala.collection.immutable.List
import scala.concurrent.Future


object RemotedataTransactionRequestAttribute extends ObpActorInit with TransactionRequestAttributeProvider {

  val cc: RemotedataTransactionRequestAttributeCaseClasses.type = RemotedataTransactionRequestAttributeCaseClasses

  override def getTransactionRequestAttributesFromProvider(transactionRequestId: TransactionRequestId): Future[Box[List[TransactionRequestAttribute]]] =
    (actor ? cc.getTransactionRequestAttributesFromProvider(transactionRequestId)).mapTo[Box[List[TransactionRequestAttribute]]]

  override def getTransactionRequestAttributes(bankId: BankId,
                                               transactionRequestId: TransactionRequestId): Future[Box[List[TransactionRequestAttribute]]] =
    (actor ? cc.getTransactionRequestAttributes(bankId, transactionRequestId)).mapTo[Box[List[TransactionRequestAttribute]]]

  override def getTransactionRequestAttributesCanBeSeenOnView(bankId: BankId,
                                                              transactionRequestId: TransactionRequestId,
                                                              viewId: ViewId): Future[Box[List[TransactionRequestAttribute]]] =
    (actor ? cc.getTransactionRequestAttributesCanBeSeenOnView(bankId, transactionRequestId, viewId)).mapTo[Box[List[TransactionRequestAttribute]]]

  override def getTransactionRequestAttributeById(transactionRequestAttributeId: String): Future[Box[TransactionRequestAttribute]] =
    (actor ? cc.getTransactionRequestAttributeById(transactionRequestAttributeId)).mapTo[Box[TransactionRequestAttribute]]

  override def getTransactionRequestIdsByAttributeNameValues(bankId: BankId, params: Map[String, List[String]]): Future[Box[List[String]]] =
    (actor ? cc.getTransactionRequestIdsByAttributeNameValues(bankId: BankId, params: Map[String, List[String]])).mapTo[Box[List[String]]]

  override def createOrUpdateTransactionRequestAttribute(bankId: BankId,
                                                         transactionRequestId: TransactionRequestId,
                                                         transactionRequestAttributeId: Option[String],
                                                         name: String,
                                                         attributeType: TransactionRequestAttributeType.Value,
                                                         value: String): Future[Box[TransactionRequestAttribute]] =
    (actor ? cc.createOrUpdateTransactionRequestAttribute(bankId, transactionRequestId, transactionRequestAttributeId, name, attributeType, value)).mapTo[Box[TransactionRequestAttribute]]

  override def createTransactionRequestAttributes(bankId: BankId,
                                                  transactionRequestId: TransactionRequestId,
                                                  transactionRequestAttributes: List[TransactionRequestAttribute]): Future[Box[List[TransactionRequestAttribute]]] =
    (actor ? cc.createTransactionRequestAttributes(bankId, transactionRequestId, transactionRequestAttributes)).mapTo[Box[List[TransactionRequestAttribute]]]

  override def deleteTransactionRequestAttribute(transactionRequestAttributeId: String): Future[Box[Boolean]] =
    (actor ? cc.deleteTransactionRequestAttribute(transactionRequestAttributeId)).mapTo[Box[Boolean]]
}
