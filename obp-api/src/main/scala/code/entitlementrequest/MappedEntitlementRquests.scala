package code.entitlementrequest

import java.util.Date

import code.api.util.{ErrorMessages, OBPAscending, OBPDescending, OBPFromDate, OBPLimit, OBPOffset, OBPOrdering, OBPQueryParam, OBPToDate}
import code.users.Users
import code.util.{MappedUUID, UUIDString}
import com.openbankproject.commons.model.User
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper._

import scala.concurrent.Future
import com.openbankproject.commons.ExecutionContext.Implicits.global

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

  override def getEntitlementRequestFuture(entitlementRequestId: String): Future[Box[EntitlementRequest]] = {
    Future {
      MappedEntitlementRequest.find(
        By(MappedEntitlementRequest.mEntitlementRequestId, entitlementRequestId)
      )
    }
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

  override def getEntitlementRequestsFuture(userId: String): Future[Box[List[EntitlementRequest]]] = {
    Future {
      Full(MappedEntitlementRequest.findAll(By(MappedEntitlementRequest.mUserId, userId)))
    }
  }

  private def getOptionalParams(queryParams: List[OBPQueryParam]): Seq[QueryParam[MappedEntitlementRequest]] = {
    val limit = queryParams.collect { case OBPLimit(value) => MaxRows[MappedEntitlementRequest](value) }.headOption
    val offset = queryParams.collect { case OBPOffset(value) => StartAt[MappedEntitlementRequest](value) }.headOption
    val fromDate = queryParams.collect { case OBPFromDate(date) => By_>=(MappedEntitlementRequest.createdAt, date) }.headOption
    val toDate = queryParams.collect { case OBPToDate(date) => By_<=(MappedEntitlementRequest.createdAt, date) }.headOption
    val ordering = queryParams.collect {
      case OBPOrdering(_, direction) =>
        direction match {
          case OBPAscending => OrderBy(MappedEntitlementRequest.createdAt, Ascending)
          case OBPDescending => OrderBy(MappedEntitlementRequest.createdAt, Descending)
        }
    }
    Seq(limit.toSeq, offset.toSeq, fromDate.toSeq, toDate.toSeq, ordering).flatten
  }

  override def getEntitlementRequestsFuture(queryParams: List[OBPQueryParam]): Future[Box[List[EntitlementRequest]]] = {
    Future {
      val optionalParams = getOptionalParams(queryParams)
      Full(MappedEntitlementRequest.findAll(optionalParams: _*))
    }
  }

  override def getEntitlementRequestsFuture(userId: String, queryParams: List[OBPQueryParam]): Future[Box[List[EntitlementRequest]]] = {
    Future {
      val optionalParams = Seq(By(MappedEntitlementRequest.mUserId, userId)) ++ getOptionalParams(queryParams)
      Full(MappedEntitlementRequest.findAll(optionalParams: _*))
    }
  }

  override def deleteEntitlementRequestFuture(entitlementRequestId: String): Future[Box[Boolean]] = {
    Future {
      MappedEntitlementRequest.find(By(MappedEntitlementRequest.mEntitlementRequestId, entitlementRequestId)) match {
        case Full(t) => Full(t.delete_!)
        case Empty   => Empty ?~! ErrorMessages.EntitlementRequestNotFound
        case _       => Full(false)
      }
    }
  }

}

class MappedEntitlementRequest extends EntitlementRequest
  with LongKeyedMapper[MappedEntitlementRequest] with IdPK with CreatedUpdated {

  def getSingleton = MappedEntitlementRequest

  object mEntitlementRequestId extends MappedUUID(this)

  object mBankId extends UUIDString(this)

  object mUserId extends UUIDString(this)

  object mRoleName extends MappedString(this, 255)

  override def entitlementRequestId: String = mEntitlementRequestId.get.toString

  override def bankId: String = mBankId.get

  override def user: Box[User] = Users.users.vend.getUserByUserId(mUserId.get)

  override def roleName: String = mRoleName.get

  override def created: Date = createdAt.get
}


object MappedEntitlementRequest extends MappedEntitlementRequest with LongKeyedMetaMapper[MappedEntitlementRequest] {
  override def dbIndexes = UniqueIndex(mEntitlementRequestId) :: super.dbIndexes
}