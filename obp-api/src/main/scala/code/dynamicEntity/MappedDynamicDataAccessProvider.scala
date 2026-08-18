package code.DynamicData

import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import scala.collection.mutable

/**
 * One user's access to one dynamic-entity data row.
 *
 * `bankId` genuinely holds NULL for system-level entities, and the scoped queries use `IS NULL`
 * when no bank is supplied — Lift's NullRef, not "no filter". A row storing "" would be invisible
 * to them, so the column stays nullable and the distinction is preserved.
 */
case class DynamicDataAccess(
  dynamicDataId: String,
  userId: String,
  canRead: Boolean,
  canUpdate: Boolean,
  canDelete: Boolean,
  canGrant: Boolean,
  grantedBy: String,
  entityName: String,
  bankId: Option[String]
) extends DynamicDataAccessT

object DynamicDataAccess {

  // ProjectionStore builds raw SQL against this table for user-scoped EXISTS joins, and used to
  // read the names off the Lift metadata. They live here so the DDL and that SQL cannot drift.
  val tableName: String = "dynamicdataaccess"
  val dataIdColumn: String = "dynamicdataid"
  val userIdColumn: String = "userid"
  val canReadColumn: String = "canread"

  private val selectColumns =
    fr"""SELECT dynamicdataid, userid, canread, canupdate, candelete, cangrant, grantedby,
                entityname, bankid
         FROM dynamicdataaccess"""

  // grantedby and entityname are bound through Option on insert, so they are read as Option; the
  // flags follow MappedBoolean, which read a NULL column as false rather than throwing.
  private type Row = (Option[String], Option[String], Option[Boolean], Option[Boolean],
    Option[Boolean], Option[Boolean], Option[String], Option[String], Option[String])

  private def fromRow(row: Row): DynamicDataAccess = row match {
    case (dynamicDataId, userId, canRead, canUpdate, canDelete, canGrant, grantedBy, entityName,
          bankId) =>
      DynamicDataAccess(dynamicDataId.orNull, userId.orNull, canRead.getOrElse(false),
        canUpdate.getOrElse(false), canDelete.getOrElse(false), canGrant.getOrElse(false),
        grantedBy.orNull, entityName.orNull, bankId)
  }

  private def query(condition: Fragment): List[DynamicDataAccess] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  /** `None` means the column must be NULL, matching Lift's NullRef — not "do not filter". */
  private def scopedBank(bankId: Option[String]): Fragment =
    bankId.map(b => fr"bankid = $b").getOrElse(fr"bankid IS NULL")

  def find(dynamicDataId: String, userId: String): Box[DynamicDataAccess] =
    query(fr"WHERE dynamicdataid = $dynamicDataId AND userid = $userId ORDER BY id ASC LIMIT 1")
      .headOption match {
        case Some(row) => Full(row)
        case None => Empty
      }

  def findAllForRow(dynamicDataId: String): List[DynamicDataAccess] =
    query(fr"WHERE dynamicdataid = $dynamicDataId ORDER BY id ASC")

  def findGrantedBy(dynamicDataId: String, grantedBy: String): List[DynamicDataAccess] =
    query(fr"WHERE dynamicdataid = $dynamicDataId AND grantedby = $grantedBy ORDER BY id ASC")

  def findReadable(userId: String, entityName: String, bankId: Option[String]): List[DynamicDataAccess] =
    query(fr"WHERE userid = $userId AND entityname = $entityName AND canread = true AND " ++
      scopedBank(bankId) ++ fr"ORDER BY id ASC")

  /** Upsert on (dynamicdataid, userid) — the unique index is what makes that pair single-valued. */
  def grant(dynamicDataId: String, userId: String, canRead: Boolean, canUpdate: Boolean,
            canDelete: Boolean, canGrant: Boolean, entityName: String, bankId: Option[String],
            grantedBy: String): DynamicDataAccess = {
    val updated = DoobieUtil.runUpdate(
      sql"""UPDATE dynamicdataaccess SET canread = $canRead, canupdate = $canUpdate,
              candelete = $canDelete, cangrant = $canGrant, entityname = ${Option(entityName)},
              bankid = $bankId, grantedby = ${Option(grantedBy)}
            WHERE dynamicdataid = $dynamicDataId AND userid = $userId""".update.run)
    if (updated == 0) {
      DoobieUtil.runUpdate(
        sql"""INSERT INTO dynamicdataaccess
              (dynamicdataid, userid, canread, canupdate, candelete, cangrant, grantedby,
               entityname, bankid)
              VALUES ($dynamicDataId, $userId, $canRead, $canUpdate, $canDelete, $canGrant,
               ${Option(grantedBy)}, ${Option(entityName)}, $bankId)"""
          .update.run)
    }
    find(dynamicDataId, userId)
      .openOrThrowException("the access row just written must be readable")
  }

