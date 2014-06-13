package code.metadata.comments

object MongoTransactionComments extends Comments {

  
  def getComments(bankId : String, accountId : String, transactionId : String) : Iterable[Comment] = {
    //TODO: get ObpEnvelope and use its methods
    null
  }
  def addComment(bankId : String, accountId : String, transactionId: String)(userId: String, viewId : Long, text : String, datePosted : Date) : Box[Comment] = {
    //TODO: get ObpEnvelope and use its methods
    null
  }
  def deleteComment(commentId : String) : Box[Unit] = {
    //TODO: get ObpEnvelope and use its methods
    null
  }
  
}