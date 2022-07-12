package code.consent

import code.model.Consumer
import com.openbankproject.commons.model.User
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

object ConsentRequests extends SimpleInjector {
  val consentRequestProvider = new Inject(buildOne _) {}
  def buildOne: ConsentRequestProvider = MappedConsentRequestProvider
}

trait ConsentRequestProvider {
  def getConsentRequestById(consentRequestId: String): Box[ConsentRequest]
  def createConsentRequest(consumer: Option[Consumer], payload: Option[String]): Box[ConsentRequest]
}

trait ConsentRequestTrait {
  def consentRequestId: String
  def payload: String
  def consumerId: String
}









