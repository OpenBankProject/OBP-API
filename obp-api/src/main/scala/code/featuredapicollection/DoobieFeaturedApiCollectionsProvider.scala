package code.featuredapicollection

import java.sql.Timestamp

import code.api.util.{APIUtil, DoobieUtil}
import code.util.Helper.MdcLoggable
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

/** One featured-api-collection row, standing in for the Lift entity in return types. */
case class FeaturedApiCollectionRow(
  featuredApiCollectionId: String,
  apiCollectionId: String,
  sortOrder: Int
) extends FeaturedApiCollectionTrait

/**
 * Doobie implementation of the featured-api-collections store, replacing the Lift
 * FeaturedApiCollection entity.
 *
 * Neither this table nor the v6.0.0 endpoints that use it had test coverage before this change -
 * FeaturedApiCollectionsProviderTest was written first to pin the contract this replaces.
 *
 * Both unique indexes are load-bearing: one on the generated id, and one on apiCollectionId,
 * which is what NewStyle.checkFeaturedApiCollectionDoesNotExist relies on - it reads the row back
 * rather than trusting the insert to fail, but a second insert past that check must still be
 * rejected by the database rather than silently creating a duplicate featured entry.
 *
 * updateFeaturedApiCollection rewrites sortOrder in place; nothing else on the row changes.
 * getAllFeaturedApiCollections stays ordered by sortOrder ascending, since
 * NewStyle.getFeaturedApiCollections presents collections in that order.
 *
 * The two delete methods return Empty rather than Full(false) when there is no matching row -
 * find-then-delete was the Mapper shape, and NewStyle.deleteFeaturedApiCollectionByApiCollectionId
 * unboxes the result with unboxFullOrFail, which only turns a missing row into an error on Empty.
 */
object DoobieFeaturedApiCollectionsProvider extends MdcLoggable with FeaturedApiCollectionsProvider {

  private def rowOf(r: (String, String, Int)): FeaturedApiCollectionRow =
    FeaturedApiCollectionRow(r._1, r._2, r._3)

  private val selectCols =
    fr"SELECT featuredapicollectionid, apicollectionid, sortorder FROM featuredapicollection"

  override def createFeaturedApiCollection(
    apiCollectionId: String,
    sortOrder: Int
  ): Box[FeaturedApiCollectionTrait] = {
    val id = APIUtil.generateUUID()
    val now = new Timestamp(System.currentTimeMillis)
    tryo {
      DoobieUtil.runUpdate(
        sql"""INSERT INTO featuredapicollection
                (featuredapicollectionid, apicollectionid, sortorder, createdat, updatedat)
              VALUES ($id, $apiCollectionId, $sortOrder, $now, $now)"""
          .update.run)
      FeaturedApiCollectionRow(id, apiCollectionId, sortOrder)
    }
  }

  override def getFeaturedApiCollectionById(featuredApiCollectionId: String): Box[FeaturedApiCollectionTrait] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE featuredapicollectionid = $featuredApiCollectionId LIMIT 1")
        .query[(String, String, Int)].option
    ) match {
      case Some(r) => Full(rowOf(r))
      case None    => Empty
    }

  override def getFeaturedApiCollectionByApiCollectionId(apiCollectionId: String): Box[FeaturedApiCollectionTrait] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE apicollectionid = $apiCollectionId LIMIT 1")
        .query[(String, String, Int)].option
    ) match {
      case Some(r) => Full(rowOf(r))
      case None    => Empty
    }

  override def updateFeaturedApiCollection(
    featuredApiCollectionId: String,
    sortOrder: Int
  ): Box[FeaturedApiCollectionTrait] =
    getFeaturedApiCollectionById(featuredApiCollectionId) match {
      case Full(existing: FeaturedApiCollectionRow) =>
        tryo {
          DoobieUtil.runUpdate(
            sql"UPDATE featuredapicollection SET sortorder = $sortOrder WHERE featuredapicollectionid = $featuredApiCollectionId"
              .update.run)
          existing.copy(sortOrder = sortOrder)
        }
      case _ => Empty
    }

  override def getAllFeaturedApiCollections(): List[FeaturedApiCollectionTrait] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"ORDER BY sortorder ASC").query[(String, String, Int)].to[List]
    ).map(rowOf)

  override def deleteFeaturedApiCollectionById(featuredApiCollectionId: String): Box[Boolean] =
    getFeaturedApiCollectionById(featuredApiCollectionId) match {
      case Full(_) =>
        tryo {
          DoobieUtil.runUpdate(
            sql"DELETE FROM featuredapicollection WHERE featuredapicollectionid = $featuredApiCollectionId".update.run)
          true
        }
      case _ => Empty
    }

  override def deleteFeaturedApiCollectionByApiCollectionId(apiCollectionId: String): Box[Boolean] =
    getFeaturedApiCollectionByApiCollectionId(apiCollectionId) match {
      case Full(_) =>
        tryo {
          DoobieUtil.runUpdate(
            sql"DELETE FROM featuredapicollection WHERE apicollectionid = $apiCollectionId".update.run)
          true
        }
      case _ => Empty
    }
}
