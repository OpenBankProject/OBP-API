package code.model.traits

trait TransactionMetadata {
  
  // Owner provided comment, done in OBP
  def ownerComment : Option[String]
  def ownerComment(comment : String) : Unit 
  def comments : List[Comment]
  def addComment(comment : Comment) : Unit
}
trait OtherBankAccountMetadata 
{
	def publicAlias : String
  def privateAlias : String
  def moreInfo : String
	def url : String
	def imageUrl : String
	def openCorporatesUrl : String
}