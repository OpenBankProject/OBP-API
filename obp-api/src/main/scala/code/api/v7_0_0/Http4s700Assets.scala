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
import code.api.Constant.ApiPathZero
import code.api.util.APIUtil.{EmptyBody, ResourceDoc, unboxFullOrFail, Http4sRoute}
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.CustomJsonFormats
import code.api.util.http4s.Http4sRequestAttributes.EndpointHelpers
import code.asset.{AssetStatuses, AssetTypes, Assets}
import code.util.Helper
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.util.ApiVersion
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
 * They need no authentication: the registry is reference data, and an app may need to check a code
 * before anyone has logged in. Nothing here writes.
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
       |The registry does not yet decide which currency codes OBP accepts or how many decimal places it
       |gives them; it shows what it will decide once it does.
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
}
