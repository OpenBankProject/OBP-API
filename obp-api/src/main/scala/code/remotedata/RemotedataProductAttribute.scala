package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.productattribute.{ProductAttributeProvider, RemotedataProductAttributeCaseClasses}
import com.openbankproject.commons.model.enums.ProductAttributeType
import com.openbankproject.commons.model.{BankId, ProductAttribute, ProductCode}
import net.liftweb.common.Box

import scala.collection.immutable.List
import scala.concurrent.Future


object RemotedataProductAttribute extends ObpActorInit with ProductAttributeProvider {

  val cc = RemotedataProductAttributeCaseClasses

  override def getProductAttributesFromProvider(bankId: BankId, productCode: ProductCode): Future[Box[List[ProductAttribute]]] = (actor ? cc.getProductAttributesFromProvider(bankId, productCode)).mapTo[Box[List[ProductAttribute]]]

  override def getProductAttributeById(productAttributeId: String): Future[Box[ProductAttribute]] = (actor ? cc.getProductAttributeById(productAttributeId)).mapTo[Box[ProductAttribute]]

  override def createOrUpdateProductAttribute(bankId: BankId, productCode: ProductCode, productAttributeId: Option[String], name: String, attributType: ProductAttributeType.Value, value: String): Future[Box[ProductAttribute]] = (actor ? cc.createOrUpdateProductAttribute(bankId, productCode, productAttributeId , name , attributType , value )).mapTo[Box[ProductAttribute]]

  override def deleteProductAttribute(productAttributeId: String): Future[Box[Boolean]] = (actor ? cc.deleteProductAttribute(productAttributeId)).mapTo[Box[Boolean]]
}
