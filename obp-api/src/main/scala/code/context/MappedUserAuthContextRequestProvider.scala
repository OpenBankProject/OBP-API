package code.context

import code.api.util.ErrorMessages
import code.util.Helper.MdcLoggable
import net.liftweb.common.{Box, Empty, Failure, Full}
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
          .mStatus(UserAuthContextUpdateRequestStatus.INITIATED.toString)
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

  override def checkAnswer(consentId: String, challenge: String): Future[Box[MappedUserAuthContextRequest]] = Future {
    MappedUserAuthContextRequest.find(By(MappedUserAuthContextRequest.mUserAuthContextRequestId, consentId)) match {
      case Full(consent) =>
        consent.status match {
          case value if value == UserAuthContextUpdateRequestStatus.INITIATED.toString =>
            val status = if (consent.challenge == challenge) UserAuthContextUpdateRequestStatus.ACCEPTED.toString else UserAuthContextUpdateRequestStatus.REJECTED.toString
            tryo(consent.mStatus(status).saveMe())
          case _ =>
            Full(consent)
        }
      case Empty =>
        Empty ?~! ErrorMessages.UserAuthContextUpdateRequestNotFound
      case Failure(msg, _, _) =>
        Failure(msg)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }

  }
}

