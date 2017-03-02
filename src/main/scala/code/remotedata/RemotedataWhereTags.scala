package code.remotedata

import java.util.Date

import akka.actor.ActorKilledException
import akka.pattern.ask
import akka.util.Timeout
import code.api.APIFailure
import code.metadata.wheretags.{RemoteWhereTagsCaseClasses, WhereTags}
import code.model._
import net.liftweb.common.{Full, _}

import scala.collection.immutable.List
import scala.concurrent.Await
import scala.concurrent.duration._


object RemotedataWhereTags extends WhereTags {

  implicit val timeout = Timeout(10000 milliseconds)
  val TIMEOUT = 10 seconds
  val rWhereTags = RemoteWhereTagsCaseClasses
  var whereTagsActor = RemotedataActorSystem.getActor("whereTags")

  def getWhereTagForTransaction(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(viewId : ViewId) : Box[GeoTag] = {
    Await.result(
      (whereTagsActor ? rWhereTags.getWhereTagForTransaction(bankId, accountId, transactionId, viewId)).mapTo[Box[GeoTag]],
      TIMEOUT
    )
  }

  def addWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)
                 (userId: UserId, viewId : ViewId, datePosted : Date, longitude : Double, latitude : Double) : Boolean = {
    Await.result(
      (whereTagsActor ? rWhereTags.addWhereTag(bankId, accountId, transactionId, userId: UserId, viewId : ViewId, datePosted : Date, longitude : Double, latitude : Double)).mapTo[Boolean],
      TIMEOUT
    )
  }

  def deleteWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(viewId : ViewId) : Boolean = {
    Await.result(
      (whereTagsActor ? rWhereTags.deleteWhereTag(bankId, accountId, transactionId, viewId)).mapTo[Boolean],
      TIMEOUT
    )
  }

  def bulkDeleteWhereTags(bankId: BankId, accountId: AccountId): Boolean = {
    Await.result(
      (whereTagsActor ? rWhereTags.bulkDeleteWhereTags(bankId, accountId)).mapTo[Boolean],
      TIMEOUT
    )
  }

}
