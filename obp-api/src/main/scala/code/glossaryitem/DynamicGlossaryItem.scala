package code.glossaryitem

import java.util.Date

import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import scala.concurrent.Future

/**
 * A Glossary Item an operator added at runtime, optionally shadowing a static one.
 *
 * Upstream declares this as a Lift Mapper entity; this branch has no Lift Mapper and no
 * Schemifier, so it is a case class over a Doobie store with its table in
 * db.changelog-develop-merge-2.yaml.
 */
case class DynamicGlossaryItem(
  glossaryItemId: String,
  title: String,
  description: String,
  overridesStaticItem: Boolean,
  createdByUserId: String,
  createdAt: Date,
  updatedAt: Date
) extends DynamicGlossaryItemTrait

object DynamicGlossaryItem {

  private val selectColumns =
    fr"""SELECT glossaryitemid, title, description, overridesstaticitem, createdbyuserid,
                creationdate, lastupdate
         FROM dynamicglossaryitem"""

  // Nullable columns read through Option: a bare String Get throws NonNullableColumnRead on a
  // SQL NULL and fails the whole query rather than the one row.
  private type Row = (Option[String], Option[String], Option[String], Option[Boolean],
    Option[String], Option[java.sql.Timestamp], Option[java.sql.Timestamp])

  /** java.sql.Timestamp is a java.util.Date subclass, but json4s renders it as {} - convert. */
  private def readDate(value: Option[java.sql.Timestamp]): Date =
    value.map(t => new Date(t.getTime)).orNull

  private def fromRow(row: Row): DynamicGlossaryItem = row match {
    case (glossaryItemId, title, description, overridesStaticItem, createdByUserId,
          creationDate, lastUpdate) =>
      // MappedBoolean read a NULL as false whatever its declared defaultValue - and false is
      // also this field's default, so a row predating the column stays non-overriding.
      DynamicGlossaryItem(glossaryItemId.orNull, title.orNull, description.orNull,
        overridesStaticItem.getOrElse(false), createdByUserId.orNull,
        readDate(creationDate), readDate(lastUpdate))
  }

  private def query(condition: Fragment): List[DynamicGlossaryItem] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  /** Lookup is on the lower-cased copy, so it is case insensitive on every database collation. */
  def findByTitle(title: String): Box[DynamicGlossaryItem] =
    query(fr"WHERE titlelowercase = ${title.toLowerCase} ORDER BY id ASC LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def findAll(): List[DynamicGlossaryItem] = query(fr"ORDER BY titlelowercase ASC")

  /** Count before limit/offset so the caller can page; the filter is bound, never spliced. */
  def findPage(titleFilter: Option[String], limit: Int, offset: Int): (List[DynamicGlossaryItem], Int) = {
    val where = titleFilter
      .map(t => fr"WHERE titlelowercase LIKE ${"%" + t.toLowerCase + "%"}")
      .getOrElse(Fragment.empty)
    val total = DoobieUtil.runQuery(
      (fr"SELECT count(*) FROM dynamicglossaryitem" ++ where).query[Int].unique)
    val rows = query(where ++ fr"ORDER BY titlelowercase ASC LIMIT $limit OFFSET $offset")
    (rows, total)
  }

  /**
   * A cheap fingerprint of the whole table, read on every Glossary call to decide whether a
   * cached rendering is still valid. Row count catches inserts and deletes; the newest lastupdate
   * catches edits. A delete plus an insert leaves the count unchanged but moves lastupdate
   * forward, so the pair is enough.
   */
  def version(): String = {
    val (count, newest) = DoobieUtil.runQuery(
      sql"""SELECT count(*), max(lastupdate) FROM dynamicglossaryitem"""
        .query[(Int, Option[java.sql.Timestamp])].unique)
    s"$count-${newest.map(_.getTime).getOrElse(0L)}"
  }

  def insert(title: String, description: String, overridesStaticItem: Boolean,
             createdByUserId: String): DynamicGlossaryItem = {
    val glossaryItemId = APIUtil.generateUUID()
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO dynamicglossaryitem
            (glossaryitemid, title, titlelowercase, description, overridesstaticitem,
             createdbyuserid, creationdate, lastupdate)
            VALUES ($glossaryItemId, ${Option(title)}, ${Option(title.toLowerCase)},
             ${Option(description)}, $overridesStaticItem, ${Option(createdByUserId)}, $now, $now)"""
        .update.run)
    findByTitle(title).openOrThrowException("the glossary item just inserted must be readable")
  }

  def update(title: String, description: String,
             overridesStaticItem: Option[Boolean]): Box[DynamicGlossaryItem] = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    // An absent overridesStaticItem leaves the stored value alone, as the Mapper path did.
    val overrideSet = overridesStaticItem
      .map(v => fr", overridesstaticitem = $v")
      .getOrElse(Fragment.empty)
    DoobieUtil.runUpdate(
      (fr"UPDATE dynamicglossaryitem SET description = ${Option(description)}, lastupdate = $now" ++
        overrideSet ++
        fr"WHERE titlelowercase = ${title.toLowerCase}").update.run)
    findByTitle(title)
  }

  def delete(title: String): Boolean = {
    DoobieUtil.runUpdate(
      sql"DELETE FROM dynamicglossaryitem WHERE titlelowercase = ${title.toLowerCase}".update.run)
    true
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM dynamicglossaryitem".update.run)
    ()
  }
}

object MappedDynamicGlossaryItemProvider extends DynamicGlossaryItemProvider {

  override def createDynamicGlossaryItem(
    title: String,
    description: String,
    overridesStaticItem: Boolean,
    createdByUserId: String
  ): Box[DynamicGlossaryItemTrait] =
    tryo { DynamicGlossaryItem.insert(title, description, overridesStaticItem, createdByUserId) }

  override def getDynamicGlossaryItemByTitle(title: String): Box[DynamicGlossaryItemTrait] =
    DynamicGlossaryItem.findByTitle(title)

  override def getDynamicGlossaryItems(
    titleFilter: Option[String],
    limit: Int,
    offset: Int
  ): Future[Box[(List[DynamicGlossaryItemTrait], Int)]] = Future {
    tryo {
      val (rows, total) = DynamicGlossaryItem.findPage(titleFilter, limit, offset)
      (rows.map(r => r: DynamicGlossaryItemTrait), total)
    }
  }

  override def getAllDynamicGlossaryItems: Box[List[DynamicGlossaryItemTrait]] =
    tryo { DynamicGlossaryItem.findAll().map(r => r: DynamicGlossaryItemTrait) }

  override def getDynamicGlossaryItemsVersion: Box[String] = tryo { DynamicGlossaryItem.version() }

  override def updateDynamicGlossaryItem(
    title: String,
    description: String,
    overridesStaticItem: Option[Boolean]
  ): Box[DynamicGlossaryItemTrait] =
    DynamicGlossaryItem.update(title, description, overridesStaticItem)

  override def deleteDynamicGlossaryItem(title: String): Box[Boolean] =
    DynamicGlossaryItem.findByTitle(title).flatMap { _ =>
      tryo { DynamicGlossaryItem.delete(title) }
    }
}
