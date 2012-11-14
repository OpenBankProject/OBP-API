package code.model.traits
import net.liftweb.common.Box
import code.model.dataAccess.LocalStorage

trait Bank 
{
	def id : String
	def name : String
	def accounts : Set[BankAccount]
}

object Bank {
  def apply(bankPermalink: String) : Box[Bank] = LocalStorage.getBank(bankPermalink)
}