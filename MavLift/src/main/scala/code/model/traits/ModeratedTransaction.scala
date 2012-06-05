package code.model.traits
import java.util.Date

class ModeratedTransaction(filteredId: Option[String], filteredAccount: Option[BankAccount], filteredOtherParty: Option[ModeratedMetaData],
  filteredTransactionType: Option[String], filteredAmount: Option[BigDecimal], filteredCurrency: Option[String], filteredLabel: Option[Option[String]],
  filteredOwnerComment: Option[String], filteredComments: Option[List[Comment]], filteredStartDate: Option[Date], filteredFinishDate: Option[Date],
  filteredBalance : String, addCommentFunc: Option[(Comment => Unit)], addOwnerComment : Option[(String => Unit)]) {
  
  //addCommentFunc: (Comment => Unit)
  //the filteredBlance type in this class is a string rather than Big decimal like in Transaction trait for snippet (display) reasons.
  //the view should be able to return a sign (- or +) or the real value. casting signs into bigdecimal is not possible  
  
  def finishDate = filteredFinishDate
  def startDate = filteredStartDate
  def balance = filteredBalance
  
  def aliasType = filteredOtherParty match{
    case Some(o) => o.alias
    case _ => NoAlias
  }
	def accountHolder = filteredOtherParty match{
		case Some(o) => o.accountHolderName
		case _ => None
	}
  def imageUrl = filteredOtherParty match{
    case Some(o) => o.imageUrl
    case _ => None
  }
  def url = filteredOtherParty match{
    case Some(o) => o.url
    case _ => None
  }
  def openCorporatesUrl = filteredOtherParty match{
    case Some(o) => o.openCorporatesUrl
    case _ => None
  }
  def moreInfo = filteredOtherParty match{
    case Some(o) => o.moreInfo
    case _ => None
  }
  def ownerComment = filteredOwnerComment 
  def ownerComment(text : String) = addOwnerComment
  
  def amount = filteredAmount 
  def comments = filteredComments 
  def id = filteredId match {
    case Some(a) => a
    case _ => ""
  }
  def addComment= addCommentFunc

}