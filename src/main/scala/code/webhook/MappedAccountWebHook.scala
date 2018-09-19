package code.webhook

import code.util.{AccountIdString, UUIDString}
import net.liftweb.common.{Box, Full}
import net.liftweb.mapper._
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
  override def getAccountWebHookByUserIdFuture(userId: String): Future[Box[List[AccountWebHook]]] = {
    Future(
      Full(
        MappedAccountWebHook.findAll(
          By(MappedAccountWebHook.mCreatedByUserId, userId),
          OrderBy(MappedAccountWebHook.updatedAt, Descending)
        )
      )
    )
  }
  override def createAccountWebHookFuture(bankId: String,
                                          accountId: String,
                                          userId: String,
                                          triggerName: String,
                                          url: String,
                                          httpMethod: String
                                         ): Future[Box[AccountWebHook]] = {
    val createAccountWebHook = MappedAccountWebHook.create
      .mBankId(bankId)
      .mAccountWebHookId(accountId)
      .mCreatedByUserId(userId)
      .mTriggerName(triggerName)
      .mUrl(url)
      .mHttpMethod(httpMethod)
      .saveMe()
    Future(Full(createAccountWebHook))
  }

}

class MappedAccountWebHook extends AccountWebHook with LongKeyedMapper[MappedAccountWebHook] with IdPK with CreatedUpdated {
  def getSingleton = MappedAccountWebHook

  object mAccountWebHookId extends UUIDString(this)
  object mBankId extends UUIDString(this)
  object mAccountId extends AccountIdString(this)
  object mTriggerName extends MappedString(this, 64)
  object mUrl extends MappedString(this, 64)
  object mHttpMethod extends MappedString(this, 64)
  object mCreatedByUserId extends UUIDString(this)

  def accountWebHookId: String = mAccountWebHookId.get
  def bankId: String = mBankId.get
  def accountId: String = mAccountId.get
  def triggerName: String = mTriggerName.get
  def url: String = mUrl.get
  def httpMethod: String = mHttpMethod.get
  def createdByUserId: String = mCreatedByUserId.get
}

object MappedAccountWebHook extends MappedAccountWebHook with LongKeyedMetaMapper[MappedAccountWebHook] {
  override def dbIndexes = UniqueIndex(mAccountWebHookId) :: super.dbIndexes
}