package code.model.implementedTraits

import code.model.dataAccess.{OBPEnvelope,OBPTransaction,OtherAccount}
import code.model.traits.{Transaction,BankAccount,MetaData}
import scala.math.BigDecimal
import java.util.Date
import scala.collection.immutable.List
import net.liftweb.common.Loggable
import net.liftweb.common.Box
import code.model.traits.Comment

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

  def metaData: MetaData = { 
    //TODO: Return something once NonObpAccount is implemented
    new MetaDataImpl(oAcc)
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
  def ownerComment(comment : String) : Unit=  
  {
    env.narrative(comment).save
  }
    
}

