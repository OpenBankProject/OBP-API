package code.context

import code.api.util.APIUtil
import com.openbankproject.commons.model.{BasicUserAuthContext, ConsentAuthContext}
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

import scala.collection.immutable.List
import scala.concurrent.Future


object ConsentAuthContextProvider extends SimpleInjector {

  val consentAuthContextProvider = new Inject(buildOne _) {}

  def buildOne: ConsentAuthContextProvider = MappedConsentAuthContextProvider
  
}

trait ConsentAuthContextProvider {
  def createConsentAuthContext(consentId: String, key: String, value: String): Future[Box[ConsentAuthContext]]
  def getConsentAuthContexts(consentId: String): Future[Box[List[ConsentAuthContext]]]
  def getConsentAuthContextsBox(consentId: String): Box[List[ConsentAuthContext]]
  def createOrUpdateConsentAuthContexts(consentId: String, userAuthContexts: List[BasicUserAuthContext]): Box[List[ConsentAuthContext]]
  def deleteConsentAuthContexts(consentId: String): Future[Box[Boolean]]
  def deleteConsentAuthContextById(consentAuthContextId: String): Future[Box[Boolean]]
}