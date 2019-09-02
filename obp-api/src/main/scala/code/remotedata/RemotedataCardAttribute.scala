package code.remotedata

import akka.pattern.ask
import code.cardattribute.{CardAttributeProvider, RemotedataCardAttributeCaseClasses}
import code.actorsystem.ObpActorInit
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.CardAttributeType
import net.liftweb.common.Box

import scala.collection.immutable.List
import scala.concurrent.Future


object RemotedataCardAttribute extends ObpActorInit with CardAttributeProvider {

  val cc = RemotedataCardAttributeCaseClasses

  override def getCardAttributesFromProvider(cardId: String): Future[Box[List[CardAttribute]]] = 
    (actor ? cc.getCardAttributesFromProvider(cardId)).mapTo[Box[List[CardAttribute]]]

  override def getCardAttributeById(productAttributeId: String): Future[Box[CardAttribute]] = 
    (actor ? cc.getCardAttributeById(productAttributeId)).mapTo[Box[CardAttribute]]

  override def createOrUpdateCardAttribute(
    bankId: Option[BankId],
    cardId: Option[String],
    cardAttributeId: Option[String],
    name: String,
    attributeType: CardAttributeType.Value,
    value: String
  ): Future[Box[CardAttribute]] = 
    (actor ? cc.createOrUpdateCardAttribute(bankId, cardId, cardAttributeId , name , attributeType , value )).mapTo[Box[CardAttribute]]

  override def deleteCardAttribute(cardAttributeId: String): Future[Box[Boolean]] = 
    (actor ? cc.deleteCardAttribute(cardAttributeId)).mapTo[Box[Boolean]]
}
