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

package code.transactionrequests

import code.util.UUIDString
import com.openbankproject.commons.model.TransactionRequestTypeCharge
import net.liftweb.mapper._

class MappedTransactionRequestTypeCharge extends TransactionRequestTypeCharge with LongKeyedMapper[MappedTransactionRequestTypeCharge] with IdPK with CreatedUpdated{
  def getSingleton = MappedTransactionRequestTypeCharge

  object mTransactionRequestTypeId extends UUIDString(this) // Add class for this
  object mBankId extends UUIDString(this)
  object mChargeCurrency extends MappedString(this, 3)
  object mChargeAmount extends MappedString(this, 32)
  object mChargeSummary extends MappedString(this, 255)

  override def transactionRequestTypeId: String = mTransactionRequestTypeId.get
  override def bankId: String = mBankId.get
  override def chargeCurrency: String = mChargeCurrency.get
  override def chargeAmount: String = mChargeAmount.get
  override def chargeSummary: String = mChargeSummary.get
  
}

object MappedTransactionRequestTypeCharge extends MappedTransactionRequestTypeCharge with LongKeyedMetaMapper[MappedTransactionRequestTypeCharge] {
  
}

/**
  * This case class is used when there is no data in database and mocked empty data to show it to user.
  */
case class TransactionRequestTypeChargeMock(
                                            mTransactionRequestTypeId: String,
                                            mBankId: String,
                                            mChargeCurrency: String,
                                            mChargeAmount: String,
                                            mChargeSummary: String
                                            ) extends TransactionRequestTypeCharge {

  override def transactionRequestTypeId: String = mTransactionRequestTypeId

  override def bankId: String = mBankId

  override def chargeCurrency: String = mChargeCurrency

  override def chargeAmount: String = mChargeAmount

  override def chargeSummary: String = mChargeSummary
}


