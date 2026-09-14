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

package code.kycchecks

import java.util.Date

import code.model.dataAccess.ResourceUser
import code.util.UUIDString
import com.openbankproject.commons.model.KycCheck
import net.liftweb.common.{Box, Full}
import net.liftweb.mapper._

object MappedKycChecksProvider extends KycCheckProvider {

  override def getKycChecks(customerId: String): List[MappedKycCheck] = {
    MappedKycCheck.findAll(
      By(MappedKycCheck.mCustomerId, customerId),
      OrderBy(MappedKycCheck.updatedAt, Descending))
  }


  override def addKycChecks(bankId: String, customerId: String, id: String, customerNumber: String, date: Date, how: String, staffUserId: String, mStaffName: String, mSatisfied: Boolean, comments: String): Box[KycCheck] = {
    val kyc_check = MappedKycCheck.find(By(MappedKycCheck.mId, id)) match {
      case Full(check) => check
        .mId(id)
        .mBankId(bankId)
        .mCustomerId(customerId)
        .mCustomerNumber(customerNumber)
        .mDate(date)
        .mHow(how)
        .mStaffUserId(staffUserId)
        .mStaffName(mStaffName)
        .mSatisfied(mSatisfied)
        .mComments(comments)
        .saveMe()
      case _ => MappedKycCheck.create
        .mId(id)
        .mBankId(bankId)
        .mCustomerId(customerId)
        .mCustomerNumber(customerNumber)
        .mDate(date)
        .mHow(how)
        .mStaffUserId(staffUserId)
        .mStaffName(mStaffName)
        .mSatisfied(mSatisfied)
        .mComments(comments)
        .saveMe()
    }
    Full(kyc_check)
  }
}

class MappedKycCheck extends KycCheck
with LongKeyedMapper[MappedKycCheck] with IdPK with CreatedUpdated {

  def getSingleton = MappedKycCheck

  object user extends MappedLongForeignKey(this, ResourceUser)
  object mBankId extends UUIDString(this)
  object mCustomerId extends UUIDString(this)

  object mId extends UUIDString(this)
  object mCustomerNumber extends MappedString(this, 50)
  object mDate extends MappedDateTime(this)
  object mHow extends MappedString(this, 32)
  object mStaffUserId extends MappedString(this, 64)
  object mStaffName extends MappedString(this, 64)
  object mSatisfied extends MappedBoolean(this)
  object mComments extends MappedString(this, 2000)


  override def bankId: String = mBankId.get
  override def customerId: String = mCustomerId.get
  override def idKycCheck: String = mId.get
  override def customerNumber: String = mCustomerNumber.get
  override def date: Date = mDate.get
  override def how: String = mHow.get
  override def staffUserId: String = mStaffUserId.get
  override def staffName: String = mStaffName.get
  override def satisfied: Boolean = mSatisfied.get
  override def comments: String = mComments.get
}

object MappedKycCheck extends MappedKycCheck with LongKeyedMetaMapper[MappedKycCheck] {
  override def dbIndexes = UniqueIndex(mId) :: super.dbIndexes
}