package code.remotedata

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.transactionRequestAttribute.{MappedTransactionRequestAttributeProvider, RemotedataTransactionRequestAttributeCaseClasses}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.TransactionRequestAttributeType
import akka.pattern.pipe
import scala.collection.immutable.List
import com.openbankproject.commons.ExecutionContext.Implicits.global

class RemotedataTransactionRequestAttributeActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedTransactionRequestAttributeProvider
  val cc = RemotedataTransactionRequestAttributeCaseClasses

  def receive: PartialFunction[Any, Unit] = {

    case cc.getTransactionRequestAttributesFromProvider(transactionRequestId: TransactionRequestId) =>
      logger.debug(s"getTransactionRequestAttributesFromProvider($transactionRequestId)")
      mapper.getTransactionRequestAttributesFromProvider(transactionRequestId) pipeTo sender

    case cc.getTransactionRequestAttributes(bankId : BankId, transactionRequestId: TransactionRequestId) =>
      logger.debug(s"getTransactionRequestAttributes($bankId, $transactionRequestId)")
      mapper.getTransactionRequestAttributes(bankId, transactionRequestId) pipeTo sender

    case cc.getTransactionRequestAttributesCanBeSeenOnView(bankId : BankId, transactionRequestId: TransactionRequestId, viewId: ViewId) =>
      logger.debug(s"getTransactionRequestAttributesCanBeSeenOnView($bankId, $transactionRequestId, $viewId)")
      mapper.getTransactionRequestAttributesCanBeSeenOnView(bankId, transactionRequestId, viewId) pipeTo sender

    case cc.getTransactionRequestAttributeById(transactionRequestAttributeId: String) =>
      logger.debug(s"getTransactionRequestAttributeById($transactionRequestAttributeId)")
      mapper.getTransactionRequestAttributeById(transactionRequestAttributeId) pipeTo sender
      
    case cc.getTransactionRequestIdsByAttributeNameValues(bankId: BankId, params: Map[String, List[String]]) =>
      logger.debug(s"getTransactionRequestIdsByAttributeNameValues($bankId, $params)")
      mapper.getTransactionRequestIdsByAttributeNameValues(bankId, params) pipeTo sender
      
    case cc.createOrUpdateTransactionRequestAttribute(bankId: BankId,
      transactionRequestId: TransactionRequestId,
      transactionRequestAttributeId: Option[String],
      name: String,
      attributeType: TransactionRequestAttributeType.Value,
      value: String) =>
      logger.debug(s"createOrUpdateTransactionRequestAttribute($bankId, $transactionRequestId, $transactionRequestAttributeId, $name, $attributeType, $value)")
      mapper.createOrUpdateTransactionRequestAttribute(bankId, transactionRequestId, transactionRequestAttributeId, name, attributeType, value) pipeTo sender


    case cc.createTransactionRequestAttributes(bankId : BankId, transactionRequestId: TransactionRequestId, transactionRequestAttributes: List[TransactionRequestAttributeTrait]) =>
      logger.debug(s"createTransactionRequestAttributes($bankId, $transactionRequestId, $transactionRequestAttributes)")
      mapper.createTransactionRequestAttributes(bankId, transactionRequestId, transactionRequestAttributes) pipeTo sender

    case cc.deleteTransactionRequestAttribute(transactionRequestAttributeId: String) =>
      logger.debug(s"deleteTransactionRequestAttribute($transactionRequestAttributeId)")
      mapper.deleteTransactionRequestAttribute(transactionRequestAttributeId) pipeTo sender
      
    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}

