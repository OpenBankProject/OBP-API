package code.model

import scala.math.BigDecimal
import java.util.Date
import scala.collection.immutable.List
import net.liftweb.common.Loggable
import code.model
import net.liftweb.common.Box

class TransactionImpl(env : OBPEnvelope) extends Transaction with Loggable {

    val transaction : OBPTransaction= env.obp_transaction.get
    val thisAccount = transaction.this_account.get
    val otherAccount = transaction.other_account.get
    val theAccount = thisAccount.theAccount
    val otherUnmediatedHolder = otherAccount.holder.get
    
    val oAccs = theAccount.get.otherAccounts.get
    val oAccOpt = oAccs.find(o => {
      otherUnmediatedHolder.equals(o.holder.get)
    })
   
    val oAcc =  oAccOpt getOrElse {
      	println("OtherAccount for transaction : "+env.id+" not found");// TODO: a debug thing to delete 
      	OtherAccount.createRecord
      }
    //println (amount.toString()+" account holder : "+oAcc.holder + " n° "+id)
    
  def id: String = { 
    env.id.is.toString()
  }

  def account: BankAccount = { 
    //TODO: Generalise to multiple bank accounts
    TesobeBankAccount.bankAccount
  }

  def otherParty: NonObpAccount = { 
    //TODO: Return something once NonObpAccount is implemented
    new NonObpAccountImpl(oAcc)
  }

  def transactionType: String = { 
    env.obp_transaction.get.details.get.type_en.get
  }

  def amount: BigDecimal = { 
    env.obp_transaction.get.details.get.value.get.amount.get
  }

  def currency: String = { 
    env.obp_transaction.get.details.get.value.get.currency.get
  }

  //Provided by the bank
  def label : Option[String] = { 
    None //TODO: Implement transaction labels
  }

  def ownerComment : Option[String] = {
    def showNarrative = {
      val narrative = env.narrative.get

      if (narrative == "") None
      else Some(narrative)
    }

    showNarrative
  }

  def comments : List[Comment] = { 
    env.obp_comments.get.map(new CommentImpl(_))
  }

  def startDate: Date = { 
    env.obp_transaction.get.details.get.posted.get
  }

  def finishDate: Date = { 
    env.obp_transaction.get.details.get.completed.get
  }
  
  def balance : BigDecimal = env.obp_transaction.get.details.get.new_balance.get.amount.get
  
  
  def addComment(comment: Comment) = {
    val emailAddress = for{
      poster <- comment.postedBy
    } yield poster.emailAddress
    
    emailAddress match{
      case Some(e) => env.addComment(e, comment.text)
      case _ => logger.warn("A comment was not added due to a lack of valid email address")
    }
    
  }
    
}

class FilteredTransaction(filteredId: Option[String], filteredAccount: Option[BankAccount], filteredOtherParty: Option[FilteredNonObpAccount],
  filteredTransactionType: Option[String], filteredAmount: Option[BigDecimal], filteredCurrency: Option[String], filteredLabel: Option[Option[String]],
  filteredOwnerComment: Option[Option[String]], filteredComments: Option[List[Comment]], filteredStartDate: Option[Date], filteredFinishDate: Option[Date],
  filteredBalance : String, addCommentFunc: (Comment => Unit)) {
  
  //the filteredBlance type in this class is a string rather than Big decimal like in Transaction trait for snippet (display) reasons.
  //the view should be able to rertun a sign (- or +) or the real value. casting signs into bigdecimal is not possible  
  
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
  def ownerComment = filteredOwnerComment match
  {
    case Some(a)=> a 
    case _ => None
  }
  
  def amount = filteredAmount 
  def comments : List[Comment] =  filteredComments match {
    case Some(o) => o
    case _ => List()
  }
  def id = filteredId match {
    case Some(a) => a
    case _ => ""
  }
}