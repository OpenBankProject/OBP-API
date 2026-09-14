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

package code.fx

import java.util.Date

import code.util.UUIDString
import com.openbankproject.commons.model.{BankId, FXRate}
import net.liftweb.mapper.{MappedStringForeignKey, _}

class MappedFXRate extends FXRate with LongKeyedMapper[MappedFXRate] with IdPK {
  def getSingleton = MappedFXRate

  object mBankId extends UUIDString(this)

  object mFromCurrencyCode extends MappedStringForeignKey(this, MappedCurrency, 3) {
    override def foreignMeta = MappedCurrency
  }

  object mToCurrencyCode extends MappedStringForeignKey(this, MappedCurrency, 3) {
    override def foreignMeta = MappedCurrency
  }



  object mConversionValue extends MappedDouble(this)

  object mInverseConversionValue extends MappedDouble(this)

  object mEffectiveDate extends MappedDateTime(this)

  override def bankId: BankId = BankId(mBankId.get)

  override def fromCurrencyCode: String = mFromCurrencyCode.get

  override def toCurrencyCode: String = mToCurrencyCode.get

  override def conversionValue: Double = mConversionValue.get

  override def inverseConversionValue: Double = mInverseConversionValue.get

  override def effectiveDate: Date = mEffectiveDate.get
}

object MappedFXRate extends MappedFXRate with LongKeyedMetaMapper[MappedFXRate] {}


