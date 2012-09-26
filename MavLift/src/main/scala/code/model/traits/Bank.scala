package code.model.traits

trait Bank 
{
	def id : String
	def name : String
	def accounts : Set[BankAccount]
}