package code.model.implementedTraits

import code.model.traits.{Bank, BankAccount}

class BankImpl(_id: String, _name : String, _bankAccounts : Set[BankAccount]) extends Bank
{
	def id = _id
	def name = _name
	def accounts = _bankAccounts	
}
