package code.remotedata

import akka.pattern.ask
import code.accountattribute.{AccountAttributeProvider, RemotedataAccountAttributeCaseClasses}
import code.actorsystem.ObpActorInit
import code.users.{RemotedataUserAttributeCaseClasses, UserAttribute, UserAttributeProvider}
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.{AccountAttributeType, UserAttributeType}
import net.liftweb.common.Box

import scala.collection.immutable.List
import scala.concurrent.Future


object RemotedataUserAttribute extends ObpActorInit with UserAttributeProvider {

  val cc = RemotedataUserAttributeCaseClasses
  
  override def getUserAttributesByUser(userId: String): Future[Box[List[UserAttribute]]] = 
    (actor ? cc.getUserAttributesByUser(userId)).mapTo[Box[List[UserAttribute]]]

  override def createOrUpdateUserAttribute(userId: String,
                                           userAttributeId: Option[String],
                                           name: String,
                                           attributeType: UserAttributeType.Value,
                                           value: String): Future[Box[UserAttribute]] = 
    (actor ? cc.createOrUpdateUserAttribute(userId, userAttributeId, name, attributeType, value )).mapTo[Box[UserAttribute]]
  
}
