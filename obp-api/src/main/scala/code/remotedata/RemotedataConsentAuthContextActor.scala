package code.remotedata

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.context.{MappedConsentAuthContextProvider, MappedUserAuthContextProvider, RemotedataConsentAuthContextCaseClasses, RemotedataUserAuthContextCaseClasses}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.BasicUserAuthContext

import scala.collection.immutable.List

class RemotedataConsentAuthContextActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedConsentAuthContextProvider
  val cc = RemotedataConsentAuthContextCaseClasses

  def receive = {

    case cc.createConsentAuthContext(consentId: String, key: String, value: String) =>
      logger.debug(s"createConsentAuthContext($consentId, $key, $value)")
      sender ! (mapper.createConsentAuthContextAkka(consentId, key, value))

    case cc.getConsentAuthContexts(consentId: String) =>
      logger.debug(s"getConsentAuthContexts($consentId)")
      sender ! (mapper.getConsentAuthContextsBox(consentId))
      
    case cc.getConsentAuthContextsBox(consentId: String) =>
      logger.debug(s"getConsentAuthContextsBox($consentId)")
      sender ! (mapper.getConsentAuthContextsBox(consentId))   
      
    case cc.createOrUpdateConsentAuthContexts(consentId: String, consentAuthContexts: List[BasicUserAuthContext]) =>
      logger.debug(s"createOrUpdateConsentAuthContexts($consentId, $consentAuthContexts)")
      sender ! (mapper.createOrUpdateConsentAuthContexts(consentId, consentAuthContexts))
      
    case cc.deleteConsentAuthContexts(consentId: String) =>
      logger.debug(msg=s"deleteUserConsentContexts(${consentId})")
      sender ! (mapper.deleteConsentAuthContextsAkka(consentId))

    case cc.deleteConsentAuthContextById(consentAuthContextId: String) =>
      logger.debug(msg=s"deleteConsentAuthContextById(${consentAuthContextId})")
      sender ! (mapper.deleteConsentAuthContextByIdAkka(consentAuthContextId))

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}


