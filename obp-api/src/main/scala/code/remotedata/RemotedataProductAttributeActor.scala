package code.remotedata

import akka.actor.Actor
import akka.pattern.pipe
import code.actorsystem.ObpActorHelper
import code.productAttributeattribute.MappedProductAttributeProvider
import code.productattribute.RemotedataProductAttributeCaseClasses
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.enums.ProductAttributeType
import com.openbankproject.commons.model.{BankId, ProductCode}

import com.openbankproject.commons.ExecutionContext.Implicits.global

class RemotedataProductAttributeActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedProductAttributeProvider
  val cc = RemotedataProductAttributeCaseClasses

  def receive = {

    case cc.getProductAttributesFromProvider(bankId: BankId, productCode: ProductCode) =>
      logger.debug(s"getProductAttributesFromProvider(${bankId}, ${productCode})")
      mapper.getProductAttributesFromProvider(bankId, productCode) pipeTo sender

    case cc.getProductAttributeById(productAttributeId: String) =>
      logger.debug(s"getProductAttributeById(${productAttributeId})")
      mapper.getProductAttributeById(productAttributeId) pipeTo sender

    case cc.createOrUpdateProductAttribute(bankId: BankId,
            productCode: ProductCode,
            productAttributeId: Option[String],
            name: String,
            attributType: ProductAttributeType.Value,
            value: String) =>
      logger.debug(s"createOrUpdateProductAttribute(${bankId}, ${productCode}, ${productAttributeId}, ${name}, ${attributType}, ${value})")
      mapper.createOrUpdateProductAttribute(bankId,
        productCode,
        productAttributeId,
        name,
        attributType,
        value) pipeTo sender

    case cc.deleteProductAttribute(productAttributeId: String) =>
      logger.debug(s"deleteProductAttribute(${productAttributeId})")
      mapper.deleteProductAttribute(productAttributeId) pipeTo sender

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)
  }

}


