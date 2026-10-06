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

import code.api.util.ErrorMessages.{AssetNotFound, AssetNotFoundByChainIdentity, InvalidAssetQueryParameter}
import code.api.v6_0_0.V600ServerSetup
import code.asset.{Asset, AssetSeed, AssetStatuses, AssetTypes, Assets, RestoresSeededAssetRegistry}
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s.JsonAST.{JInt, JString, JValue}
import net.liftweb.mapper.By
import org.scalatest.Tag

/**
 * This suite checks the three read-only asset registry endpoints: they answer without
 * authentication, list and filter the seeded registry, find one asset by code ignoring case, find a
 * token by its chain identity, and refuse a bad query parameter with 400 rather than ignore it.
 *
 * The test setup empties every table after each test, so each scenario seeds the registry itself,
 * and the suite puts the seeded registry back when it ends (RestoresSeededAssetRegistry).
 */
class AssetsEndpointTest extends V600ServerSetup with RestoresSeededAssetRegistry {

  def v7_0_0_Request = baseRequest / "obp" / "v7.0.0"
  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object ApiEndpoint extends Tag("assets")

  private def assets = v7_0_0_Request / "assets"

  private def seedRegistry(): Unit = {
    Asset.bulkDelete_!!()
    AssetSeed.run()
  }

  private def codesIn(body: JValue): List[String] =
    (body \ "assets").children.map(asset => (asset \ "asset_code").asInstanceOf[JString].s)

  private def total(body: JValue): BigInt =
    (body \ "pagination" \ "total").asInstanceOf[JInt].num

  /** Registers a token issued by bank-a and records it on Cardano mainnet, as the write endpoints will later. */
  private val tokenChainAssetId = "f0ff48bbb7bbe9d59a40f1ce90e9e9d0ff5002ec48f232b49ca0fb9a.545a424f4e443239"
  private def registerCardanoToken(): Unit = {
    Assets.createAsset("TZBOND29", AssetTypes.DEBT_SECURITY, "Example 2029 Note", 0, Some("bank-a"), AssetStatuses.ACTIVE, "user-1")
      .openOrThrowException("the token is created")
    Asset.find(By(Asset.AssetCode, "TZBOND29")).openOrThrowException("the token exists")
      .ChainScheme("CARDANO_MAINNET").ChainAssetId(tokenChainAssetId).saveMe()
  }

  feature(s"Get Assets - GET /obp/v7.0.0/assets - $VersionOfApi") {

    scenario("anyone can list the seeded registry, ordered by code", ApiEndpoint, VersionOfApi) {
      seedRegistry()
      val response = makeGetRequest(assets.GET)
      response.code should equal(200)
      val codes = codesIn(response.body)
      total(response.body) should equal(AssetSeed.entries.size)
      codes.size should equal(AssetSeed.entries.size)
      codes should equal(codes.sorted)
      val eur = (response.body \ "assets").children.find(asset => (asset \ "asset_code") == JString("EUR")).get
      (eur \ "asset_type") should equal(JString("FIAT"))
      (eur \ "decimal_places").values should equal(2)
      (eur \ "status") should equal(JString("ACTIVE"))
    }

    scenario("filters match ignoring the letter case of the value", ApiEndpoint, VersionOfApi) {
      seedRegistry()
      val metals = makeGetRequest(assets.GET <<? List("asset_type" -> "precious_metal"))
      metals.code should equal(200)
      codesIn(metals.body) should equal(List("XAG", "XAU", "XPD", "XPT"))

      val suspended = makeGetRequest(assets.GET <<? List("status" -> "SUSPENDED"))
      suspended.code should equal(200)
      total(suspended.body) should equal(0)
    }

    scenario("issuer_bank_id and chain_scheme find a bank's token", ApiEndpoint, VersionOfApi) {
      seedRegistry()
      registerCardanoToken()
      codesIn(makeGetRequest(assets.GET <<? List("issuer_bank_id" -> "bank-a")).body) should equal(List("TZBOND29"))
      codesIn(makeGetRequest(assets.GET <<? List("chain_scheme" -> "cardano_mainnet")).body) should equal(List("TZBOND29"))
      codesIn(makeGetRequest(assets.GET <<? List("issuer_bank_id" -> "bank-b")).body) should equal(Nil)
    }

    scenario("limit and offset page through the list, and total counts every match", ApiEndpoint, VersionOfApi) {
      seedRegistry()
      val allCodes = codesIn(makeGetRequest(assets.GET).body)
      val page = makeGetRequest(assets.GET <<? List("limit" -> "2", "offset" -> "1"))
      page.code should equal(200)
      codesIn(page.body) should equal(allCodes.slice(1, 3))
      total(page.body) should equal(AssetSeed.entries.size)
    }

    scenario("a bad query parameter is refused with 400, not ignored", ApiEndpoint, VersionOfApi) {
      seedRegistry()
      val badParameters = List(
        "asset_type" -> "BOGUS",
        "status" -> "FROZEN",
        "limit" -> "0",
        "limit" -> "501",
        "limit" -> "ten",
        "offset" -> "-1"
      )
      badParameters.foreach { parameter =>
        val response = makeGetRequest(assets.GET <<? List(parameter))
        withClue(parameter) {
          response.code should equal(400)
          response.body.extract[ErrorMessage].message should startWith(InvalidAssetQueryParameter)
        }
      }
    }
  }

  feature(s"Get Asset - GET /obp/v7.0.0/assets/ASSET_CODE - $VersionOfApi") {

    scenario("anyone can get an asset by code, in any letter case", ApiEndpoint, VersionOfApi) {
      seedRegistry()
      val response = makeGetRequest((assets / "jpy").GET)
      response.code should equal(200)
      (response.body \ "asset_code") should equal(JString("JPY"))
      (response.body \ "decimal_places").values should equal(0)
    }

    scenario("an unknown code is 404, and lovelace is not an asset", ApiEndpoint, VersionOfApi) {
      seedRegistry()
      List("NOPE", "lovelace").foreach { code =>
        val response = makeGetRequest((assets / code).GET)
        withClue(code) {
          response.code should equal(404)
          response.body.extract[ErrorMessage].message should startWith(AssetNotFound)
        }
      }
    }
  }

  feature(s"Get Asset by Chain Identity - GET /obp/v7.0.0/assets/chain/CHAIN_SCHEME/CHAIN_ASSET_ID - $VersionOfApi") {

    scenario("a registered token is found by its chain and network and its identity there", ApiEndpoint, VersionOfApi) {
      seedRegistry()
      registerCardanoToken()
      val response = makeGetRequest((assets / "chain" / "cardano_mainnet" / tokenChainAssetId).GET)
      response.code should equal(200)
      (response.body \ "asset_code") should equal(JString("TZBOND29"))
      (response.body \ "issuer_bank_id") should equal(JString("bank-a"))
      (response.body \ "chain_scheme") should equal(JString("CARDANO_MAINNET"))
    }

    scenario("the same identity on another network is not found", ApiEndpoint, VersionOfApi) {
      seedRegistry()
      registerCardanoToken()
      val response = makeGetRequest((assets / "chain" / "CARDANO_PREPROD" / tokenChainAssetId).GET)
      response.code should equal(404)
      response.body.extract[ErrorMessage].message should startWith(AssetNotFoundByChainIdentity)
    }
  }
}
