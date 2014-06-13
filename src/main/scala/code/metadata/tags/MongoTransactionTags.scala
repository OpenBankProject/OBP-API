package code.metadata.tags

object MongoTransactionTags extends Tags {
  
  def getTags(bankId : String, accountId : String, transactionIdGivenByBank: String) : List[Tag] = {
    //TODO
    null
  }
  def addTag(bankId : String, accountId : String, transactionIdGivenByBank: String)(userId: String, viewId : Long, tag : String, datePosted : Date) : Box[Tag] = {
    //TODO
    null
  }
  def deleteTag(tagId : String) : Box[Unit] = {
    //TODO
    null
  }
  
}
