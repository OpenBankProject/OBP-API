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
  
  override def getPersonalUserAttributes(userId: String): Future[Box[List[UserAttribute]]] = 
    (actor ? cc.getPersonalUserAttributes(userId)).mapTo[Box[List[UserAttribute]]]
  
  override def getNonPersonalUserAttributes(userId: String): Future[Box[List[UserAttribute]]] = 
    (actor ? cc.getNonPersonalUserAttributes(userId)).mapTo[Box[List[UserAttribute]]]
  
  override def getUserAttributesByUsers(userIds: List[String]): Future[Box[List[UserAttribute]]] = 
    (actor ? cc.getUserAttributesByUsers(userIds)).mapTo[Box[List[UserAttribute]]]

  override def deleteUserAttribute(userAttributeId: String): Future[Box[Boolean]]  = 
    (actor ? cc.deleteUserAttribute(userAttributeId: String)).mapTo[Box[Boolean]]

  override def createOrUpdateUserAttribute(userId: String,
                                           userAttributeId: Option[String],
                                           name: String,
                                           attributeType: UserAttributeType.Value,
                                           value: String,
                                           isPersonal: Boolean): Future[Box[UserAttribute]] = 
    (actor ? cc.createOrUpdateUserAttribute(userId, userAttributeId, name, attributeType, value, isPersonal)).mapTo[Box[UserAttribute]]
  
}
