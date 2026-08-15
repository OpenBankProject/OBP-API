package code.entitlement

import code.api.dynamic.entity.helper.DynamicEntityInfo
import code.api.util.ApiRole.{
  CanCreateEntitlementAtAnyBank,
  CanCreateEntitlementAtOneBank
}
import code.api.util.{ErrorMessages, NotificationUtil}
import code.util.{MappedUUID, UUIDString}
import net.liftweb.common.{Box, Failure, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

import scala.concurrent.Future
import com.openbankproject.commons.ExecutionContext.Implicits.global
import net.liftweb.common

object MappedEntitlementsProvider extends EntitlementProvider {
  override def getEntitlement(
      bankId: String,
      userId: String,
      roleName: String
  ): Box[MappedEntitlement] = {
    // Return a Box so we can handle errors later.
    MappedEntitlement.find(
      By(MappedEntitlement.mBankId, bankId),
      By(MappedEntitlement.mUserId, userId),
      By(MappedEntitlement.mRoleName, roleName)
    )
  }

  override def getEntitlementById(entitlementId: String): Box[Entitlement] = {
    // Return a Box so we can handle errors later.
    MappedEntitlement.find(
      By(MappedEntitlement.mEntitlementId, entitlementId)
    )
  }

  override def getEntitlementsByUserId(
      userId: String
  ): Box[List[Entitlement]] = {
    // Return a Box so we can handle errors later.
    Some(
      MappedEntitlement.findAll(
        By(MappedEntitlement.mUserId, userId),
        OrderBy(MappedEntitlement.updatedAt, Descending)
      )
    )
  }
  override def getEntitlementsByUserIdFuture(
      userId: String
  ): Future[Box[List[Entitlement]]] = {
    // Return a Box so we can handle errors later.
    Future {
      getEntitlementsByUserId(userId)
    }
  }

  override def getEntitlementsByBankId(
      bankId: String
  ): Future[Box[List[Entitlement]]] = {
    // Return a Box so we can handle errors later.
    Future {
      Some(
        MappedEntitlement.findAll(
          By(MappedEntitlement.mBankId, bankId),
          OrderBy(MappedEntitlement.mUserId, Descending)
        )
      )
    }
  }

  override def getEntitlements(): Box[List[MappedEntitlement]] = {
    // Return a Box so we can handle errors later.
    Some(
      MappedEntitlement.findAll(
        OrderBy(MappedEntitlement.updatedAt, Descending)
      )
    )
  }

  override def getEntitlementsByRole(
      roleName: String
  ): Box[List[MappedEntitlement]] = {
    // Return a Box so we can handle errors later.
    Some(
      MappedEntitlement.findAll(
        By(MappedEntitlement.mRoleName, roleName),
        OrderBy(MappedEntitlement.updatedAt, Descending)
      )
    )
  }

  override def getEntitlementsFuture(): Future[Box[List[Entitlement]]] = {
    Future {
      getEntitlements()
    }
  }

  override def getEntitlementsByRoleFuture(
      roleName: String
  ): Future[Box[List[Entitlement]]] = {
    Future {
      if (roleName == null || roleName.isEmpty) {
        getEntitlements()
      } else {
        getEntitlementsByRole(roleName)
      }
    }
  }

  override def getEntitlementsByGroupId(
      groupId: String
  ): Future[Box[List[Entitlement]]] = {
    Future {
      Some(
        MappedEntitlement.findAll(
          By(MappedEntitlement.mGroupId, groupId),
          OrderBy(MappedEntitlement.updatedAt, Descending)
        )
      )
    }
  }

  override def deleteEntitlement(
      entitlement: Box[Entitlement]
  ): Box[Boolean] = {
    // Return a Box so we can handle errors later.
    for {
      findEntitlement <- entitlement
      bankId <- Some(findEntitlement.bankId)
      userId <- Some(findEntitlement.userId)
      roleName <- Some(findEntitlement.roleName)
      foundEntitlement <- MappedEntitlement.find(
        By(MappedEntitlement.mBankId, bankId),
        By(MappedEntitlement.mUserId, userId),
        By(MappedEntitlement.mRoleName, roleName)
      )
    } yield {
      MappedEntitlement.delete_!(foundEntitlement)
    }
  }

  override def deleteDynamicEntityEntitlement(
      entityName: String,
      bankId: Option[String]
  ): Box[Boolean] = {
    val roleNames = DynamicEntityInfo.roleNames(entityName, bankId)
    deleteEntitlements(roleNames)
  }

  override def deleteEntitlements(entityNames: List[String]): Box[Boolean] = {
    Box.tryo {
      MappedEntitlement.bulkDelete_!!(
        ByList(MappedEntitlement.mRoleName, entityNames)
      )
    }
  }

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
    def addEntitlementToUser(): Box[MappedEntitlement] = {
      val entitlement = MappedEntitlement.create
        .mBankId(bankId)
        .mUserId(userId)
        .mRoleName(roleName)
        .mCreatedByProcess(createdByProcess)
      grantedByUserId.foreach(g => entitlement.mGrantedByUserId(g))
      groupId.foreach(gid => entitlement.mGroupId(gid))
      process.foreach(p => entitlement.mProcess(p))
      tryo(entitlement.saveMe()) match {
        case Full(saved) =>
          NotificationUtil.sendEmailRegardingAssignedRole(userId, saved)
          Full(saved)
        case Failure(_, _, _) =>
          // UniqueIndex(mBankId, mUserId, mRoleName) violated by concurrent grant — return the committed row
          MappedEntitlement.find(
            By(MappedEntitlement.mBankId, bankId),
            By(MappedEntitlement.mUserId, userId),
            By(MappedEntitlement.mRoleName, roleName)
          )
        case other => other
      }
    }
    addEntitlementToUser()
  }
}

