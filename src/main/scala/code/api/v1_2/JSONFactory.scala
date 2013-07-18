/**
Open Bank Project - API
Copyright (C) 2011, 2013, TESOBE / Music Pictures Ltd

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
TESOBE / Music Pictures Ltd
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
package code.api.v1_2

import java.util.Date
import net.liftweb.common.{Box, Full}
import code.model._
import code.api.ExampleValue

import scala.annotation.target._
import java.text.SimpleDateFormat

case class APIInfoJSON(
  version : String,
  git_commit : String,
  hosted_by : HostedBy
)
case class HostedBy(
  organisation : String,
  email : String,
  phone : String
)
case class ErrorMessage(
  error : String
)
case class SuccessMessage(
  success : String
)
case class BanksJSON(
  banks : List[BankJSON]
)
case class MinimalBankJSON(
  @ExampleValue("DHK-4545") national_identifier : String,
  @ExampleValue("Example Bank of Flibbernshire") name : String
)

case class BankJSON(
  @ExampleValue("exbank") id : String,
  @ExampleValue("ExBank") short_name : String,
  @ExampleValue("Example Bank of Flibbernshire") full_name : String,
  @ExampleValue("http://example.com/bank_logo.png") logo : String,
  @ExampleValue("http://example.com") website : String
)
case class ViewsJSON(
  views : List[ViewJSON]
)
case class ViewJSON(
  id : String,
  short_name : String,
  description : String,
  is_public : Boolean
)
case class AccountsJSON(
  accounts : List[AccountJSON]
)
case class AccountJSON(
  id : String,
  label : String,
  views_available : Set[ViewJSON],
  bank_id : String
)
case class ModeratedAccountJSON(
  id : String,
  label : String,
  number : String,
  owners : List[UserJSON],
  `type` : String,
  balance : AmountOfMoneyJSON,
  IBAN : String,
  views_available : Set[ViewJSON],
  bank_id : String
)
case class UserJSON(
  @ExampleValue("pspsDSnDsznzwinhfCaawGbLLcwaDucwIWWabhcw") id : String,
  @ExampleValue("exbank") provider : String,
  @ExampleValue("bob.shelforth@example.com") display_name : String
)
case class PermissionsJSON(
  permissions : List[PermissionJSON]
)
case class PermissionJSON(
  user : UserJSON,
  views : List[ViewJSON]
)
case class AmountOfMoneyJSON(
  @ExampleValue("GBP") currency : String,
  @ExampleValue("325.22") amount : String
)
case class AccountHolderJSON(
  @ExampleValue("Max Mustermann") name : String,
  @ExampleValue(false) is_alias : Boolean
)
case class ThisAccountJSON(
  @ExampleValue("89398d98dh8dh983h983h98") id : String,
  holders : List[AccountHolderJSON],
  @ExampleValue("3243244") number : String,
  @ExampleValue("savings") kind : String,
  @ExampleValue("GB32 0923 4898 4992 0023 23") IBAN : String,
  bank : MinimalBankJSON
)
case class OtherAccountsJSON(
  other_accounts : List[OtherAccountJSON]
)
case class OtherAccountJSON(
  @ExampleValue("994940-ff-484893444444453") id : String,
  holder : AccountHolderJSON,
  @ExampleValue("390029-b") number : String,
  @ExampleValue("current") kind : String,
  @ExampleValue("AU33 6124 3343 0023 55") IBAN : String,
  bank : MinimalBankJSON,
  metadata : OtherAccountMetadataJSON
)
case class OtherAccountMetadataJSON(
  @ExampleValue("Office Supplies Vendor") public_alias : String,
  @ExampleValue("Alfredson Donnington Beringsford Supplies (Montchester Av.) ")private_alias : String,
  @ExampleValue("Our local office supplies provider") more_info : String,
  @ExampleValue("http://example.net") URL : String,
  @ExampleValue("http://example.net/adb_logo.png") image_URL : String,
  @ExampleValue("http://opencorporates.com/companies/gb/04351490") open_corporates_URL : String,
  corporate_location : LocationJSON,
  physical_location : LocationJSON
)
case class LocationJSON(
  @ExampleValue(52.525) latitude : Double,
  @ExampleValue(13.372) longitude : Double,
  @ExampleValue({
    new SimpleDateFormat("yyyy/MM/dd").parse("2011/12/14") : Date
  }) date : Date,
  user : UserJSON
)
case class TransactionDetailsJSON(
  @ExampleValue("wire transfer") `type` : String,
  @ExampleValue("ORDER #993 (STAPLERS)")label : String,
  @ExampleValue({
    new SimpleDateFormat("yyyy/MM/dd").parse("2012/08/24") : Date
  }) posted : Date,
  @ExampleValue({
    new SimpleDateFormat("yyyy/MM/dd").parse("2012/08/25") : Date
  }) completed : Date,
  new_balance : AmountOfMoneyJSON,
  value : AmountOfMoneyJSON
)
case class TransactionMetadataJSON(
  @ExampleValue("Staplers for the internal auditing department") narrative : String,
  comments : List[TransactionCommentJSON],
  tags :  List[TransactionTagJSON],
  images :  List[TransactionImageJSON],
  where : LocationJSON
)
case class TransactionsJSON(
  transactions: List[TransactionJSON]
)
case class TransactionJSON(
  @ExampleValue("hh-398383977929017278729")id : String,
  this_account : ThisAccountJSON,
  other_account : OtherAccountJSON,
  details : TransactionDetailsJSON,
  metadata : TransactionMetadataJSON
)
case class TransactionImagesJSON(
  images : List[TransactionImageJSON]
)
case class TransactionImageJSON(
  @ExampleValue("djx-eie-xf332323") id : String,
  @ExampleValue("One of the staplers") label : String,
  @ExampleValue("http://example.com/uploads/stapler1.jpg") URL : String,
  @ExampleValue({
    new SimpleDateFormat("yyyy/MM/dd").parse("2012/08/24") : Date
  }) date : Date,
  user : UserJSON
)
case class PostTransactionImageJSON(
  label : String,
  URL : String
)
case class PostTransactionCommentJSON(
  value: String
)
case class PostTransactionTagJSON(
  value : String
)
case class TransactionTagJSON(
  @ExampleValue("30928300010101991001001090101") id : String,
  @ExampleValue("supplies") value : String,
  @ExampleValue({
    new SimpleDateFormat("yyyy/MM/dd").parse("2012/08/27") : Date
  }) date : Date,
  user : UserJSON
)
case class TransactionTagsJSON(
  tags: List[TransactionTagJSON]
)
case class TransactionCommentJSON(
  @ExampleValue("390980398--ndsiisums-921849111") id : String,
  @ExampleValue("We got 14 staplers in this batch.") value : String,
  @ExampleValue({
    new SimpleDateFormat("yyyy/MM/dd").parse("2012/08/28") : Date
  }) date: Date,
  user : UserJSON
)
case class TransactionCommentsJSON(
  comments: List[TransactionCommentJSON]
)
case class TransactionWhereJSON(
  where: LocationJSON
)
case class PostTransactionWhereJSON(
  where: LocationPlainJSON
)
case class AliasJSON(
  alias: String
)
case class MoreInfoJSON(
  more_info: String
)
case class UrlJSON(
  URL:String
)
case class ImageUrlJSON(
  image_URL: String
)
case class OpenCorporateUrlJSON(
  open_corporates_URL: String
)
case class CorporateLocationJSON(
  corporate_location: LocationPlainJSON
)
case class PhysicalLocationJSON(
  physical_location: LocationPlainJSON
)
case class LocationPlainJSON(
  latitude : Double,
  longitude : Double
)
case class TransactionNarrativeJSON(
  narrative : String
)

case class ViewIdsJson(
  views : List[String]
)

object JSONFactory{
  def stringOrNull(text : String) =
    if(text.isEmpty)
      null
    else
      text

  def stringOptionOrNull(text : Option[String]) =
    text match {
      case Some(t) => stringOrNull(t)
      case _ => null
    }

  def createBankJSON(bank : Bank) : BankJSON = {
    new BankJSON(
      stringOrNull(bank.permalink),
      stringOrNull(bank.shortName),
      stringOrNull(bank.fullName),
      stringOrNull(bank.logoURL),
      stringOrNull(bank.website)
    )
  }

  def createViewsJSON(views : List[View]) : ViewsJSON = {
    val list : List[ViewJSON] = views.map(createViewJSON)
    new ViewsJSON(list)
  }

  def createViewJSON(view : View) : ViewJSON = {
    new ViewJSON(
      view.permalink,
      stringOrNull(view.name),
      stringOrNull(view.description),
      view.isPublic
    )
  }

  def createAccountJSON(account : BankAccount, viewsAvailable : Set[ViewJSON] ) : AccountJSON = {
    new AccountJSON(
      account.permalink,
      stringOrNull(account.label),
      viewsAvailable,
      account.bankPermalink
    )
  }

  def createBankAccountJSON(account : ModeratedBankAccount, viewsAvailable : Set[ViewJSON]) : ModeratedAccountJSON =  {
    val bankName = account.bankName.getOrElse("")
    new ModeratedAccountJSON(
      account.id,
      stringOptionOrNull(account.label),
      stringOptionOrNull(account.number),
      createOwnersJSON(account.owners.getOrElse(Set()), bankName),
      stringOptionOrNull(account.accountType),
      createAmountOfMoneyJSON(account.currency.getOrElse(""), account.balance),
      stringOptionOrNull(account.iban),
      viewsAvailable,
      stringOptionOrNull(account.bankPermalink)
    )
  }

  def createTransactionsJSON(transactions: List[ModeratedTransaction]) : TransactionsJSON = {
    new TransactionsJSON(transactions.map(createTransactionJSON))
  }

  def createTransactionJSON(transaction : ModeratedTransaction) : TransactionJSON = {
    new TransactionJSON(
        id = transaction.id,
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

  def createTransactionTagsJSON(tags : List[Tag]) : TransactionTagsJSON = {
    new TransactionTagsJSON(tags.map(createTransactionTagJSON))
  }

  def createTransactionTagJSON(tag : Tag) : TransactionTagJSON = {
    new TransactionTagJSON(
      id = tag.id_,
      value = tag.value,
      date = tag.datePosted,
      user = createUserJSON(tag.postedBy)
    )
  }

  def createLocationJSON(location : GeoTag) : LocationJSON = {
    val user = createUserJSON(location.postedBy)
    //test if the GeoTag is set to its default value
    if(location.latitude == 0.0 & location.longitude == 0.0 & user == null)
      null
    else
      new LocationJSON(
        latitude = location.latitude,
        longitude = location.longitude,
        date = location.datePosted,
        user = user
      )
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
      label = stringOptionOrNull(transaction.label),
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

  def createThisAccountJSON(bankAccount : ModeratedBankAccount) : ThisAccountJSON = {
    new ThisAccountJSON(
      id = bankAccount.id,
      number = stringOptionOrNull(bankAccount.number),
      kind = stringOptionOrNull(bankAccount.accountType),
      IBAN = stringOptionOrNull(bankAccount.iban),
      bank = createMinimalBankJSON(bankAccount),
      holders = null //TODO //bankAccount.owners.map(x => x.toList.map(h => createAccountHolderJSON(h, ??))).getOrElse(null)
    )
  }

  def createAccountHolderJSON(owner : AccountOwner, isAlias : Boolean) : AccountHolderJSON = {
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

  def createOtherBankAccount(bankAccount : ModeratedOtherBankAccount) : OtherAccountJSON = {
    new OtherAccountJSON(
      id = bankAccount.id,
      number = stringOptionOrNull(bankAccount.number),
      kind = stringOptionOrNull(bankAccount.kind),
      IBAN = stringOptionOrNull(bankAccount.iban),
      bank = createMinimalBankJSON(bankAccount),
      holder = createAccountHolderJSON(bankAccount.label.display, bankAccount.isAlias),
      metadata = bankAccount.metadata.map(createOtherAccountMetaDataJSON).getOrElse(null)
    )
  }

  def createOtherBankAccountsJSON(otherBankAccounts : List[ModeratedOtherBankAccount]) : OtherAccountsJSON =  {
    val otherAccountsJSON : List[OtherAccountJSON] = otherBankAccounts.map(createOtherBankAccount)
    OtherAccountsJSON(otherAccountsJSON)
  }

  def createUserJSON(user : User) : UserJSON = {
    new UserJSON(
          user.id_,
          stringOrNull(user.provider),
          stringOrNull(user.emailAddress)
        )
  }

  def createUserJSON(user : Box[User]) : UserJSON = {
    user match {
      case Full(u) => createUserJSON(u)
      case _ => null
    }
  }

  def createOwnersJSON(owners : Set[AccountOwner], bankName : String) : List[UserJSON] = {
    owners.map(o => {
        new UserJSON(
          o.id,
          stringOrNull(bankName),
          stringOrNull(o.name)
        )
      }
    ).toList
  }

  def createAmountOfMoneyJSON(currency : String, amount  : String) : AmountOfMoneyJSON = {
    new AmountOfMoneyJSON(
      stringOrNull(currency),
      stringOrNull(amount)
    )
  }

  def createAmountOfMoneyJSON(currency : Option[String], amount  : Option[String]) : AmountOfMoneyJSON = {
    new AmountOfMoneyJSON(
      stringOptionOrNull(currency),
      stringOptionOrNull(amount)
    )
  }

  def createAmountOfMoneyJSON(currency : Option[String], amount  : String) : AmountOfMoneyJSON = {
    new AmountOfMoneyJSON(
      stringOptionOrNull(currency),
      stringOrNull(amount)
    )
  }

  def createPermissionsJSON(permissions : List[Permission]) : PermissionsJSON = {
    val permissionsJson = permissions.map(p => {
        new PermissionJSON(
          createUserJSON(p.user),
          p.views.map(createViewJSON)
        )
      })
    new PermissionsJSON(permissionsJson)
  }

  def createAliasJSON(alias: String): AliasJSON = {
    AliasJSON(stringOrNull(alias))
  }

  def createTransactionNarrativeJSON(narrative: String): TransactionNarrativeJSON = {
    TransactionNarrativeJSON(stringOrNull(narrative))
  }

}