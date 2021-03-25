package code.remotedata

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.context.{ConsentAuthContextProvider, RemotedataConsentAuthContextCaseClasses}
import com.openbankproject.commons.model.{BasicUserAuthContext, ConsentAuthContext}
import net.liftweb.common.Box

import scala.collection.immutable.List
import scala.concurrent.Future


object RemotedataConsentAuthContext extends ObpActorInit with ConsentAuthContextProvider {

  val cc = RemotedataConsentAuthContextCaseClasses

  def getConsentAuthContexts(consentId: String): Future[Box[List[ConsentAuthContext]]] =
    (actor ? cc.getConsentAuthContexts(consentId)).mapTo[Box[List[ConsentAuthContext]]]
  
  def getConsentAuthContextsBox(consentId: String): Box[List[ConsentAuthContext]] = getValueFromFuture(
    (actor ? cc.getConsentAuthContextsBox(consentId)).mapTo[Box[List[ConsentAuthContext]]]
  )  
  def createOrUpdateConsentAuthContexts(consentId: String, consentAuthContexts: List[BasicUserAuthContext]): Box[List[ConsentAuthContext]] = getValueFromFuture(
    (actor ? cc.createOrUpdateConsentAuthContexts(consentId, consentAuthContexts)).mapTo[Box[List[ConsentAuthContext]]]
  )

  def createConsentAuthContext(consentId: String, key: String, value: String): Future[Box[ConsentAuthContext]] =
    (actor ? cc.createConsentAuthContext(consentId, key, value)).mapTo[Box[ConsentAuthContext]]

  override def deleteConsentAuthContexts(consentId: String): Future[Box[Boolean]] =
    (actor ? cc.deleteConsentAuthContexts(consentId)).mapTo[Box[Boolean]]

  override def deleteConsentAuthContextById(consentAuthContextId: String): Future[Box[Boolean]] =
    (actor ? cc.deleteConsentAuthContextById(consentAuthContextId)).mapTo[Box[Boolean]]
}
