package code.accountaccessrequest

import java.util.Date
import code.api.util.ErrorMessages
import code.util.{MappedUUID, UUIDString}
import com.openbankproject.commons.model.enums.AccountAccessRequestStatus
import net.liftweb.common.{Box, Failure, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

object MappedAccountAccessRequestProvider extends AccountAccessRequestProvider {

  override def createAccountAccessRequest(
    bankId: String,
    accountId: String,
    viewId: String,
    isSystemView: Boolean,
    requestorUserId: String,
    targetUserId: String,
    businessJustification: String
  ): Box[AccountAccessRequestTrait] = {
    tryo {
      AccountAccessRequest.create
        .BankId(bankId)
        .AccountId(accountId)
        .ViewId(viewId)
        .IsSystemView(isSystemView)
        .RequestorUserId(requestorUserId)
        .TargetUserId(targetUserId)
        .BusinessJustification(businessJustification)
        .Status(AccountAccessRequestStatus.INITIATED.toString)
        .CheckerUserId("")
        .CheckerComment("")
        .saveMe()
    }
  }

  override def getById(accountAccessRequestId: String): Box[AccountAccessRequestTrait] = {
    AccountAccessRequest.find(By(AccountAccessRequest.AccountAccessRequestId, accountAccessRequestId))
  }

  override def getByAccount(bankId: String, accountId: String): Box[List[AccountAccessRequestTrait]] = {
    tryo {
      AccountAccessRequest.findAll(
        By(AccountAccessRequest.BankId, bankId),
        By(AccountAccessRequest.AccountId, accountId),
        OrderBy(AccountAccessRequest.id, Descending)
      )
    }
  }

  override def getByAccountAndStatus(bankId: String, accountId: String, status: String): Box[List[AccountAccessRequestTrait]] = {
    tryo {
      AccountAccessRequest.findAll(
        By(AccountAccessRequest.BankId, bankId),
        By(AccountAccessRequest.AccountId, accountId),
        By(AccountAccessRequest.Status, status),
        OrderBy(AccountAccessRequest.id, Descending)
      )
    }
  }

  override def getByRequestorUserId(requestorUserId: String): Box[List[AccountAccessRequestTrait]] = {
    tryo {
      AccountAccessRequest.findAll(
        By(AccountAccessRequest.RequestorUserId, requestorUserId),
        OrderBy(AccountAccessRequest.id, Descending)
      )
    }
  }

  override def getByUserAccountView(
    targetUserId: String,
    bankId: String,
    accountId: String,
    viewId: String
  ): Box[AccountAccessRequestTrait] = {
    AccountAccessRequest.find(
      By(AccountAccessRequest.TargetUserId, targetUserId),
      By(AccountAccessRequest.BankId, bankId),
      By(AccountAccessRequest.AccountId, accountId),
      By(AccountAccessRequest.ViewId, viewId),
      By(AccountAccessRequest.Status, AccountAccessRequestStatus.INITIATED.toString)
    )
  }

  override def updateStatus(
    accountAccessRequestId: String,
    status: String,
    checkerUserId: String,
    checkerComment: String
  ): Box[AccountAccessRequestTrait] = {
    AccountAccessRequest.find(By(AccountAccessRequest.AccountAccessRequestId, accountAccessRequestId)).flatMap { request =>
      // Atomic guarded transition: an access request is actioned once, from INITIATED. The loser of a
      // concurrent approve/decline gets 0 rows -> Failure, instead of silently overwriting the decision.
      val rows = code.bankconnectors.DoobieBusinessStatusQueries.conditionalAccountAccessRequestStatus(
        request.id.get, AccountAccessRequestStatus.INITIATED.toString, status, checkerUserId, checkerComment)
      if (rows == 1) AccountAccessRequest.find(By(AccountAccessRequest.AccountAccessRequestId, accountAccessRequestId))
      else Failure(ErrorMessages.AccountAccessRequestStatusNotInitiated)
    }
  }
}

class AccountAccessRequest extends AccountAccessRequestTrait with LongKeyedMapper[AccountAccessRequest] with IdPK with CreatedUpdated {

  def getSingleton = AccountAccessRequest

  object AccountAccessRequestId extends MappedUUID(this)
  object BankId extends UUIDString(this)
  object AccountId extends UUIDString(this)
  object ViewId extends MappedString(this, 255)
  object IsSystemView extends MappedBoolean(this)
  object RequestorUserId extends UUIDString(this)
  object TargetUserId extends UUIDString(this)
  object BusinessJustification extends MappedText(this)
  object Status extends MappedString(this, 64)
  object CheckerUserId extends MappedString(this, 255)
  object CheckerComment extends MappedText(this)

  override def accountAccessRequestId: String = AccountAccessRequestId.get.toString
  override def bankId: String = BankId.get
  override def accountId: String = AccountId.get
  override def viewId: String = ViewId.get
  override def isSystemView: Boolean = IsSystemView.get
  override def requestorUserId: String = RequestorUserId.get
  override def targetUserId: String = TargetUserId.get
  override def businessJustification: String = BusinessJustification.get
  override def status: String = Status.get
  override def checkerUserId: String = CheckerUserId.get
  override def checkerComment: String = CheckerComment.get
  override def created: Date = createdAt.get
  override def updated: Date = updatedAt.get
}

object AccountAccessRequest extends AccountAccessRequest with LongKeyedMetaMapper[AccountAccessRequest] {
  override def dbTableName = "AccountAccessRequest"
  override def dbIndexes = Index(AccountAccessRequestId) :: Index(BankId, AccountId) :: Index(RequestorUserId) :: Index(Status) :: super.dbIndexes
}
