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

package code.kycdocuments

import java.util.Date

import net.liftweb.common.{Box, Full}
import code.model.dataAccess.ResourceUser
import code.util.UUIDString
import com.openbankproject.commons.model.KycDocument
import net.liftweb.mapper._

object MappedKycDocumentsProvider extends KycDocumentProvider {

  // TODO Add bankId (customerNumber is not unique)
  override def getKycDocuments(customerId: String): List[MappedKycDocument] = {
    MappedKycDocument.findAll(
      By(MappedKycDocument.mCustomerId, customerId),
      OrderBy(MappedKycDocument.updatedAt, Descending))
  }


  override def addKycDocuments(bankId: String, customerId: String, id: String, customerNumber: String, `type`: String, number: String, issueDate: Date, issuePlace: String, expiryDate: Date): Box[MappedKycDocument] = {
    val kyc_document = MappedKycDocument.find(By(MappedKycDocument.mId, id)) match {
      case Full(document) => document
        .mBankId(bankId)
        .mCustomerId(customerId)
        .mId(id)
        .mCustomerNumber(customerNumber)
        .mType(`type`)
        .mNumber(number)
        .mIssueDate(issueDate)
        .mIssuePlace(issuePlace)
        .mExpiryDate(expiryDate)
        .saveMe()
      case _ => MappedKycDocument.create
        .mBankId(bankId)
        .mCustomerId(customerId)
        .mId(id)
        .mCustomerNumber(customerNumber)
        .mType(`type`)
        .mNumber(number)
        .mIssueDate(issueDate)
        .mIssuePlace(issuePlace)
        .mExpiryDate(expiryDate)
        .saveMe()
    }
    Full(kyc_document)
  }
}

class MappedKycDocument extends KycDocument
with LongKeyedMapper[MappedKycDocument] with IdPK with CreatedUpdated {

  def getSingleton = MappedKycDocument

  object user extends MappedLongForeignKey(this, ResourceUser)
  object mBankId extends UUIDString(this)
  object mCustomerId extends UUIDString(this)

  object mId extends UUIDString(this)
  object mCustomerNumber extends MappedString(this, 50)
  object mType extends MappedString(this, 50)
  object mNumber extends MappedString(this, 50)
  object mIssueDate extends MappedDateTime(this)
  object mIssuePlace extends MappedString(this, 512)
  object mExpiryDate extends MappedDateTime(this)


  override def bankId: String = mBankId.get
  override def customerId: String = mCustomerId.get
  override def idKycDocument: String = mId.get
  override def customerNumber: String = mCustomerNumber.get
  override def `type`: String = mType.get
  override def number: String = mNumber.get
  override def issueDate: Date = mIssueDate.get
  override def issuePlace: String = mIssuePlace.get
  override def expiryDate: Date = mExpiryDate.get
}

object MappedKycDocument extends MappedKycDocument with LongKeyedMetaMapper[MappedKycDocument] {
  override def dbIndexes = UniqueIndex(mId) :: super.dbIndexes
}