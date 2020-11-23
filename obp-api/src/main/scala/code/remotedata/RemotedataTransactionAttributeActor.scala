package code.remotedata

import akka.actor.Actor
import akka.pattern.pipe
import code.transactionattribute.{MappedTransactionAttributeProvider, RemotedataTransactionAttributeCaseClasses}
import code.actorsystem.ObpActorHelper
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.enums.TransactionAttributeType
import com.openbankproject.commons.model.{BankId, ProductAttribute, ProductCode, TransactionAttribute, TransactionId, ViewId}

import scala.collection.immutable.List
import scala.concurrent.ExecutionContext.Implicits.global

class RemotedataTransactionAttributeActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedTransactionAttributeProvider
  val cc = RemotedataTransactionAttributeCaseClasses

  def receive: PartialFunction[Any, Unit] = {

    case cc.getTransactionAttributesFromProvider(transactionId: TransactionId) =>
      logger.debug(s"getTransactionAttributesFromProvider(${transactionId})")
      mapper.getTransactionAttributesFromProvider(transactionId) pipeTo sender
      
    case cc.getTransactionAttributes(bankId: BankId, transactionId: TransactionId) =>
      logger.debug(s"getTransactionAttributes(${bankId}, ${transactionId})")
      mapper.getTransactionAttributes(bankId, transactionId) pipeTo sender      
      
    case cc.getTransactionAttributesCanBeSeenOnView(bankId: BankId, transactionId: TransactionId, viewId: ViewId) =>
      logger.debug(s"getTransactionAttributesCanBeSeenOnView(${bankId}, ${transactionId}, ${viewId})")
      mapper.getTransactionAttributesCanBeSeenOnView(bankId, transactionId, viewId) pipeTo sender 
      
    case cc.getTransactionsAttributesCanBeSeenOnView(bankId: BankId, transactionIds: List[TransactionId], viewId: ViewId) =>
      logger.debug(s"getTransactionsAttributesCanBeSeenOnView(${bankId}, ${transactionIds}, ${viewId})")
      mapper.getTransactionsAttributesCanBeSeenOnView(bankId, transactionIds, viewId) pipeTo sender

    case cc.getTransactionAttributeById(transactionAttributeId: String) =>
      logger.debug(s"getTransactionAttributeById(${transactionAttributeId})")
      mapper.getTransactionAttributeById(transactionAttributeId) pipeTo sender

    case cc.getTransactionIdsByAttributeNameValues(bankId: BankId, params: Map[String, List[String]]) =>
      logger.debug(s"getTransactionIdsByAttributeNameValues(${bankId}, ${params.toSeq.toString()})")
      mapper.getTransactionIdsByAttributeNameValues(bankId: BankId, params: Map[String, List[String]]) pipeTo sender
      
    case cc.createOrUpdateTransactionAttribute(bankId: BankId,
            transactionId: TransactionId,
            transactionAttributeId: Option[String],
            name: String,
            attributeType: TransactionAttributeType.Value,
            value: String) =>
      logger.debug(s"createOrUpdateTransactionAttribute(${bankId}, ${transactionId}, ${transactionAttributeId}, ${name}, ${attributeType}, ${value})")
      mapper.createOrUpdateTransactionAttribute(bankId, transactionId,
        transactionAttributeId,
        name,
        attributeType,
        value) pipeTo sender
      
    case cc.createTransactionAttributes(bankId: BankId,
            transactionId: TransactionId,
            transactionAttributes: List[TransactionAttribute]) =>
      mapper.createTransactionAttributes(
        bankId, 
        transactionId,
        transactionAttributes) pipeTo sender

    case cc.deleteTransactionAttribute(transactionAttributeId: String) =>
      logger.debug(s"deleteTransactionAttribute(${transactionAttributeId})")
      mapper.deleteTransactionAttribute(transactionAttributeId) pipeTo sender

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)
  }

}


