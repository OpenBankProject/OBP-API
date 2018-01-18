package code.entitlementrequest

import code.util.{MappedUUID, UUIDString}
import net.liftweb.common.{Box, Full}
import net.liftweb.mapper._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object MappedEntitlementRequestsProvider extends EntitlementRequestProvider {

  override def addEntitlementRequest(bankId: String, userId: String, roleName: String): Box[EntitlementRequest] = {
    val addEntitlementRequet =
      MappedEntitlementRequest.create
        .mBankId(bankId)
        .mUserId(userId)
        .mRoleName(roleName)
        .saveMe()
    Some(addEntitlementRequet)
  }
  override def addEntitlementRequestFuture(bankId: String, userId: String, roleName: String): Future[Box[EntitlementRequest]] = {
    Future {
      addEntitlementRequest(bankId, userId, roleName)
    }
  }


  override def getEntitlementRequest(bankId: String, userId: String, roleName: String): Box[MappedEntitlementRequest] = {
    MappedEntitlementRequest.find(
      By(MappedEntitlementRequest.mBankId, bankId),
      By(MappedEntitlementRequest.mUserId, userId),
      By(MappedEntitlementRequest.mRoleName, roleName)
    )
  }
  override def getEntitlementRequestFuture(bankId: String, userId: String, roleName: String): Future[Box[EntitlementRequest]] = {
    Future {
      getEntitlementRequest(bankId, userId, roleName)
    }
  }

  override def getEntitlementRequestsFuture(): Future[Box[List[EntitlementRequest]]] = {
    Future {
      Full(MappedEntitlementRequest.findAll())
    }
  }

}

class MappedEntitlementRequest extends EntitlementRequest
  with LongKeyedMapper[MappedEntitlementRequest] with IdPK with CreatedUpdated {

  def getSingleton = MappedEntitlementRequest

  object mEntitlementId extends MappedUUID(this)

  object mBankId extends UUIDString(this)

  object mUserId extends UUIDString(this)

  object mRoleName extends MappedString(this, 64)

  override def entitlementId: String = mEntitlementId.get.toString

  override def bankId: String = mBankId.get

  override def userId: String = mUserId.get

  override def roleName: String = mRoleName.get
}


object MappedEntitlementRequest extends MappedEntitlementRequest with LongKeyedMetaMapper[MappedEntitlementRequest] {
  override def dbIndexes = UniqueIndex(mEntitlementId) :: super.dbIndexes
}