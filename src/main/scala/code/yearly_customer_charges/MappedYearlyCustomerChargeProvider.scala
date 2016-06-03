package code.yearly_customer_charges

import java.util.Date

import code.customer.{CustomerFaceImage, Customer, CustomerProvider, MappedCustomer}
import code.model.{CustomerId, AmountOfMoney, BankId, User}
import code.model.dataAccess.{MappedBank, APIUser}
import code.util.{DefaultStringField, MappedUUID}
import net.liftweb.common.Box
import net.liftweb.mapper._


//
//object MappedYearlyCustomerChargeProvider extends YearlyCustomerChargeProvider {
//
//
//
//  override def getChargesForCustomer(bankId : String, customerId: String): List[YearlyCustomerCharge] = {
//    MappedYearlyCustomerCharge.findAll(
//      By(MappedYearlyCustomerCharge.mBankId, bankId),
//      By(MappedYearlyCustomerCharge.mCustomerId, customerId)
//    )
//  }
//
//
//
//  override def addYearlyCharge(bankId: String, customerId: String) : Box[YearlyCustomerCharge] = {
//
//    val createdCharge = MappedYearlyCustomerCharge.create
//      .customerId("123".toInt).SaveMe
//
//    Some(createdCharge)
//  }
//
//}
//
//
//
//
//class MappedYearlyCustomerCharge extends YearlyCustomerCharge with LongKeyedMapper[MappedYearlyCustomerCharge] with IdPK with CreatedUpdated {
//
//  def getSingleton = MappedYearlyCustomerCharge
//
//  object mBankId extends DefaultStringField(this)
//  object mCustomerId extends DefaultStringField(this)
//  object mCustomerNumber extends DefaultStringField(this)
//
//  object mYear extends MappedInt(this)
//
//  object mCategoryId extends DefaultStringField(this)
//  object mForcastIndictor extends DefaultStringField(this)
//  object mTypeId extends DefaultStringField(this)
//  object mNatureId extends DefaultStringField(this)
//
//
//  object mCharge_Currency extends DefaultStringField(this)
//  object mCharge_Amount extends DefaultStringField(this)
//
//  object mUpdateDate extends MappedDateTime(this)
//
//
//  override def bankId: String = mBankId.get
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
//
//
//}
//
//object MappedYearlyCustomerCharge extends MappedYearlyCustomerCharge with LongKeyedMetaMapper[YearlyCustomerCharge] {
//  override def dbIndexes = super.dbIndexes
//}