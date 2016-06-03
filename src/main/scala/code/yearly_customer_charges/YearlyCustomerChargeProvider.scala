package code.yearly_customer_charges

import java.util.Date

import code.customer.MappedCustomerProvider
import code.model.{CustomerId, AmountOfMoney, BankId, User}
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

//
//object YearlyCustomerCharge extends SimpleInjector {
//
//  val yearlyCustomerChargeProvider = new Inject(buildOne _) {}
//
//  def buildOne: YearlyCustomerChargeProvider = MappedYearlyCustomerChargeProvider
//
//}
//
//trait YearlyCustomerChargeProvider {
//
//  def getChargesForCustomer(bankId : BankId, customerId: CustomerId) : List[YearlyCustomerCharge]
//
//
//  def addChargeForCustomer(charge: YearlyCustomerCharge)
//
//}
//
//
//// This defines what a YearlyCustomerCharge looks like
//trait YearlyCustomerCharge {
//
//  def bankId : String
//  def customerId : String // The UUID for the customer. To be used in URLs
//  def customerNumber : String
//
//  def year : Integer
//
//  def categoryId : String
//  def forcastIndictor : String
//  def typeId : String
//  def natureId : String
//  def charge : AmountOfMoney
//  def updateDate : Date
//
//}
