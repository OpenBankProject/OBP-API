package code.model.traits
import java.util.Date
import net.liftweb.json.JsonAST.JObject
import net.liftweb.json.JsonAST.JString
import net.liftweb.json.JsonAST.JField
import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import net.liftweb.http.JsonResponse
import net.liftweb.http.LiftResponse
import java.text.SimpleDateFormat

class ModeratedOtherBankAccount (filteredId : String, filteredLabel : AccountName, 
  filteredNationalIdentifier : Option[String], filteredSWIFT_BIC : Option[Option[String]], 
  filtredIBAN : Option[Option[String]], filteredMetadata : Option[ModeratedOtherBankAccountMetadata]) 
{
    def id = filteredId
    def label = filteredLabel
    def nationalIdentifier = filteredNationalIdentifier
    def swift_bic = filteredSWIFT_BIC
    def metadata = filteredMetadata 
    def isAlias = filteredLabel.aliasType match{
    case Public | Private => true
    case _ => false
  }
}

object ModeratedOtherBankAccount {
  implicit def moderatedOtherBankAccount2Json(mOtherBank: ModeratedOtherBankAccount) : JObject = {
    val holderName = Some(mOtherBank.label.display)
    val isAlias = mOtherBank.isAlias
    val number = Some("TODO")
    val kind = Some("TODO")
    val bankIBAN = Some(Some("TODO"))
    val bankNatIdent = mOtherBank.nationalIdentifier
    val bankName = Some("TODO")
    ModeratedBankAccount.bankJson(holderName, isAlias, number, kind, bankIBAN, bankNatIdent, bankName)
  }
}

class ModeratedOtherBankAccountMetadata(filteredMoreInfo : Option[String], 
  filteredUrl : Option[String], filteredImageUrl : Option[String], filteredOpenCorporatesUrl : Option[String]) {
  def moreInfo = filteredMoreInfo 
  def url = filteredUrl
  def imageUrl = filteredImageUrl
  def openCorporatesUrl = filteredOpenCorporatesUrl
}

object ModeratedOtherBankAccountMetadata {
  implicit def moderatedOtherBankAccountMetadata2Json(mOtherBankMeta: ModeratedOtherBankAccountMetadata) : JObject = {
    JObject(JField("blah", JString("test")) :: Nil)
  }
}


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

object ModeratedTransaction {
  
  implicit def dateOption2JString(date: Option[Date]) : JString = {
    val format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    JString(date.map(d => format.format(d)) getOrElse "")
  }
  
  implicit def moderatedTransaction2Json(mTransaction: ModeratedTransaction) : JObject = {
    ("this_account" -> mTransaction.bankAccount) ~
    ("other_account" -> mTransaction.otherBankAccount) ~
    ("details" -> 
    	("type_en" -> mTransaction.transactionType) ~ //TODO: Need translations for transaction types and a way to
    	("type_de" -> mTransaction.transactionType) ~ // figure out what language the original type is in
    	("posted" -> mTransaction.startDate) ~
    	("completed" -> mTransaction.finishDate) ~
    	("new_balance" -> 
    		("currency" -> mTransaction.currency) ~ //TODO: Need separate currency for balances and values?
    		("amount" -> mTransaction.balance)) ~
    	("value" ->
    		("currency" -> mTransaction.currency) ~
    		("amount" -> mTransaction.amount)))
  }
  
  implicit def moderatedTransactions2Json(mTransactions: List[ModeratedTransaction]) : LiftResponse = {
    JsonResponse(("transactions" -> mTransactions))
  }
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

object ModeratedTransactionMetadata {
  implicit def moderatedTransactionMetadata2Json(mTransactionMeta: ModeratedTransactionMetadata) : JObject = {
    JObject(JField("blah", JString("test")) :: Nil)
  }
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

object ModeratedBankAccount {
  
  def bankJson(holderName: Option[String], isAlias: Boolean, number: Option[String],
      	kind: Option[String], bankIBAN: Option[Option[String]], bankNatIdent: Option[String],
      	bankName: Option[String]) : JObject = {
    ("holder" -> 
    	("name" -> holderName) ~
    	("alias" -> {if(isAlias) "yes" else "no"})) ~
    ("number" -> number) ~
    ("kind" -> kind) ~
    ("bank" ->
    	("IBAN" -> bankIBAN) ~
    	("national_identifier" -> bankNatIdent) ~
    	("name" -> bankName))
  }
  
  implicit def moderatedBankAccount2Json(mBankAccount: ModeratedBankAccount) : JObject = {
    val holderName = Some(mBankAccount.owners.mkString(","))
    val isAlias = false
    val number = Some("TODO")
    val kind = mBankAccount.accountType
    val bankIBAN = mBankAccount.iban
    val bankNatIdent = mBankAccount.nationalIdentifier
    val bankName = Some("TODO")
    bankJson(holderName, isAlias, number, kind, bankIBAN, bankNatIdent, bankName)
  }
}