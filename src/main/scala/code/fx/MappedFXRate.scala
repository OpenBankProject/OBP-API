package code.fx

import java.util.Date

import code.model.BankId
import code.util.{UUIDString}
import net.liftweb.mapper.{MappedStringForeignKey, _}

class MappedFXRate extends FXRate with LongKeyedMapper[MappedFXRate] with IdPK {
  def getSingleton = MappedFXRate

  object mBankId extends UUIDString(this)

  object mFromCurrencyCode extends MappedStringForeignKey(this, MappedCurrency, 3) {
    override def foreignMeta = MappedCurrency
  }

  object mToCurrencyCode extends MappedStringForeignKey(this, MappedCurrency, 3) {
    override def foreignMeta = MappedCurrency
  }



  object mConversionValue extends MappedDouble(this)

  object mInverseConversionValue extends MappedDouble(this)

  object mEffectiveDate extends MappedDateTime(this)

  override def bankId: BankId = BankId(mBankId.get)

  override def fromCurrencyCode: String = mFromCurrencyCode.get

  override def toCurrencyCode: String = mToCurrencyCode.get

  override def conversionValue: Double = mConversionValue.get

  override def inverseConversionValue: Double = mInverseConversionValue.get

  override def effectiveDate: Date = mEffectiveDate.get
}

object MappedFXRate extends MappedFXRate with LongKeyedMetaMapper[MappedFXRate] {}

trait FXRate {

  def bankId : BankId

  def fromCurrencyCode: String

  def toCurrencyCode: String

  def conversionValue: Double

  def inverseConversionValue: Double

  def effectiveDate: Date
}
