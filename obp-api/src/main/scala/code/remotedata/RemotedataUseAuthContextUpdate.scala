package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.context.{RemotedataUserAuthContextUpdateCaseClasses, UserAuthContextUpdateProvider}
import com.openbankproject.commons.model.UserAuthContextUpdate
import net.liftweb.common.Box

import scala.collection.immutable.List
import scala.concurrent.Future


object RemotedataUserAuthContextUpdate extends ObpActorInit with UserAuthContextUpdateProvider {

  val cc = RemotedataUserAuthContextUpdateCaseClasses

  def getUserAuthContextUpdates(userId: String): Future[Box[List[UserAuthContextUpdate]]] =
    (actor ? cc.getUserAuthContextUpdates(userId)).mapTo[Box[List[UserAuthContextUpdate]]]
  
  def getUserAuthContextUpdatesBox(userId: String): Box[List[UserAuthContextUpdate]] = getValueFromFuture(
    (actor ? cc.getUserAuthContextUpdatesBox(userId)).mapTo[Box[List[UserAuthContextUpdate]]]
  )

  def createUserAuthContextUpdates(userId: String, key: String, value: String): Future[Box[UserAuthContextUpdate]] =
    (actor ? cc.createUserAuthContextUpdate(userId, key, value)).mapTo[Box[UserAuthContextUpdate]]

  override def deleteUserAuthContextUpdates(userId: String): Future[Box[Boolean]] =
    (actor ? cc.deleteUserAuthContextUpdates(userId)).mapTo[Box[Boolean]]

  override def deleteUserAuthContextUpdateById(userAuthContextId: String): Future[Box[Boolean]] =
    (actor ? cc.deleteUserAuthContextUpdateById(userAuthContextId)).mapTo[Box[Boolean]]
  
  def checkAnswer(authContextUpdateId: String, challenge: String): Future[Box[UserAuthContextUpdate]] =
    (actor ? cc.checkAnswer(authContextUpdateId, challenge)).mapTo[Box[UserAuthContextUpdate]]
}
