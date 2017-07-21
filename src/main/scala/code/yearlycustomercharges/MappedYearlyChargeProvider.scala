package code.yearlycustomercharges

import code.model.{CustomerId, BankId}


import code.util.{UUIDString}
import net.liftweb.common.Box
import net.liftweb.mapper._



object MappedYearlyChargeProvider extends YearlyChargeProvider {

//  override protected def getYearlyChargeFromProvider(thingId: YearlyChargeId): Option[YearlyCharge] =
//    MappedYearlyCharge.find(By(MappedYearlyCharge.thingId_, thingId.value))

  override protected def getYearlyChargesFromProvider(bankId: BankId, customerId: CustomerId, year: Int): Option[List[YearlyCharge]] = {
    Some(MappedYearlyCharge.findAll(By(MappedYearlyCharge.bankId_, bankId.value), By(MappedYearlyCharge.customerId_, customerId.value)))
  }
}

class MappedYearlyCharge extends YearlyCharge with LongKeyedMapper[MappedYearlyCharge] with IdPK {

  override def getSingleton = MappedYearlyCharge

  object bankId_ extends UUIDString(this)
  object customerId_ extends UUIDString(this)

  object year_ extends MappedInt(this)



  //override def yearlyChargeId: YearlyChargeId = YearlyChargeId(id.get)
  override def year: Int = year_.get


  //  override def getSingleton = MappedYearlyCustomerCharge
  //
 // WIP
  //  object mCustomerNumber extends MappedString(this,123)
  //
  //  object mYear extends MappedInt(this)
  //
  //  object mCategoryId extends UUIDString(this)
  //  object mForcastIndictor extends MappedString(this,123)
  //  object mTypeId extends MappedString(this,123)
  //  object mNatureId extends UUIDString(this)
  //
  //
  //  object mCharge_Currency extends MappedString(this,3)
  //  object mCharge_Amount extends MappedString(this,32)
  //
  //  object mUpdateDate extends MappedDateTime(this)
  //
  //
  //  //override def bankId: String = mBankId.get
  //  override def customerId: String = mCustomerId.get // id.toString
  //  override def customerNumber: String = mCustomerNumber.get
  //  override def year: Integer = mYear.get
  //
  //  override def categoryId: String = mCategoryId.get
  //  override def forcastIndictor: String = mForcastIndictor.get
  //  override def typeId: String = mTypeId.get
  //  override def natureId : String = mNatureId.get
  //
  //  override def charge: AmountOfMoney = AmountOfMoney(mCharge_Currency.get, mCharge_Amount.get)
  //  override def updateDate : Date = mUpdateDate.get




}


object MappedYearlyCharge extends MappedYearlyCharge with LongKeyedMetaMapper[MappedYearlyCharge] {
  override def dbIndexes = UniqueIndex(bankId_, customerId_, year_) :: Index(bankId_) :: super.dbIndexes
}

