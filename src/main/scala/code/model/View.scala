/**
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd

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


package code.model

import java.util.Date

import code.api.util.ErrorMessages
import code.metadata.counterparties.Counterparties
import code.util.Helper
import net.liftweb.common._
import code.views.Views
import net.liftweb.json.JsonDSL._
import net.liftweb.json.JsonAST.JObject


class AliasType
class Alias extends AliasType
object PublicAlias extends Alias
object PrivateAlias extends Alias
object NoAlias extends AliasType
case class AccountName(display: String, aliasType: AliasType)
case class Permission(
  user : User,
  views : List[View]
)


/*
View Specification
Defines how the View should be named, i.e. if it is public, the Alias behaviour, what fields can be seen and what actions can be done through it.
 */
trait ViewSpecification {
  def description: String
  def metadata_view: String
  def is_public: Boolean
  def which_alias_to_use: String
  def hide_metadata_if_alias_used: Boolean
  def allowed_actions : List[String]
}

/*
The JSON that should be supplied to create a view. Conforms to ViewSpecification
 */
case class CreateViewJson(
  name: String,
  description: String,
  metadata_view: String,
  is_public: Boolean,
  which_alias_to_use: String,
  hide_metadata_if_alias_used: Boolean,
  allowed_actions : List[String]
) extends ViewSpecification

/*
The JSON that should be supplied to update a view. Conforms to ViewSpecification
 */
case class UpdateViewJSON(
  description: String,
  metadata_view: String,
  is_public: Boolean,
  which_alias_to_use: String,
  hide_metadata_if_alias_used: Boolean,
  allowed_actions: List[String]) extends ViewSpecification



