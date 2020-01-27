package code.remotedata

import akka.actor.Actor
import akka.pattern.pipe
import code.actorsystem.ObpActorHelper
import code.context.{MappedUserAuthContextUpdateProvider, RemotedataUserAuthContextUpdateCaseClasses}
import code.util.Helper.MdcLoggable

import com.openbankproject.commons.ExecutionContext.Implicits.global

class RemotedataUserAuthContextUpdateActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedUserAuthContextUpdateProvider
  val cc = RemotedataUserAuthContextUpdateCaseClasses

  def receive = {

    case cc.createUserAuthContextUpdate(userId: String, key: String, value: String) =>
      logger.debug("createUserAuthContext(" + userId + ", " + key + ", " + value + ")")
      mapper.createUserAuthContextUpdates(userId, key, value) pipeTo sender

    case cc.getUserAuthContextUpdates(userId: String) =>
      logger.debug("getUserAuthContexts(" + userId + ")")
      mapper.getUserAuthContextUpdates(userId) pipeTo sender
      
    case cc.getUserAuthContextUpdatesBox(userId: String) =>
      logger.debug("getUserAuthContextsBox(" + userId + ")")
      sender ! (mapper.getUserAuthContextUpdatesBox(userId))
      
    case cc.deleteUserAuthContextUpdates(userId: String) =>
      logger.debug(msg=s"deleteUserAuthContexts(${userId})")
      mapper.deleteUserAuthContextUpdates(userId) pipeTo sender

    case cc.deleteUserAuthContextUpdateById(userAuthContextId: String) =>
      logger.debug(msg=s"deleteUserAuthContextById(${userAuthContextId})")
      mapper.deleteUserAuthContextUpdateById(userAuthContextId) pipeTo sender
      
    case cc.checkAnswer(authContextUpdateId: String, challenge: String) =>
      logger.debug("checkAnswer(" + authContextUpdateId + ", " + challenge + ")")
      mapper.checkAnswer(authContextUpdateId, challenge) pipeTo sender

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}


