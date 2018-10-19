package code.webhook

import code.bankconnectors._
import code.util.{AccountIdString, MappedUUID, UUIDString}
import net.liftweb.common.{Box, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.List
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object MappedAccountWebhookProvider extends AccountWebhookProvider {
  override def getAccountWebhookByIdFuture(accountWebhookId: String): Future[Box[AccountWebhook]] = {
    Future(
      MappedAccountWebhook.find(
        By(MappedAccountWebhook.mAccountWebhookId, accountWebhookId)
      )
    )
  }
  override def getAccountWebhooksByUserIdFuture(userId: String): Future[Box[List[AccountWebhook]]] = {
    Future(
      Full(
        MappedAccountWebhook.findAll(
          By(MappedAccountWebhook.mCreatedByUserId, userId),
          OrderBy(MappedAccountWebhook.updatedAt, Descending)
        )
      )
    )
  }
  override def getAccountWebhooksFuture(queryParams: List[OBPQueryParam]): Future[Box[List[AccountWebhook]]] = {
    val limit = queryParams.collectFirst { case OBPLimit(value) => MaxRows[MappedAccountWebhook](value) }
    val offset = queryParams.collectFirst { case OBPOffset(value) => StartAt[MappedAccountWebhook](value) }
    val userId = queryParams.collectFirst { case OBPUserId(value) => By(MappedAccountWebhook.mCreatedByUserId, value) }
    val bankId = queryParams.collectFirst { case OBPBankId(value) => By(MappedAccountWebhook.mBankId, value) }
    val accountId = queryParams.collectFirst { case OBPAccountId(value) => By(MappedAccountWebhook.mAccountId, value) }
    val optionalParams: Seq[QueryParam[MappedAccountWebhook]] = Seq(limit.toSeq, offset.toSeq, userId.toSeq, bankId.toSeq, accountId.toSeq).flatten
    Future(
      Full(
        MappedAccountWebhook.findAll(optionalParams: _*)
      )
    )
  }

  override def createAccountWebhookFuture(bankId: String,
                                          accountId: String,
                                          userId: String,
                                          triggerName: String,
                                          url: String,
                                          httpMethod: String,
                                          isActive: Boolean
                                         ): Future[Box[AccountWebhook]] = {
    val createAccountWebhook = MappedAccountWebhook.create
      .mBankId(bankId)
      .mAccountId(accountId)
      .mCreatedByUserId(userId)
      .mTriggerName(triggerName)
      .mUrl(url)
      .mHttpMethod(httpMethod)
      .mIsActive(isActive)
      .saveMe()
    Future(Full(createAccountWebhook))
  }

  override def updateAccountWebhookFuture(accountWebhookId: String,
                                          isActive: Boolean
                                         ): Future[Box[AccountWebhook]] = {
    val createAccountWebhook = MappedAccountWebhook.find(By(MappedAccountWebhook.mAccountWebhookId, accountWebhookId))
    createAccountWebhook match {
      case Full(c) =>
        Future(
          tryo {
            c.mAccountWebhookId(accountWebhookId)
             .mIsActive(isActive)
             .saveMe()
          }
        )
      case _ => Future(createAccountWebhook)
    }
  }

}

class MappedAccountWebhook extends AccountWebhook with LongKeyedMapper[MappedAccountWebhook] with IdPK with CreatedUpdated {
  def getSingleton = MappedAccountWebhook

  object mAccountWebhookId extends MappedUUID(this)
  object mBankId extends UUIDString(this)
  object mAccountId extends AccountIdString(this)
  object mTriggerName extends MappedString(this, 64)
  object mUrl extends MappedString(this, 64)
  object mHttpMethod extends MappedString(this, 64)
  object mCreatedByUserId extends UUIDString(this)
  object mIsActive extends MappedBoolean(this)

  def accountWebhookId: String = mAccountWebhookId.get
  def bankId: String = mBankId.get
  def accountId: String = mAccountId.get
  def triggerName: String = mTriggerName.get
  def url: String = mUrl.get
  def httpMethod: String = mHttpMethod.get
  def createdByUserId: String = mCreatedByUserId.get
  def isActive: Boolean = mIsActive.get
}

object MappedAccountWebhook extends MappedAccountWebhook with LongKeyedMetaMapper[MappedAccountWebhook] {
  override def dbIndexes = UniqueIndex(mAccountWebhookId) :: super.dbIndexes
}