/** Views moderate access to an Account. That is, they are used to:
  * 1) Show/hide fields on the account, its transactions and related counterparties
  * 2) Store/partition meta data  - e.g. comments posted on a "team" view are not visible via a "public" view and visa versa.
  *
  * Users can be granted access to one or more Views
  * Each View has a set of entitlements aka permissions which hide / show data fields and enable / disable operations on the account
  *
  * @define viewId A short url friendly, (singular) human readable name for the view. e.g. "team", "auditor" or "public". Note: "owner" is a default and reserved name. Other reserved names should include "public", "accountant" and "auditor"
  * @define accountId The account that the view moderates
  * @define bankId The bank where the account is held
  * @define name The name of the view
  * @define description A description of the view
  * @define isPublic Set to True if the view should be open to the public (no authorisation required!) Set to False to require authorisation
  * @define users A list of users that can use this view
  * @define usePublicAliasIfOneExists If true and the counterparty in a transaction has a public alias set, use it. Else use the raw counterparty name (if both usePublicAliasIfOneExists and usePrivateAliasIfOneExists are true, public alias will be used)
  * @define usePrivateAliasIfOneExists If true and the counterparty in a transaction has a private alias set, use it. Else use the raw counterparty name (if both usePublicAliasIfOneExists and usePrivateAliasIfOneExists are true, public alias will be used)
  * @define hideOtherAccountMetadataIfAlias If true, the view will hide counterparty metadata if the counterparty has an alias. This is to preserve anonymity if required.
  *
  * @define canSeeTransactionThisBankAccount If true, the view will show information about the Transaction account (this account)
  * @define canSeeTransactionOtherBankAccount If true, the view will show information about the Transaction counterparty
  * @define canSeeTransactionMetadata If true, the view will show any Transaction metadata
  * @define canSeeTransactionDescription If true, the view will show the Transaction description
  * @define canSeeTransactionAmount If true, the view will show the Transaction amount (value, not currency)
  * @define canSeeTransactionType If true, the view will show the Transaction type
  * @define canSeeTransactionCurrency If true, the view will show the Transaction currency (not value)
  * @define canSeeTransactionStartDate If true, the view will show the Transaction start date
  * @define canSeeTransactionFinishDate If true, the view will show the Transaction finish date
  * @define canSeeTransactionBalance If true, the view will show the Transaction balance (after each transaction)
  *
  * @define canSeeComments If true, the view will show the Transaction Metadata comments
  * @define canSeeOwnerComment If true, the view will show the Transaction Metadata owner comment
  * @define canSeeTags If true, the view will show the Transaction Metadata tags
  * @define canSeeImages If true, the view will show the Transaction Metadata images

  * @define canSeeBankAccountOwners If true, the view will show the Account owners
  * @define canSeeBankAccountType If true, the view will show the Account type. The account type is a human friendly financial product name
  * @define canSeeBankAccountBalance If true, the view will show the Account balance
  * @define canSeeBankAccountCurrency If true, the view will show the Account currency
  * @define canSeeBankAccountLabel If true, the view will show the Account label. The label can be edited via the API. It does not come from the core banking system.
  * @define canSeeBankAccountNationalIdentifier If true, the view will show the national identifier of the bank
  * @define canSeeBankAccountSwift_bic If true, the view will show the Swift / Bic code of the bank
  * @define canSeeBankAccountIban If true, the view will show the IBAN
  * @define canSeeBankAccountNumber If true, the view will show the account number
  * @define canSeeBankAccountBankName If true, the view will show the bank name
  * @define canSeeBankRoutingScheme If true, the view will show the BankRoutingScheme 
  * @define canSeeBankRoutingAddress If true, the view will show the BankRoutingAddress 
  * @define canSeeBankAccountRoutingScheme If true, the view will show the BankAccountRoutingScheme 
  * @define canSeeBankAccountRoutingAddress If true, the view will show the BankAccountRoutingAddress 

  * @define canSeeOtherAccountNationalIdentifier If true, the view will show the Counterparty bank national identifier
  * @define canSeeOtherAccountSWIFT_BIC If true, the view will show the Counterparty SWIFT BIC
  * @define canSeeOtherAccountIBAN If true, the view will show the Counterparty IBAN
  * @define canSeeOtherAccountBankName If true, the view will show the Counterparty Bank Name
  * @define canSeeOtherAccountNumber If true, the view will show the Counterparty Account Number
  * @define canSeeOtherAccountMetadata If true, the view will show the Counterparty Metadata
  * @define canSeeOtherAccountKind If true, the view will show the Counterparty Account Type. This is unlikely to be a full financial product name.
  * @define canSeeOtherBankRoutingScheme If true, the view will show the OtherBankRoutingScheme       
  * @define canSeeOtherBankRoutingAddress If true, the view will show the OtherBankRoutingScheme       
  * @define canSeeOtherAccountRoutingScheme If true, the view will show the OtherBankRoutingScheme     
  * @define canSeeOtherAccountRoutingAddress If true, the view will show the OtherBankRoutingScheme     

  * @define canSeeMoreInfo If true, the view will show the Counterparty More Info text
  * @define canSeeUrl If true, the view will show the Counterparty Url
  * @define canSeeImageUrl If true, the view will show the Counterparty Image Url
  * @define canSeeOpenCorporatesUrl If true, the view will show the Counterparty OpenCorporatesUrl
  * @define canSeeCorporateLocation If true, the view will show the Counterparty CorporateLocation
  * @define canSeePhysicalLocation If true, the view will show the Counterparty PhysicalLocation
  * @define canSeePublicAlias If true, the view will show the Counterparty PublicAlias
  * @define canSeePrivateAlias If true, the view will show the Counterparty PrivateAlias
  *
  * @define canAddMoreInfo If true, the view can add the Counterparty MoreInfo
  * @define canAddURL If true, the view can add the Counterparty Url
  * @define canAddImageURL If true, the view can add the Counterparty Image Url
  * @define canAddOpenCorporatesUrl If true, the view can add the Counterparty OpenCorporatesUrl
  * @define canAddCorporateLocation If true, the view can add the Counterparty CorporateLocation
  * @define canAddPhysicalLocation If true, the view can add the Counterparty PhysicalLocation
  * @define canAddPublicAlias If true, the view can add the Counterparty PublicAlias
  * @define canAddPrivateAlias  If true, the view can add the Counterparty PrivateAlias
  * @define canDeleteCorporateLocation If true, the can add show the Counterparty CorporateLocation
  * @define canDeletePhysicalLocation If true, the can add show the Counterparty PhysicalLocation
  *
  * @define canEditOwnerComment If true, the view can edit the Transaction Owner Comment
  * @define canAddComment If true, the view can add a Transaction Comment
  * @define canDeleteComment If true, the view can delete a Transaction Comment
  * @define canAddTag If true, the view can add a Transaction Tag
  * @define canDeleteTag If true, the view can delete a Transaction Tag
  * @define canAddImage If true, the view can add a Transaction Image
  * @define canDeleteImage If true, the view can delete a Transaction Image
  * @define canAddWhereTag If true, the view can add a Transaction Where Tag
  * @define canSeeWhereTag If true, the view can show the Transaction Where Tag
  * @define canDeleteWhereTag If true, the view can delete the Transaction Where Tag

  * @define canAddCounterparty If true, view can add counterparty / create counterparty.


  */


trait View {

  val viewLogger = Logger(classOf[View])

  // metedataView is tricky, it used for all the transaction meta in different views share the same metadataView.
  // we create, get, update transaction.meta call the deufault metadataView. Not the currentView.
  // eg: If current view is _tesobe, you set metadataView is `owner`, then all the transaction.meta will just point to `owner` view.
  // Look into the following method in code, you will know more about it: 
  //  code.metadata.wheretags.MapperWhereTags.addWhereTag 
  //  val metadateViewId = Views.views.vend.getMetadataViewId(BankIdAccountId(bankId, accountId), viewId)
  def metadataView : String
  
