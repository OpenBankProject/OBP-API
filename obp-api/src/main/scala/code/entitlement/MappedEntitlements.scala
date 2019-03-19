package code.entitlement


import code.util.{MappedUUID, UUIDString}
import net.liftweb.common.Box
import net.liftweb.mapper._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.tools.scalap.scalax.util.StringUtil

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

  override def addEntitlement(bankId: String, userId: String, roleName: String): Box[Entitlement] = {
    // Return a Box so we can handle errors later.
    val addEntitlement = MappedEntitlement.create
      .mBankId(bankId)
      .mUserId(userId)
      .mRoleName(roleName)
      .saveMe()
    Some(addEntitlement)
  }
}

class MappedEntitlement extends Entitlement
  with LongKeyedMapper[MappedEntitlement] with IdPK with CreatedUpdated {

  def getSingleton = MappedEntitlement

  object mEntitlementId extends MappedUUID(this)
  object mBankId extends UUIDString(this)
  object mUserId extends UUIDString(this)
  object mRoleName extends MappedString(this, 64)

  override def entitlementId: String = mEntitlementId.get.toString
  override def bankId: String = mBankId.get
  override def userId: String = mUserId.get
  override def roleName: String = mRoleName.get
}


object MappedEntitlement extends MappedEntitlement with LongKeyedMetaMapper[MappedEntitlement] {
  override def dbIndexes = UniqueIndex(mEntitlementId) :: super.dbIndexes
}