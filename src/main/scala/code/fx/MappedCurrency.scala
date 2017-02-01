package code.fx

import net.liftweb.mapper._

class MappedCurrency extends Currency with KeyedMapper[String, MappedCurrency]{
  def getSingleton = MappedCurrency

  object mCurrencyCode extends MappedStringIndex(this, 3){
    override def dbNotNull_? = true
    override def dbIndexed_? = true
  }

  object mCurrencyName extends MappedString(this, 50)

  object mCurrencySymbol extends MappedString(this, 3)

  override def currencyCode: String = mCurrencyCode.get

  override def currencyName: String = mCurrencyName.get

  override def currencySymbol: String = mCurrencySymbol.get

  override def primaryKeyField: MappedField[String, MappedCurrency] with IndexedField[String] = mCurrencyCode
}

object MappedCurrency extends MappedCurrency with KeyedMetaMapper[String, MappedCurrency]{}

trait Currency {
  def currencyCode: String

  def currencyName: String

  def currencySymbol: String
}