  //This is used for distinguishing all the views
  //For now, we need have some system views and user created views.
  // 1 `System Views` : eg: owner, accountant ... They are the fixed views, developers can not modify it. 
  // 2 `User Created Views`: Start with _, eg _son, _wife ... The developers can update the fields for these views. 
  def isSystem : Boolean
  def isFirehose : Boolean
  def isPublic : Boolean
  def isPrivate : Boolean
  
  //these three Ids are used together to uniquely identify a view: 
  // eg: a view = viewId(`owner`) + accountId('e4f001fe-0f0d-4f93-a8b2-d865077315ec')+bankId('gh.29.uk')
  // the viewId is not OBP uuid here, view.viewId is view.name without spaces and lowerCase.  (view.name = my life) <---> (view-permalink = mylife)
  // aslo @code.views.MapperViews.createView see how we create the viewId.  
  def viewId : ViewId
  def accountId : AccountId
  def bankId : BankId

  //and here is the unique identifier
  def uid : ViewIdBankIdAccountId = ViewIdBankIdAccountId(viewId, bankId, accountId)

  //The name is the orignal value from developer, when they create the views.
  // It can be any string value, also see the viewId,
  // the viewId is not OBP uuid here, view.viewId is view.name without spaces and lowerCase.  (view.name = my life) <---> (view-permalink = mylife)
  // aslo @code.views.MapperViews.createView see how we create the viewId.  
  def name: String
  //the Value from developer, can be any string value.
  def description : String
  
  /**This users is tricky, this use ManyToMany relationship, 
   1st: when create view, we need carefully map this view to the owner user. 
   2rd: the view can grant the access to any other (not owner) users. eg: Simon's accountant view can grant access to Carola, then Carola can see Simon's accountant data 
   also look into some createView methods in code, you can understand more: 
      create1: code.bankconnectors.Connector.createViews 
               need also look into here code.bankconnectors.vMar2017.KafkaMappedConnector_vMar2017.updateUserAccountViewsOld
                    after createViews method, always need call addPermission(v.uid, user). This will create this field
      Create2: code.model.dataAccess.BankAccountCreation.createOwnerView
                    after create view, always need call `addPermission(ownerViewUID, user)`, this will create this field
      create3: code.model.dataAccess.AuthUser#updateUserAccountViews
                    after create view, always need call `getOrCreateViewPrivilege(view,user)`, this will create this filed
    Both uses should be in this List.
    */
  def users: List[User]

  //the view settings
  def usePublicAliasIfOneExists: Boolean
  def usePrivateAliasIfOneExists: Boolean
  def hideOtherAccountMetadataIfAlias: Boolean

  //reading access

  //transaction fields
  def canSeeTransactionThisBankAccount : Boolean
  def canSeeTransactionOtherBankAccount : Boolean
  def canSeeTransactionMetadata : Boolean
  def canSeeTransactionDescription: Boolean
  def canSeeTransactionAmount: Boolean
  def canSeeTransactionType: Boolean
  def canSeeTransactionCurrency: Boolean
  def canSeeTransactionStartDate: Boolean
  def canSeeTransactionFinishDate: Boolean
  def canSeeTransactionBalance: Boolean

  //transaction metadata
  def canSeeComments: Boolean
  def canSeeOwnerComment: Boolean
  def canSeeTags : Boolean
  def canSeeImages : Boolean

  //Bank account fields
  def canSeeBankAccountOwners : Boolean
  def canSeeBankAccountType : Boolean
  def canSeeBankAccountBalance : Boolean
  def canQueryAvailableFunds : Boolean
  def canSeeBankAccountCurrency : Boolean
  def canSeeBankAccountLabel : Boolean
  def canSeeBankAccountNationalIdentifier : Boolean
  def canSeeBankAccountSwift_bic : Boolean
  def canSeeBankAccountIban : Boolean
  def canSeeBankAccountNumber : Boolean
  def canSeeBankAccountBankName : Boolean
  def canSeeBankRoutingScheme : Boolean
  def canSeeBankRoutingAddress : Boolean
  def canSeeBankAccountRoutingScheme : Boolean
  def canSeeBankAccountRoutingAddress : Boolean

  //other bank account (counterparty) fields
  def canSeeOtherAccountNationalIdentifier : Boolean
  def canSeeOtherAccountSWIFT_BIC : Boolean
  def canSeeOtherAccountIBAN : Boolean
  def canSeeOtherAccountBankName : Boolean
  def canSeeOtherAccountNumber : Boolean
  def canSeeOtherAccountMetadata : Boolean
  def canSeeOtherAccountKind : Boolean
  def canSeeOtherBankRoutingScheme : Boolean
  def canSeeOtherBankRoutingAddress : Boolean
  def canSeeOtherAccountRoutingScheme : Boolean
  def canSeeOtherAccountRoutingAddress : Boolean

