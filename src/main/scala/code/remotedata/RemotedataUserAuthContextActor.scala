package code.remotedata

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.context.{MappedUserAuthContextProvider, RemotedataUserAuthContextCaseClasses}
import code.util.Helper.MdcLoggable

class RemotedataUserAuthContextActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedUserAuthContextProvider
  val cc = RemotedataUserAuthContextCaseClasses

  def receive = {

    case cc.createUserAuthContext(userId: String, key: String, value: String) =>
      logger.debug("createUserAuthContext(" + userId + ", " + key + ", " + value + ")")
      sender ! (mapper.createUserAuthContextAkka(userId, key, value))

    case cc.getUserAuthContexts(userId: String) =>
      logger.debug("getUserAuthContexts(" + userId + ")")
      sender ! (mapper.getUserAuthContextsAkka(userId))

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}


