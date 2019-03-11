package code.nonce

import java.util.Date

import code.api.util.APIUtil
import code.model.{MappedNonceProvider, Nonce}
import code.remotedata.RemotedataNonces
import net.liftweb.common.Box
import net.liftweb.util.{Props, SimpleInjector}

import scala.concurrent.Future


object Nonces extends SimpleInjector {

  val nonces = new Inject(buildOne _) {}

  def buildOne: NoncesProvider =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
      case false  => MappedNonceProvider
      case true => RemotedataNonces     // We will use Akka as a middleware
    }

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

class RemotedataNoncesCaseClasses {
  case class createNonce(id: Option[Long],
                         consumerKey: Option[String],
                         tokenKey: Option[String],
                         timestamp: Option[Date],
                         value: Option[String])
  case class deleteExpiredNonces(currentDate: Date)
  case class countNonces(consumerKey: String,
                         tokenKey: String,
                         timestamp: Date,
                         value: String)
  case class countNoncesFuture(consumerKey: String,
                               tokenKey: String,
                               timestamp: Date,
                               value: String)
}

object RemotedataNoncesCaseClasses extends RemotedataNoncesCaseClasses
