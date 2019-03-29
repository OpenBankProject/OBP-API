package code.remotedata

import akka.actor.Actor
import akka.pattern.pipe
import code.actorsystem.ObpActorHelper
import code.context.{MappedUserAuthContextRequestProvider, RemotedataUserAuthContextRequestCaseClasses}
import code.util.Helper.MdcLoggable

import scala.concurrent.ExecutionContext.Implicits.global

class RemotedataUserAuthContextRequestActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedUserAuthContextRequestProvider
  val cc = RemotedataUserAuthContextRequestCaseClasses

  def receive = {

    case cc.createUserAuthContextRequest(userId: String, key: String, value: String) =>
      logger.debug("createUserAuthContext(" + userId + ", " + key + ", " + value + ")")
      mapper.createUserAuthContextRequest(userId, key, value) pipeTo sender

    case cc.getUserAuthContextRequests(userId: String) =>
      logger.debug("getUserAuthContexts(" + userId + ")")
      mapper.getUserAuthContextRequests(userId) pipeTo sender
      
    case cc.getUserAuthContextRequestsBox(userId: String) =>
      logger.debug("getUserAuthContextsBox(" + userId + ")")
      sender ! (mapper.getUserAuthContextRequestsBox(userId))
      
    case cc.deleteUserAuthContextRequests(userId: String) =>
      logger.debug(msg=s"deleteUserAuthContexts(${userId})")
      mapper.deleteUserAuthContextRequests(userId) pipeTo sender

    case cc.deleteUserAuthContextRequestById(userAuthContextId: String) =>
      logger.debug(msg=s"deleteUserAuthContextById(${userAuthContextId})")
      mapper.deleteUserAuthContextRequestById(userAuthContextId) pipeTo sender
      
    case cc.checkAnswer(authContextUpdateRequestId: String, challenge: String) =>
      logger.debug("checkAnswer(" + authContextUpdateRequestId + ", " + challenge + ")")
      mapper.checkAnswer(authContextUpdateRequestId, challenge) pipeTo sender

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}


