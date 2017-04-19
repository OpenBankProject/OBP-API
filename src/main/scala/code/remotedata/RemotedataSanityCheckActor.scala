package code.remotedata

import akka.actor.Actor
import akka.event.Logging
import code.sanitycheck.{RemotedataSanityCheckCaseClasses, SanityChecksImpl}


class RemotedataSanityCheckActor extends Actor with ActorHelper {

  val logger = Logging(context.system, this)

  val mapper = SanityChecksImpl
  val cc = RemotedataSanityCheckCaseClasses

  def receive = {

    case cc.remoteAkkaSanityCheck(remoteDataSecret: String) =>
      logger.debug("remoteAkkaSanityCheck()")
      sender ! extractResult(mapper.remoteAkkaSanityCheck(remoteDataSecret))

    case message => logger.warning("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}

