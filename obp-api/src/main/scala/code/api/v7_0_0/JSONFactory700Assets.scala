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
package code.api.v7_0_0

import java.util.Date

import code.asset.AssetTrait

/*
 * The JSON of the v7.0.0 asset registry endpoints. Package-level case classes, for the reason given in
 * JSONFactory700Operations.
 */

/** One asset in the registry: a unit amounts can be held in, such as EUR, XAU, ETH or a bank's deposit token. */
case class AssetJsonV700(
  asset_id: String,
  asset_code: String,
  asset_type: String,
  name: String,
  decimal_places: Int,
  issuer_bank_id: Option[String],
  issuer_product_code: Option[String],
  chain_scheme: Option[String],
  chain_asset_id: Option[String],
  dti: Option[String],
  status: String,
  created_by_user_id: String,
  created_at: Date,
  updated_at: Date
)

case class AssetsPaginationJsonV700(total: Int, limit: Int, offset: Int)

case class AssetsJsonV700(assets: List[AssetJsonV700], pagination: AssetsPaginationJsonV700)

object JSONFactory700Assets {

  def createAssetJson(asset: AssetTrait): AssetJsonV700 =
    AssetJsonV700(
      asset_id = asset.assetId,
      asset_code = asset.assetCode,
      asset_type = asset.assetType,
      name = asset.name,
      decimal_places = asset.decimalPlaces,
      issuer_bank_id = asset.issuerBankId,
      issuer_product_code = asset.issuerProductCode,
      chain_scheme = asset.chainScheme,
      chain_asset_id = asset.chainAssetId,
      dti = asset.dti,
      status = asset.status,
      created_by_user_id = asset.createdByUserId,
      created_at = asset.createdAt,
      updated_at = asset.updatedAt
    )

  def createAssetsJson(assets: List[AssetTrait], total: Int, limit: Int, offset: Int): AssetsJsonV700 =
    AssetsJsonV700(assets.map(createAssetJson), AssetsPaginationJsonV700(total, limit, offset))

  /** A seeded currency, as the registry holds it today. */
  val assetJsonExample: AssetJsonV700 = AssetJsonV700(
    asset_id = "7a1c3e52-9b0d-4f6a-8c2e-1d5b9f0a3c47",
    asset_code = "EUR",
    asset_type = "FIAT",
    name = "Euro",
    decimal_places = 2,
    issuer_bank_id = None,
    issuer_product_code = None,
    chain_scheme = None,
    chain_asset_id = None,
    dti = None,
    status = "ACTIVE",
    created_by_user_id = "system:asset-seed",
    created_at = new Date(),
    updated_at = new Date()
  )

  /** A token issued by a bank and recorded on Cardano, the shape the reverse chain lookup returns. */
  val issuedAssetJsonExample: AssetJsonV700 = AssetJsonV700(
    asset_id = "0c9e4b7d-2f61-4a8e-b3d5-6a1f8e2c9b04",
    asset_code = "TZBOND29",
    asset_type = "DEBT_SECURITY",
    name = "Example Bank 2029 Fixed Rate Note",
    decimal_places = 0,
    issuer_bank_id = Some("example.bank.tz"),
    issuer_product_code = Some("TZBOND29"),
    chain_scheme = Some("CARDANO_MAINNET"),
    chain_asset_id = Some("f0ff48bbb7bbe9d59a40f1ce90e9e9d0ff5002ec48f232b49ca0fb9a.545a424f4e443239"),
    dti = None,
    status = "ACTIVE",
    created_by_user_id = "9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
    created_at = new Date(),
    updated_at = new Date()
  )

  val assetsJsonExample: AssetsJsonV700 =
    AssetsJsonV700(List(assetJsonExample), AssetsPaginationJsonV700(total = 1, limit = 500, offset = 0))
}
