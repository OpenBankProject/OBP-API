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

package code.customeraddress

import java.util.Date

import code.api.util.ErrorMessages
import code.customer.MappedCustomer
import code.util.{MappedUUID, MediumString}
import com.openbankproject.commons.model.CustomerAddress
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

import scala.concurrent.Future
import com.openbankproject.commons.ExecutionContext.Implicits.global

object MappedCustomerAddressProvider extends CustomerAddressProvider {

  override def getAddress(customerId: String) =  Future {
    val id: Box[MappedCustomer] = MappedCustomer.find(By(MappedCustomer.mCustomerId, customerId))
    id.map(customer => MappedCustomerAddress.findAll(By(MappedCustomerAddress.mCustomerId, customer.id.get)))
  }

  override def createAddress(customerId: String,
                             line1: String,
                             line2: String,
                             line3: String,
                             city: String,
                             county: String,
                             state: String,
                             postcode: String,
                             countryCode: String,
                             tags: String,
                             status: String
                ): Future[Box[CustomerAddress]] = Future {
    val id: Box[MappedCustomer] = MappedCustomer.find(By(MappedCustomer.mCustomerId, customerId))
    id match {
      case Full(customer) =>
        tryo(MappedCustomerAddress
          .create
          .mCustomerId(customer.id.get)
          .mLine1(line1)
          .mLine2(line2)
          .mLine3(line3)
          .mCity(city)
          .mCounty(county)
          .mState(state)
          .mCountryCode(countryCode)
          .mPostCode(postcode)
          .mStatus(status)
          .mTags(tags)
          .saveMe())
      case Empty =>
        Empty ?~! ErrorMessages.CustomerNotFoundByCustomerId
      case Failure(msg, _, _) =>
        Failure(msg)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }
  }
  override def updateAddress(customerAddressId: String,
                             line1: String,
                             line2: String,
                             line3: String,
                             city: String,
                             county: String,
                             state: String,
                             postcode: String,
                             countryCode: String,
                             tags: String,
                             status: String
                ): Future[Box[CustomerAddress]] = Future {
    val id: Box[MappedCustomerAddress] = MappedCustomerAddress.find(By(MappedCustomerAddress.mCustomerAddressId, customerAddressId))
    id match {
      case Full(address) =>
        tryo(address
          .mLine1(line1)
          .mLine2(line2)
          .mLine3(line3)
          .mCity(city)
          .mCounty(county)
          .mState(state)
          .mCountryCode(countryCode)
          .mPostCode(postcode)
          .mStatus(status)
          .mTags(tags)
          .saveMe())
      case Empty =>
        Empty ?~! ErrorMessages.CustomerAddressNotFound
      case Failure(msg, _, _) =>
        Failure(msg)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }
  }
  
  override def deleteAddress(customerAddressId: String): Future[Box[Boolean]] = Future {
    MappedCustomerAddress.find(By(MappedCustomerAddress.mCustomerAddressId, customerAddressId)) match {
      case Full(t) => Full(t.delete_!)
      case Empty   => Empty ?~! ErrorMessages.CustomerAddressNotFound
      case _       => Full(false)
    }
  }
}

class MappedCustomerAddress extends CustomerAddress with LongKeyedMapper[MappedCustomerAddress] with IdPK with CreatedUpdated {

  def getSingleton = MappedCustomerAddress

  object mCustomerId extends MappedLongForeignKey(this, MappedCustomer)
  object mCustomerAddressId extends MappedUUID(this)
  object mLine1 extends MappedString(this, 255)
  object mLine2 extends MappedString(this, 255)
  object mLine3 extends MappedString(this, 255)
  object mCity extends MappedString(this, 255)
  object mCounty extends MappedString(this, 255)
  object mState extends MappedString(this, 255)
  object mCountryCode extends MappedString(this, 2)
  object mPostCode extends MappedString(this, 20)
  object mTags extends MappedString(this, 20)
  object mStatus extends MediumString(this)

  override def customerId: String = mCustomerId.obj.map(_.mCustomerId.get).getOrElse("")
  override def customerAddressId: String = mCustomerAddressId.get
  override def line1: String = mLine1.get
  override def line2: String = mLine2.get
  override def line3: String = mLine3.get
  override def city: String = mCity.get
  override def county: String = mCounty.get
  override def state: String = mState.get
  override def postcode: String = mPostCode.get
  override def countryCode: String = mCountryCode.get
  override def status: String = mState.get
  override def tags: String = mTags.get
  override def insertDate: Date = createdAt.get

}

object MappedCustomerAddress extends MappedCustomerAddress with LongKeyedMetaMapper[MappedCustomerAddress] {
  override def dbIndexes = UniqueIndex(mCustomerAddressId) :: super.dbIndexes
}