  //other bank account meta data - read
  def canSeeMoreInfo: Boolean
  def canSeeUrl: Boolean
  def canSeeImageUrl: Boolean
  def canSeeOpenCorporatesUrl: Boolean
  def canSeeCorporateLocation : Boolean
  def canSeePhysicalLocation : Boolean
  def canSeePublicAlias : Boolean
  def canSeePrivateAlias : Boolean

  //other bank account (Counterparty) meta data - write
  def canAddMoreInfo : Boolean
  def canAddURL : Boolean
  def canAddImageURL : Boolean
  def canAddOpenCorporatesUrl : Boolean
  def canAddCorporateLocation : Boolean
  def canAddPhysicalLocation : Boolean
  def canAddPublicAlias : Boolean
  def canAddPrivateAlias : Boolean
  def canAddCounterparty : Boolean
  def canDeleteCorporateLocation : Boolean
  def canDeletePhysicalLocation : Boolean

  //writing access
  def canEditOwnerComment: Boolean
  def canAddComment : Boolean
  def canDeleteComment: Boolean
  def canAddTag : Boolean
  def canDeleteTag : Boolean
  def canAddImage : Boolean
  def canDeleteImage : Boolean
  def canAddWhereTag : Boolean
  def canSeeWhereTag : Boolean
  def canDeleteWhereTag : Boolean

  def canAddTransactionRequestToOwnAccount: Boolean   //added following two for payments
  def canAddTransactionRequestToAnyAccount: Boolean  
  def canSeeBankAccountCreditLimit: Boolean

  def moderateTransaction(transaction : Transaction): Box[ModeratedTransaction] = {
    moderateTransactionUsingModeratedAccount(transaction, moderateAccount(transaction.thisAccount))
  }

  // In the future we can add a method here to allow someone to show only transactions over a certain limit
  private def moderateTransactionUsingModeratedAccount(transaction: Transaction, moderatedAccount : Option[ModeratedBankAccount]): Box[ModeratedTransaction] = {

    lazy val moderatedTransaction = {
      //transaction data
      val transactionId = transaction.id
      val transactionUUID = transaction.uuid
      val otherBankAccount = moderateOtherAccount(transaction.otherAccount)

      //transaction metadata
      val transactionMetadata =
        if(canSeeTransactionMetadata)
        {
          val ownerComment = if (canSeeOwnerComment) Some(transaction.metadata.ownerComment()) else None
          val comments =
            if (canSeeComments)
              Some(transaction.metadata.comments(viewId))
            else None
          val addCommentFunc= if(canAddComment) Some(transaction.metadata.addComment) else None
          val deleteCommentFunc =
            if(canDeleteComment)
              Some(transaction.metadata.deleteComment)
            else
              None
          val addOwnerCommentFunc:Option[String=> Boolean] = if (canEditOwnerComment) Some(transaction.metadata.addOwnerComment) else None
          val tags =
            if(canSeeTags)
              Some(transaction.metadata.tags(viewId))
            else None
          val addTagFunc =
            if(canAddTag)
              Some(transaction.metadata.addTag)
            else
              None
          val deleteTagFunc =
            if(canDeleteTag)
              Some(transaction.metadata.deleteTag)
            else
              None
          val images =
            if(canSeeImages) Some(transaction.metadata.images(viewId))
            else None

          val addImageFunc =
            if(canAddImage) Some(transaction.metadata.addImage)
            else None

          val deleteImageFunc =
            if(canDeleteImage) Some(transaction.metadata.deleteImage)
            else None

          val whereTag =
            if(canSeeWhereTag)
              Some(transaction.metadata.whereTags(viewId))
            else
              None

          val addWhereTagFunc : Option[(UserPrimaryKey, ViewId, Date, Double, Double) => Boolean] =
            if(canAddWhereTag)
              Some(transaction.metadata.addWhereTag)
            else
              Empty

          val deleteWhereTagFunc : Option[(ViewId) => Boolean] =
            if (canDeleteWhereTag)
              Some(transaction.metadata.deleteWhereTag)
            else
              Empty


          new Some(
            new ModeratedTransactionMetadata(
              ownerComment = ownerComment,
              addOwnerComment = addOwnerCommentFunc,
              comments = comments,
              addComment = addCommentFunc,
              deleteComment = deleteCommentFunc,
              tags = tags,
              addTag = addTagFunc,
              deleteTag = deleteTagFunc,
              images = images,
              addImage = addImageFunc,
              deleteImage = deleteImageFunc,
              whereTag = whereTag,
              addWhereTag = addWhereTagFunc,
              deleteWhereTag = deleteWhereTagFunc
            )
          )
        }
        else
          None

      val transactionType =
        if (canSeeTransactionType) Some(transaction.transactionType)
        else None

      val transactionAmount =
        if (canSeeTransactionAmount) Some(transaction.amount)
        else None

      val transactionCurrency =
        if (canSeeTransactionCurrency) Some(transaction.currency)
        else None

      val transactionDescription =
        if (canSeeTransactionDescription) transaction.description
        else None

      val transactionStartDate =
        if (canSeeTransactionStartDate) Some(transaction.startDate)
        else None

      val transactionFinishDate =
        if (canSeeTransactionFinishDate) Some(transaction.finishDate)
        else None

      val transactionBalance =
        if (canSeeTransactionBalance) transaction.balance.toString()
        else ""

      new ModeratedTransaction(
                                UUID = transactionUUID,
                                id = transactionId,
                                bankAccount = moderatedAccount,
                                otherBankAccount = otherBankAccount,
                                metadata = transactionMetadata,
                                transactionType = transactionType,
                                amount = transactionAmount,
                                currency = transactionCurrency,
                                description = transactionDescription,
                                startDate = transactionStartDate,
                                finishDate = transactionFinishDate,
                                balance = transactionBalance
      )
    }


    val belongsToModeratedAccount : Boolean = moderatedAccount match {
      case Some(acc) => acc.accountId == transaction.accountId && acc.bankId == transaction.bankId
      case None => true
    }

    if(!belongsToModeratedAccount) {
      val failMsg = "Attempted to moderate a transaction using the incorrect moderated account"
      viewLogger.warn(failMsg)
      Failure(failMsg)
    } else {
      Full(moderatedTransaction)
    }

  }

