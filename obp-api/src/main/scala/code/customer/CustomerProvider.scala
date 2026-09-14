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

package code.customer

import java.util.Date

import code.api.util.{APIUtil, OBPQueryParam}
import com.openbankproject.commons.model.{User, _}
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector
import org.apache.pekko.pattern.pipe

import scala.collection.immutable.List
import scala.concurrent.Future


object CustomerX extends SimpleInjector {

  val customerProvider = new Inject(() => buildOne) {}

  def buildOne: CustomerProvider = MappedCustomerProvider

}

trait CustomerProvider {
  def getCustomersAtAllBanks(queryParams: List[OBPQueryParam]): Future[Box[List[Customer]]]
  
  def getCustomersFuture(bankId : BankId, queryParams: List[OBPQueryParam]): Future[Box[List[Customer]]]

  def getCustomersByCustomerPhoneNumber(bankId: BankId, phoneNumber: String): Future[Box[List[Customer]]]
  def getCustomersByCustomerLegalName(bankId: BankId, legalName: String): Future[Box[List[Customer]]]

  def getCustomerByUserId(bankId: BankId, userId: String): Box[Customer]

  def getCustomersByUserId(userId: String): List[Customer]

  def getCustomersByUserIdFuture(userId: String): Future[Box[List[Customer]]]

  def getCustomerByCustomerId(customerId: String): Box[Customer]

  def getCustomerByCustomerIdFuture(customerId: String): Future[Box[Customer]]

  def getBankIdByCustomerId(customerId: String): Box[String]

  def getCustomerByCustomerNumber(customerNumber: String, bankId : BankId): Box[Customer]

  def getCustomerByCustomerNumberFuture(customerNumber: String, bankId : BankId): Future[Box[Customer]]

  def getUser(bankId : BankId, customerNumber : String) : Box[User]

  def checkCustomerNumberAvailable(bankId : BankId, customerNumber : String) : Boolean


  def addCustomer(bankId: BankId,
                  number: String,
                  legalName: String,
                  mobileNumber: String,
                  email: String,
                  faceImage:
                  CustomerFaceImageTrait,
                  dateOfBirth: Date,
                  relationshipStatus: String,
                  dependents: Int,
                  dobOfDependents: List[Date],
                  highestEducationAttained: String,
                  employmentStatus: String,
                  kycStatus: Boolean,
                  lastOkDate: Date,
                  creditRating: Option[CreditRatingTrait],
                  creditLimit: Option[AmountOfMoneyTrait],
                  title: String,
                  branchId: String,
                  nameSuffix: String,
                  customerType: String = "",
                  parentCustomerId: String = ""
                 ): Box[Customer]

  def updateCustomerScaData(customerId: String, 
                            mobileNumber: Option[String], 
                            email: Option[String],
                            customerNumber: Option[String]): Future[Box[Customer]]
  
  def updateCustomerCreditData(customerId: String,
                               creditRating: Option[String],
                               creditSource: Option[String],
                               creditLimit: Option[AmountOfMoney]): Future[Box[Customer]]
  
  def updateCustomerGeneralData(customerId: String,
                                legalName: Option[String],
                                faceImage: Option[CustomerFaceImageTrait],
                                dateOfBirth: Option[Date],
                                relationshipStatus: Option[String],
                                dependents: Option[Int],
                                highestEducationAttained: Option[String],
                                employmentStatus: Option[String],
                                title: Option[String],
                                branchId: Option[String],
                                nameSuffix: Option[String],
                                customerType: Option[String] = None,
                                parentCustomerId: Option[String] = None
                               ): Future[Box[Customer]]
  
  def getCustomersByParentCustomerId(bankId: BankId, parentCustomerId: String): Future[Box[List[Customer]]]

  def getCustomersByCustomerTypes(bankId: BankId, customerTypes: List[String], queryParams: List[OBPQueryParam]): Future[Box[List[Customer]]]

  def bulkDeleteCustomers(): Boolean
  def populateMissingUUIDs(): Boolean
}