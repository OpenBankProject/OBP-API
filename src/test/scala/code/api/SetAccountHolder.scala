package code.api

import code.model.dataAccess.MappedAccountHolder
import code.model.{User, AccountId, BankId}

trait SetAccountHolder {

  def setAccountHolder(user: User, bankId : BankId, accountId : AccountId) = {
    MappedAccountHolder.create.
      user(user.apiId.value).
      accountBankPermalink(bankId.value).
      accountPermalink(accountId.value).save
  }

}
