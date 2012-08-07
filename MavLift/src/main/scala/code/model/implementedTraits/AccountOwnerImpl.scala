package code.model.implementedTraits

import code.model.traits.{BankAccount,AccountOwner}

class AccountOwnerImpl(id_ : String, accountName : String, ownedBankAccounts: Set[BankAccount]) extends AccountOwner{
  def id = id_
  def name = accountName
  def bankAccounts = ownedBankAccounts
}

