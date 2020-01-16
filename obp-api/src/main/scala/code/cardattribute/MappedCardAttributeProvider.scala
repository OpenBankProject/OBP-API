package code.cardattribute

import code.util.{MappedUUID, UUIDString}
import com.openbankproject.commons.model.enums.CardAttributeType
import com.openbankproject.commons.model.{BankId, CardAttribute}
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

import com.openbankproject.commons.ExecutionContext.Implicits.global
import scala.concurrent.Future


object MappedCardAttributeProvider extends CardAttributeProvider {

  override def getCardAttributesFromProvider(cardId: String): Future[Box[List[CardAttribute]]] =
    Future {
      Box !!  MappedCardAttribute.findAll(By(MappedCardAttribute.mCardId, cardId))
    }

  override def getCardAttributeById(cardAttributeId: String): Future[Box[CardAttribute]] = Future {
    MappedCardAttribute.find(By(MappedCardAttribute.mCardAttributeId, cardAttributeId))
  }

  override def createOrUpdateCardAttribute(
    bankId: Option[BankId],
    cardId: Option[String],
    cardAttributeId: Option[String],
    name: String,
    attributeType: CardAttributeType.Value,
    value: String
  ): Future[Box[CardAttribute]] =  {
    cardAttributeId match {
      case Some(id) => Future {
        MappedCardAttribute.find(By(MappedCardAttribute.mCardAttributeId, id)) match {
            case Full(attribute) => tryo {
              attribute
                .mCardId(cardId.getOrElse(null))
                .mBankId(bankId.map(_.value).getOrElse(null))
                .mName(name)
                .mType(attributeType.toString)
                .mValue(value)
                .saveMe()
            }
            case _ => Empty
          }
      }
      case None => Future {
        Full {
          MappedCardAttribute.create
            .mCardId(cardId.getOrElse(null))
            .mBankId(bankId.map(_.value).getOrElse(null))
            .mName(name)
            .mType(attributeType.toString())
            .mValue(value)
            .saveMe()
        }
      }
    }
  }

  override def deleteCardAttribute(cardAttributeId: String): Future[Box[Boolean]] = Future {
    Some(
      MappedCardAttribute.bulkDelete_!!(By(MappedCardAttribute.mCardAttributeId, cardAttributeId))
    )
  }
}


