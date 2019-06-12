package code.remotedata

import akka.pattern.ask
import code.accountattribute.{AccountAttributeProvider, RemotedataAccountAttributeCaseClasses}
import code.actorsystem.ObpActorInit
import com.openbankproject.commons.model._
import net.liftweb.common.Box

import scala.collection.immutable.List
import scala.concurrent.Future


object RemotedataAccountAttribute extends ObpActorInit with AccountAttributeProvider {

  val cc = RemotedataAccountAttributeCaseClasses

  override def getAccountAttributesFromProvider(accountId: AccountId,
                                                productCode: ProductCode): Future[Box[List[AccountAttribute]]] = 
    (actor ? cc.getAccountAttributesFromProvider(accountId, productCode)).mapTo[Box[List[AccountAttribute]]]
  
  override def getAccountAttributesByAccount(bankId: BankId,
                                             accountId: AccountId): Future[Box[List[AccountAttribute]]] = 
    (actor ? cc.getAccountAttributesByAccount(bankId, accountId)).mapTo[Box[List[AccountAttribute]]]

  override def getAccountAttributeById(productAttributeId: String): Future[Box[AccountAttribute]] = 
    (actor ? cc.getAccountAttributeById(productAttributeId)).mapTo[Box[AccountAttribute]]

  override def createOrUpdateAccountAttribute(bankId: BankId,
                                              accountId: AccountId,
                                              productCode: ProductCode,
                                              productAttributeId: Option[String],
                                              name: String,
                                              attributeType: AccountAttributeType.Value,
                                              value: String): Future[Box[AccountAttribute]] = 
    (actor ? cc.createOrUpdateAccountAttribute(bankId, accountId, productCode, productAttributeId , name , attributeType , value )).mapTo[Box[AccountAttribute]]

  override def createAccountAttributes(bankId: BankId,
                                              accountId: AccountId,
                                              productCode: ProductCode,
                                              productAttributes: List[ProductAttribute]): Future[Box[List[AccountAttribute]]] = 
    (actor ? cc.createAccountAttributes(bankId, accountId, productCode, productAttributes)).mapTo[Box[List[AccountAttribute]]]

  override def deleteAccountAttribute(accountAttributeId: String): Future[Box[Boolean]] = 
    (actor ? cc.deleteAccountAttribute(accountAttributeId)).mapTo[Box[Boolean]]
}
