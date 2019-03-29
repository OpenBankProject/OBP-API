package code.context

import code.api.util.ErrorMessages
import code.util.Helper.MdcLoggable
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.tryo

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object MappedUserAuthContextRequestProvider extends UserAuthContextRequestProvider with MdcLoggable {
  
  override def createUserAuthContextRequest(userId: String, key: String, value: String): Future[Box[MappedUserAuthContextRequest]] =
    Future {
      tryo {
        MappedUserAuthContextRequest
          .create
          .mUserId(userId)
          .mKey(key)
          .mValue(value)
          .mStatus(ConsentRequestStatus.INITIATED.toString)
          .saveMe()
      }
    }

  override def getUserAuthContextRequests(userId: String): Future[Box[List[MappedUserAuthContextRequest]]] = Future {
    getUserAuthContextRequestsBox(userId)
  }
  override def getUserAuthContextRequestsBox(userId: String): Box[List[MappedUserAuthContextRequest]] = {
    tryo {
      MappedUserAuthContextRequest.findAll(By(MappedUserAuthContextRequest.mUserId, userId))
    }
  }
 override def deleteUserAuthContextRequests(userId: String): Future[Box[Boolean]] =
    Future(tryo{MappedUserAuthContextRequest.bulkDelete_!!(By(MappedUserAuthContextRequest.mUserId, userId))})

  override def deleteUserAuthContextRequestById(userAuthContextId: String): Future[Box[Boolean]] =
    Future{
      MappedUserAuthContextRequest.find(By(MappedUserAuthContextRequest.mUserAuthContextRequestId, userAuthContextId)) match {
        case Full(userAuthContext) => Full(userAuthContext.delete_!)
        case Empty => Empty ?~! ErrorMessages.DeleteUserAuthContextNotFound
        case _ => Full(false)
      }
    }
}

