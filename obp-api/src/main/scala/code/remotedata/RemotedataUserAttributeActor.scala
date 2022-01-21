package code.remotedata

import akka.actor.Actor
import akka.pattern.pipe
import code.actorsystem.ObpActorHelper
import code.users.{MappedUserAttributeProvider, RemotedataUserAttributeCaseClasses}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.enums.UserAttributeType

class RemotedataUserAttributeActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedUserAttributeProvider
  val cc = RemotedataUserAttributeCaseClasses

  def receive: PartialFunction[Any, Unit] = {

    case cc.getUserAttributesByUser(userId: String) =>
      logger.debug(s"getUserAttributesByUser(${userId})")
      mapper.getUserAttributesByUser(userId) pipeTo sender

    case cc.createOrUpdateUserAttribute(userId: String, userAttributeId: Option[String], name: String, attributeType: UserAttributeType.Value, value: String) =>
      logger.debug(s"createOrUpdateUserAttribute(${userId}, ${userAttributeId}, ${name}, ${attributeType}, ${value})")
      mapper.createOrUpdateUserAttribute(userId, userAttributeId, name, attributeType, value) pipeTo sender

    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)
  }

}


