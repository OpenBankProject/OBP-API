package code.signingbaskets


import com.openbankproject.commons.model.{SigningBasketContent, SigningBasketTrait}
import net.liftweb.common.{Box, Logger}
import net.liftweb.util.SimpleInjector
import code.util.Helper.MdcLoggable

object SigningBasketX extends SimpleInjector {
  val signingBasketProvider: SigningBasketX.Inject[SigningBasketProvider] = new Inject(buildOne _) {}
  private def buildOne: SigningBasketProvider = MappedSigningBasketProvider
}

trait SigningBasketProvider extends MdcLoggable {

  def getSigningBaskets(): List[SigningBasketTrait]

  def getSigningBasketByBasketId(entityId: String): Box[SigningBasketContent]
  def saveSigningBasketStatus(entityId: String, status: String): Box[SigningBasketContent]

  def createSigningBasket(paymentIds: Option[List[String]],
                          consentIds: Option[List[String]],
                         ): Box[SigningBasketTrait]

  def deleteSigningBasket(id: String): Box[Boolean]

}
