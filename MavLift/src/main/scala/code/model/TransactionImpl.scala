package code.model

import scala.math.BigDecimal
import java.util.Date
import scala.collection.immutable.List
import net.liftweb.common.Loggable

class TransactionImpl(env : OBPEnvelope) extends Transaction with Loggable {

  def id: String = { 
    ""
  }

  def account: BankAccount = { 
    //TODO: Generalise to multiple bank accounts
    TesobeBankAccount.bankAccount
  }

  def otherParty: NonObpAccount = { 
    //TODO: Return something once NonObpAccount is implemented
    null
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

class FilteredTransaction(filteredId: Option[String], filteredAccount: Option[BankAccount], filteredOtherParty: Option[NonObpAccount],
  filteredTransactionType: Option[String], filteredAmount: Option[BigDecimal], filteredCurrency: Option[String], filteredLabel: Option[Option[String]],
  filteredOwnerComment: Option[Option[String]], filteredComments: Option[List[Comment]], filteredStartDate: Option[Date], filteredFinishDate: Option[Date],
  addCommentFunc: (Comment => Unit)) {
}