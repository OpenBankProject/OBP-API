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

package code.regulatedentities.attribute

import code.util.{MappedUUID, UUIDString}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.enums.RegulatedEntityAttributeType
import com.openbankproject.commons.model.{RegulatedEntityAttributeTrait, RegulatedEntityId}
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper.{MappedBoolean, _}
import net.liftweb.util.Helpers.tryo

import scala.concurrent.Future


object RegulatedEntityAttributeProvider extends RegulatedEntityAttributeProviderTrait {

  override def getRegulatedEntityAttributes(regulatedEntityId: RegulatedEntityId): Future[Box[List[RegulatedEntityAttribute]]] =
    Future {
      Box !! RegulatedEntityAttribute.findAll(
        By(RegulatedEntityAttribute.RegulatedEntityId_, regulatedEntityId.value)
      )
    }

  override def getRegulatedEntityAttributeById(RegulatedEntityAttributeId: String): Future[Box[RegulatedEntityAttribute]] = Future {
    RegulatedEntityAttribute.find(By(RegulatedEntityAttribute.RegulatedEntityAttributeId, RegulatedEntityAttributeId))
  }

  override def createOrUpdateRegulatedEntityAttribute(
    regulatedEntityId: RegulatedEntityId,
    RegulatedEntityAttributeId: Option[String],
    name: String,
    attributeType: RegulatedEntityAttributeType.Value,
    value: String,
    isActive: Option[Boolean]
  ): Future[Box[RegulatedEntityAttribute]] =  {
     RegulatedEntityAttributeId match {
      case Some(id) => Future {
        RegulatedEntityAttribute.find(By(RegulatedEntityAttribute.RegulatedEntityAttributeId, id)) match {
            case Full(attribute) => tryo {
              attribute
                .RegulatedEntityId_(regulatedEntityId.value)
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
          RegulatedEntityAttribute.create
            .RegulatedEntityId_(regulatedEntityId.value)
            .Name(name)
            .Type(attributeType.toString())
            .`Value`(value)
            .IsActive(isActive.getOrElse(true))
            .saveMe()
        }
      }
    }
  }

  override def deleteRegulatedEntityAttribute(RegulatedEntityAttributeId: String): Future[Box[Boolean]] = Future {
    tryo (
      RegulatedEntityAttribute.bulkDelete_!!(By(RegulatedEntityAttribute.RegulatedEntityAttributeId, RegulatedEntityAttributeId))
    )
  }

  override def deleteRegulatedEntityAttributesByRegulatedEntityId(regulatedEntityId: RegulatedEntityId): Future[Box[Boolean]]= Future {
    tryo(
      RegulatedEntityAttribute.bulkDelete_!!(By(RegulatedEntityAttribute.RegulatedEntityId_, regulatedEntityId.value))
    )
  }
}

class RegulatedEntityAttribute extends RegulatedEntityAttributeTrait with LongKeyedMapper[RegulatedEntityAttribute] with IdPK {

  override def getSingleton = RegulatedEntityAttribute

  object RegulatedEntityId_ extends UUIDString(this) {
    override def dbColumnName = "RegulatedEntityId"
  }
  object RegulatedEntityAttributeId extends MappedUUID(this)
  object Name extends MappedString(this, 50)
  object Type extends MappedString(this, 50)
  object `Value` extends MappedString(this, 255)
  object IsActive extends MappedBoolean(this) {
    override def defaultValue = true
  }

  override def regulatedEntityId: RegulatedEntityId = RegulatedEntityId(RegulatedEntityId_.get)
  override def regulatedEntityAttributeId: String = RegulatedEntityAttributeId.get
  override def name: String = Name.get
  override def attributeType: RegulatedEntityAttributeType.Value = RegulatedEntityAttributeType.withName(Type.get)
  override def value: String = `Value`.get
  override def isActive: Option[Boolean] = if (IsActive.jdbcFriendly(IsActive.calcFieldName) == null) { None } else Some(IsActive.get)
  
}

object RegulatedEntityAttribute extends RegulatedEntityAttribute with LongKeyedMetaMapper[RegulatedEntityAttribute] {
  override def dbIndexes: List[BaseIndex[RegulatedEntityAttribute]] = Index(RegulatedEntityId_) :: super.dbIndexes
}

