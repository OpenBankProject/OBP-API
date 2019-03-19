package code.remotedata

import akka.actor.Actor
import akka.pattern.pipe
import code.accountattribute.AccountAttribute.AccountAttributeType
import code.accountattribute.{MappedAccountAttributeProvider, RemotedataAccountAttributeCaseClasses}
import code.actorsystem.ObpActorHelper
import code.products.Products.ProductCode
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.{AccountId, BankId}

import scala.concurrent.ExecutionContext.Implicits.global

class RemotedataAccountAttributeActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedAccountAttributeProvider
  val cc = RemotedataAccountAttributeCaseClasses

  def receive: PartialFunction[Any, Unit] = {

    case cc.getAccountAttributesFromProvider(accountId: AccountId, productCode: ProductCode) =>
      logger.debug(s"getAccountAttributesFromProvider(${accountId}, ${productCode})")
      mapper.getAccountAttributesFromProvider(accountId, productCode) pipeTo sender

    case cc.getAccountAttributeById(accountAttributeId: String) =>
      logger.debug(s"getAccountAttributeById(${accountAttributeId})")
      mapper.getAccountAttributeById(accountAttributeId) pipeTo sender

    case cc.createOrUpdateAccountAttribute(bankId: BankId,
            accountId: AccountId,
            productCode: ProductCode,
            accountAttributeId: Option[String],
            name: String,
            attributeType: AccountAttributeType.Value,
            value: String) =>
      logger.debug(s"createOrUpdateAccountAttribute(${bankId}, ${accountId}, ${productCode}, ${accountAttributeId}, ${name}, ${attributeType}, ${value})")
      mapper.createOrUpdateAccountAttribute(bankId, accountId,
        productCode,
        accountAttributeId,
        name,
        attributeType,
        value) pipeTo sender

    case cc.deleteAccountAttribute(accountAttributeId: String) =>
      logger.debug(s"deleteAccountAttribute(${accountAttributeId})")
      mapper.deleteAccountAttribute(accountAttributeId) pipeTo sender

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)
  }

}


