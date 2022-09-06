package code.bankattribute

import code.util.{MappedUUID, UUIDString}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.enums.BankAttributeType
import com.openbankproject.commons.model.{BankAttributeTrait, BankId}
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper.{MappedBoolean, _}
import net.liftweb.util.Helpers.tryo

import scala.concurrent.Future


object BankAttributeProvider extends BankAttributeProviderTrait {

  override def getBankAttributesFromProvider(bankId: BankId): Future[Box[List[BankAttribute]]] =
    Future {
      Box !!  BankAttribute.findAll(
          By(BankAttribute.BankId_, bankId.value)
        )
    }

  override def getBankAttributeById(bankAttributeId: String): Future[Box[BankAttribute]] = Future {
    BankAttribute.find(By(BankAttribute.BankAttributeId, bankAttributeId))
  }

  override def createOrUpdateBankAttribute(bankId: BankId,
                                              bankAttributeId: Option[String],
                                              name: String,
                                              attributType: BankAttributeType.Value,
                                              value: String,
                                              isActive: Option[Boolean]): Future[Box[BankAttribute]] =  {
     bankAttributeId match {
      case Some(id) => Future {
        BankAttribute.find(By(BankAttribute.BankAttributeId, id)) match {
            case Full(attribute) => tryo {
              attribute.BankId_(bankId.value)
                .Name(name)
                .Type(attributType.toString)
                .`Value`(value)
                .IsActive(isActive.getOrElse(true))
                .saveMe()
            }
            case _ => Empty
          }
      }
      case None => Future {
        Full {
          BankAttribute.create
            .BankId_(bankId.value)
            .Name(name)
            .Type(attributType.toString())
            .`Value`(value)
            .IsActive(isActive.getOrElse(true))
            .saveMe()
        }
      }
    }
  }

  override def deleteBankAttribute(bankAttributeId: String): Future[Box[Boolean]] = Future {
    Some(
      BankAttribute.bulkDelete_!!(By(BankAttribute.BankAttributeId, bankAttributeId))
    )
  }
}

class BankAttribute extends BankAttributeTrait with LongKeyedMapper[BankAttribute] with IdPK {

  override def getSingleton = BankAttribute

  object BankId_ extends UUIDString(this) // combination of this
  object BankAttributeId extends MappedUUID(this)
  object Name extends MappedString(this, 50)
  object Type extends MappedString(this, 50)
  object `Value` extends MappedString(this, 255)
  object IsActive extends MappedBoolean(this) {
    override def defaultValue = true
  }


  override def bankId: BankId = BankId(BankId_.get)
  override def bankAttributeId: String = BankAttributeId.get
  override def name: String = Name.get
  override def attributeType: BankAttributeType.Value = BankAttributeType.withName(Type.get)
  override def value: String = `Value`.get
  override def isActive: Option[Boolean] = if (IsActive.jdbcFriendly(IsActive.calcFieldName) == null) { None } else Some(IsActive.get)
  
}

object BankAttribute extends BankAttribute with LongKeyedMetaMapper[BankAttribute] {
  override def dbIndexes = Index(BankId_) :: super.dbIndexes
}

