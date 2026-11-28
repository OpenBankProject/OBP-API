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

import code.api.util.{APIUtil, DoobieUtil}
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}

import java.util.Date

/**
 * This class is one row of the asset registry: a unit that amounts can be held in, such as a
 * currency (EUR), a precious metal (XAU), a crypto asset (ETH) or an asset a bank issues (a deposit
 * token, a bond). Its `assetCode` is the value that appears in `currency` fields.
 *
 * The design is in ideas/ASSET_REGISTRY.md. The registry is seeded at boot (see [[AssetSeed]]), and
 * `APIUtil.isValidCurrencyISOCode` and `Helper.currencyDecimalPlaces` read it through [[AssetLookup]].
 *
 * An empty string in an optional column means "not set"; the accessors on [[AssetTrait]] turn it
 * into `None`.
 *
 * Upstream declares this as a Lift Mapper entity; here it is a Doobie store over the table the
 * changelog creates (db.changelog-develop-merge-3.yaml). The asset code is always stored upper case,
 * so a lookup that upper-cases its input ignores case.
 */
case class Asset(
  assetId: String,
  assetCode: String,
  assetType: String,
  name: String,
  decimalPlaces: Int,
  private[asset] val issuerBankIdRaw: String,
  private[asset] val issuerProductCodeRaw: String,
  private[asset] val chainSchemeRaw: String,
  private[asset] val chainAssetIdRaw: String,
  private[asset] val dtiRaw: String,
  status: String,
  createdByUserId: String,
  createdAt: Date,
  updatedAt: Date
) extends AssetTrait {
  override def issuerBankId: Option[String] = nonEmpty(issuerBankIdRaw)
  override def issuerProductCode: Option[String] = nonEmpty(issuerProductCodeRaw)
  override def chainScheme: Option[String] = nonEmpty(chainSchemeRaw)
  override def chainAssetId: Option[String] = nonEmpty(chainAssetIdRaw)
  override def dti: Option[String] = nonEmpty(dtiRaw)

  private def nonEmpty(value: String): Option[String] = Option(value).filter(_.nonEmpty)

  /** A copy with the chain identity set, as `ChainScheme(...).ChainAssetId(...)` did on the Mapper row. */
  def withChainIdentity(chainScheme: String, chainAssetId: String): Asset =
    copy(chainSchemeRaw = chainScheme, chainAssetIdRaw = chainAssetId)
}

/**
 * The unique indexes on (chain_scheme, chain_asset_id) and on dti that the design describes are not
 * declared: they must ignore rows where the value is not set, and the schema does not declare such a
 * partial index. The Create Asset endpoint checks both before it writes, and answers 409 when one is taken.
 */
object Asset {

  private val selectColumns =
    fr"""SELECT assetid, assetcode, assettype, name, decimalplaces, issuerbankid, issuerproductcode,
                chainscheme, chainassetid, dti, status, createdbyuserid, creationdate, lastupdate
         FROM asset"""

  private type Row = (Option[String], Option[String], Option[String], Option[String], Option[Int],
    Option[String], Option[String], Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[java.sql.Timestamp], Option[java.sql.Timestamp])

  private def readDate(t: Option[java.sql.Timestamp]): Date = t.map(ts => new Date(ts.getTime)).orNull

  private def ts(d: Date): Option[java.sql.Timestamp] = Option(d).map(x => new java.sql.Timestamp(x.getTime))

  private def fromRow(r: Row): Asset =
    Asset(r._1.orNull, r._2.orNull, r._3.orNull, r._4.orNull, r._5.getOrElse(0), r._6.orNull, r._7.orNull,
      r._8.orNull, r._9.orNull, r._10.orNull, r._11.orNull, r._12.orNull, readDate(r._13), readDate(r._14))

  private def query(condition: Fragment): List[Asset] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def first(condition: Fragment): Box[Asset] =
    query(condition ++ fr"ORDER BY id ASC").headOption match {
      case Some(asset) => Full(asset)
      case None => Empty
    }

  /** The WHERE clause of every filter given; a filter of `None` matches everything. */
  private def where(filters: List[Fragment]): Fragment =
    if (filters.isEmpty) Fragment.empty else fr"WHERE" ++ filters.reduce((a, b) => a ++ fr"AND" ++ b)

  /** Equality on a column; the value is wrapped so a null binds as SQL NULL, which matches nothing. */
  private[asset] def column(name: String, value: String): Fragment =
    Fragment.const(name) ++ fr"= ${Option(value)}"

  def findByAssetCode(assetCode: String): Box[Asset] = first(fr"WHERE assetcode = ${Option(assetCode)}")

  def findByChainIdentity(chainScheme: String, chainAssetId: String): Box[Asset] =
    first(fr"WHERE chainscheme = ${Option(chainScheme)} AND chainassetid = ${Option(chainAssetId)}")

  def findByDti(dti: String): Box[Asset] = first(fr"WHERE dti = ${Option(dti)}")

  def findAllOrderedByCode(): List[Asset] = query(fr"ORDER BY assetcode ASC")

