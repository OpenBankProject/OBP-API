package code.entitlement

import code.api.dynamic.entity.helper.DynamicEntityInfo
import code.api.util.{APIUtil, DoobieUtil, NotificationUtil}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import scala.concurrent.Future

/**
 * One (bank, user, role) grant.
 *
 * The unique index on that triple is load-bearing for authorisation: addEntitlement depends on the
 * database rejecting a concurrent duplicate grant, and on being able to re-read the committed row
 * afterwards. Without it a role could be held twice and one revoke would leave the other behind.
 *
 * Four columns carry explicit names that break the m-prefix convention — group_id, process,
 * granted_by_user_id and entitlement_request_id — because the entity overrode dbColumnName.
 *
 * The first three default to "" and are read through an empty check. entitlement_request_id is the
 * exception: it defaults to NULL and its reader also rejects the all-zero UUID, since only
 * request-born grants set it.
 */
case class MappedEntitlement(
  entitlementId: String,
  bankId: String,
  userId: String,
  roleName: String,
  private val createdByProcessRaw: String,
  private val groupIdRaw: String,
  private val processRaw: String,
  private val grantedByUserIdRaw: String,
  private val entitlementRequestIdRaw: Option[String]
) extends Entitlement {

  override def createdByProcess: String =
    if (createdByProcessRaw == null || createdByProcessRaw.isEmpty) "manual" else createdByProcessRaw

  override def groupId: Option[String] =
    if (groupIdRaw == null || groupIdRaw.isEmpty) None else Some(groupIdRaw)

  override def process: Option[String] =
    if (processRaw == null || processRaw.isEmpty) None else Some(processRaw)

  override def grantedByUserId: Option[String] =
    if (grantedByUserIdRaw == null || grantedByUserIdRaw.isEmpty) None else Some(grantedByUserIdRaw)

  override def entitlementRequestId: Option[String] =
    // The column defaults to null (only request-born grants set it).
    entitlementRequestIdRaw
      .filter(uuid => uuid.nonEmpty && uuid != "00000000-0000-0000-0000-000000000000")
}

object MappedEntitlement {

  private val selectColumns =
    fr"""SELECT mentitlementid, mbankid, muserid, mrolename, mcreatedbyprocess, group_id, process,
                granted_by_user_id, entitlement_request_id
         FROM mappedentitlement"""

  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[String], Option[String])

  private def fromRow(row: Row): MappedEntitlement = row match {
    case (entitlementId, bankId, userId, roleName, createdByProcess, groupId, process,
          grantedByUserId, entitlementRequestId) =>
      MappedEntitlement(entitlementId.orNull, bankId.orNull, userId.orNull, roleName.orNull,
        createdByProcess.orNull, groupId.orNull, process.orNull, grantedByUserId.orNull,
        entitlementRequestId)
  }

  private def query(condition: Fragment): List[MappedEntitlement] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def one(condition: Fragment): Box[MappedEntitlement] =
    query(condition ++ fr"ORDER BY id ASC LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  def find(bankId: String, userId: String, roleName: String): Box[MappedEntitlement] =
    one(fr"WHERE mbankid = $bankId AND muserid = $userId AND mrolename = $roleName")

  def findByEntitlementId(entitlementId: String): Box[MappedEntitlement] =
    one(fr"WHERE mentitlementid = $entitlementId")

  def findAllByUserId(userId: String): List[MappedEntitlement] =
    query(fr"WHERE muserid = $userId ORDER BY updatedat DESC, id DESC")

  def findAllByBankId(bankId: String): List[MappedEntitlement] =
    query(fr"WHERE mbankid = $bankId ORDER BY muserid DESC, id DESC")

  def findAll(): List[MappedEntitlement] = query(fr"ORDER BY updatedat DESC, id DESC")

  def findAllByRoleName(roleName: String): List[MappedEntitlement] =
    query(fr"WHERE mrolename = $roleName ORDER BY updatedat DESC, id DESC")

  def findAllByGroupId(groupId: String): List[MappedEntitlement] =
    query(fr"WHERE group_id = $groupId ORDER BY updatedat DESC, id DESC")

  def findAllByUserIds(userIds: List[String]): List[MappedEntitlement] =
    // Mapper's ByList with an empty list rendered "0 = 1", i.e. no rows — not "no filter".
    if (userIds.isEmpty) Nil
    else {
      val in = Fragments.in(fr"muserid", cats.data.NonEmptyList.fromListUnsafe(userIds.distinct))
      query(fr"WHERE " ++ in ++ fr"ORDER BY id ASC")
    }

  def insert(bankId: String, userId: String, roleName: String, createdByProcess: String,
             grantedByUserId: Option[String], groupId: Option[String],
             process: Option[String]): MappedEntitlement = {
    val entitlementId = APIUtil.generateUUID()
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    // The three optional columns default to "" rather than NULL when the caller omits them, which
    // is what Mapper's untouched MappedString defaults wrote.
    DoobieUtil.runUpdate(
      sql"""INSERT INTO mappedentitlement
            (mentitlementid, mbankid, muserid, mrolename, mcreatedbyprocess, group_id, process,
             granted_by_user_id, createdat, updatedat)
            VALUES ($entitlementId, $bankId, $userId, $roleName, $createdByProcess,
             ${groupId.getOrElse("")}, ${process.getOrElse("")},
             ${grantedByUserId.getOrElse("")}, $now, $now)"""
        .update.run)
    findByEntitlementId(entitlementId)
      .openOrThrowException("the entitlement just inserted must be readable")
  }

  def updateRoleName(entitlementId: String, roleName: String): Unit = {
    DoobieUtil.runUpdate(
      sql"""UPDATE mappedentitlement SET mrolename = $roleName,
              updatedat = ${new java.sql.Timestamp(System.currentTimeMillis())}
            WHERE mentitlementid = $entitlementId""".update.run)
    ()
  }

  def deleteByEntitlementId(entitlementId: String): Boolean =
    DoobieUtil.runUpdate(
      sql"DELETE FROM mappedentitlement WHERE mentitlementid = $entitlementId".update.run) > 0

  def deleteByRoleNames(roleNames: List[String]): Boolean =
    // Matching Mapper's ByList: an empty list deletes nothing rather than everything.
    if (roleNames.isEmpty) true
    else {
      val in = Fragments.in(fr"mrolename", cats.data.NonEmptyList.fromListUnsafe(roleNames.distinct))
      DoobieUtil.runUpdate((fr"DELETE FROM mappedentitlement WHERE " ++ in).update.run)
      true
    }

  def deleteByBankIdAndUserIds(bankId: String, userIds: List[String]): Boolean =
    if (userIds.isEmpty) true
    else {
      val in = Fragments.in(fr"muserid", cats.data.NonEmptyList.fromListUnsafe(userIds.distinct))
      DoobieUtil.runUpdate(
        (fr"DELETE FROM mappedentitlement WHERE mbankid = $bankId AND " ++ in).update.run)
      true
    }

  def count(bankId: String, userId: String, roleName: String): Long =
    DoobieUtil.runQuery(
      sql"""SELECT COUNT(*) FROM mappedentitlement
            WHERE mbankid = $bankId AND muserid = $userId AND mrolename = $roleName"""
        .query[Long].unique)

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappedentitlement".update.run)
    ()
  }
}

