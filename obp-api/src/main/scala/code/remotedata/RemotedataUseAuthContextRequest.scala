package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.context._
import net.liftweb.common.Box

import scala.collection.immutable.List
import scala.concurrent.Future


object RemotedataUserAuthContextRequest extends ObpActorInit with UserAuthContextRequestProvider {

  val cc = RemotedataUserAuthContextRequestCaseClasses

  def getUserAuthContextRequests(userId: String): Future[Box[List[UserAuthContextRequest]]] =
    (actor ? cc.getUserAuthContextRequests(userId)).mapTo[Box[List[UserAuthContextRequest]]]
  
  def getUserAuthContextRequestsBox(userId: String): Box[List[UserAuthContextRequest]] = getValueFromFuture(
    (actor ? cc.getUserAuthContextRequestsBox(userId)).mapTo[Box[List[UserAuthContextRequest]]]
  )

  def createUserAuthContextRequest(userId: String, key: String, value: String): Future[Box[UserAuthContextRequest]] =
    (actor ? cc.createUserAuthContextRequest(userId, key, value)).mapTo[Box[UserAuthContextRequest]]

  override def deleteUserAuthContextRequests(userId: String): Future[Box[Boolean]] =
    (actor ? cc.deleteUserAuthContextRequests(userId)).mapTo[Box[Boolean]]

  override def deleteUserAuthContextRequestById(userAuthContextId: String): Future[Box[Boolean]] =
    (actor ? cc.deleteUserAuthContextRequestById(userAuthContextId)).mapTo[Box[Boolean]]
}
