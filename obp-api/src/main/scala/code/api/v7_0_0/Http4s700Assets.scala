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

import cats.effect.IO
import code.api.Constant.{ApiPathZero, DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID}
import code.api.util.APIUtil.{EmptyBody, ResourceDoc, unboxFullOrFail, Http4sRoute}
import code.api.util.ApiRole.canCreateAsset
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.{CallContext, CustomJsonFormats, NewStyle}
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers
import code.asset.{AssetStatuses, AssetTrait, AssetTypes, Assets, ChainSchemes}
import code.util.Helper
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{BankId, ProductCode}
import com.openbankproject.commons.util.ApiVersion
import net.liftweb.common.{Box, Empty, Failure, Full}
import org.http4s._
import org.http4s.dsl.io._
import org.json4s.Formats

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.util.Try

/**
 * This object holds the v7.0.0 endpoints that read the asset registry: the currencies, precious
 * metals, accounting units, crypto assets and bank-issued assets that amounts can be held in (see
 * [[code.asset.Assets]] and ideas/ASSET_REGISTRY.md).
 *
 * The read endpoints need no authentication: the registry is reference data, and an app may need to
 * check a code before anyone has logged in. Create Asset adds an asset to the registry; it needs the
 * CanCreateAsset Role at the asset's administering bank, which is the bank in the path.
 *
 * It is declared in its own object to keep Http4s700's initialiser under the JVM's 64KB method limit.
 */
object Http4s700Assets {

  implicit val formats: Formats = CustomJsonFormats.formats

  private val implementedInApiVersion = ApiVersion.v7_0_0
  private val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString

  val resourceDocs = ArrayBuffer[ResourceDoc]()

  /** The most assets one page returns, and the default page size: large enough for every seeded asset. */
  val MaxPageSize = 500

