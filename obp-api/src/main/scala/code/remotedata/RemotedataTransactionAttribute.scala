package code.remotedata

import akka.pattern.ask
import code.transactionattribute.{TransactionAttributeProvider, RemotedataTransactionAttributeCaseClasses}
import code.actorsystem.ObpActorInit
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.TransactionAttributeType
import net.liftweb.common.Box

import scala.collection.immutable.List
import scala.concurrent.Future


object RemotedataTransactionAttribute extends ObpActorInit with TransactionAttributeProvider {

  val cc = RemotedataTransactionAttributeCaseClasses

  override def getTransactionAttributesFromProvider(transactionId: TransactionId): Future[Box[List[TransactionAttribute]]] = 
    (actor ? cc.getTransactionAttributesFromProvider(transactionId)).mapTo[Box[List[TransactionAttribute]]]
  
  override def getTransactionAttributes(bankId: BankId,
                                             transactionId: TransactionId): Future[Box[List[TransactionAttribute]]] = 
    (actor ? cc.getTransactionAttributes(bankId, transactionId)).mapTo[Box[List[TransactionAttribute]]] 
  
  override def getTransactionAttributesCanBeSeenOnView(bankId: BankId,
                                                       transactionId: TransactionId, 
                                                       viewId: ViewId): Future[Box[List[TransactionAttribute]]] = 
    (actor ? cc.getTransactionAttributesCanBeSeenOnView(bankId, transactionId, viewId)).mapTo[Box[List[TransactionAttribute]]]
  
  override def getTransactionsAttributesCanBeSeenOnView(bankId: BankId,
                                                        transactionIds: List[TransactionId], 
                                                        viewId: ViewId): Future[Box[List[TransactionAttribute]]] = 
    (actor ? cc.getTransactionsAttributesCanBeSeenOnView(bankId, transactionIds, viewId)).mapTo[Box[List[TransactionAttribute]]]

  override def getTransactionAttributeById(transactionAttributeId: String): Future[Box[TransactionAttribute]] = 
    (actor ? cc.getTransactionAttributeById(transactionAttributeId)).mapTo[Box[TransactionAttribute]]

  override def getTransactionIdsByAttributeNameValues(bankId: BankId, params: Map[String, List[String]]): Future[Box[List[String]]] =
    (actor ? cc.getTransactionIdsByAttributeNameValues(bankId: BankId, params: Map[String, List[String]])).mapTo[Box[List[String]]]
  
  override def createOrUpdateTransactionAttribute(bankId: BankId,
                                              transactionId: TransactionId,
                                              transactionAttributeId: Option[String],
                                              name: String,
                                              attributeType: TransactionAttributeType.Value,
                                              value: String): Future[Box[TransactionAttribute]] = 
    (actor ? cc.createOrUpdateTransactionAttribute(bankId, transactionId, transactionAttributeId , name , attributeType , value )).mapTo[Box[TransactionAttribute]]

  override def createTransactionAttributes(bankId: BankId,
                                              transactionId: TransactionId,
                                              transactionAttributes: List[TransactionAttribute]): Future[Box[List[TransactionAttribute]]] = 
    (actor ? cc.createTransactionAttributes(bankId, transactionId, transactionAttributes)).mapTo[Box[List[TransactionAttribute]]]

  override def deleteTransactionAttribute(transactionAttributeId: String): Future[Box[Boolean]] = 
    (actor ? cc.deleteTransactionAttribute(transactionAttributeId)).mapTo[Box[Boolean]]
}
