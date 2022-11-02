package code.entitlement

import code.api.dynamic.entity.helper.DynamicEntityInfo
import code.api.util.ApiRole.{CanCreateEntitlementAtAnyBank, CanCreateEntitlementAtOneBank}
import code.api.util.{ErrorMessages, NotificationUtil}
import code.util.{MappedUUID, UUIDString}
import net.liftweb.common.{Box, Failure, Full}
import net.liftweb.mapper._

import scala.concurrent.Future
import com.openbankproject.commons.ExecutionContext.Implicits.global
import net.liftweb.common

object MappedEntitlementsProvider extends EntitlementProvider {
  override def getEntitlement(bankId: String, userId: String, roleName: String): Box[MappedEntitlement] = {
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

  override def getEntitlementsByUserId(userId: String): Box[List[Entitlement]] = {
    // Return a Box so we can handle errors later.
    Some(MappedEntitlement.findAll(
      By(MappedEntitlement.mUserId, userId),
      OrderBy(MappedEntitlement.updatedAt, Descending)))
  }
  override def getEntitlementsByUserIdFuture(userId: String): Future[Box[List[Entitlement]]] = {
    // Return a Box so we can handle errors later.
    Future {
      getEntitlementsByUserId(userId)
    }
  }

  override def getEntitlementsByBankId(bankId: String): Future[Box[List[Entitlement]]] = {
    // Return a Box so we can handle errors later.
    Future {
      Some(MappedEntitlement.findAll(
        By(MappedEntitlement.mBankId, bankId),
        OrderBy(MappedEntitlement.mUserId, Descending)))
    }
  }

  override def getEntitlements: Box[List[MappedEntitlement]] = {
    // Return a Box so we can handle errors later.
    Some(MappedEntitlement.findAll(OrderBy(MappedEntitlement.updatedAt, Descending)))
  }

  override def getEntitlementsByRole(roleName: String): Box[List[MappedEntitlement]] = {
    // Return a Box so we can handle errors later.
    Some(MappedEntitlement.findAll(By(MappedEntitlement.mRoleName, roleName),OrderBy(MappedEntitlement.updatedAt, Descending)))
  }

  override def getEntitlementsFuture(): Future[Box[List[Entitlement]]] = {
    Future {
      getEntitlements()
    }
  }

  override def getEntitlementsByRoleFuture(roleName: String): Future[Box[List[Entitlement]]] = {
    Future {
      if(roleName == null || roleName.isEmpty){
        getEntitlements()
      } else {
        getEntitlementsByRole(roleName)
      }
    }
  }

  override def deleteEntitlement(entitlement: Box[Entitlement]): Box[Boolean] = {
    // Return a Box so we can handle errors later.
    for {
      findEntitlement <- entitlement
      bankId <- Some(findEntitlement.bankId)
      userId <- Some(findEntitlement.userId)
      roleName <- Some(findEntitlement.roleName)
      foundEntitlement <-  MappedEntitlement.find(
        By(MappedEntitlement.mBankId, bankId),
        By(MappedEntitlement.mUserId, userId),
        By(MappedEntitlement.mRoleName, roleName)
      )
    }
      yield {
        MappedEntitlement.delete_!(foundEntitlement)
      }
  }

  override def deleteDynamicEntityEntitlement(entityName: String, bankId:Option[String]): Box[Boolean] = {
    val roleNames = DynamicEntityInfo.roleNames(entityName,bankId)
    deleteEntitlements(roleNames)
  }

  override def deleteEntitlements(entityNames: List[String]) : Box[Boolean] = {
    Box.tryo{
      MappedEntitlement.bulkDelete_!!(ByList(MappedEntitlement.mRoleName, entityNames))
    }
  }

  override def addEntitlement(bankId: String, userId: String, roleName: String, createdByProcess: String ="manual", grantorUserId: Option[String]=None): Box[Entitlement] = {
    def addEntitlementToUser(): Full[MappedEntitlement] = {
      val addEntitlement: MappedEntitlement = 
        MappedEntitlement.create.mBankId(bankId).mUserId(userId).mRoleName(roleName).mCreatedByProcess(createdByProcess)
        .saveMe()
      // When a role is Granted, we should send an email to the Recipient telling them they have been granted the role.
      NotificationUtil.sendEmailRegardingAssignedRole(userId: String, addEntitlement: Entitlement)
      Full(addEntitlement)
    }
    // Return a Box so we can handle errors later.
    grantorUserId match {
      case Some(userId) =>
        val canCreateEntitlementAtAnyBank = MappedEntitlement.findAll(By(MappedEntitlement.mUserId, userId)).exists(e => e.roleName == CanCreateEntitlementAtAnyBank)
        val canCreateEntitlementAtOneBank = MappedEntitlement.findAll(By(MappedEntitlement.mUserId, userId)).exists(e => e.roleName == CanCreateEntitlementAtOneBank && e.bankId == bankId)
        if(canCreateEntitlementAtAnyBank || canCreateEntitlementAtOneBank) {
          addEntitlementToUser()
        } else {
          Failure(ErrorMessages.EntitlementCannotBeGrantedGrantorIssue)
        }
      case None =>
        addEntitlementToUser()
    }
  }
}

class MappedEntitlement extends Entitlement
  with LongKeyedMapper[MappedEntitlement] with IdPK with CreatedUpdated {

  def getSingleton = MappedEntitlement

  object mEntitlementId extends MappedUUID(this)
  object mBankId extends UUIDString(this)
  object mUserId extends UUIDString(this)
  object mRoleName extends MappedString(this, 64)
  object mCreatedByProcess extends MappedString(this, 255)

  override def entitlementId: String = mEntitlementId.get.toString
  override def bankId: String = mBankId.get
  override def userId: String = mUserId.get
  override def roleName: String = mRoleName.get
  override def createdByProcess: String = 
    if(mCreatedByProcess.get == null || mCreatedByProcess.get.isEmpty) "manual" else mCreatedByProcess.get
}


object MappedEntitlement extends MappedEntitlement with LongKeyedMetaMapper[MappedEntitlement] {
  override def dbIndexes = UniqueIndex(mEntitlementId) :: super.dbIndexes
}