  private def moderateCore(transactionCore: TransactionCore, moderatedAccount : Option[ModeratedBankAccount]): Box[ModeratedTransactionCore] = {

    lazy val moderatedTransaction = {
      //transaction data
      val transactionId = transactionCore.id
      val otherBankAccount = moderateCore(transactionCore.otherAccount)

      val transactionType =
        if (canSeeTransactionType) Some(transactionCore.transactionType)
        else None

      val transactionAmount =
        if (canSeeTransactionAmount) Some(transactionCore.amount)
        else None

      val transactionCurrency =
        if (canSeeTransactionCurrency) Some(transactionCore.currency)
        else None

      val transactionDescription =
        if (canSeeTransactionDescription) transactionCore.description
        else None

      val transactionStartDate =
        if (canSeeTransactionStartDate) Some(transactionCore.startDate)
        else None

      val transactionFinishDate =
        if (canSeeTransactionFinishDate) Some(transactionCore.finishDate)
        else None

      val transactionBalance =
        if (canSeeTransactionBalance) transactionCore.balance.toString()
        else ""

      new ModeratedTransactionCore(
        id = transactionId,
        bankAccount = moderatedAccount,
        otherBankAccount = otherBankAccount,
        transactionType = transactionType,
        amount = transactionAmount,
        currency = transactionCurrency,
        description = transactionDescription,
        startDate = transactionStartDate,
        finishDate = transactionFinishDate,
        balance = transactionBalance
      )
    }


    val belongsToModeratedAccount : Boolean = moderatedAccount match {
      case Some(acc) => acc.accountId == transactionCore.thisAccount.accountId && acc.bankId == transactionCore.thisAccount.bankId
      case None => true
    }

    if(!belongsToModeratedAccount) {
      val failMsg = "Attempted to moderate a transaction using the incorrect moderated account"
      viewLogger.warn(failMsg)
      Failure(failMsg)
    } else {
      Full(moderatedTransaction)
    }

  }


  def moderateTransactionsWithSameAccount(transactions : List[Transaction]) : Box[List[ModeratedTransaction]] = {

    val accountUids = transactions.map(t => BankIdAccountId(t.bankId, t.accountId))

    // This function will only accept transactions which have the same This Account.
    if(accountUids.toSet.size > 1) {
      viewLogger.warn("Attempted to moderate transactions not belonging to the same account in a call where they should")
      Failure("Could not moderate transactions as they do not all belong to the same account")
    } else {
      transactions.headOption match {
        case Some(firstTransaction) =>
          // Moderate the *This Account* based on the first transaction, Because all the transactions share the same thisAccount. So we only need modetaed one account is enough for all the transctions.
          val moderatedAccount = moderateAccount(firstTransaction.thisAccount)
          // Moderate each *Transaction* based on the moderated Account
          Full(transactions.flatMap(transaction => moderateTransactionUsingModeratedAccount(transaction, moderatedAccount)))
        case None =>
          Full(Nil)
      }
    }
  }