  /** One page of the rows matching every filter, ordered by code, and how many rows match in total. */
  def findPage(filters: List[Fragment], limit: Int, offset: Int): (List[Asset], Int) = {
    val total = DoobieUtil.runQuery((fr"SELECT COUNT(*) FROM asset" ++ where(filters)).query[Long].unique).toInt
    val page = query(where(filters) ++ fr"ORDER BY assetcode ASC" ++ DoobieUtil.pagination(limit, offset))
    (page, total)
  }

  def count(): Long = DoobieUtil.runQuery(sql"SELECT COUNT(*) FROM asset".query[Long].unique)

  /** Writes a new row; the asset id and both dates are given their defaults, as Mapper's were. */
  def insert(assetCode: String, assetType: String, name: String, decimalPlaces: Int, issuerBankId: String,
             issuerProductCode: String, chainScheme: String, chainAssetId: String, dti: String, status: String,
             createdByUserId: String): Asset = {
    val asset = Asset(APIUtil.generateUUID(), assetCode, assetType, name, decimalPlaces, issuerBankId,
      issuerProductCode, chainScheme, chainAssetId, dti, status, createdByUserId, new Date(), new Date())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO asset (assetid, assetcode, assettype, name, decimalplaces, issuerbankid, issuerproductcode,
              chainscheme, chainassetid, dti, status, createdbyuserid, creationdate, lastupdate)
            VALUES (${Option(asset.assetId)}, ${Option(asset.assetCode)}, ${Option(asset.assetType)},
              ${Option(asset.name)}, ${asset.decimalPlaces}, ${Option(asset.issuerBankIdRaw)},
              ${Option(asset.issuerProductCodeRaw)}, ${Option(asset.chainSchemeRaw)}, ${Option(asset.chainAssetIdRaw)},
              ${Option(asset.dtiRaw)}, ${Option(asset.status)}, ${Option(asset.createdByUserId)},
              ${ts(asset.createdAt)}, ${ts(asset.updatedAt)})""".update.run)
    asset
  }

  /** Rewrites every column of the row with this asset id except the id itself and the creation date. */
  def update(asset: Asset): Asset = {
    DoobieUtil.runUpdate(
      sql"""UPDATE asset SET assetcode = ${Option(asset.assetCode)}, assettype = ${Option(asset.assetType)},
              name = ${Option(asset.name)}, decimalplaces = ${asset.decimalPlaces},
              issuerbankid = ${Option(asset.issuerBankIdRaw)}, issuerproductcode = ${Option(asset.issuerProductCodeRaw)},
              chainscheme = ${Option(asset.chainSchemeRaw)}, chainassetid = ${Option(asset.chainAssetIdRaw)},
              dti = ${Option(asset.dtiRaw)}, status = ${Option(asset.status)},
              createdbyuserid = ${Option(asset.createdByUserId)}, lastupdate = ${ts(asset.updatedAt)}
            WHERE assetid = ${Option(asset.assetId)}""".update.run)
    asset
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM asset".update.run)
    ()
  }
}

/**
 * This class is one change of an asset's status, for example ACTIVE to SUSPENDED, with who made it,
 * when and why. The design keeps this separate from the metrics because metrics can be switched off
 * and record API calls rather than state changes. Nothing writes it yet: the seed creates assets as
 * ACTIVE, which is not a change, and there is no endpoint that changes a status.
 *
 * Upstream declares this as a Lift Mapper entity; here it is a row of the assetstatushistory table
 * the changelog creates, with the store below.
 */
case class AssetStatusHistory(
  assetId: String,
  fromStatus: String,
  toStatus: String,
  reason: String,
  changedByUserId: String,
  changedAt: Date
)

object AssetStatusHistory {

  private type Row = (Option[String], Option[String], Option[String], Option[String], Option[String],
    Option[java.sql.Timestamp])

  /** Records a change; `changedAt` defaults to now, as Mapper's did. */
  def insert(assetId: String, fromStatus: String, toStatus: String, reason: String, changedByUserId: String,
             changedAt: Date = new Date()): AssetStatusHistory = {
    DoobieUtil.runUpdate(
      sql"""INSERT INTO assetstatushistory (assetid, fromstatus, tostatus, reason, changedbyuserid, changedat)
            VALUES (${Option(assetId)}, ${Option(fromStatus)}, ${Option(toStatus)}, ${Option(reason)},
              ${Option(changedByUserId)}, ${Option(changedAt).map(d => new java.sql.Timestamp(d.getTime))})"""
        .update.run)
    AssetStatusHistory(assetId, fromStatus, toStatus, reason, changedByUserId, changedAt)
  }

  /** An asset's status changes, oldest first. */
  def findByAssetId(assetId: String): List[AssetStatusHistory] =
    DoobieUtil.runQuery(
      sql"""SELECT assetid, fromstatus, tostatus, reason, changedbyuserid, changedat FROM assetstatushistory
            WHERE assetid = ${Option(assetId)} ORDER BY id ASC""".query[Row].to[List]).map {
      case (a, f, t, r, c, at) =>
        AssetStatusHistory(a.orNull, f.orNull, t.orNull, r.orNull, c.orNull, at.map(x => new Date(x.getTime)).orNull)
    }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM assetstatushistory".update.run)
    ()
  }
}
