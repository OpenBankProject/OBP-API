package code.remotedata

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.api.pemusage.{MappedPemUsageProvider, RemotedatPemUsageCaseClasses}
import code.util.Helper.MdcLoggable

class RemotedataPemUsageActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedPemUsageProvider
  val cc = RemotedatPemUsageCaseClasses

  def receive: PartialFunction[Any, Unit] = {
        
      
    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}


