package code.remotedata

import java.util.Date

import akka.actor.ActorKilledException
import akka.pattern.ask
import akka.util.Timeout
import code.api.APIFailure
import code.metadata.tags.{RemoteTagsCaseClasses, Tags}
import code.model._
import net.liftweb.common.{Full, _}

import scala.collection.immutable.List
import scala.concurrent.Await
import scala.concurrent.duration._


object RemotedataTags extends Tags {

  implicit val timeout = Timeout(10000 milliseconds)
  val TIMEOUT = 10 seconds
  val rTags = RemoteTagsCaseClasses
  var tagsActor = RemotedataActorSystem.getActor("tags")


  def getTags(bankId : BankId, accountId : AccountId, transactionId : TransactionId)(viewId : ViewId) : List[TransactionTag] = {
    Await.result(
      (tagsActor ? rTags.getTags(bankId, accountId, transactionId, viewId)).mapTo[List[TransactionTag]],
      TIMEOUT
    )
  }

  def addTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(userId: UserId, viewId : ViewId, tagText : String, datePosted : Date) : Box[TransactionTag] = {
    Full(
      Await.result(
        (tagsActor ? rTags.addTag(bankId, accountId, transactionId, userId, viewId, tagText, datePosted)).mapTo[TransactionTag],
        TIMEOUT
      )
    )
  }

  def deleteTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(tagId : String) : Box[Boolean] = {
    val res = try {
      Full(
        Await.result(
          (tagsActor ? rTags.deleteTag(bankId, accountId, transactionId, tagId)).mapTo[Boolean],
          TIMEOUT
        )
      )
    }
    catch {
      case k: ActorKilledException =>  Empty ~> APIFailure(s"Cannot delete the tag", 404)
      case e: Throwable => throw e
    }
    res
  }

  def bulkDeleteTags(bankId: BankId, accountId: AccountId): Boolean = {
    Await.result(
      (tagsActor ? rTags.bulkDeleteTags(bankId, accountId)).mapTo[Boolean],
      TIMEOUT
    )
  }


}
