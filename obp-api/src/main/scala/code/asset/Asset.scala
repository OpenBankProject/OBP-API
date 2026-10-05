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

package code.asset

import code.util.MappedUUID
import net.liftweb.mapper._

/**
 * This class is one row of the asset registry: a unit that amounts can be held in, such as a
 * currency (EUR), a precious metal (XAU), a crypto asset (ETH) or an asset a bank issues (a deposit
 * token, a bond). Its `AssetCode` is the value that appears in `currency` fields.
 *
 * The design is in ideas/ASSET_REGISTRY.md. The registry is seeded at boot (see [[AssetSeed]]), and
 * `APIUtil.isValidCurrencyISOCode` and `Helper.currencyDecimalPlaces` read it through [[AssetLookup]].
 *
 * An empty string in an optional column means "not set"; the accessors on [[AssetTrait]] turn it
 * into `None`.
 */
class Asset extends AssetTrait with LongKeyedMapper[Asset] with IdPK {
  def getSingleton = Asset

  object AssetId extends MappedUUID(this)
  /** Always stored upper case, so a lookup that upper-cases its input ignores case. */
  object AssetCode extends MappedString(this, 10)
  object AssetType extends MappedString(this, 32)
  object Name extends MappedString(this, 125)
  object DecimalPlaces extends MappedInt(this)
  object IssuerBankId extends MappedString(this, 255)
  object IssuerProductCode extends MappedString(this, 50)
  object ChainScheme extends MappedString(this, 64)
  object ChainAssetId extends MappedString(this, 255)
  object Dti extends MappedString(this, 9)
  object Status extends MappedString(this, 16)
  object CreatedByUserId extends MappedString(this, 255)
  object CreationDate extends MappedDateTime(this) {
    override def defaultValue = new java.util.Date()
  }
  object LastUpdate extends MappedDateTime(this) {
    override def defaultValue = new java.util.Date()
  }

  override def assetId: String = AssetId.get
  override def assetCode: String = AssetCode.get
  override def assetType: String = AssetType.get
  override def name: String = Name.get
  override def decimalPlaces: Int = DecimalPlaces.get
  override def issuerBankId: Option[String] = nonEmpty(IssuerBankId.get)
  override def issuerProductCode: Option[String] = nonEmpty(IssuerProductCode.get)
  override def chainScheme: Option[String] = nonEmpty(ChainScheme.get)
  override def chainAssetId: Option[String] = nonEmpty(ChainAssetId.get)
  override def dti: Option[String] = nonEmpty(Dti.get)
  override def status: String = Status.get
  override def createdByUserId: String = CreatedByUserId.get
  override def createdAt: java.util.Date = CreationDate.get
  override def updatedAt: java.util.Date = LastUpdate.get

  private def nonEmpty(value: String): Option[String] = Option(value).filter(_.nonEmpty)
}

/**
 * The unique indexes on (chain_scheme, chain_asset_id) and on dti that the design describes are not
 * declared here: they must ignore rows where the value is not set, and Mapper cannot declare such a
 * partial index. Nothing writes those columns yet; the write endpoints will enforce the uniqueness.
 */
object Asset extends Asset with LongKeyedMetaMapper[Asset] {
  override def dbTableName = "Asset"
  override def dbIndexes = UniqueIndex(AssetCode) :: UniqueIndex(AssetId) :: Index(IssuerBankId) :: super.dbIndexes
}

/**
 * This class is one change of an asset's status, for example ACTIVE to SUSPENDED, with who made it,
 * when and why. The design keeps this separate from the metrics because metrics can be switched off
 * and record API calls rather than state changes. Nothing writes it yet: the seed creates assets as
 * ACTIVE, which is not a change, and there is no endpoint that changes a status.
 */
class AssetStatusHistory extends LongKeyedMapper[AssetStatusHistory] with IdPK {
  def getSingleton = AssetStatusHistory

  object AssetId extends MappedString(this, 36)
  object FromStatus extends MappedString(this, 16)
  object ToStatus extends MappedString(this, 16)
  object Reason extends MappedText(this)
  object ChangedByUserId extends MappedString(this, 255)
  object ChangedAt extends MappedDateTime(this) {
    override def defaultValue = new java.util.Date()
  }
}

object AssetStatusHistory extends AssetStatusHistory with LongKeyedMetaMapper[AssetStatusHistory] {
  override def dbTableName = "AssetStatusHistory"
  override def dbIndexes = Index(AssetId) :: super.dbIndexes
}
