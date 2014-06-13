package code.metadata.tags

import net.liftweb.util.SimpleInjector
import java.util.Date
import net.liftweb.common.Box
import code.model.Tag

object Tags  extends SimpleInjector {

  val tags = new Inject(buildOne _) {}
  
  def buildOne: Tags = MongoTransactionTags
  
}

trait Tags {
  
  def getTags(bankId : String, accountId : String, transactionIdGivenByBank: String) : List[Tag]
  def addTag(bankId : String, accountId : String, transactionIdGivenByBank: String)(userId: String, viewId : Long, tag : String, datePosted : Date) : Box[Tag]
  def deleteTag(tagId : String) : Box[Unit]
  
}