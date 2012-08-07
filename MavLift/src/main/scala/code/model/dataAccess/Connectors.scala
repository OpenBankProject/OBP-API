package code.model.dataAccess

import code.model.traits._
import code.model.implementedTraits._

  object PostBankLocalStorage 
  {
    def getTransactions : List[Transaction] = 
    {
      def createTransaction(env : OBPEnvelope) : Transaction = 
      {
        val transaction : OBPTransaction = env.obp_transaction.get
        val thisAccount_ = transaction.this_account.get
        val otherAccount_ = transaction.other_account.get
        val theAccount = thisAccount_.theAccount
        val otherUnmediatedHolder = otherAccount_.holder.get
        
        val oAccs = theAccount.get.otherAccounts.get
        val oAccOpt = oAccs.find(o => {
          otherUnmediatedHolder.equals(o.holder.get)
        })
       
        val oAcc =  oAccOpt getOrElse {
            OtherAccount.createRecord
          }
        
        val id = env.id.is.toString()
        val otherAccount = new OtherBankAccountImpl("", otherAccount_.holder.get,otherAccount_.number.get,
          None,None, new OtherBankAccountMetadataImpl(oAcc))
        val metadata = new TransactionMetadataImpl(env.narrative.get, env.obp_comments.get.map(new CommentImpl(_)),
        (text => env.narrative(text).save), env.addComment _)   
        val transactionType=  env.obp_transaction.get.details.get.type_en.get
        val amount = env.obp_transaction.get.details.get.value.get.amount.get
        val currency = env.obp_transaction.get.details.get.value.get.currency.get
        val label = None
        val startDate = env.obp_transaction.get.details.get.posted.get
        val finishDate = env.obp_transaction.get.details.get.completed.get
        val balance = env.obp_transaction.get.details.get.new_balance.get.amount.get
        new TransactionImpl(id,null, otherAccount,metadata, transactionType,amount,currency,
          label, startDate, finishDate, balance)
      }
      import com.mongodb.QueryBuilder
      val qry = QueryBuilder.start().get
      val envelopesToDisplay = OBPEnvelope.findAll(qry)
      val transactions = envelopesToDisplay.map(createTransaction(_))
      val bankAccountBalance = (envelopesToDisplay.maxBy(a => a)(OBPEnvelope.DateDescending)).obp_transaction.get.
      details.get.new_balance.get.amount.get
      val bankAccount : BankAccount = new BankAccountImpl("01", Set(),"Buisness",bankAccountBalance, 
        "EUR", "Tesobe main account","",None,None, transactions.toSet, true)
      bankAccount.owners = Set(new AccountOwnerImpl("01","Music Pictures LTD", Set(bankAccount)))
      bankAccount.transactions.map( _.thisAccount = bankAccount)      
      transactions
    }
  }