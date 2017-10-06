package code.remotedata

import java.util.Date

import akka.actor.Actor
import code.actorsystem.ObpActorHelper
import code.model._
import code.nonce.RemotedataNoncesCaseClasses
import code.util.Helper.MdcLoggable

class RemotedataNoncesActor extends Actor with ObpActorHelper with MdcLoggable {

  val mapper = MappedNonceProvider
  val cc = RemotedataNoncesCaseClasses

  def receive = {

    case cc.createNonce(id: Option[Long],
                        consumerKey: Option[String],
                        tokenKey: Option[String],
                        timestamp: Option[Date],
                        value: Option[String]) =>
      logger.debug("createNonce(" + id + ", " +
                                    consumerKey+ ", " +
                                    tokenKey + ", " +
                                    timestamp + ", " +
                                    value + ")")
      sender ! extractResult(mapper.createNonce(id, consumerKey, tokenKey, timestamp, value))

    case cc.deleteExpiredNonces(currentDate: Date) =>
      logger.debug("deleteExpiredNonces(" + currentDate +")")
      sender ! extractResult(mapper.deleteExpiredNonces(currentDate))

    case cc.countNonces(consumerKey: String,
                        tokenKey: String,
                        timestamp: Date,
                        value: String) =>
      logger.debug("countNonces(" + consumerKey + ", " +
                                    tokenKey+ ", " +
                                    timestamp + ", " +
                                    value + ", " +
                                    ")")
      sender ! extractResult(mapper.countNonces(consumerKey, tokenKey, timestamp, value))

    case cc.countNoncesFuture(consumerKey: String,
                              tokenKey: String,
                              timestamp: Date,
                              value: String) =>
      logger.debug("countNoncesFuture(" + consumerKey + ", " +
                                          tokenKey+ ", " +
                                          timestamp + ", " +
                                          value + ", " +
                                          ")")
      sender ! (mapper.countNonces(consumerKey, tokenKey, timestamp, value))


    case message => logger.warn("[AKKA ACTOR ERROR - REQUEST NOT RECOGNIZED] " + message)

  }

}


