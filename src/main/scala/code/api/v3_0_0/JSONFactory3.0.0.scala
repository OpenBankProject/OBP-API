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

import code.api.v1_2_1._
import code.model._
import net.liftweb.common.{Box, Full}
import code.api.v1_2_1.JSONFactory._

case class TransactionsJsonV300(
  transactions: List[TransactionJsonV300]
)

case class OtherAccountJsonV300(
  id : String,
  holder : AccountHolderJSON,
  number : String,
  kind : String,
  bank : MinimalBankJSON,
  metadata : OtherAccountMetadataJSON
)
case class OtherAccountsJsonV300(
  other_accounts : List[OtherAccountJsonV300]
)
case class ThisAccountJsonV300(
  id : String,
  holders : List[AccountHolderJSON],
  number : String,
  kind : String,
  bank : MinimalBankJSON
)

case class TransactionJsonV300(
  id : String,
  this_account : ThisAccountJsonV300,
  other_account : OtherAccountJsonV300,
  details : TransactionDetailsJSON,
  metadata : TransactionMetadataJSON
)

object JSONFactory300{
  
  def stringOrNull(text : String) =
    if(text == null || text.isEmpty)
      null
    else
      text
  
  def stringOptionOrNull(text : Option[String]) =
    text match {
      case Some(t) => stringOrNull(t)
      case _ => null
    }
  
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
  
  def createTransactionCommentsJSON(comments : List[Comment]) : TransactionCommentsJSON = {
    new TransactionCommentsJSON(comments.map(createTransactionCommentJSON))
  }
  
  def createTransactionCommentJSON(comment : Comment) : TransactionCommentJSON = {
    new TransactionCommentJSON(
      id = comment.id_,
      value = comment.text,
      date = comment.datePosted,
      user = createUserJSON(comment.postedBy)
    )
  }
  
  def createTransactionImagesJSON(images : List[TransactionImage]) : TransactionImagesJSON = {
    new TransactionImagesJSON(images.map(createTransactionImageJSON))
  }
  
  def createTransactionImageJSON(image : TransactionImage) : TransactionImageJSON = {
    new TransactionImageJSON(
      id = image.id_,
      label = image.description,
      URL = image.imageUrl.toString,
      date = image.datePosted,
      user = createUserJSON(image.postedBy)
    )
  }
  
  def createTransactionTagsJSON(tags : List[TransactionTag]) : TransactionTagsJSON = {
    new TransactionTagsJSON(tags.map(createTransactionTagJSON))
  }
  
  def createTransactionTagJSON(tag : TransactionTag) : TransactionTagJSON = {
    new TransactionTagJSON(
      id = tag.id_,
      value = tag.value,
      date = tag.datePosted,
      user = createUserJSON(tag.postedBy)
    )
  }
  
  def createLocationJSON(loc : Option[GeoTag]) : LocationJSONV121 = {
    loc match {
      case Some(location) => {
        val user = createUserJSON(location.postedBy)
        //test if the GeoTag is set to its default value
        if(location.latitude == 0.0 & location.longitude == 0.0 & user == null)
          null
        else
          new LocationJSONV121(
            latitude = location.latitude,
            longitude = location.longitude,
            date = location.datePosted,
            user = user
          )
      }
      case _ => null
    }
  }
  
  def createLocationPlainJSON(lat: Double, lon: Double) : LocationPlainJSON = {
    new LocationPlainJSON(
      latitude = lat,
      longitude = lon
    )
  }
  
  def createTransactionMetadataJSON(metadata : ModeratedTransactionMetadata) : TransactionMetadataJSON = {
    new TransactionMetadataJSON(
      narrative = stringOptionOrNull(metadata.ownerComment),
      comments = metadata.comments.map(_.map(createTransactionCommentJSON)).getOrElse(null),
      tags = metadata.tags.map(_.map(createTransactionTagJSON)).getOrElse(null),
      images = metadata.images.map(_.map(createTransactionImageJSON)).getOrElse(null),
      where = metadata.whereTag.map(createLocationJSON).getOrElse(null)
    )
  }
  