  def moderateTransactionsWithSameAccountCore(transactionsCore : List[TransactionCore]) : Box[List[ModeratedTransactionCore]] = {

    val accountUids = transactionsCore.map(t => BankIdAccountId(t.thisAccount.bankId, t.thisAccount.accountId))

    // This function will only accept transactions which have the same This Account.
    if(accountUids.toSet.size > 1) {
      viewLogger.warn("Attempted to moderate transactions not belonging to the same account in a call where they should")
      Failure("Could not moderate transactions as they do not all belong to the same account")
    } else {
      transactionsCore.headOption match {
        case Some(firstTransaction) =>
          // Moderate the *This Account* based on the first transaction, Because all the transactions share the same thisAccount. So we only need modetaed one account is enough for all the transctions.
          val moderatedAccount = moderateAccount(firstTransaction.thisAccount)
          // Moderate each *Transaction* based on the moderated Account
          Full(transactionsCore.flatMap(transactionCore => moderateCore(transactionCore, moderatedAccount)))
        case None =>
          Full(Nil)
      }
    }
  }

  def moderateAccount(bankAccount: BankAccount) : Box[ModeratedBankAccount] = {
    if(canSeeTransactionThisBankAccount)
    {
      val owners : Set[User] = if(canSeeBankAccountOwners) bankAccount.userOwners else Set()
      val balance = if(canSeeBankAccountBalance) bankAccount.balance.toString else ""
      val accountType = if(canSeeBankAccountType) Some(bankAccount.accountType) else None
      val currency = if(canSeeBankAccountCurrency) Some(bankAccount.currency) else None
      val label = if(canSeeBankAccountLabel) Some(bankAccount.label) else None
      val nationalIdentifier = if(canSeeBankAccountNationalIdentifier) Some(bankAccount.nationalIdentifier) else None
      val swiftBic = if(canSeeBankAccountSwift_bic) bankAccount.swift_bic else None
      val iban = if(canSeeBankAccountIban) bankAccount.iban else None
      val number = if(canSeeBankAccountNumber) Some(bankAccount.number) else None
      val bankName = if(canSeeBankAccountBankName) Some(bankAccount.bankName) else None
      val bankId = bankAccount.bankId
      //From V300, use scheme and address stuff...
      val bankRoutingScheme = if(canSeeBankRoutingScheme) Some(bankAccount.bankRoutingScheme) else None
      val bankRoutingAddress = if(canSeeBankRoutingAddress) Some(bankAccount.bankRoutingAddress) else None
      val accountRoutingScheme = if(canSeeBankAccountRoutingScheme) Some(bankAccount.accountRoutingScheme) else None
      val accountRoutingAddress = if(canSeeBankAccountRoutingAddress) Some(bankAccount.accountRoutingAddress) else None
      val accountRoutings = if(canSeeBankAccountRoutingScheme && canSeeBankAccountRoutingAddress) bankAccount.accountRoutings else Nil
      val accountRules = if(canSeeBankAccountCreditLimit) bankAccount.accountRules else Nil

      Some(
        new ModeratedBankAccount(
          accountId = bankAccount.accountId,
          owners = Some(owners),
          accountType = accountType,
          balance = balance,
          currency = currency,
          label = label,
          nationalIdentifier = nationalIdentifier,
          swift_bic = swiftBic,
          iban = iban,
          number = number,
          bankName = bankName,
          bankId = bankId,
          bankRoutingScheme = bankRoutingScheme,
          bankRoutingAddress = bankRoutingAddress,
          accountRoutingScheme = accountRoutingScheme,
          accountRoutingAddress = accountRoutingAddress,
          accountRoutings = accountRoutings,
          accountRules = accountRules
        )
      )
    }
    else
      Failure(s"${ErrorMessages.ViewDoesNotPermitAccess} You need the `canSeeTransactionThisBankAccount` access for the view(${this.viewId.value})")
  }

