package code.remotedata

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.context.{MappedUserAuthContextProvider, RemotedataUserAuthContextCaseClasses}
import code.util.Helper.MdcLoggable
import akka.pattern.pipe
import scala.concurrent.ExecutionContext.Implicits.global

class RemotedataUserAuthContextActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedUserAuthContextProvider
  val cc = RemotedataUserAuthContextCaseClasses

  def receive = {

    case cc.createUserAuthContext(userId: String, key: String, value: String) =>
      logger.debug("createUserAuthContext(" + userId + ", " + key + ", " + value + ")")
      sender ! (mapper.createUserAuthContextAkka(userId, key, value))

    case cc.getUserAuthContexts(userId: String) =>
      logger.debug("getUserAuthContexts(" + userId + ")")
      sender ! (mapper.getUserAuthContextsBox(userId))
      
    case cc.getUserAuthContextsBox(userId: String) =>
      logger.debug("getUserAuthContextsBox(" + userId + ")")
      sender ! (mapper.getUserAuthContextsBox(userId))
      
    case cc.deleteUserAuthContexts(userId: String) =>
      logger.debug(msg=s"deleteUserAuthContexts(${userId})")
      sender ! (mapper.deleteUserAuthContextsAkka(userId))

    case cc.deleteUserAuthContextById(userAuthContextId: String) =>
      logger.debug(msg=s"deleteUserAuthContextById(${userAuthContextId})")
      sender ! (mapper.deleteUserAuthContextByIdAkka(userAuthContextId))

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}


