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

import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.canCreateAsset
import code.api.util.ErrorMessages.{AssetAlreadyExists, AssetChainIdentityAlreadyExists, AssetDtiAlreadyExists, AssetNotFound,
  AssetNotFoundByChainIdentity, AuthenticatedUserIsRequired, InvalidAsset, InvalidAssetQueryParameter, ProductNotFoundByProductCode,
  UserHasMissingRoles}
import code.api.util.APIUtil
import code.api.v6_0_0.V600ServerSetup
import code.asset.{Asset, AssetSeed, AssetStatuses, AssetTypes, Assets, RestoresSeededAssetRegistry}
import code.bankconnectors.Connector
import code.entitlement.Entitlement
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.ApiVersion
import org.json4s.JsonAST.{JInt, JNothing, JNull, JString, JValue}
import org.json4s.native.Serialization.write
import net.liftweb.mapper.By
import org.scalatest.Tag

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * This suite checks the asset registry endpoints. The three reads answer without authentication,
 * list and filter the seeded registry, find one asset by code ignoring case, find a token by its
 * chain identity, and refuse a bad query parameter with 400 rather than ignore it. Create Asset
 * needs the CanCreateAsset Role at the bank in the path, records that bank as the issuer (or no
 * issuer at SYS), and refuses a taken code, chain identity or DTI with 409.
 *
 * The test setup empties every table after each test, so each scenario seeds the registry itself,
 * and the suite puts the seeded registry back when it ends (RestoresSeededAssetRegistry).
 */
class AssetsEndpointTest extends V600ServerSetup with RestoresSeededAssetRegistry {

  def v7_0_0_Request = baseRequest / "obp" / "v7.0.0"
  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object ApiEndpoint extends Tag("assets")
  object CreateAssetEndpoint extends Tag("createAsset")

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

