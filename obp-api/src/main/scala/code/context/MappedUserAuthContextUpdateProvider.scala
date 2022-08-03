package code.context

import code.api.util.APIUtil.transactionRequestChallengeTtl
import code.api.util.{APIUtil, ErrorMessages}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.UserAuthContextUpdateStatus
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.tryo
import com.openbankproject.commons.ExecutionContext.Implicits.global
import net.liftweb.util.Helpers

import scala.compat.Platform
import scala.concurrent.Future

object MappedUserAuthContextUpdateProvider extends UserAuthContextUpdateProvider with MdcLoggable {
  
  override def createUserAuthContextUpdates(userId: String, consumerId:String, key: String, value: String): Future[Box[MappedUserAuthContextUpdate]] =
    Future {
      tryo {
        MappedUserAuthContextUpdate
          .create
          .mUserId(userId)
          .mConsumerId(consumerId)
          .mKey(key)
          .mValue(value)
          .mStatus(UserAuthContextUpdateStatus.INITIATED.toString)
          .saveMe()
      }
    }

  override def getUserAuthContextUpdates(userId: String): Future[Box[List[MappedUserAuthContextUpdate]]] = Future {
    getUserAuthContextUpdatesBox(userId)
  }
  override def getUserAuthContextUpdatesBox(userId: String): Box[List[MappedUserAuthContextUpdate]] = {
    tryo {
      MappedUserAuthContextUpdate.findAll(By(MappedUserAuthContextUpdate.mUserId, userId))
    }
  }
 override def deleteUserAuthContextUpdates(userId: String): Future[Box[Boolean]] =
    Future(tryo{MappedUserAuthContextUpdate.bulkDelete_!!(By(MappedUserAuthContextUpdate.mUserId, userId))})

  override def deleteUserAuthContextUpdateById(userAuthContextId: String): Future[Box[Boolean]] =
    Future{
      MappedUserAuthContextUpdate.find(By(MappedUserAuthContextUpdate.mUserAuthContextUpdateId, userAuthContextId)) match {
        case Full(userAuthContext) => Full(userAuthContext.delete_!)
        case Empty => Empty ?~! ErrorMessages.DeleteUserAuthContextNotFound
        case _ => Full(false)
      }
    }

  override def checkAnswer(consentId: String, challenge: String): Future[Box[MappedUserAuthContextUpdate]] = Future {
    MappedUserAuthContextUpdate.find(By(MappedUserAuthContextUpdate.mUserAuthContextUpdateId, consentId)) match {
      case Full(consent) =>
        val createDateTime = consent.createdAt.get
        val challengeTTL : Long = Helpers.seconds(APIUtil.userAuthContextUpdateRequestChallengeTtl)
        val expiredDateTime: Long = createDateTime.getTime+challengeTTL
        val currentTime: Long = Platform.currentTime
        
        if(expiredDateTime > currentTime)
          consent.status match {
            case value if value == UserAuthContextUpdateStatus.INITIATED.toString =>
              val status = if (consent.challenge == challenge) UserAuthContextUpdateStatus.ACCEPTED.toString else UserAuthContextUpdateStatus.REJECTED.toString
              tryo(consent.mStatus(status).saveMe())
            case _ =>
              Full(consent)
          } 
        else{
          Failure(s"${ErrorMessages.OneTimePasswordExpired} Current expiration time is ${APIUtil.userAuthContextUpdateRequestChallengeTtl} seconds")
        }
      case Empty =>
        Empty ?~! ErrorMessages.UserAuthContextUpdateNotFound
      case Failure(msg, _, _) =>
        Failure(msg)
      case _ =>
        Failure(ErrorMessages.UnknownError)
    }

  }
}

