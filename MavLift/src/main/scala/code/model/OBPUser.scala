package code.model
import code.model.View

trait OBPUser {
  
  def emailAddress : String
  
  def permittedViews(bankAccount : BankAccount) : Set[View]
  
}