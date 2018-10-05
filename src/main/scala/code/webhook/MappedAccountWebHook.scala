package code.webhook

import code.bankconnectors._
import code.util.{AccountIdString, MappedUUID, UUIDString}
import net.liftweb.common.{Box, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.List
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object MappedAccountWebHookProvider extends AccountWebHookProvider {
  override def getAccountWebHookByIdFuture(accountWebHookId: String): Future[Box[AccountWebHook]] = {
    Future(
      MappedAccountWebHook.find(
        By(MappedAccountWebHook.mAccountWebHookId, accountWebHookId)
      )
    )
  }
  override def getAccountWebHooksByUserIdFuture(userId: String): Future[Box[List[AccountWebHook]]] = {
    Future(
      Full(
        MappedAccountWebHook.findAll(
          By(MappedAccountWebHook.mCreatedByUserId, userId),
          OrderBy(MappedAccountWebHook.updatedAt, Descending)
        )
      )
    )
  }
  override def getAccountWebHooksFuture(queryParams: List[OBPQueryParam]): Future[Box[List[AccountWebHook]]] = {
    val limit = queryParams.collectFirst { case OBPLimit(value) => MaxRows[MappedAccountWebHook](value) }
    val offset = queryParams.collectFirst { case OBPOffset(value) => StartAt[MappedAccountWebHook](value) }
    val userId = queryParams.collectFirst { case OBPUserId(value) => By(MappedAccountWebHook.mCreatedByUserId, value) }
    val bankId = queryParams.collectFirst { case OBPBankId(value) => By(MappedAccountWebHook.mBankId, value) }
    val accountId = queryParams.collectFirst { case OBPAccountId(value) => By(MappedAccountWebHook.mAccountId, value) }
    val optionalParams: Seq[QueryParam[MappedAccountWebHook]] = Seq(limit.toSeq, offset.toSeq, userId.toSeq, bankId.toSeq, accountId.toSeq).flatten
    Future(
      Full(
        MappedAccountWebHook.findAll(optionalParams: _*)
      )
    )
  }

  override def createAccountWebHookFuture(bankId: String,
                                          accountId: String,
                                          userId: String,
                                          triggerName: String,
                                          url: String,
                                          httpMethod: String,
                                          isActive: Boolean
                                         ): Future[Box[AccountWebHook]] = {
    val createAccountWebHook = MappedAccountWebHook.create
      .mBankId(bankId)
      .mAccountId(accountId)
      .mCreatedByUserId(userId)
      .mTriggerName(triggerName)
      .mUrl(url)
      .mHttpMethod(httpMethod)
      .mIsActive(isActive)
      .saveMe()
    Future(Full(createAccountWebHook))
  }

  override def updateAccountWebHookFuture(accountWebHookId: String,
                                          isActive: Boolean
                                         ): Future[Box[AccountWebHook]] = {
    val createAccountWebHook = MappedAccountWebHook.find(By(MappedAccountWebHook.mAccountWebHookId, accountWebHookId))
    createAccountWebHook match {
      case Full(c) =>
        Future(
          tryo {
            c.mAccountWebHookId(accountWebHookId)
             .mIsActive(isActive)
             .saveMe()
          }
        )
      case _ => Future(createAccountWebHook)
    }
  }

}

class MappedAccountWebHook extends AccountWebHook with LongKeyedMapper[MappedAccountWebHook] with IdPK with CreatedUpdated {
  def getSingleton = MappedAccountWebHook

  object mAccountWebHookId extends MappedUUID(this)
  object mBankId extends UUIDString(this)
  object mAccountId extends AccountIdString(this)
  object mTriggerName extends MappedString(this, 64)
  object mUrl extends MappedString(this, 64)
  object mHttpMethod extends MappedString(this, 64)
  object mCreatedByUserId extends UUIDString(this)
  object mIsActive extends MappedBoolean(this)

  def accountWebHookId: String = mAccountWebHookId.get
  def bankId: String = mBankId.get
  def accountId: String = mAccountId.get
  def triggerName: String = mTriggerName.get
  def url: String = mUrl.get
  def httpMethod: String = mHttpMethod.get
  def createdByUserId: String = mCreatedByUserId.get
  def isActive: Boolean = mIsActive.get
}

object MappedAccountWebHook extends MappedAccountWebHook with LongKeyedMetaMapper[MappedAccountWebHook] {
  override def dbIndexes = UniqueIndex(mAccountWebHookId) :: super.dbIndexes
}