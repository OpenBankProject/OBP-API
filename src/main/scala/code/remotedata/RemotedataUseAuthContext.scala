package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.context.{RemotedataUserAuthContextCaseClasses, UserAuthContext, UserAuthContextProvider}
import code.remotedata.RemotedataNonces.extractFutureToBox
import net.liftweb.common.Box

import scala.collection.immutable.List
import scala.concurrent.Future


object RemotedataUserAuthContext extends ObpActorInit with UserAuthContextProvider {

  val cc = RemotedataUserAuthContextCaseClasses

  def getUserAuthContexts(userId: String): Future[Box[List[UserAuthContext]]] =
    (actor ? cc.getUserAuthContexts(userId)).mapTo[Box[List[UserAuthContext]]]
  
  def getUserAuthContextsBox(userId: String): Box[List[UserAuthContext]] =
    extractFutureToBox(actor ? cc.getUserAuthContextsBox(userId))

  def createUserAuthContext(userId: String, key: String, value: String): Future[Box[UserAuthContext]] =
    (actor ? cc.createUserAuthContext(userId, key, value)).mapTo[Box[UserAuthContext]]

  override def deleteUserAuthContexts(userId: String): Future[Box[Boolean]] =
    (actor ? cc.deleteUserAuthContexts(userId)).mapTo[Box[Boolean]]

  override def deleteUserAuthContextById(userAuthContextId: String): Future[Box[Boolean]] =
    (actor ? cc.deleteUserAuthContextById(userAuthContextId)).mapTo[Box[Boolean]]
}
