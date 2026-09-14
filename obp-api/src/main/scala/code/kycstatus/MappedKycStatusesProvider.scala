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

package code.kycstatuses

import java.util.Date

import code.model.dataAccess.ResourceUser
import code.util.UUIDString
import com.openbankproject.commons.model.KycStatus
import net.liftweb.common.{Box, Full}
import net.liftweb.mapper.{By, _}

object MappedKycStatusesProvider extends KycStatusProvider {

  override def getKycStatuses(customerId: String): List[MappedKycStatus] = {
    MappedKycStatus.findAll(
      By(MappedKycStatus.mCustomerId, customerId),
      OrderBy(MappedKycStatus.updatedAt, Descending))
  }


  override def addKycStatus(bankId: String, customerId: String, customerNumber: String, ok: Boolean, date: Date): Box[KycStatus] = {
    val kyc_status = MappedKycStatus.find(By(MappedKycStatus.mBankId, bankId), By(MappedKycStatus.mCustomerId, customerId)) match {
      case Full(status) => status
        .mBankId(bankId)
        .mCustomerId(customerId)
        .mCustomerNumber(customerNumber)
        .mOk(ok)
        .mDate(date)
        .saveMe()
      case _ => MappedKycStatus.create
        .mBankId(bankId)
        .mCustomerId(customerId)
        .mCustomerNumber(customerNumber)
        .mOk(ok)
        .mDate(date)
        .saveMe()
    }
    Full(kyc_status)
  }
}

class MappedKycStatus extends KycStatus
with LongKeyedMapper[MappedKycStatus] with IdPK with CreatedUpdated {

  def getSingleton = MappedKycStatus

  object user extends MappedLongForeignKey(this, ResourceUser)
  object mBankId extends UUIDString(this)
  object mCustomerId extends UUIDString(this)

  object mCustomerNumber extends MappedString(this, 64)
  object mOk extends MappedBoolean(this)
  object mDate extends MappedDateTime(this)


  override def bankId: String = mBankId.get
  override def customerId: String = mCustomerId.get
  override def customerNumber: String = mCustomerNumber.get
  override def ok: Boolean = mOk.get
  override def date: Date = mDate.get

}

object MappedKycStatus extends MappedKycStatus with LongKeyedMetaMapper[MappedKycStatus] {
  override def dbIndexes = super.dbIndexes
}