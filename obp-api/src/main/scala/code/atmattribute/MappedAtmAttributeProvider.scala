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

package code.atmattribute

import code.util.{MappedUUID, UUIDString}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.enums.AtmAttributeType
import com.openbankproject.commons.model.{AtmAttributeTrait, AtmId, BankId}
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper.{MappedBoolean, _}
import net.liftweb.util.Helpers.tryo

import scala.concurrent.Future


object AtmAttributeProvider extends AtmAttributeProviderTrait {

  override def getAtmAttributesFromProvider(bankId: BankId, atmId: AtmId): Future[Box[List[AtmAttribute]]] =
    Future {
      Box !! AtmAttribute.findAll(
        By(AtmAttribute.BankId_, bankId.value),
        By(AtmAttribute.AtmId_, atmId.value)
      )
    }

  override def getAtmAttributeById(AtmAttributeId: String): Future[Box[AtmAttribute]] = Future {
    AtmAttribute.find(By(AtmAttribute.AtmAttributeId, AtmAttributeId))
  }

  override def createOrUpdateAtmAttribute(bankId: BankId,
                                          atmId: AtmId,
                                          AtmAttributeId: Option[String],
                                          name: String,
                                          attributeType: AtmAttributeType.Value,
                                          value: String,
                                          isActive: Option[Boolean]): Future[Box[AtmAttribute]] =  {
     AtmAttributeId match {
      case Some(id) => Future {
        AtmAttribute.find(By(AtmAttribute.AtmAttributeId, id)) match {
            case Full(attribute) => tryo {
              attribute
                .BankId_(bankId.value)
                .AtmId_(atmId.value)
                .Name(name)
                .Type(attributeType.toString)
                .`Value`(value)
                .IsActive(isActive.getOrElse(true))
                .saveMe()
            }
            case _ => Empty
          }
      }
      case None => Future {
        Full {
          AtmAttribute.create
            .BankId_(bankId.value)
            .AtmId_(atmId.value)
            .Name(name)
            .Type(attributeType.toString())
            .`Value`(value)
            .IsActive(isActive.getOrElse(true))
            .saveMe()
        }
      }
    }
  }

  override def deleteAtmAttribute(AtmAttributeId: String): Future[Box[Boolean]] = Future {
    tryo (
      AtmAttribute.bulkDelete_!!(By(AtmAttribute.AtmAttributeId, AtmAttributeId))
    )
  }

  override def deleteAtmAttributesByAtmId(atmId: AtmId): Future[Box[Boolean]]= Future {
    tryo(
      AtmAttribute.bulkDelete_!!(By(AtmAttribute.AtmId_, atmId.value))
    )
  }
}

class AtmAttribute extends AtmAttributeTrait with LongKeyedMapper[AtmAttribute] with IdPK {

  override def getSingleton = AtmAttribute

  object BankId_ extends UUIDString(this) {
    override def dbColumnName = "BankId"
  }
  object AtmId_ extends UUIDString(this) {
    override def dbColumnName = "AtmId"
  }
  object AtmAttributeId extends MappedUUID(this)
  object Name extends MappedString(this, 50)
  object Type extends MappedString(this, 50)
  object `Value` extends MappedString(this, 255)
  object IsActive extends MappedBoolean(this) {
    override def defaultValue = true
  }


  override def bankId: BankId = BankId(BankId_.get)
  override def atmId: AtmId = AtmId(AtmId_.get)
  override def atmAttributeId: String = AtmAttributeId.get
  override def name: String = Name.get
  override def attributeType: AtmAttributeType.Value = AtmAttributeType.withName(Type.get)
  override def value: String = `Value`.get
  override def isActive: Option[Boolean] = if (IsActive.jdbcFriendly(IsActive.calcFieldName) == null) { None } else Some(IsActive.get)
  
}

object AtmAttribute extends AtmAttribute with LongKeyedMetaMapper[AtmAttribute] {
  override def dbIndexes: List[BaseIndex[AtmAttribute]] = Index(BankId_, AtmId_) :: super.dbIndexes
}

