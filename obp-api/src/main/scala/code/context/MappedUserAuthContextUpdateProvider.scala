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
      case Full(consent)      => processUacAnswer(consent, challenge, consentId)
      case Empty              => Empty ?~! ErrorMessages.UserAuthContextUpdateNotFound
      case Failure(msg, _, _) => Failure(msg)
      case _                  => Failure(ErrorMessages.UnknownError)
    }
  }

  private def processUacAnswer(consent: MappedUserAuthContextUpdate, challenge: String, consentId: String): Box[MappedUserAuthContextUpdate] = {
    val expiredDateTime: Long = consent.createdAt.get.getTime + Helpers.seconds(APIUtil.userAuthContextUpdateRequestChallengeTtl)
    if (expiredDateTime <= Platform.currentTime) {
      Failure(s"${ErrorMessages.OneTimePasswordExpired} Current expiration time is ${APIUtil.userAuthContextUpdateRequestChallengeTtl} seconds")
    } else {
      consent.status match {
        case value if value == UserAuthContextUpdateStatus.INITIATED.toString =>
          val status = if (consent.challenge == challenge) UserAuthContextUpdateStatus.ACCEPTED.toString else UserAuthContextUpdateStatus.REJECTED.toString
          // Atomic guarded transition: only one concurrent answer may move INITIATED -> status,
          // so two correct answers cannot both be accepted (MFA double-authorisation).
          val rows = code.bankconnectors.DoobieUserAuthContextUpdateQueries
            .conditionalStatusTransition(consent.id.get, UserAuthContextUpdateStatus.INITIATED.toString, status)
          if (rows == 1) MappedUserAuthContextUpdate.find(By(MappedUserAuthContextUpdate.mUserAuthContextUpdateId, consentId))
          else Failure(ErrorMessages.UserAuthContextUpdateStatusError)
        case _ =>
          Full(consent)
      }
    }
  }
}