  def createTransactionDetailsJSON(transaction : ModeratedTransaction) : TransactionDetailsJSON = {
    new TransactionDetailsJSON(
      `type` = stringOptionOrNull(transaction.transactionType),
      description = stringOptionOrNull(transaction.description),
      posted = transaction.startDate.getOrElse(null),
      completed = transaction.finishDate.getOrElse(null),
      new_balance = createAmountOfMoneyJSON(transaction.currency, transaction.balance),
      value= createAmountOfMoneyJSON(transaction.currency, transaction.amount.map(_.toString))
    )
  }
  
  def createMinimalBankJSON(bankAccount : ModeratedBankAccount) : MinimalBankJSON = {
    new MinimalBankJSON(
      national_identifier = stringOptionOrNull(bankAccount.nationalIdentifier),
      name = stringOptionOrNull(bankAccount.bankName)
    )
  }
  
  def createMinimalBankJSON(bankAccount : ModeratedOtherBankAccount) : MinimalBankJSON = {
    new MinimalBankJSON(
      national_identifier = stringOptionOrNull(bankAccount.nationalIdentifier),
      name = stringOptionOrNull(bankAccount.bankName)
    )
  }
  
  def createThisAccountJSON(bankAccount : ModeratedBankAccount) : ThisAccountJsonV300 = {
    new ThisAccountJsonV300(
      id = bankAccount.accountId.value,
      number = stringOptionOrNull(bankAccount.number),
      kind = stringOptionOrNull(bankAccount.accountType),
      bank = createMinimalBankJSON(bankAccount),
      holders = bankAccount.owners.map(x => x.toList.map(h => createAccountHolderJSON(h, false))).getOrElse(null)
    )
  }
  
  def createAccountHolderJSON(owner : User, isAlias : Boolean) : AccountHolderJSON = {
    new AccountHolderJSON(
      name = owner.name,
      is_alias = isAlias
    )
  }
  
  def createAccountHolderJSON(name : String, isAlias : Boolean) : AccountHolderJSON = {
    new AccountHolderJSON(
      name = name,
      is_alias = isAlias
    )
  }
  
  def createOtherAccountMetaDataJSON(metadata : ModeratedOtherBankAccountMetadata) : OtherAccountMetadataJSON = {
    new OtherAccountMetadataJSON(
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
    new OtherAccountJsonV300(
      id = bankAccount.id,
      number = stringOptionOrNull(bankAccount.number),
      kind = stringOptionOrNull(bankAccount.kind),
      bank = createMinimalBankJSON(bankAccount),
      holder = createAccountHolderJSON(bankAccount.label.display, bankAccount.isAlias),
      metadata = bankAccount.metadata.map(createOtherAccountMetaDataJSON).getOrElse(null)
    )
  }
  
  def createOtherBankAccountsJSON(otherBankAccounts : List[ModeratedOtherBankAccount]) : OtherAccountsJsonV300 =  {
    val otherAccountsJSON : List[OtherAccountJsonV300] = otherBankAccounts.map(createOtherBankAccount)
    OtherAccountsJsonV300(otherAccountsJSON)
  }
  
  def createUserJSON(user : User) : UserJSONV121 = {
    new UserJSONV121(
      user.idGivenByProvider,
      stringOrNull(user.provider),
      stringOrNull(user.emailAddress) //TODO: shouldn't this be the display name?
    )
  }
  
  def createUserJSON(user : Box[User]) : UserJSONV121 = {
    user match {
      case Full(u) => createUserJSON(u)
      case _ => null
    }
  }
  
  def createOwnersJSON(owners : Set[User], bankName : String) : List[UserJSONV121] = {
    owners.map(o => {
      new UserJSONV121(
        o.idGivenByProvider,
        stringOrNull(o.provider),
        stringOrNull(o.name)
      )
    }
    ).toList
  }
  
  def createAmountOfMoneyJSON(currency : String, amount  : String) : AmountOfMoneyJsonV121 = {
    new AmountOfMoneyJsonV121(
      stringOrNull(currency),
      stringOrNull(amount)
    )
  }
  
  def createAmountOfMoneyJSON(currency : Option[String], amount  : Option[String]) : AmountOfMoneyJsonV121 = {
    new AmountOfMoneyJsonV121(
      stringOptionOrNull(currency),
      stringOptionOrNull(amount)
    )
  }
  
  def createAmountOfMoneyJSON(currency : Option[String], amount  : String) : AmountOfMoneyJsonV121 = {
    new AmountOfMoneyJsonV121(
      stringOptionOrNull(currency),
      stringOrNull(amount)
    )
  }
  
}