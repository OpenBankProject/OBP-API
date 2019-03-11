package code.remotedata

import java.util.Date

import code.model.Nonce
import code.nonce.{NoncesProvider, RemotedataNoncesCaseClasses}
import net.liftweb.common.Box
import akka.pattern.ask
import code.actorsystem.ObpActorInit
import scala.concurrent.Future


object RemotedataNonces extends ObpActorInit with NoncesProvider {

  val cc = RemotedataNoncesCaseClasses

  def createNonce(id: Option[Long],
                  consumerKey: Option[String],
                  tokenKey: Option[String],
                  timestamp: Option[Date],
                  value: Option[String]): Box[Nonce] = getValueFromFuture(
    (actor ? cc.createNonce(id, consumerKey, tokenKey, timestamp, value)).mapTo[Box[Nonce]]
  )

  def deleteExpiredNonces(currentDate: Date): Boolean = getValueFromFuture(
    (actor ? cc.deleteExpiredNonces(currentDate)).mapTo[Boolean]
  )

  def countNonces(consumerKey: String,
                  tokenKey: String,
                  timestamp: Date,
                  value: String): Long = getValueFromFuture(
    (actor ? cc.countNonces(consumerKey, tokenKey, timestamp, value)).mapTo[Long]
  )

  def countNoncesFuture(consumerKey: String,
                        tokenKey: String,
                        timestamp: Date,
                        value: String): Future[Long] =
    (actor ? cc.countNonces(consumerKey, tokenKey, timestamp, value)).mapTo[Long]


}
