/**
Open Bank Project - API
Copyright (C) 2011-2016, TESOBE Ltd

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)
  by
  Simon Redfern : simon AT tesobe DOT com
  Stefan Bethge : stefan AT tesobe DOT com
  Everett Sochowski : everett AT tesobe DOT com
  Ayoub Benali: ayoub AT tesobe DOT com

 */
package code.api.v3_0_0

import java.util.Date

import code.api.v1_2_1.{ThisAccountJSON, _}
import code.model._
import code.api.v1_2_1.JSONFactory._
import code.api.v2_0_0.JSONFactory200
import code.api.v2_0_0.JSONFactory200.CoreTransactionDetailsJSON
import code.model.dataAccess.AuthUser
import code.users.Users
import code.api.util.APIUtil._


//stated -- Transaction relevant case classes /////
case class ThisAccountJsonV300(
  id: String,
  bank_routing: BankRoutingJsonV121,
  account_routing: AccountRoutingJsonV121,
  holders: List[AccountHolderJSON],
  kind: String
)

case class OtherAccountJsonV300(
  id: String,
  bank_routing: BankRoutingJsonV121,
  account_routing: AccountRoutingJsonV121,
  kind: String,
  metadata: OtherAccountMetadataJSON
)

case class OtherAccountsJsonV300(
  other_accounts: List[OtherAccountJsonV300]
)

case class TransactionJsonV300(
  id: String,
  this_account: ThisAccountJsonV300,
  other_account: OtherAccountJsonV300,
  details: TransactionDetailsJSON,
  metadata: TransactionMetadataJSON
)

case class TransactionsJsonV300(
  transactions: List[TransactionJsonV300]
)

case class CoreCounterpartyJsonV300(
  id: String,
  bank_routing: BankRoutingJsonV121,
  account_routing: AccountRoutingJsonV121,
  kind: String
)

case class CoreTransactionJsonV300(
  id: String,
  account: ThisAccountJsonV300,
  counterparty: CoreCounterpartyJsonV300,
  details: CoreTransactionDetailsJSON
)

case class CoreCounterpartiesJsonV300(
  counterparties: List[CoreCounterpartyJsonV300]
)

case class CoreTransactionsJsonV300(
  transactions: List[CoreTransactionJsonV300]
)

//ended -- Transaction relevant case classes /////

object JSONFactory300{
  //stated -- Transaction relevant methods /////
  def createTransactionsJson(transactions: List[ModeratedTransaction]) : TransactionsJsonV300 = {
    TransactionsJsonV300(transactions.map(createTransactionJSON))
  }
  
  def createTransactionJSON(transaction : ModeratedTransaction) : TransactionJsonV300 = {
    TransactionJsonV300(
      id = transaction.id.value,
      this_account = transaction.bankAccount.map(createThisAccountJSON).getOrElse(null),
      other_account = transaction.otherBankAccount.map(createOtherBankAccount).getOrElse(null),
      details = createTransactionDetailsJSON(transaction),
      metadata = transaction.metadata.map(createTransactionMetadataJSON).getOrElse(null)
    )
  }
  
  def createTransactionMetadataJSON(metadata : ModeratedTransactionMetadata) : TransactionMetadataJSON = {
    TransactionMetadataJSON(
      narrative = stringOptionOrNull(metadata.ownerComment),
      comments = metadata.comments.map(_.map(createTransactionCommentJSON)).getOrElse(null),
      tags = metadata.tags.map(_.map(createTransactionTagJSON)).getOrElse(null),
      images = metadata.images.map(_.map(createTransactionImageJSON)).getOrElse(null),
      where = metadata.whereTag.map(createLocationJSON).getOrElse(null)
    )
  }
  
  def createTransactionDetailsJSON(transaction : ModeratedTransaction) : TransactionDetailsJSON = {
    TransactionDetailsJSON(
      `type` = stringOptionOrNull(transaction.transactionType),
      description = stringOptionOrNull(transaction.description),
      posted = transaction.startDate.getOrElse(null),
      completed = transaction.finishDate.getOrElse(null),
      new_balance = createAmountOfMoneyJSON(transaction.currency, transaction.balance),
      value= createAmountOfMoneyJSON(transaction.currency, transaction.amount.map(_.toString))
    )
  }
  
