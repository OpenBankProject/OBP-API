package code.remotedata

import akka.actor.Actor
import akka.pattern.pipe
import code.actorsystem.ObpActorHelper
import code.bankattribute.{BankAttributeProvider, RemotedataBankAttributeCaseClasses}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.BankId
import com.openbankproject.commons.model.enums.BankAttributeType

class RemotedataBankAttributeActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = BankAttributeProvider
  val cc = RemotedataBankAttributeCaseClasses

  def receive = {

    case cc.getBankAttributesFromProvider(bankId: BankId) =>
      logger.debug(s"getBankAttributesFromProvider(${bankId})")
      mapper.getBankAttributesFromProvider(bankId) pipeTo sender

    case cc.getBankAttributeById(bankAttributeId: String) =>
      logger.debug(s"getBankAttributeById(${bankAttributeId})")
      mapper.getBankAttributeById(bankAttributeId) pipeTo sender

    case cc.createOrUpdateBankAttribute(bankId: BankId,
            productAttributeId: Option[String],
            name: String,
            attributType: BankAttributeType.Value,
            value: String, 
            isActive: Option[Boolean]) =>
      logger.debug(s"createOrUpdateBankAttribute(${bankId}, ${productAttributeId}, ${name}, ${attributType}, ${value}, ${isActive})")
      mapper.createOrUpdateBankAttribute(bankId,
        productAttributeId,
        name,
        attributType,
        value, 
        isActive) pipeTo sender

    case cc.deleteBankAttribute(bankAttributeId: String) =>
      logger.debug(s"deleteBankAttribute(${bankAttributeId})")
      mapper.deleteBankAttribute(bankAttributeId) pipeTo sender

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)
  }

}


