package code.entitlement

import code.api.dynamic.entity.helper.DynamicEntityInfo
import code.api.Constant
import code.api.util.{APIUtil, DoobieUtil, NotificationUtil}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
import code.api.util.ApiRole.{
  CanCreateEntitlementAtAnyBank,
  CanCreateEntitlementAtOneBank
}
import code.api.util.{ErrorMessages, NotificationUtil}
import code.util.Helper.MdcLoggable
import net.liftweb.common.{Box, Failure, Full}
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
  private val grantedByUserIdRaw: String,
  private val entitlementRequestIdRaw: Option[String]
) extends Entitlement {

  override def createdByProcess: String =
    if (createdByProcessRaw == null || createdByProcessRaw.isEmpty) "manual" else createdByProcessRaw

  override def groupId: Option[String] =
    if (groupIdRaw == null || groupIdRaw.isEmpty) None else Some(groupIdRaw)

  override def grantedByUserId: Option[String] =
    if (grantedByUserIdRaw == null || grantedByUserIdRaw.isEmpty) None else Some(grantedByUserIdRaw)

  override def entitlementRequestId: Option[String] =
    // The column defaults to null (only request-born grants set it).
    entitlementRequestIdRaw
      .filter(uuid => uuid.nonEmpty && uuid != "00000000-0000-0000-0000-000000000000")
}

object MappedEntitlement {

  private val selectColumns =
    // `process` is not selected: develop retired it from the Entitlement trait, so there is no
    // member to carry it. The column remains in the table until it is dropped.
    fr"""SELECT mentitlementid, mbankid, muserid, mrolename, mcreatedbyprocess, group_id,
                granted_by_user_id, entitlement_request_id
         FROM mappedentitlement"""

  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[String])

  private def fromRow(row: Row): MappedEntitlement = row match {
    case (entitlementId, bankId, userId, roleName, createdByProcess, groupId,
          grantedByUserId, entitlementRequestId) =>
      MappedEntitlement(entitlementId.orNull, bankId.orNull, userId.orNull, roleName.orNull,
        createdByProcess.orNull, groupId.orNull, grantedByUserId.orNull,
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

  // No `process` parameter: develop retired that column (a duplicate of createdByProcess written
  // only by the Groups feature). Group rows are identified by group_id and provenance lives in
  // createdByProcess. The column stays in the table, written empty, until it is dropped.
  def insert(bankId: String, userId: String, roleName: String, createdByProcess: String,
             grantedByUserId: Option[String], groupId: Option[String]): MappedEntitlement = {
    val entitlementId = APIUtil.generateUUID()
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    // The three optional columns default to "" rather than NULL when the caller omits them, which
    // is what Mapper's untouched MappedString defaults wrote.
    DoobieUtil.runUpdate(
      // `process` is deliberately absent: develop retired the field, and the column is nullable,
      // so leaving it out of the column list stores NULL. It must not be listed with a `""`
      // value - in SQL that is a quoted IDENTIFIER, not an empty string, so the statement fails
      // to parse. The failure is invisible here because the caller wraps this in `tryo`: the
      // grant silently does not happen and every role-gated endpoint answers 403 instead.
      sql"""INSERT INTO mappedentitlement
            (mentitlementid, mbankid, muserid, mrolename, mcreatedbyprocess, group_id,
             granted_by_user_id, createdat, updatedat)
            VALUES ($entitlementId, $bankId, $userId, $roleName, $createdByProcess,
             ${groupId.getOrElse("")},
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

object MappedEntitlementsProvider extends EntitlementProvider with MdcLoggable {

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
      groupId: Option[String] = None
  ): Box[Entitlement] = {
    // grantedByUserId is audit metadata, stored as-is: authorization is the
    // calling endpoint's responsibility. (Until 2026-08-09 an unused
    // grantorUserId parameter gated on the grantor's granting roles here —
    // no caller ever passed it, and the check ignored super admins, whose
    // granting rights are virtual and have no rows to find.)
    // On-behalf-of guard, ported from the Mapper implementation this branch replaced.
    // A consent user (the per-consent principal a Consent-JWT authenticates as; its
    // resourceuser row carries createdByConsentId) must not accumulate durable roles - they
    // strand when the consent dies, invisible to the human's next consent (see the simon.bank
    // creator-grant incident, 2026-08-31). Any grant targeting one is redirected to the
    // consent's granting human. The one legitimate writer of consent-user rows is the consent
    // engine copying the consent's own scope, which tags itself Constant.consent_user.
    //
    // createdByConsentId is an Option on the Doobie row, so the null/empty dance the Mapper
    // version needed is gone; the shape of the decision is otherwise unchanged.
    val targetUserId =
      if (createdByProcess == Constant.consent_user) userId
      else {
        val grantingHumanUserId = for {
          resourceUser <- code.model.dataAccess.ResourceUser.findByUserId(userId)
          consentId <- Box(resourceUser.createdByConsentId.filter(_.nonEmpty))
          consent <- code.consent.Consents.consentProvider.vend.getConsentByConsentId(consentId)
          humanUserId <- Full(consent.userId).filter(id => id != null && id.nonEmpty)
        } yield humanUserId
        grantingHumanUserId match {
          case Full(humanUserId) =>
            logger.warn(s"addEntitlement: target user $userId is a consent user; granting role " +
              s"'$roleName' (bankId '$bankId', createdByProcess '$createdByProcess') to its " +
              s"granting human $humanUserId instead")
            humanUserId
          case _ => userId
        }
      }

    tryo(MappedEntitlement.insert(bankId, targetUserId, roleName, createdByProcess,
      grantedByUserId, groupId)) match {
      case Full(saved) =>
        NotificationUtil.sendEmailRegardingAssignedRole(targetUserId, saved)
        Full(saved)
      case _: net.liftweb.common.Failure =>
        // UniqueIndex(mBankId, mUserId, mRoleName) violated by concurrent grant - return the committed row
        MappedEntitlement.find(bankId, targetUserId, roleName)
      case other => other
    }
  }
}
