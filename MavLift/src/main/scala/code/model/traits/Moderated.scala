package code.model.traits

class ModeratedOtherBankAccount (filteredId : String, filteredLabel : AccountName, 
  filteredNationalIdentifier : Option[String], filteredSWIFT_BIC : Option[Option[String]], 
  filtredIBAN : Option[Option[String]], filteredMetadata : Option[ModeratedOtherBankAccountMetadata]) 
{
    def id = filteredId
    def label = filteredLabel
    def NationalIdentifier = filteredNationalIdentifier
    def SWIFT_BIC = filteredSWIFT_BIC
    def metadata = filteredMetadata 
    def isAlias = filteredLabel.aliasType match{
    case Public | Private => true
    case _ => false
  }
}
class ModeratedOtherBankAccountMetadata(filteredMoreInfo : Option[String], 
  filteredUrl : Option[String], filteredImageUrl : Option[String], filteredOpenCorporatesUrl : Option[String]) {
  def moreInfo = filteredMoreInfo 
  def url = filteredUrl
  def imageUrl = filteredImageUrl
  def openCorporatesUrl = filteredOpenCorporatesUrl
}


import java.util.Date

class ModeratedTransaction(filteredId: String, filteredBankAccount: Option[ModeratedBankAccount], 
  filteredOtherBankAccount: Option[ModeratedOtherBankAccount], filteredMetaData : Option[ModeratedTransactionMetadata], 
  filteredTransactionType: Option[String], filteredAmount: Option[BigDecimal], filteredCurrency: Option[String], 
  filteredLabel: Option[Option[String]],filteredStartDate: Option[Date], filteredFinishDate: Option[Date],
  filteredBalance : String) {
  
  //the filteredBlance type in this class is a string rather than Big decimal like in Transaction trait for snippet (display) reasons.
  //the view should be able to return a sign (- or +) or the real value. casting signs into bigdecimal is not possible  
  def id = filteredId 
  def bankAccount = filteredBankAccount
  def otherBankAccount = filteredOtherBankAccount
  def metadata = filteredMetaData
  def transactionType = filteredTransactionType
  def amount = filteredAmount 
  def currency = filteredCurrency
  def label = filteredLabel
  def startDate = filteredStartDate
  def finishDate = filteredFinishDate
  def balance = filteredBalance
}

class ModeratedTransactionMetadata(filteredOwnerComment : Option[String], filteredComments : Option[List[Comment]], 
  addOwnerComment : Option[(String => Unit)], addCommentFunc: Option[(Comment => Unit)])
  {
    def ownerComment = filteredOwnerComment
    def comments = filteredComments
    def ownerComment(text : String) = addOwnerComment match {
      case None => None
      case Some(o) => o.apply(text)
    }
    def addComment= addCommentFunc

  }
class ModeratedBankAccount(filteredId : String, 
  filteredOwners : Option[Set[AccountOwner]], filteredAccountType : Option[String], 
  filteredBalance: String, filteredCurrency : Option[String], 
  filteredLabel : Option[String], filteredNationalIdentifier : Option[String],
  filteredSwift_bic : Option[Option[String]], filteredIban : Option[Option[String]])
{
  def id = filteredId
  def owners = filteredOwners
  def accountType = filteredAccountType
  def balance = filteredBalance 
  def currency = filteredCurrency
  def label = filteredLabel
  def nationalIdentifier = filteredNationalIdentifier
  def swift_bic = filteredSwift_bic
  def iban = filteredIban
}