  // Route: GET /obp/v7.0.0/assets
  lazy val getAssets: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "assets" =>
      EndpointHelpers.executeAndRespond(req) { cc =>
        val parameters = req.uri.query.params
        def filter(name: String): Option[String] = parameters.get(name).map(_.trim).filter(_.nonEmpty)
        val assetType = filter("asset_type").map(_.toUpperCase)
        val status = filter("status").map(_.toUpperCase)
        val chainScheme = filter("chain_scheme").map(_.toUpperCase)
        val issuerBankId = filter("issuer_bank_id")
        val limit = filter("limit").map(value => Try(value.toInt).getOrElse(-1)).getOrElse(MaxPageSize)
        val offset = filter("offset").map(value => Try(value.toInt).getOrElse(-1)).getOrElse(0)
        for {
          _ <- Helper.booleanToFuture(s"$InvalidAssetQueryParameter asset_type must be one of ${AssetTypes.all.toList.sorted.mkString(", ")}.", cc = Some(cc)) {
            assetType.forall(AssetTypes.all.contains)
          }
          _ <- Helper.booleanToFuture(s"$InvalidAssetQueryParameter status must be one of ${AssetStatuses.all.toList.sorted.mkString(", ")}.", cc = Some(cc)) {
            status.forall(AssetStatuses.all.contains)
          }
          _ <- Helper.booleanToFuture(s"$InvalidAssetQueryParameter limit must be a whole number from 1 to $MaxPageSize.", cc = Some(cc)) {
            limit >= 1 && limit <= MaxPageSize
          }
          _ <- Helper.booleanToFuture(s"$InvalidAssetQueryParameter offset must be a whole number, 0 or more.", cc = Some(cc)) {
            offset >= 0
          }
          (assets, total) <- Future(Assets.getAssets(assetType, issuerBankId, status, chainScheme, limit, offset))
        } yield JSONFactory700Assets.createAssetsJson(assets, total, limit, offset)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAssets),
    "GET",
    "/assets",
    "Get Assets",
    s"""Lists the assets in this instance's asset registry: the units an amount can be held in. That is
       |every currency, precious metal and accounting unit in ISO 4217, the crypto assets XBT, ADA and ETH,
       |and assets issued by a bank, such as deposit tokens, stablecoins, bonds or fund shares.
       |
       |For each asset the response gives its code (the value that appears in `currency` fields), its type,
       |how many decimal places its amounts have, the bank that issued it (none for currencies, metals,
       |accounting units and crypto assets, which are administered at the `SYS` bank), and its status:
       |`ACTIVE`, `SUSPENDED` or `RETIRED`.
       |
       |The registry decides which currency codes OBP accepts and how many decimal places each one has.
       |
       |Query parameters, all optional; a filter matches exactly, apart from letter case for the first three:
       |
       |- `asset_type`: one of ${AssetTypes.all.toList.sorted.mkString(", ")}
       |- `status`: one of ${AssetStatuses.all.toList.sorted.mkString(", ")}. Without it, assets of every status are listed.
       |- `chain_scheme`: the chain and network an asset is recorded on, e.g. `CARDANO_MAINNET`
       |- `issuer_bank_id`: the bank that issued the asset
       |- `limit`: the most assets to return, from 1 to $MaxPageSize (default $MaxPageSize)
       |- `offset`: how many assets to skip (default 0)
       |
       |Assets are ordered by code. `pagination.total` is the number of assets matching the filters.
       |
       |No Authentication is Required.""".stripMargin,
    EmptyBody,
    JSONFactory700Assets.assetsJsonExample,
    List(InvalidAssetQueryParameter, UnknownError),
    List(apiTagAsset),
    None,
    http4sPartialFunction = Some(getAssets)
  )

  // Route: GET /obp/v7.0.0/assets/ASSET_CODE
  lazy val getAsset: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "assets" / assetCode if assetCode.nonEmpty =>
      EndpointHelpers.executeAndRespond(req) { cc =>
        Future(Assets.getAsset(assetCode))
          .map(unboxFullOrFail(_, Some(cc), s"$AssetNotFound Current value is $assetCode", 404))
          .map(JSONFactory700Assets.createAssetJson)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAsset),
    "GET",
    "/assets/ASSET_CODE",
    "Get Asset",
    s"""Returns one asset from the asset registry by its code, for example `EUR`, `XAU` or `ETH`.
       |The code is matched ignoring letter case: `eur` returns `EUR`.
       |
       |See Get Assets for what the fields mean.
       |
       |No Authentication is Required.""".stripMargin,
    EmptyBody,
    JSONFactory700Assets.assetJsonExample,
    List(AssetNotFound, UnknownError),
    List(apiTagAsset),
    None,
    http4sPartialFunction = Some(getAsset)
  )

  // Route: GET /obp/v7.0.0/assets/chain/CHAIN_SCHEME/CHAIN_ASSET_ID
  lazy val getAssetByChainIdentity: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "assets" / "chain" / chainScheme / chainAssetId if chainScheme.nonEmpty && chainAssetId.nonEmpty =>
      EndpointHelpers.executeAndRespond(req) { cc =>
        Future(Assets.getAssetByChainIdentity(chainScheme.toUpperCase, chainAssetId))
          .map(unboxFullOrFail(_, Some(cc), s"$AssetNotFoundByChainIdentity Current value is $chainScheme/$chainAssetId", 404))
          .map(JSONFactory700Assets.createAssetJson)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getAssetByChainIdentity),
    "GET",
    "/assets/chain/CHAIN_SCHEME/CHAIN_ASSET_ID",
    "Get Asset by Chain Identity",
    s"""Returns the asset recorded on a blockchain under this identity. It answers the question "a
       |transfer of this token arrived; which asset is it?", for reconciling OBP balances with the chain.
       |
       |`CHAIN_SCHEME` names both the chain and the network, because the same identity can be a different
       |token on another network: for example `CARDANO_MAINNET`, `CARDANO_PREPROD` or `ETHEREUM_SEPOLIA`.
       |It is matched ignoring letter case. `CHAIN_ASSET_ID` is the token's identity on that network: on
       |Cardano, the policy id and the hex asset name joined by a dot; on Ethereum, the token's contract address.
       |
       |Only tokens have a chain identity. A chain's own currency, such as ADA or ETH, has none.
       |
       |No Authentication is Required.""".stripMargin,
    EmptyBody,
    JSONFactory700Assets.issuedAssetJsonExample,
    List(AssetNotFoundByChainIdentity, UnknownError),
    List(apiTagAsset),
    None,
    http4sPartialFunction = Some(getAssetByChainIdentity)
  )

  /** Treats a missing, empty or blank optional value as not set. */
  private def given(value: Option[String]): Option[String] = value.map(_.trim).filter(_.nonEmpty)

  /** Turns the registry's refusal into a 400 that says what was wrong with the asset. */
  private def createdOrFail(created: Box[AssetTrait], cc: CallContext): AssetTrait = created match {
    case Full(asset) => asset
    case Failure(message, _, _) => unboxFullOrFail(Empty, Some(cc), s"$InvalidAsset $message", 400)
    case _ => unboxFullOrFail(Empty, Some(cc), s"$InvalidAsset", 400)
  }

  // Route: POST /obp/v7.0.0/banks/BANK_ID/assets (201)
  lazy val createAsset: Http4sRoute = Http4sRoute {
    case req @ POST -> `prefixPath` / "banks" / bankIdInUrl / "assets" =>
      EndpointHelpers.withUserAndBodyCreated[PostAssetJsonV700, AssetJsonV700](req) { (user, body, cc) =>
        val atSystemBank = bankIdInUrl == DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID
        val assetCode = Assets.normaliseCode(Option(body.asset_code).getOrElse(""))
        val assetType = Option(body.asset_type).map(_.trim.toUpperCase).getOrElse("")
        val issuerProductCode = given(body.issuer_product_code)
        val chainScheme = given(body.chain_scheme).map(_.toUpperCase)
        val chainAssetId = given(body.chain_asset_id)
        val dti = given(body.dti).map(_.toUpperCase)
        for {
          _ <- Helper.booleanToFuture(s"$InvalidAsset At the SYS bank only an asset of type ${AssetTypes.CRYPTO} can be created. Current asset_type is $assetType.", 400, Some(cc)) {
            !atSystemBank || assetType == AssetTypes.CRYPTO
          }
          _ <- Helper.booleanToFuture(s"$InvalidAsset At a bank, asset_type must be one of ${AssetTypes.issued.toList.sorted.mkString(", ")}. Current asset_type is $assetType.", 400, Some(cc)) {
            atSystemBank || AssetTypes.issued.contains(assetType)
          }
          _ <- Helper.booleanToFuture(s"$InvalidAsset An asset at the SYS bank has no issuer, so it has no issuer_product_code.", 400, Some(cc)) {
            !atSystemBank || issuerProductCode.isEmpty
          }
          // The issuer's product describes the instrument, so it must exist at the issuing bank
          _ <- issuerProductCode match {
            case Some(productCode) => NewStyle.function.getProduct(BankId(bankIdInUrl), ProductCode(productCode), Some(cc))
            case None => Future.successful(())
          }
          _ <- Helper.booleanToFuture(s"$AssetAlreadyExists Current asset_code is $assetCode.", 409, Some(cc)) {
            Assets.getAsset(assetCode).isEmpty
          }
          _ <- Helper.booleanToFuture(s"$AssetChainIdentityAlreadyExists Current value is ${chainScheme.getOrElse("")}/${chainAssetId.getOrElse("")}.", 409, Some(cc)) {
            (for { scheme <- chainScheme; id <- chainAssetId } yield Assets.getAssetByChainIdentity(scheme, id).isEmpty).getOrElse(true)
          }
          _ <- Helper.booleanToFuture(s"$AssetDtiAlreadyExists Current dti is ${dti.getOrElse("")}.", 409, Some(cc)) {
            dti.forall(value => Assets.getAssetByDti(value).isEmpty)
          }
          created <- Future(Assets.createAsset(
            assetCode = assetCode,
            assetType = assetType,
            name = Option(body.name).getOrElse(""),
            decimalPlaces = body.decimal_places,
            issuerBankId = if (atSystemBank) None else Some(bankIdInUrl),
            status = AssetStatuses.ACTIVE,
            createdByUserId = user.userId,
            issuerProductCode = issuerProductCode,
            chainScheme = chainScheme,
            chainAssetId = chainAssetId,
            dti = dti
          )).map(createdOrFail(_, cc))
        } yield JSONFactory700Assets.createAssetJson(created)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(createAsset),
    "POST",
    "/banks/BANK_ID/assets",
    "Create Asset",
    s"""Adds an asset to the asset registry, so that accounts, transactions and product fees can hold
       |amounts in it. Its code becomes a value OBP accepts in `currency` fields.
       |
       |BANK_ID is the bank that administers the asset, and the Role is checked there:
       |
       |- At a bank, the asset is one that bank issues, and the bank is recorded as its issuer. `asset_type`
       |  must be one of ${AssetTypes.issued.toList.sorted.mkString(", ")}.
       |- At `SYS`, the asset has no issuer. Only a crypto asset (`asset_type` ${AssetTypes.CRYPTO}) can be
       |  created there. Currencies, precious metals and accounting units come from ISO 4217 and cannot be created.
       |
       |The fields:
       |
       |- `asset_code`: 3 to 10 letters or digits, stored upper case. A bank-issued asset's code is 4 to 10, so
       |  it can never be mistaken for an ISO 4217 currency code, which is always 3. It must not be taken.
       |- `name`: the display name, at most ${Assets.MaxNameLength} characters.
       |- `decimal_places`: how many digits an amount in this asset may have after the decimal point, from 0 to
       |  ${Assets.MaxDecimalPlaces}. It cannot be changed later.
       |- `issuer_product_code`: optional, the product at BANK_ID that describes the instrument, such as a bond's
       |  coupon and maturity date. The product must exist.
       |- `chain_scheme` and `chain_asset_id`: optional, both or neither. The chain and network the token is
       |  recorded on, one of ${ChainSchemes.all.toList.sorted.mkString(", ")}, and its identity there: on
       |  Cardano, the policy id and the hex asset name joined by a dot; on Ethereum, the token's contract
       |  address. No other asset may have the same chain identity.
       |- `dti`: optional, the ISO 24165 Digital Token Identifier, 9 letters or digits. No other asset may have it.
       |
       |A new asset is `ACTIVE`.
       |
       |Authentication is Required.""".stripMargin,
    JSONFactory700Assets.postAssetJsonExample,
    JSONFactory700Assets.issuedAssetJsonExample,
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidJsonFormat, InvalidAsset,
      ProductNotFoundByProductCode, AssetAlreadyExists, AssetChainIdentityAlreadyExists, AssetDtiAlreadyExists, UnknownError),
    List(apiTagAsset),
    Some(List(canCreateAsset)),
    http4sPartialFunction = Some(createAsset)
  ).allowSystemSpace()
}
