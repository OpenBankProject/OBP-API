package code.remotedata

import java.util.Date

import akka.pattern.ask
import code.actorsystem.ObpActorInit
import code.metadata.wheretags.{RemotedataWhereTagsCaseClasses, WhereTags}
import code.model._
import com.openbankproject.commons.model._
import net.liftweb.common.Box


object RemotedataWhereTags extends ObpActorInit with WhereTags {

  val cc = RemotedataWhereTagsCaseClasses

  def getWhereTagForTransaction(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(viewId : ViewId) : Box[GeoTag] = getValueFromFuture(
      (actor ? cc.getWhereTagForTransaction(bankId, accountId, transactionId, viewId)).mapTo[Box[GeoTag]]
  )

  def addWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)
                 (userId: UserPrimaryKey, viewId : ViewId, datePosted : Date, longitude : Double, latitude : Double) : Boolean = getValueFromFuture(
      (actor ? cc.addWhereTag(bankId, accountId, transactionId, userId: UserPrimaryKey, viewId : ViewId, datePosted : Date, longitude : Double, latitude : Double)).mapTo[Boolean]
  )

  def deleteWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(viewId : ViewId) : Boolean = getValueFromFuture(
      (actor ? cc.deleteWhereTag(bankId, accountId, transactionId, viewId)).mapTo[Boolean]
  )

  def bulkDeleteWhereTags(bankId: BankId, accountId: AccountId): Boolean = getValueFromFuture(
      (actor ? cc.bulkDeleteWhereTags(bankId, accountId)).mapTo[Boolean]
  )
  
  def bulkDeleteWhereTagsOnTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId): Boolean = getValueFromFuture(
      (actor ? cc.bulkDeleteWhereTagsOnTransaction(bankId, accountId, transactionId)).mapTo[Boolean]
  )


}
