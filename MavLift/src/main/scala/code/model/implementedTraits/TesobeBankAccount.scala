package code.model.implementedTraits

import scala.math.BigDecimal
import java.util.Date
import scala.collection.immutable.Set
import net.liftweb.json.JsonDSL._
import net.liftweb.common.Full
import sun.reflect.generics.reflectiveObjects.NotImplementedException
import code.model.traits.{BankAccount,AccountOwner}
import code.model.dataAccess.{Account,OBPEnvelope}
import code.model.traits.Transaction

class TesobeBankAccount extends BankAccount {

  val theOnlyAccount = Account.find(("holder", "Music Pictures Limited"))
  
  def id: String = { 
	theOnlyAccount match{
	  case Full(a) => a.id.get.toString() 
	  case _ => ""
	}
  }

  def owners: Set[AccountOwner] = { 
    Set(TesobeBankAccountOwner.account)
  }

  def accountType: String = { 
    theOnlyAccount match{
	  case Full(a) => a.kind.get
	  case _ => ""
	}
  }

  def balance: BigDecimal = { 
    
    def getMostRecentBalance(a : Account) : BigDecimal = {
        a.allEnvelopes.size match{
	      case 0 => 0
	      case _ => {
	        val mostRecent = a.allEnvelopes.maxBy(a => a)(OBPEnvelope.DateDescending)
	        mostRecent.obp_transaction.get.details.get.new_balance.get.amount.get
	      }
	    }
      }
    
    theOnlyAccount match{
	  case Full(a) => getMostRecentBalance(a)
	  case _ => 0
	}
  }

  def currency: String = {
    "EUR"
  }

  def label: String = { 
//    theOnlyAccount match{
//	  case Full(a) => a.holder.get
//	  case _ => ""
//	}
    "TESOBE / Music Pictures Ltd. Account (Postbank)"
  }

  def nationalIdentifier: String = { 
    theOnlyAccount match{
	  case Full(a) => a.bank.get.national_identifier.get
	  case _ => ""
	}
  }

  def SWIFT_BIC : Option[String] = { 
    None //TODO: Keep track of this somewhere
  }

  def IBAN : Option[String] = { 
    theOnlyAccount match{
	  case Full(a) => {
	    val iban = a.bank.get.IBAN.get
	    if(iban == "") None
	    else Full(iban)
	  }
	  case _ => None
	}
  }

  def transactions: Set[Transaction] = { 
    theOnlyAccount match{
	  case Full(a) => a.allEnvelopes.map(new TransactionImpl(_)).toSet
	  case _ => Set()
	}
  }

  def transactions(from: Date, to: Date): Set[Transaction] = { 
    throw new NotImplementedException
  }

  def transaction(id: String): Option[Transaction] = { 
    throw new NotImplementedException
  }
  
  def anonAccess = {
    theOnlyAccount match{
	  case Full(a) => a.anonAccess.get
	  case _ => false
	}
  }

}

object TesobeBankAccount {
  def bankAccount = new TesobeBankAccount
}