  def delete(dynamicDataId: String, userId: String): Int =
    DoobieUtil.runUpdate(
      sql"DELETE FROM dynamicdataaccess WHERE dynamicdataid = $dynamicDataId AND userid = $userId"
        .update.run)

  def deleteAllForRow(dynamicDataId: String): Boolean = {
    DoobieUtil.runUpdate(
      sql"DELETE FROM dynamicdataaccess WHERE dynamicdataid = $dynamicDataId".update.run)
    true
  }

  def deleteAllForEntity(entityName: String, bankId: Option[String]): Boolean = {
    DoobieUtil.runUpdate(
      (fr"DELETE FROM dynamicdataaccess WHERE entityname = $entityName AND " ++
        scopedBank(bankId)).update.run)
    true
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM dynamicdataaccess".update.run)
    ()
  }
}

object MappedDynamicDataAccessProvider extends DynamicDataAccessProvider {

  override def grant(dynamicDataId: String, userId: String,
                     canRead: Boolean, canUpdate: Boolean, canDelete: Boolean, canGrant: Boolean,
                     entityName: String, bankId: Option[String], grantedBy: String): Box[DynamicDataAccessT] = tryo {
    DynamicDataAccess.grant(dynamicDataId, userId, canRead, canUpdate, canDelete, canGrant,
      entityName, bankId, grantedBy)
  }

  override def revoke(dynamicDataId: String, userId: String): Box[Int] = tryo {
    // Walk the GrantedBy edges within this single data row: remove the target user and
    // everyone they granted downstream. The visited-set makes re-share cycles terminate
    // and absorbs the owner row's self-edge (GrantedBy == UserId).
    val toRemove = mutable.LinkedHashSet[String](userId)
    val visited  = mutable.Set[String]()
    var frontier = List(userId)
    while (frontier.nonEmpty) {
      val current = frontier.head
      frontier = frontier.tail
      if (!visited.contains(current)) {
        visited += current
        val children = DynamicDataAccess.findGrantedBy(dynamicDataId, current)
          .map(_.userId).filterNot(visited.contains)
        children.foreach { child =>
          toRemove += child
          frontier = child :: frontier
        }
      }
    }
    // Counts the users whose row actually went, as the Mapper version's count(identity) over
    // delete_! results did — a user in the walk with no row here contributes nothing.
    toRemove.toList.map(uid => DynamicDataAccess.delete(dynamicDataId, uid)).count(_ > 0)
  }

  override def getAccessForRow(dynamicDataId: String): List[DynamicDataAccessT] =
    DynamicDataAccess.findAllForRow(dynamicDataId)

  override def getReadableDynamicDataIds(userId: String, entityName: String, bankId: Option[String]): List[String] =
    DynamicDataAccess.findReadable(userId, entityName, bankId).map(_.dynamicDataId)

  override def allows(dynamicDataId: String, userId: String, permission: DynamicDataAccessPermission): Boolean = {
    import DynamicDataAccessPermission._
    DynamicDataAccess.find(dynamicDataId, userId).map { row =>
      permission match {
        case Read   => row.canRead
        case Update => row.canUpdate
        case Delete => row.canDelete
        case Grant  => row.canGrant
      }
    }.getOrElse(false)
  }

  override def deleteAllForRow(dynamicDataId: String): Box[Boolean] =
    tryo(DynamicDataAccess.deleteAllForRow(dynamicDataId))

  override def deleteAllForEntity(entityName: String, bankId: Option[String]): Box[Boolean] =
    tryo(DynamicDataAccess.deleteAllForEntity(entityName, bankId))
}
