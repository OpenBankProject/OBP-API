package code.nonce

import java.util.Date

import code.api.util.APIUtil
import code.model.{MappedNonceProvider, Nonce}
import net.liftweb.common.Box
import net.liftweb.util.{Props, SimpleInjector}

import scala.concurrent.Future


object Nonces extends SimpleInjector {

  val nonces = new Inject(buildOne _) {}

  def buildOne: NoncesProvider = MappedNonceProvider
  
}

trait NoncesProvider {
   def createNonce(id: Option[Long],
                   consumerKey: Option[String],
                   tokenKey: Option[String],
                   timestamp: Option[Date],
                   value: Option[String]): Box[Nonce]
  def deleteExpiredNonces(currentDate: Date): Boolean
  def countNonces(consumerKey: String,
                  tokenKey: String,
                  timestamp: Date,
                  value: String): Long
  def countNoncesFuture(consumerKey: String,
                        tokenKey: String,
                        timestamp: Date,
                        value: String): Future[Long]
}