package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.context.{RemotedataUserAuthContextCaseClasses, UserAuthContext, UserAuthContextProvider}
import net.liftweb.common.Box

import scala.collection.immutable.List
import scala.concurrent.Future


object RemotedataUserAuthContext extends ObpActorInit with UserAuthContextProvider {

  val cc = RemotedataUserAuthContextCaseClasses

  def getUserAuthContexts(userId: String): Future[Box[List[UserAuthContext]]] =
    (actor ? cc.getUserAuthContexts(userId)).mapTo[Box[List[UserAuthContext]]]

  def createUserAuthContext(userId: String, key: String, value: String): Future[Box[UserAuthContext]] =
    (actor ? cc.createUserAuthContext(userId, key, value)).mapTo[Box[UserAuthContext]]


}
