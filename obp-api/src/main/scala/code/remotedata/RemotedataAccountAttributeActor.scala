package code.remotedata

import akka.actor.Actor
import akka.pattern.pipe
import code.accountattribute.{MappedAccountAttributeProvider, RemotedataAccountAttributeCaseClasses}
import code.actorsystem.ObpActorHelper
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.enums.AccountAttributeType
import com.openbankproject.commons.model.{AccountId, BankId, BankIdAccountId, ProductAttribute, ProductCode, ViewId}
import com.openbankproject.commons.ExecutionContext.Implicits.global

import scala.collection.immutable.List

class RemotedataAccountAttributeActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedAccountAttributeProvider
  val cc = RemotedataAccountAttributeCaseClasses

  def receive: PartialFunction[Any, Unit] = {

    case cc.getAccountAttributesFromProvider(accountId: AccountId, productCode: ProductCode) =>
      logger.debug(s"getAccountAttributesFromProvider(${accountId}, ${productCode})")
      mapper.getAccountAttributesFromProvider(accountId, productCode) pipeTo sender
      
    case cc.getAccountAttributesByAccount(bankId: BankId, accountId: AccountId) =>
      logger.debug(s"getAccountAttributesByAccount(${bankId}, ${accountId})")
      mapper.getAccountAttributesByAccount(bankId, accountId) pipeTo sender 
      
    case cc.getAccountAttributesByAccountCanBeSeenOnView(bankId: BankId, accountId: AccountId, viewId: ViewId) =>
      logger.debug(s"getAccountAttributesByAccount(${bankId}, ${accountId}, ${viewId})")
      mapper.getAccountAttributesByAccountCanBeSeenOnView(bankId, accountId, viewId) pipeTo sender
      
    case cc.getAccountAttributesByAccountsCanBeSeenOnView(accounts: List[BankIdAccountId], viewId: ViewId) =>
      logger.debug(s"getAccountAttributesByAccountsCanBeSeenOnView(${accounts}, ${viewId})")
      mapper.getAccountAttributesByAccountsCanBeSeenOnView(accounts, viewId) pipeTo sender

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
      
    case cc.createAccountAttributes(bankId: BankId,
            accountId: AccountId,
            productCode: ProductCode,
            accountAttributes: List[ProductAttribute]) =>
      mapper.createAccountAttributes(bankId, accountId,
        productCode,
        accountAttributes) pipeTo sender

    case cc.deleteAccountAttribute(accountAttributeId: String) =>
      logger.debug(s"deleteAccountAttribute(${accountAttributeId})")
      mapper.deleteAccountAttribute(accountAttributeId) pipeTo sender

    case cc.getAccountIdsByParams(bankId: BankId, params: Map[String, List[String]]) =>
      logger.debug(s"getAccountIdsByParams($bankId, $params)")
      mapper.getAccountIdsByParams(bankId, params) pipeTo sender

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)
  }

}


