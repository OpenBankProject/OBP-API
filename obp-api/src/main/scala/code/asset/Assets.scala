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

import net.liftweb.common.{Box, Failure, Full}
import net.liftweb.mapper.{Ascending, By, MaxRows, OrderBy, QueryParam, StartAt}
import net.liftweb.util.Helpers.tryo

/** This trait is what the rest of OBP sees of one asset in the registry; see [[Asset]]. */
trait AssetTrait {
  def assetId: String
  def assetCode: String
  def assetType: String
  def name: String
  def decimalPlaces: Int
  def issuerBankId: Option[String]
  def issuerProductCode: Option[String]
  def chainScheme: Option[String]
  def chainAssetId: Option[String]
  def dti: Option[String]
  def status: String
  def createdByUserId: String
  def createdAt: java.util.Date
  def updatedAt: java.util.Date
}

/**
 * This object lists the kinds of asset the registry knows. Fiat currencies, precious metals,
 * accounting units and crypto assets have no issuer and are administered at the SYS bank; every
 * other kind is issued by a bank, which administers it.
 */
object AssetTypes {
  val FIAT = "FIAT"
  val PRECIOUS_METAL = "PRECIOUS_METAL"
  val ACCOUNTING_UNIT = "ACCOUNTING_UNIT"
  val CRYPTO = "CRYPTO"
  val DEPOSIT_TOKEN = "DEPOSIT_TOKEN"
  val STABLECOIN = "STABLECOIN"
  val DEBT_SECURITY = "DEBT_SECURITY"
  val EQUITY = "EQUITY"
  val FUND_SHARE = "FUND_SHARE"
  val OTHER = "OTHER"

  val withoutIssuer: Set[String] = Set(FIAT, PRECIOUS_METAL, ACCOUNTING_UNIT, CRYPTO)
  val issued: Set[String] = Set(DEPOSIT_TOKEN, STABLECOIN, DEBT_SECURITY, EQUITY, FUND_SHARE, OTHER)
  val all: Set[String] = withoutIssuer ++ issued
}

/** This object lists the statuses an asset can have. Only ACTIVE assets may be used for new accounts and transactions. */
object AssetStatuses {
  val ACTIVE = "ACTIVE"
  val SUSPENDED = "SUSPENDED"
  val RETIRED = "RETIRED"

  val all: Set[String] = Set(ACTIVE, SUSPENDED, RETIRED)
}

/** This trait is the set of operations on the asset registry. */
trait AssetProvider {
  /** Finds an asset by its code, ignoring case: `eur` finds `EUR`. */
  def getAsset(assetCode: String): Box[AssetTrait]
  def getAssets(): List[AssetTrait]
  /**
   * Returns one page of the assets matching every filter given, ordered by code, and how many assets
   * match in total. A filter of `None` matches everything.
   */
  def getAssets(
    assetType: Option[String],
    issuerBankId: Option[String],
    status: Option[String],
    chainScheme: Option[String],
    limit: Int,
    offset: Int
  ): (List[AssetTrait], Int)
  /** Finds the asset with this on-chain identity, for example a Cardano token's policy id and asset name. */
  def getAssetByChainIdentity(chainScheme: String, chainAssetId: String): Box[AssetTrait]
  def createAsset(
    assetCode: String,
    assetType: String,
    name: String,
    decimalPlaces: Int,
    issuerBankId: Option[String],
    status: String,
    createdByUserId: String
  ): Box[AssetTrait]
}

/**
 * This object is the asset registry stored in the OBP database. It checks the rules every asset
 * must follow before it writes a row, so a bad row cannot be created by the seed or, later, by an
 * endpoint.
 */
object Assets extends AssetProvider {

  /** Codes are 3 to 10 upper case letters or digits once upper-cased. */
  private val AssetCodePattern = "^[A-Z0-9]{3,10}$".r

  /**
   * Amounts will be stored as DECIMAL(38, 18) (ideas/ASSET_REGISTRY.md, section 5), so no asset can
   * use more than 18 decimal places.
   */
  val MaxDecimalPlaces = 18

  def normaliseCode(assetCode: String): String = assetCode.trim.toUpperCase

  override def getAsset(assetCode: String): Box[AssetTrait] =
    Asset.find(By(Asset.AssetCode, normaliseCode(assetCode)))

  override def getAssets(): List[AssetTrait] =
    Asset.findAll(OrderBy(Asset.AssetCode, Ascending))

  override def getAssets(
    assetType: Option[String],
    issuerBankId: Option[String],
    status: Option[String],
    chainScheme: Option[String],
    limit: Int,
    offset: Int
  ): (List[AssetTrait], Int) = {
    val filters: List[QueryParam[Asset]] =
      assetType.map(value => By(Asset.AssetType, value)).toList :::
      issuerBankId.map(value => By(Asset.IssuerBankId, value)).toList :::
      status.map(value => By(Asset.Status, value)).toList :::
      chainScheme.map(value => By(Asset.ChainScheme, value)).toList
    val total = Asset.count(filters: _*).toInt
    val page = Asset.findAll((filters :+ OrderBy(Asset.AssetCode, Ascending) :+ StartAt[Asset](offset) :+ MaxRows[Asset](limit)): _*)
    (page, total)
  }

  override def getAssetByChainIdentity(chainScheme: String, chainAssetId: String): Box[AssetTrait] =
    if (chainScheme.isEmpty || chainAssetId.isEmpty) Failure("A chain identity needs both a chain scheme and a chain asset id.")
    else Asset.find(By(Asset.ChainScheme, chainScheme), By(Asset.ChainAssetId, chainAssetId))

  override def createAsset(
    assetCode: String,
    assetType: String,
    name: String,
    decimalPlaces: Int,
    issuerBankId: Option[String],
    status: String,
    createdByUserId: String
  ): Box[AssetTrait] = {
    val code = normaliseCode(assetCode)
    val issuer = issuerBankId.map(_.trim).filter(_.nonEmpty)
    if (AssetCodePattern.findFirstIn(code).isEmpty)
      Failure(s"Asset code '$assetCode' must be 3 to 10 letters or digits.")
    else if (!AssetTypes.all.contains(assetType))
      Failure(s"Asset type '$assetType' must be one of ${AssetTypes.all.toList.sorted.mkString(", ")}.")
    else if (decimalPlaces < 0 || decimalPlaces > MaxDecimalPlaces)
      Failure(s"Decimal places $decimalPlaces must be between 0 and $MaxDecimalPlaces.")
    else if (AssetTypes.issued.contains(assetType) && issuer.isEmpty)
      Failure(s"An asset of type $assetType must have an issuer bank.")
    else if (AssetTypes.withoutIssuer.contains(assetType) && issuer.nonEmpty)
      Failure(s"An asset of type $assetType has no issuer bank; it is administered at the SYS bank.")
    else if (!AssetStatuses.all.contains(status))
      Failure(s"Status '$status' must be one of ${AssetStatuses.all.toList.sorted.mkString(", ")}.")
    else
      tryo {
        Asset.create
          .AssetCode(code)
          .AssetType(assetType)
          .Name(name)
          .DecimalPlaces(decimalPlaces)
          .IssuerBankId(issuer.getOrElse(""))
          .Status(status)
          .CreatedByUserId(createdByUserId)
          .saveMe()
      } match {
        case created @ Full(_) =>
          AssetLookup.invalidate()
          created
        case other => other
      }
  }
}