  feature(s"Create Asset - POST /obp/v7.0.0/banks/BANK_ID/assets - $VersionOfApi") {

    val bankId = testBankId1.value
    def createAt(bank: String) = (v7_0_0_Request / "banks" / bank / "assets").POST

    def bondBody(code: String = "BANKBOND30",
                 assetType: String = AssetTypes.DEBT_SECURITY,
                 productCode: Option[String] = None,
                 chainScheme: Option[String] = None,
                 chainAssetId: Option[String] = None,
                 dti: Option[String] = None): String =
      write(PostAssetJsonV700(code, assetType, "Bank Bond 2030", 0, productCode, chainScheme, chainAssetId, dti))

    def grantAt(bank: String): Unit =
      Entitlement.entitlement.vend.addEntitlement(bank, resourceUser1.userId, canCreateAsset.toString)

    def errorOf(body: JValue): String = body.extract[ErrorMessage].message

    scenario("without logging in, or without the Role, nothing is created", CreateAssetEndpoint, VersionOfApi) {
      seedRegistry()
      val anonymous = makePostRequest(createAt(bankId), bondBody())
      anonymous.code should equal(401)
      errorOf(anonymous.body) should startWith(AuthenticatedUserIsRequired)

      val withoutRole = makePostRequest(createAt(bankId) <@ (user1), bondBody())
      withoutRole.code should equal(403)
      errorOf(withoutRole.body) should startWith(UserHasMissingRoles)
      errorOf(withoutRole.body) should include(canCreateAsset.toString)
      Assets.getAsset("BANKBOND30").isEmpty shouldBe true
    }

    scenario("a bank creates an asset it issues, and its code is accepted as a currency at once", CreateAssetEndpoint, VersionOfApi) {
      seedRegistry()
      grantAt(bankId)
      val response = makePostRequest(createAt(bankId) <@ (user1), bondBody(code = "bankbond30"))
      response.code should equal(201)
      (response.body \ "asset_code") should equal(JString("BANKBOND30"))
      (response.body \ "asset_type") should equal(JString(AssetTypes.DEBT_SECURITY))
      (response.body \ "issuer_bank_id") should equal(JString(bankId))
      (response.body \ "status") should equal(JString(AssetStatuses.ACTIVE))
      (response.body \ "created_by_user_id") should equal(JString(resourceUser1.userId))

      makeGetRequest((assets / "BANKBOND30").GET).code should equal(200)
      APIUtil.isValidCurrencyISOCode("BANKBOND30") shouldBe true
    }

    scenario("a taken code is refused with 409", CreateAssetEndpoint, VersionOfApi) {
      seedRegistry()
      grantAt(bankId)
      makePostRequest(createAt(bankId) <@ (user1), bondBody()).code should equal(201)
      val again = makePostRequest(createAt(bankId) <@ (user1), bondBody(code = "BankBond30"))
      again.code should equal(409)
      errorOf(again.body) should startWith(AssetAlreadyExists)
    }

    scenario("a taken chain identity or DTI is refused with 409", CreateAssetEndpoint, VersionOfApi) {
      seedRegistry()
      grantAt(bankId)
      val first = makePostRequest(createAt(bankId) <@ (user1),
        bondBody(chainScheme = Some("cardano_mainnet"), chainAssetId = Some(tokenChainAssetId), dti = Some("X9J9K872S")))
      first.code should equal(201)
      (first.body \ "chain_scheme") should equal(JString("CARDANO_MAINNET"))

      val sameChainIdentity = makePostRequest(createAt(bankId) <@ (user1),
        bondBody(code = "OTHERBOND", chainScheme = Some("CARDANO_MAINNET"), chainAssetId = Some(tokenChainAssetId)))
      sameChainIdentity.code should equal(409)
      errorOf(sameChainIdentity.body) should startWith(AssetChainIdentityAlreadyExists)

      val sameDti = makePostRequest(createAt(bankId) <@ (user1), bondBody(code = "OTHERBOND", dti = Some("x9j9k872s")))
      sameDti.code should equal(409)
      errorOf(sameDti.body) should startWith(AssetDtiAlreadyExists)

      // The same identity on another network is another token
      makePostRequest(createAt(bankId) <@ (user1),
        bondBody(code = "OTHERBOND", chainScheme = Some("CARDANO_PREPROD"), chainAssetId = Some(tokenChainAssetId))).code should equal(201)
    }

    scenario("an asset that breaks the registry's rules is refused with 400", CreateAssetEndpoint, VersionOfApi) {
      seedRegistry()
      grantAt(bankId)
      val badBodies = List(
        "a three-character code for a bank-issued asset" -> bondBody(code = "BND"),
        "a currency type at a bank" -> bondBody(assetType = AssetTypes.FIAT),
        "a crypto asset at a bank" -> bondBody(assetType = AssetTypes.CRYPTO),
        "an unknown chain scheme" -> bondBody(chainScheme = Some("CARDANO"), chainAssetId = Some(tokenChainAssetId)),
        "a chain scheme without a chain asset id" -> bondBody(chainScheme = Some("CARDANO_MAINNET")),
        "a DTI that is not 9 characters" -> bondBody(dti = Some("X9J9K872")),
        "more decimal places than the registry allows" ->
          write(PostAssetJsonV700("BANKBOND30", AssetTypes.DEBT_SECURITY, "Bank Bond 2030", Assets.MaxDecimalPlaces + 1, None, None, None, None))
      )
      badBodies.foreach { case (clue, body) =>
        val response = makePostRequest(createAt(bankId) <@ (user1), body)
        withClue(clue) {
          response.code should equal(400)
          errorOf(response.body) should startWith(InvalidAsset)
        }
      }
      Assets.getAsset("BANKBOND30").isEmpty shouldBe true
      // BND is in the registry already, as the seeded Brunei dollar: it must still be that, not a bank's bond
      Assets.getAsset("BND").map(_.issuerBankId) should equal(net.liftweb.common.Full(None))
    }

    scenario("an issuer product must exist at the issuing bank", CreateAssetEndpoint, VersionOfApi) {
      seedRegistry()
      grantAt(bankId)
      val missing = makePostRequest(createAt(bankId) <@ (user1), bondBody(productCode = Some("NO-SUCH-PRODUCT")))
      missing.code should equal(404)
      errorOf(missing.body) should startWith(ProductNotFoundByProductCode)

      Await.result(Connector.connector.vend.createOrUpdateProduct(bankId, "BOND-TERMS-30", None, "Bond terms", "bonds",
        "bonds", "bonds", "", "", "", "Terms of the 2030 bond", "", "", None), 30.seconds)
      val found = makePostRequest(createAt(bankId) <@ (user1), bondBody(productCode = Some("BOND-TERMS-30")))
      found.code should equal(201)
      (found.body \ "issuer_product_code") should equal(JString("BOND-TERMS-30"))
    }

    scenario("at SYS a crypto asset is created with no issuer, and nothing else can be", CreateAssetEndpoint, VersionOfApi) {
      seedRegistry()
      val systemBank = "SYS"
      makePostRequest(createAt(systemBank) <@ (user1), bondBody(code = "SOL", assetType = AssetTypes.CRYPTO)).code should equal(403)

      grantAt(systemBank)
      val crypto = makePostRequest(createAt(systemBank) <@ (user1),
        write(PostAssetJsonV700("SOL", "crypto", "Solana", 9, None, None, None, None)))
      crypto.code should equal(201)
      (crypto.body \ "asset_type") should equal(JString(AssetTypes.CRYPTO))
      (crypto.body \ "decimal_places").values should equal(9)
      List(JNothing, JNull) should contain(crypto.body \ "issuer_bank_id")

      val issuedAtSys = makePostRequest(createAt(systemBank) <@ (user1), bondBody())
      issuedAtSys.code should equal(400)
      errorOf(issuedAtSys.body) should startWith(InvalidAsset)
    }

    scenario("the Role at one bank does not let the caller create an asset at another", CreateAssetEndpoint, VersionOfApi) {
      seedRegistry()
      grantAt(bankId)
      val response = makePostRequest(createAt(testBankId2.value) <@ (user1), bondBody())
      response.code should equal(403)
      errorOf(response.body) should startWith(UserHasMissingRoles)
    }
  }
}
