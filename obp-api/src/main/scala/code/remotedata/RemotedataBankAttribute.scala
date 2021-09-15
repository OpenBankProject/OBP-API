package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.bankattribute.{BankAttribute, BankAttributeProviderTrait, RemotedataBankAttributeCaseClasses}
import com.openbankproject.commons.model.BankId
import com.openbankproject.commons.model.enums.BankAttributeType
import net.liftweb.common.Box

import scala.collection.immutable.List
import scala.concurrent.Future


object RemotedataBankAttribute extends ObpActorInit with BankAttributeProviderTrait {

  val cc = RemotedataBankAttributeCaseClasses

  override def getBankAttributesFromProvider(bankId: BankId): Future[Box[List[BankAttribute]]] = 
    (actor ? cc.getBankAttributesFromProvider(bankId)).mapTo[Box[List[BankAttribute]]]

  override def getBankAttributeById(bankAttributeId: String): Future[Box[BankAttribute]] = 
    (actor ? cc.getBankAttributeById(bankAttributeId)).mapTo[Box[BankAttribute]]

  override def createOrUpdateBankAttribute(bankId: BankId, bankAttributeId: Option[String], name: String, attributType: BankAttributeType.Value, value: String, isActive: Option[Boolean]): Future[Box[BankAttribute]] = 
    (actor ? cc.createOrUpdateBankAttribute(bankId, bankAttributeId , name , attributType , value, isActive)).mapTo[Box[BankAttribute]]

  override def deleteBankAttribute(bankAttributeId: String): Future[Box[Boolean]] = 
    (actor ? cc.deleteBankAttribute(bankAttributeId)).mapTo[Box[Boolean]]
}
