package code.cardattribute

/* For CardAttribute */

import code.api.util.APIUtil
import code.remotedata.RemotedataCardAttribute
import com.openbankproject.commons.model.enums.CardAttributeType
import com.openbankproject.commons.model.{AccountId, BankId, CardAttribute, ProductCode}
import net.liftweb.common.{Box, Logger}
import net.liftweb.util.SimpleInjector

import scala.concurrent.Future

object CardAttributeX extends SimpleInjector {

  val cardAttributeProvider = new Inject(buildOne _) {}

  def buildOne: CardAttributeProvider =
    APIUtil.getPropsAsBoolValue("use_akka", false) match {
      case false  => MappedCardAttributeProvider
      case true => RemotedataCardAttribute     // We will use Akka as a middleware
    }

  // Helper to get the count out of an option
  def countOfCardAttribute(listOpt: Option[List[CardAttribute]]): Int = {
    val count = listOpt match {
      case Some(list) => list.size
      case None => 0
    }
    count
  }


}

trait CardAttributeProvider {

  private val logger = Logger(classOf[CardAttributeProvider])

  def getCardAttributesFromProvider(cardId: String): Future[Box[List[CardAttribute]]]

  def getCardAttributeById(cardAttributeId: String): Future[Box[CardAttribute]]

  def createOrUpdateCardAttribute(
    bankId: Option[BankId],
    cardId: Option[String],
    cardAttributeId: Option[String],
    name: String,
    attributeType: CardAttributeType.Value,
    value: String
  ): Future[Box[CardAttribute]]
  
  def deleteCardAttribute(cardAttributeId: String): Future[Box[Boolean]]
  // End of Trait
}

class RemotedataCardAttributeCaseClasses {
  case class getCardAttributesFromProvider(cardId: String)

  case class getCardAttributeById(cardAttributeId: String)

  case class createOrUpdateCardAttribute(
    bankId: Option[BankId],
    cardId: Option[String],
    cardAttributeId: Option[String],
    name: String,
    attributeType: CardAttributeType.Value,
    value: String
  )

  case class deleteCardAttribute(cardAttributeId: String)
}

object RemotedataCardAttributeCaseClasses extends RemotedataCardAttributeCaseClasses