  // Moderate the Counterparty side of the Transaction (i.e. the Other Account involved in the transaction)
  def moderateOtherAccount(otherBankAccount : Counterparty) : Box[ModeratedOtherBankAccount] = {
    if (canSeeTransactionOtherBankAccount)
    {
      //other account data
      val otherAccountId = otherBankAccount.counterpartyId
      val otherAccountLabel: AccountName = {
        val realName = otherBankAccount.counterpartyName

        if (usePublicAliasIfOneExists) {

          val publicAlias = Counterparties.counterparties.vend.getPublicAlias(otherBankAccount.counterpartyId).getOrElse("Unknown")

          if (! publicAlias.isEmpty ) AccountName(publicAlias, PublicAlias)
          else AccountName(realName, NoAlias)

        } else if (usePrivateAliasIfOneExists) {

          // Note: this assumes that the id in Counterparty and otherBankAccount match!
          val privateAlias = Counterparties.counterparties.vend.getPrivateAlias(otherBankAccount.counterpartyId).getOrElse("Unknown")

          if (! privateAlias.isEmpty) AccountName(privateAlias, PrivateAlias)
          else AccountName(realName, PrivateAlias)
        } else
          AccountName(realName, NoAlias)
      }

      def isAlias = otherAccountLabel.aliasType match {
        case NoAlias => false
        case _ => true
      }

      def moderateField[T](canSeeField: Boolean, field: T) : Option[T] = {
        if(isAlias & hideOtherAccountMetadataIfAlias)
            None
        else
          if(canSeeField)
            Some(field)
          else
            None
      }

      implicit def optionStringToString(x : Option[String]) : String = x.getOrElse("")
      val otherAccountNationalIdentifier = if(canSeeOtherAccountNationalIdentifier) Some(otherBankAccount.nationalIdentifier) else None
      val otherAccountSWIFT_BIC = if(canSeeOtherAccountSWIFT_BIC) otherBankAccount.otherBankRoutingAddress else None
      val otherAccountIBAN = if(canSeeOtherAccountIBAN) otherBankAccount.otherAccountRoutingAddress else None
      val otherAccountBankName = if(canSeeOtherAccountBankName) Some(otherBankAccount.thisBankId.value) else None
      val otherAccountNumber = if(canSeeOtherAccountNumber) Some(otherBankAccount.thisAccountId.value) else None
      val otherAccountKind = if(canSeeOtherAccountKind) Some(otherBankAccount.kind) else None
      val otherBankRoutingScheme = if(canSeeOtherBankRoutingScheme) Some(otherBankAccount.otherBankRoutingScheme) else None
      val otherBankRoutingAddress = if(canSeeOtherBankRoutingAddress) otherBankAccount.otherBankRoutingAddress else None
      val otherAccountRoutingScheme = if(canSeeOtherAccountRoutingScheme) Some(otherBankAccount.otherAccountRoutingScheme) else None
      val otherAccountRoutingAddress = if(canSeeOtherAccountRoutingAddress) otherBankAccount.otherAccountRoutingAddress else None
      val otherAccountMetadata =
        if(canSeeOtherAccountMetadata){
          //other bank account metadata
          val moreInfo = moderateField(canSeeMoreInfo, Counterparties.counterparties.vend.getMoreInfo(otherBankAccount.counterpartyId).getOrElse("Unknown"))
          val url = moderateField(canSeeUrl, Counterparties.counterparties.vend.getUrl(otherBankAccount.counterpartyId).getOrElse("Unknown"))
          val imageUrl = moderateField(canSeeImageUrl, Counterparties.counterparties.vend.getImageURL(otherBankAccount.counterpartyId).getOrElse("Unknown"))
          val openCorporatesUrl = moderateField (canSeeOpenCorporatesUrl, Counterparties.counterparties.vend.getOpenCorporatesURL(otherBankAccount.counterpartyId).getOrElse("Unknown"))
          val corporateLocation : Option[Option[GeoTag]] = moderateField(canSeeCorporateLocation, Counterparties.counterparties.vend.getCorporateLocation(otherBankAccount.counterpartyId).toOption)
          val physicalLocation : Option[Option[GeoTag]] = moderateField(canSeePhysicalLocation, Counterparties.counterparties.vend.getPhysicalLocation(otherBankAccount.counterpartyId).toOption)
          val addMoreInfo = moderateField(canAddMoreInfo, otherBankAccount.metadata.addMoreInfo)
          val addURL = moderateField(canAddURL, otherBankAccount.metadata.addURL)
          val addImageURL = moderateField(canAddImageURL, otherBankAccount.metadata.addImageURL)
          val addOpenCorporatesUrl = moderateField(canAddOpenCorporatesUrl, otherBankAccount.metadata.addOpenCorporatesURL)
          val addCorporateLocation = moderateField(canAddCorporateLocation, otherBankAccount.metadata.addCorporateLocation)
          val addPhysicalLocation = moderateField(canAddPhysicalLocation, otherBankAccount.metadata.addPhysicalLocation)
          val publicAlias = moderateField(canSeePublicAlias, Counterparties.counterparties.vend.getPublicAlias(otherBankAccount.counterpartyId).getOrElse("Unknown"))
          val privateAlias = moderateField(canSeePrivateAlias, Counterparties.counterparties.vend.getPrivateAlias(otherBankAccount.counterpartyId).getOrElse("Unknown"))
          val addPublicAlias = moderateField(canAddPublicAlias, otherBankAccount.metadata.addPublicAlias)
          val addPrivateAlias = moderateField(canAddPrivateAlias, otherBankAccount.metadata.addPrivateAlias)
          val deleteCorporateLocation = moderateField(canDeleteCorporateLocation, otherBankAccount.metadata.deleteCorporateLocation)
          val deletePhysicalLocation= moderateField(canDeletePhysicalLocation, otherBankAccount.metadata.deletePhysicalLocation)

          Some(
            new ModeratedOtherBankAccountMetadata(
              moreInfo = moreInfo,
              url = url,
              imageURL = imageUrl,
              openCorporatesURL = openCorporatesUrl,
              corporateLocation = corporateLocation,
              physicalLocation = physicalLocation,
              publicAlias = publicAlias,
              privateAlias = privateAlias,
              addMoreInfo = addMoreInfo,
              addURL = addURL,
              addImageURL = addImageURL,
              addOpenCorporatesURL = addOpenCorporatesUrl,
              addCorporateLocation = addCorporateLocation,
              addPhysicalLocation = addPhysicalLocation,
              addPublicAlias = addPublicAlias,
              addPrivateAlias = addPrivateAlias,
              deleteCorporateLocation = deleteCorporateLocation,
              deletePhysicalLocation = deletePhysicalLocation
            )
          )
        }
        else
            None

      Some(
        new ModeratedOtherBankAccount(
          id = otherAccountId,
          label = otherAccountLabel,
          nationalIdentifier = otherAccountNationalIdentifier,
          swift_bic = otherAccountSWIFT_BIC,
          iban = otherAccountIBAN,
          bankName = otherAccountBankName,
          number = otherAccountNumber,
          metadata = otherAccountMetadata,
          kind = otherAccountKind,
          bankRoutingAddress = otherBankRoutingScheme,
          bankRoutingScheme = otherBankRoutingAddress,
          accountRoutingScheme = otherAccountRoutingScheme,
          accountRoutingAddress = otherAccountRoutingAddress
        )
      )
    }
    else
      Failure(s"${ErrorMessages.ViewDoesNotPermitAccess} You need the `canSeeTransactionOtherBankAccount` access for the view(${this.viewId.value})")
  }
  
