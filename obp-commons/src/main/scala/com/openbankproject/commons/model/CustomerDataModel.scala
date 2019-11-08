/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH.

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

package com.openbankproject.commons.model

import java.lang
import java.util.Date

import scala.collection.immutable.List


trait Customer {
  def customerId : String // The UUID for the customer. To be used in URLs
  def bankId : String
  def number : String // The Customer number i.e. the bank identifier for the customer.
  def legalName : String
  def mobileNumber : String
  def email : String
  def faceImage : CustomerFaceImageTrait
  def dateOfBirth: Date
  def relationshipStatus: String
  def dependents: Integer
  def dobOfDependents: List[Date]
  def highestEducationAttained: String
  def employmentStatus: String
  def creditRating : CreditRatingTrait
  def creditLimit: AmountOfMoneyTrait
  def kycStatus: lang.Boolean
  def lastOkDate: Date
  def title: String
  def branchId: String
  def nameSuffix: String
}

trait CustomerFaceImageTrait {
  def url : String
  def date : Date
}

trait AmountOfMoneyTrait {
  def currency: String
  def amount: String
}

trait CreditRatingTrait {
  def rating: String
  def source: String
}

case class CustomerFaceImage(date : Date, url : String) extends CustomerFaceImageTrait
object CustomerFaceImage extends Converter[CustomerFaceImageTrait, CustomerFaceImage]

case class CreditRating(rating: String, source: String) extends CreditRatingTrait
object CreditRating extends Converter[CreditRatingTrait, CreditRating]

case class CreditLimit(currency: String, amount: String) extends AmountOfMoneyTrait
object CreditLimit extends Converter[AmountOfMoneyTrait, CreditLimit]