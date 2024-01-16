package code.signingbaskets

import com.openbankproject.commons.model.SigningBasketTrait
import net.liftweb.common.{Box, Logger}
import net.liftweb.util.SimpleInjector

object SigningBasketX extends SimpleInjector {
  val signingBasketProvider: SigningBasketX.Inject[SigningBasketProvider] = new Inject(buildOne _) {}
  private def buildOne: SigningBasketProvider = MappedSigningBasketProvider
}

trait SigningBasketProvider {

  private val logger = Logger(classOf[SigningBasketProvider])

  def getSigningBaskets(): List[SigningBasketTrait]

  def getSigningBasketByBasketId(entityId: String): Box[SigningBasketTrait]

  def createSigningBasket(basketId: Option[String],
                          status: Option[String],
                          description: Option[String],
                         ): Box[SigningBasketTrait]

  def deleteSigningBasket(id: String): Box[Boolean]

}