  def moderateCore(counterpartyCore : CounterpartyCore) : Box[ModeratedOtherBankAccountCore] = {
    if (canSeeTransactionOtherBankAccount)
    {
      //other account data
      val otherAccountId = counterpartyCore.counterpartyId
      val otherAccountLabel: AccountName = AccountName(counterpartyCore.counterpartyName, NoAlias)
      
      def isAlias = otherAccountLabel.aliasType match {
        case NoAlias => false
        case _ => true
      }
      
      def moderateField[T](canSeeField: Boolean, field: T) : Option[T] = {
        if(canSeeField)
          Some(field)
        else
          None
      }
      
      implicit def optionStringToString(x : Option[String]) : String = x.getOrElse("")
      val otherAccountSWIFT_BIC = if(canSeeOtherAccountSWIFT_BIC) counterpartyCore.otherBankRoutingAddress else None
      val otherAccountIBAN = if(canSeeOtherAccountIBAN) counterpartyCore.otherAccountRoutingAddress else None
      val otherAccountBankName = if(canSeeOtherAccountBankName) Some(counterpartyCore.thisBankId.value) else None
      val otherAccountNumber = if(canSeeOtherAccountNumber) Some(counterpartyCore.thisAccountId.value) else None
      val otherAccountKind = if(canSeeOtherAccountKind) Some(counterpartyCore.kind) else None
      val otherBankRoutingScheme = if(canSeeOtherBankRoutingScheme) Some(counterpartyCore.otherBankRoutingScheme) else None
      val otherBankRoutingAddress = if(canSeeOtherBankRoutingAddress) counterpartyCore.otherBankRoutingAddress else None
      val otherAccountRoutingScheme = if(canSeeOtherAccountRoutingScheme) Some(counterpartyCore.otherAccountRoutingScheme) else None
      val otherAccountRoutingAddress = if(canSeeOtherAccountRoutingAddress) counterpartyCore.otherAccountRoutingAddress else None
      Some(
        new ModeratedOtherBankAccountCore(
          id = counterpartyCore.counterpartyId,
          label = otherAccountLabel,
          swift_bic = otherAccountSWIFT_BIC,
          iban = otherAccountIBAN,
          bankName = otherAccountBankName,
          number = otherAccountNumber,
          kind = otherAccountKind,
          bankRoutingAddress = otherBankRoutingScheme,
          bankRoutingScheme = otherBankRoutingAddress,
          accountRoutingScheme = otherAccountRoutingScheme,
          accountRoutingAddress = otherAccountRoutingAddress
        )
      )
    }
    else
      Failure(s"${ErrorMessages.ViewDoesNotPermitAccess} You need the `canSeeTransactionOtherBankAccount` access for the view(${this.viewId.value})")
  }
}