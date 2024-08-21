package code.metadata.wheretags

import java.util.Date

import code.api.util.APIUtil
import code.model._
import com.openbankproject.commons.model._
import net.liftweb.common.Box
import net.liftweb.util.{Props, SimpleInjector}

object WhereTags  extends SimpleInjector {

  val whereTags = new Inject(buildOne _) {}

  def buildOne: WhereTags = MapperWhereTags

}

trait WhereTags {

  //TODO: it probably makes more sense for this to return Box[GeoTag]. Leaving it as a Boolean for now...
  def addWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)
                 (userId: UserPrimaryKey, viewId : ViewId, datePosted : Date, longitude : Double, latitude : Double) : Boolean

  //TODO: would be nicer to change this to return Box[Unit] like in e.g. comments. Or perhaps change the way the other ones work
  //instead, with the end effect of keeping them consistent. Leaving it as a Boolean for now...
  def deleteWhereTag(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(viewId : ViewId) : Boolean

  def getWhereTagForTransaction(bankId : BankId, accountId : AccountId, transactionId: TransactionId)(viewId : ViewId) : Box[GeoTag]

  def bulkDeleteWhereTagsOnTransaction(bankId: BankId, accountId: AccountId, transactionId: TransactionId) : Boolean
  
  def bulkDeleteWhereTags(bankId: BankId, accountId: AccountId) : Boolean

}