class MappedEntitlement
    extends Entitlement
    with LongKeyedMapper[MappedEntitlement]
    with IdPK
    with CreatedUpdated {

  def getSingleton: code.entitlement.MappedEntitlement.type = MappedEntitlement

  object mEntitlementId extends MappedUUID(this)
  object mBankId extends UUIDString(this)
  object mUserId extends UUIDString(this)
  object mRoleName extends MappedString(this, 255)
  object mCreatedByProcess extends MappedString(this, 255)

  object mGroupId extends MappedString(this, 255) {
    override def dbColumnName = "group_id"
    override def defaultValue = ""
  }

  object mProcess extends MappedString(this, 255) {
    override def dbColumnName = "process"
    override def defaultValue = ""
  }

  object entitlement_request_id extends MappedUUID(this) {
    override def dbColumnName = "entitlement_request_id"
    override def defaultValue: Null = null
  }

  object mGrantedByUserId extends UUIDString(this) {
    override def dbColumnName = "granted_by_user_id"
    override def defaultValue = ""
  }

  override def entitlementId: String = mEntitlementId.get.toString
  override def bankId: String = mBankId.get
  override def userId: String = mUserId.get
  override def roleName: String = mRoleName.get
  override def createdByProcess: String =
    if (mCreatedByProcess.get == null || mCreatedByProcess.get.isEmpty) "manual"
    else mCreatedByProcess.get
  override def groupId: Option[String] = {
    val gid = mGroupId.get
    if (gid == null || gid.isEmpty) None else Some(gid)
  }
  override def process: Option[String] = {
    val p = mProcess.get
    if (p == null || p.isEmpty) None else Some(p)
  }
  override def grantedByUserId: Option[String] = {
    val g = mGrantedByUserId.get
    if (g == null || g.isEmpty) None else Some(g)
  }
  override def entitlementRequestId: Option[String] = {
    // The column defaults to null (only request-born grants set it).
    Option(entitlement_request_id.get)
      .map(_.toString)
      .filter(uuid => uuid.nonEmpty && uuid != "00000000-0000-0000-0000-000000000000")
  }
}

object MappedEntitlement
    extends MappedEntitlement
    with LongKeyedMetaMapper[MappedEntitlement] {
  override def dbIndexes = UniqueIndex(mEntitlementId) :: UniqueIndex(mBankId, mUserId, mRoleName) :: super.dbIndexes
}
