/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

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


