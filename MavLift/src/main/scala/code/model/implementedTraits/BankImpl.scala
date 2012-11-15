package code.model.implementedTraits

import code.model.traits.{Bank, BankAccount}
import code.model.dataAccess.LocalStorage

class BankImpl(_id: String, _name : String) extends Bank
{
	def id = _id
	def name = _name
	def accounts = LocalStorage.getBankAccounts(this)
}