  def createThisAccountJSON(bankAccount : ModeratedBankAccount) : ThisAccountJsonV300 = {
    ThisAccountJsonV300(
      id = bankAccount.accountId.value,
      kind = stringOptionOrNull(bankAccount.accountType),
      bank_routing = BankRoutingJsonV121(stringOptionOrNull(bankAccount.accountRoutingScheme),stringOptionOrNull(bankAccount.accountRoutingAddress)),
      account_routing = AccountRoutingJsonV121(stringOptionOrNull(bankAccount.accountRoutingScheme),stringOptionOrNull(bankAccount.accountRoutingAddress)),
      holders = bankAccount.owners.map(x => x.toList.map(holder => AccountHolderJSON(name = holder.name, is_alias = false))).getOrElse(null)
    )
  }
  
  def createOtherAccountMetaDataJSON(metadata : ModeratedOtherBankAccountMetadata) : OtherAccountMetadataJSON = {
    OtherAccountMetadataJSON(
      public_alias = stringOptionOrNull(metadata.publicAlias),
      private_alias = stringOptionOrNull(metadata.privateAlias),
      more_info = stringOptionOrNull(metadata.moreInfo),
      URL = stringOptionOrNull(metadata.url),
      image_URL = stringOptionOrNull(metadata.imageURL),
      open_corporates_URL = stringOptionOrNull(metadata.openCorporatesURL),
      corporate_location = metadata.corporateLocation.map(createLocationJSON).getOrElse(null),
      physical_location = metadata.physicalLocation.map(createLocationJSON).getOrElse(null)
    )
  }
  
  def createOtherBankAccount(bankAccount : ModeratedOtherBankAccount) : OtherAccountJsonV300 = {
    OtherAccountJsonV300(
      id = bankAccount.id,
      kind = stringOptionOrNull(bankAccount.kind),
      bank_routing = BankRoutingJsonV121(stringOptionOrNull(bankAccount.accountRoutingScheme),stringOptionOrNull(bankAccount.accountRoutingAddress)),
      account_routing = AccountRoutingJsonV121(stringOptionOrNull(bankAccount.accountRoutingScheme),stringOptionOrNull(bankAccount.accountRoutingAddress)),
      metadata = bankAccount.metadata.map(createOtherAccountMetaDataJSON).getOrElse(null)
    )
  }
  
  // following are create core transactions, without the meta data parts
  def createCoreTransactionsJSON(transactions: List[ModeratedTransaction]) : CoreTransactionsJsonV300 = {
    CoreTransactionsJsonV300(transactions.map(createCoreTransactionJSON))
  }

  def createCoreTransactionJSON(transaction : ModeratedTransaction) : CoreTransactionJsonV300 = {
    CoreTransactionJsonV300(
      id = transaction.id.value,
      account = transaction.bankAccount.map(createThisAccountJSON).getOrElse(null),
      counterparty = transaction.otherBankAccount.map(createCoreCounterparty).getOrElse(null),
      details = JSONFactory200.createCoreTransactionDetailsJSON(transaction)
    )
  }
  
  def createCoreCounterparty(bankAccount : ModeratedOtherBankAccount) : CoreCounterpartyJsonV300 = {
    CoreCounterpartyJsonV300(
      id = bankAccount.id,
      bank_routing = BankRoutingJsonV121(stringOptionOrNull(bankAccount.accountRoutingScheme),stringOptionOrNull(bankAccount.accountRoutingAddress)),
      account_routing = AccountRoutingJsonV121(stringOptionOrNull(bankAccount.accountRoutingScheme),stringOptionOrNull(bankAccount.accountRoutingAddress)),
      kind = stringOptionOrNull(bankAccount.kind)
    )
  }
  
  //ended -- Transaction relevant methods /////
  
}