package code.cardattribute

import code.util.{MappedUUID, UUIDString}
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.CardAttributeType
import net.liftweb.mapper._

class MappedCardAttribute extends CardAttribute with LongKeyedMapper[MappedCardAttribute] with IdPK {

  override def getSingleton = MappedCardAttribute

  object mBankId extends UUIDString(this) // combination of this
  object mCardId extends UUIDString(this) // combination of this

  object mCardAttributeId extends MappedUUID(this)

  object mName extends MappedString(this, 50)

  object mType extends MappedString(this, 50)

  object mValue extends MappedString(this, 255)


  override def bankId = Some(BankId(mBankId.get))

  override def cardId = Some(mCardId.get)

  override def cardAttributeId = Some(mCardAttributeId.get)

  override def name: String = mName.get

  override def attributeType: CardAttributeType.Value = CardAttributeType.withName(mType.get)

  override def value: String = mValue.get


}


object MappedCardAttribute extends MappedCardAttribute with LongKeyedMetaMapper[MappedCardAttribute] {
  override def dbIndexes: List[BaseIndex[MappedCardAttribute]] = Index(mCardId) :: Index(mCardAttributeId) :: super.dbIndexes
}

