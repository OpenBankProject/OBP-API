package code.remotedata

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.sanitycheck.{RemotedataSanityCheckCaseClasses, SanityChecksImpl}
import code.util.Helper.MdcLoggable

class RemotedataSanityCheckActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = SanityChecksImpl
  val cc = RemotedataSanityCheckCaseClasses

  def receive = {

    case cc.remoteAkkaSanityCheck(remoteDataSecret: String) =>
      logger.debug("remoteAkkaSanityCheck()")
      sender ! extractResult(mapper.remoteAkkaSanityCheck(remoteDataSecret))

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}

