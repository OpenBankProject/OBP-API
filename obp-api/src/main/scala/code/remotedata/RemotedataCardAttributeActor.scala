package code.remotedata

import akka.actor.Actor
import akka.pattern.pipe
import code.cardattribute.{MappedCardAttributeProvider, RemotedataCardAttributeCaseClasses}
import code.actorsystem.ObpActorHelper
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.enums.CardAttributeType
import com.openbankproject.commons.model.BankId

import com.openbankproject.commons.ExecutionContext.Implicits.global

class RemotedataCardAttributeActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedCardAttributeProvider
  val cc = RemotedataCardAttributeCaseClasses

  def receive: PartialFunction[Any, Unit] = {

    case cc.getCardAttributesFromProvider(cardId: String) =>
      logger.debug(s"getCardAttributesFromProvider(${cardId})")
      mapper.getCardAttributesFromProvider(cardId) pipeTo sender

    case cc.getCardAttributeById(cardAttributeId: String) =>
      logger.debug(s"getCardAttributeById(${cardAttributeId})")
      mapper.getCardAttributeById(cardAttributeId) pipeTo sender

    case cc.createOrUpdateCardAttribute(
      bankId: Option[BankId],
      cardId: Option[String],
      cardAttributeId: Option[String],
      name: String,
      attributeType: CardAttributeType.Value,
      value: String) =>
      logger.debug(s"createOrUpdateCardAttribute(${bankId}, ${cardId}, ${cardAttributeId}, ${name}, ${attributeType}, ${value})")
      mapper.createOrUpdateCardAttribute(
        bankId, 
        cardId,
        cardAttributeId,
        name,
        attributeType,
        value
      ) pipeTo sender

    case cc.deleteCardAttribute(cardAttributeId: String) =>
      logger.debug(s"deleteCardAttribute(${cardAttributeId})")
      mapper.deleteCardAttribute(cardAttributeId) pipeTo sender

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)
  }

}