object MappedEntitlementsProvider extends EntitlementProvider {

  override def getEntitlement(bankId: String, userId: String, roleName: String): Box[MappedEntitlement] =
    MappedEntitlement.find(bankId, userId, roleName)

  override def getEntitlementById(entitlementId: String): Box[Entitlement] =
    MappedEntitlement.findByEntitlementId(entitlementId)

  override def getEntitlementsByUserId(userId: String): Box[List[Entitlement]] =
    Some(MappedEntitlement.findAllByUserId(userId))

  override def getEntitlementsByUserIdFuture(userId: String): Future[Box[List[Entitlement]]] =
    Future(getEntitlementsByUserId(userId))

  override def getEntitlementsByBankId(bankId: String): Future[Box[List[Entitlement]]] =
    Future(Some(MappedEntitlement.findAllByBankId(bankId)))

  override def getEntitlements(): Box[List[MappedEntitlement]] =
    Some(MappedEntitlement.findAll())

  override def getEntitlementsByRole(roleName: String): Box[List[MappedEntitlement]] =
    Some(MappedEntitlement.findAllByRoleName(roleName))

  override def getEntitlementsFuture(): Future[Box[List[Entitlement]]] =
    Future(getEntitlements())

  override def getEntitlementsByRoleFuture(roleName: String): Future[Box[List[Entitlement]]] =
    Future {
      if (roleName == null || roleName.isEmpty) getEntitlements()
      else getEntitlementsByRole(roleName)
    }

  override def getEntitlementsByGroupId(groupId: String): Future[Box[List[Entitlement]]] =
    Future(Some(MappedEntitlement.findAllByGroupId(groupId)))

  override def deleteEntitlement(entitlement: Box[Entitlement]): Box[Boolean] =
    for {
      findEntitlement <- entitlement
      foundEntitlement <- MappedEntitlement.find(findEntitlement.bankId, findEntitlement.userId,
        findEntitlement.roleName)
    } yield MappedEntitlement.deleteByEntitlementId(foundEntitlement.entitlementId)

  override def deleteDynamicEntityEntitlement(entityName: String, bankId: Option[String]): Box[Boolean] =
    deleteEntitlements(DynamicEntityInfo.roleNames(entityName, bankId))

  override def deleteEntitlements(entityNames: List[String]): Box[Boolean] =
    Box.tryo(MappedEntitlement.deleteByRoleNames(entityNames))

  override def addEntitlement(
      bankId: String,
      userId: String,
      roleName: String,
      createdByProcess: String = "manual",
      grantedByUserId: Option[String] = None,
      groupId: Option[String] = None,
      process: Option[String] = None
  ): Box[Entitlement] = {
    // grantedByUserId is audit metadata, stored as-is: authorization is the
    // calling endpoint's responsibility. (Until 2026-08-09 an unused
    // grantorUserId parameter gated on the grantor's granting roles here —
    // no caller ever passed it, and the check ignored super admins, whose
    // granting rights are virtual and have no rows to find.)
    tryo(MappedEntitlement.insert(bankId, userId, roleName, createdByProcess, grantedByUserId,
      groupId, process)) match {
      case Full(saved) =>
        NotificationUtil.sendEmailRegardingAssignedRole(userId, saved)
        Full(saved)
      case _: net.liftweb.common.Failure =>
        // UniqueIndex(mBankId, mUserId, mRoleName) violated by concurrent grant — return the committed row
        MappedEntitlement.find(bankId, userId, roleName)
      case other => other
    }
  }
}
