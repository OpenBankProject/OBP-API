package code.remotedata

import akka.actor.Actor
import akka.pattern.pipe
import code.transactionattribute.{MappedTransactionAttributeProvider, RemotedataTransactionAttributeCaseClasses}
import code.actorsystem.ObpActorHelper
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.enums.TransactionAttributeType
import com.openbankproject.commons.model.{BankId, TransactionAttribute, TransactionId, ProductAttribute, ProductCode}

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

    case cc.getTransactionAttributeById(transactionAttributeId: String) =>
      logger.debug(s"getTransactionAttributeById(${transactionAttributeId})")
      mapper.getTransactionAttributeById(transactionAttributeId) pipeTo sender

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
      //TODO 这里的 productArributes 是什么? 
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


