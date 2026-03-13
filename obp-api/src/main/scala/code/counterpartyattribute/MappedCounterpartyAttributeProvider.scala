package code.counterpartyattribute

import code.util.{MappedUUID, UUIDString}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.enums.CounterpartyAttributeType
import com.openbankproject.commons.model.{CounterpartyAttributeTrait, CounterpartyId}
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper.{MappedBoolean, _}
import net.liftweb.util.Helpers.tryo

import scala.concurrent.Future


object CounterpartyAttributeProvider extends CounterpartyAttributeProviderTrait {

  override def getCounterpartyAttributes(counterpartyId: CounterpartyId): Future[Box[List[CounterpartyAttribute]]] =
    Future {
      Box !! CounterpartyAttribute.findAll(
        By(CounterpartyAttribute.CounterpartyId_, counterpartyId.value)
      )
    }

  override def getCounterpartyAttributeById(counterpartyAttributeId: String): Future[Box[CounterpartyAttribute]] = Future {
    CounterpartyAttribute.find(By(CounterpartyAttribute.CounterpartyAttributeId, counterpartyAttributeId))
  }

  override def createOrUpdateCounterpartyAttribute(
    counterpartyId: CounterpartyId,
    counterpartyAttributeId: Option[String],
    name: String,
    attributeType: CounterpartyAttributeType.Value,
    value: String,
    isActive: Option[Boolean]
  ): Future[Box[CounterpartyAttribute]] = {
    counterpartyAttributeId match {
      case Some(id) => Future {
        CounterpartyAttribute.find(By(CounterpartyAttribute.CounterpartyAttributeId, id)) match {
          case Full(attribute) => tryo {
            attribute
              .CounterpartyId_(counterpartyId.value)
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
          CounterpartyAttribute.create
            .CounterpartyId_(counterpartyId.value)
            .Name(name)
            .Type(attributeType.toString())
            .`Value`(value)
            .IsActive(isActive.getOrElse(true))
            .saveMe()
        }
      }
    }
  }

  override def deleteCounterpartyAttribute(counterpartyAttributeId: String): Future[Box[Boolean]] = Future {
    tryo(
      CounterpartyAttribute.bulkDelete_!!(By(CounterpartyAttribute.CounterpartyAttributeId, counterpartyAttributeId))
    )
  }

  override def deleteCounterpartyAttributesByCounterpartyId(counterpartyId: CounterpartyId): Future[Box[Boolean]] = Future {
    tryo(
      CounterpartyAttribute.bulkDelete_!!(By(CounterpartyAttribute.CounterpartyId_, counterpartyId.value))
    )
  }
}

class CounterpartyAttribute extends CounterpartyAttributeTrait with LongKeyedMapper[CounterpartyAttribute] with IdPK {

  override def getSingleton = CounterpartyAttribute

  object CounterpartyId_ extends UUIDString(this) {
    override def dbColumnName = "CounterpartyId"
  }
  object CounterpartyAttributeId extends MappedUUID(this)
  object Name extends MappedString(this, 50)
  object Type extends MappedString(this, 50)
  object `Value` extends MappedString(this, 255)
  object IsActive extends MappedBoolean(this) {
    override def defaultValue = true
  }

  override def counterpartyId: CounterpartyId = CounterpartyId(CounterpartyId_.get)
  override def counterpartyAttributeId: String = CounterpartyAttributeId.get
  override def name: String = Name.get
  override def attributeType: CounterpartyAttributeType.Value = CounterpartyAttributeType.withName(Type.get)
  override def value: String = `Value`.get
  override def isActive: Option[Boolean] = if (IsActive.jdbcFriendly(IsActive.calcFieldName) == null) { None } else Some(IsActive.get)

}

object CounterpartyAttribute extends CounterpartyAttribute with LongKeyedMetaMapper[CounterpartyAttribute] {
  override def dbIndexes: List[BaseIndex[CounterpartyAttribute]] = Index(CounterpartyId_) :: super.dbIndexes
}
