/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH.

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
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */


package code.model

import code.api.Constant._
import code.api.util.ErrorMessages
import code.metadata.counterparties.Counterparties
import code.views.system.ViewPermission
import com.openbankproject.commons.model._
import com.openbankproject.commons.model.enums.AccountRoutingScheme
import net.liftweb.common._
import net.liftweb.util.StringHelpers
import code.util.Helper.MdcLoggable

import java.util.Date

case class ViewExtended(val view: View) extends MdcLoggable {

  def getViewPermissions: List[String] =
    if (view.isSystem) {
      ViewPermission.findSystemViewPermissions(view.viewId).map(_.permission.get)
    } else {
      ViewPermission.findCustomViewPermissions(view.bankId, view.accountId, view.viewId).map(_.permission.get)
    }

  def moderateTransaction(transaction : Transaction): Box[ModeratedTransaction] = {
    moderateTransactionUsingModeratedAccount(transaction, moderateAccountLegacy(transaction.thisAccount))
  }

  // In the future we can add a method here to allow someone to show only transactions over a certain limit
  private def moderateTransactionUsingModeratedAccount(transaction: Transaction, moderatedAccount : Option[ModeratedBankAccount]): Box[ModeratedTransaction] = {

    val viewPermissions = getViewPermissions

    lazy val moderatedTransaction = {
      //transaction data
      val transactionId = transaction.id
      val transactionUUID = transaction.uuid
      val otherBankAccount = moderateOtherAccount(transaction.otherAccount)

      //transaction metadata
      val transactionMetadata =
        if(viewPermissions.exists(_ == CAN_SEE_TRANSACTION_METADATA))
        {
          val ownerComment = if (viewPermissions.exists(_ == CAN_SEE_OWNER_COMMENT)) Some(transaction.metadata.ownerComment()) else None
          val comments =
            if (viewPermissions.exists(_ == CAN_SEE_COMMENTS))
              Some(transaction.metadata.comments(view.viewId))
            else None
          val addCommentFunc= if(viewPermissions.exists(_ == CAN_ADD_COMMENT)) Some(transaction.metadata.addComment) else None
          val deleteCommentFunc =
            if(viewPermissions.exists(_ == CAN_DELETE_COMMENT))
              Some(transaction.metadata.deleteComment)
            else
              None
          val addOwnerCommentFunc:Option[String=> Boolean] = if (viewPermissions.exists(_ == CAN_EDIT_OWNER_COMMENT)) Some(transaction.metadata.addOwnerComment) else None
          val tags =
            if(viewPermissions.exists(_ == CAN_SEE_TAGS))
              Some(transaction.metadata.tags(view.viewId))
            else None
          val addTagFunc =
            if(viewPermissions.exists(_ == CAN_ADD_TAG))
              Some(transaction.metadata.addTag)
            else
              None
          val deleteTagFunc =
            if(viewPermissions.exists(_ == CAN_DELETE_TAG))
              Some(transaction.metadata.deleteTag)
            else
              None
          val images =
            if(viewPermissions.exists(_ == CAN_SEE_IMAGES)) Some(transaction.metadata.images(view.viewId))
            else None

          val addImageFunc =
            if(viewPermissions.exists(_ == CAN_ADD_IMAGE)) Some(transaction.metadata.addImage)
            else None

          val deleteImageFunc =
            if(viewPermissions.exists(_ == CAN_DELETE_IMAGE)) Some(transaction.metadata.deleteImage)
            else None

          val whereTag =
            if(viewPermissions.exists(_ == CAN_SEE_WHERE_TAG))
              Some(transaction.metadata.whereTags(view.viewId))
            else
              None

          val addWhereTagFunc : Option[(UserPrimaryKey, ViewId, Date, Double, Double) => Boolean] =
            if(viewPermissions.exists(_ == CAN_ADD_WHERE_TAG))
              Some(transaction.metadata.addWhereTag)
            else
              Empty

          val deleteWhereTagFunc : Option[(ViewId) => Boolean] =
            if (viewPermissions.exists(_ == CAN_DELETE_WHERE_TAG))
              Some(transaction.metadata.deleteWhereTag)
            else
              Empty


          Some(
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
        if (viewPermissions.exists(_ == CAN_SEE_TRANSACTION_TYPE)) Some(transaction.transactionType)
        else None

      val transactionAmount =
        if (viewPermissions.exists(_ == CAN_SEE_TRANSACTION_AMOUNT)) Some(transaction.amount)
        else None

      val transactionCurrency =
        if (viewPermissions.exists(_ == CAN_SEE_TRANSACTION_CURRENCY)) Some(transaction.currency)
        else None

      val transactionDescription =
        if (viewPermissions.exists(_ == CAN_SEE_TRANSACTION_DESCRIPTION)) transaction.description
        else None

      val transactionStartDate =
        if (viewPermissions.exists(_ == CAN_SEE_TRANSACTION_START_DATE)) Some(transaction.startDate)
        else None

      val transactionFinishDate =
        if (viewPermissions.exists(_ == CAN_SEE_TRANSACTION_FINISH_DATE)) transaction.finishDate
        else None

      val transactionBalance =
        if (viewPermissions.exists(_ == CAN_SEE_TRANSACTION_BALANCE) && transaction.balance != null) transaction.balance.toString()
        else ""

      val transactionStatus =
        if (viewPermissions.exists(_ == CAN_SEE_TRANSACTION_STATUS)) transaction.status
        else None

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
        balance = transactionBalance,
        status = transactionStatus
      )
    }


    val belongsToModeratedAccount : Boolean = moderatedAccount match {
      case Some(acc) => acc.accountId == transaction.accountId && acc.bankId == transaction.bankId
      case None => true
    }

    if(!belongsToModeratedAccount) {
      val failMsg = "Attempted to moderate a transaction using the incorrect moderated account"
      logger.warn(failMsg)
      Failure(failMsg)
    } else {
      Full(moderatedTransaction)
    }

  }

  private def moderateCore(transactionCore: TransactionCore, moderatedAccount : Option[ModeratedBankAccount]): Box[ModeratedTransactionCore] = {

    val viewPermissions = getViewPermissions

    lazy val moderatedTransaction = {
      //transaction data
      val transactionId = transactionCore.id
      val otherBankAccount = moderateCore(transactionCore.otherAccount)

      val transactionType =
        if (viewPermissions.exists(_ == CAN_SEE_TRANSACTION_TYPE)) Some(transactionCore.transactionType)
        else None

      val transactionAmount =
        if (viewPermissions.exists(_ == CAN_SEE_TRANSACTION_AMOUNT)) Some(transactionCore.amount)
        else None

      val transactionCurrency =
        if (viewPermissions.exists(_ == CAN_SEE_TRANSACTION_CURRENCY)) Some(transactionCore.currency)
        else None

      val transactionDescription =
        if (viewPermissions.exists(_ == CAN_SEE_TRANSACTION_DESCRIPTION)) transactionCore.description
        else None

      val transactionStartDate =
        if (viewPermissions.exists(_ == CAN_SEE_TRANSACTION_START_DATE)) Some(transactionCore.startDate)
        else None

      val transactionFinishDate =
        if (viewPermissions.exists(_ == CAN_SEE_TRANSACTION_FINISH_DATE)) Some(transactionCore.finishDate)
        else None

      val transactionBalance =
        if (viewPermissions.exists(_ == CAN_SEE_TRANSACTION_BALANCE) && transactionCore.balance != null) transactionCore.balance.toString()
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
      logger.warn(failMsg)
      Failure(failMsg)
    } else {
      Full(moderatedTransaction)
    }

  }


  def moderateTransactionsWithSameAccount(bank: Bank, transactions : List[Transaction]) : Box[List[ModeratedTransaction]] = {

    val accountUids = transactions.map(t => BankIdAccountId(t.bankId, t.accountId))

    // This function will only accept transactions which have the same This Account.
    if(accountUids.toSet.size > 1) {
      logger.warn("Attempted to moderate transactions not belonging to the same account in a call where they should")
      Failure("Could not moderate transactions as they do not all belong to the same account")
    } else {
      Full(transactions.flatMap(
        transaction => {
          // for CBS mode, we can not guarantee this account is the same, each transaction this account fields maybe different, so we need to moderate each transaction using the moderated account.
          val moderatedAccount = moderateAccount(bank, transaction.thisAccount)
          moderateTransactionUsingModeratedAccount(transaction, moderatedAccount)
        })
      )
    }
  }

  def moderateTransactionsWithSameAccountCore(bank: Bank, transactionsCore : List[TransactionCore]) : Box[List[ModeratedTransactionCore]] = {

    val accountUids = transactionsCore.map(t => BankIdAccountId(t.thisAccount.bankId, t.thisAccount.accountId))

    // This function will only accept transactions which have the same This Account.
    if(accountUids.toSet.size > 1) {
      logger.warn("Attempted to moderate transactions not belonging to the same account in a call where they should")
      Failure("Could not moderate transactions as they do not all belong to the same account")
    } else {

      Full(transactionsCore.flatMap(
        transaction => {
          // for CBS mode, we can not guarantee this account is the same, each transaction this account fields maybe different, so we need to moderate each transaction using the moderated account.
          val moderatedAccount = moderateAccount(bank, transaction.thisAccount)
          moderateCore(transaction, moderatedAccount)
        })
      )
    }
  }

  /**
   * this is the new function to replace the @moderateAccountLegacy, we will get the bank object from parameter,
   * no need to call the Connector.connector.vend.getBankLegacy several times.
   */
  def moderateAccount(bank: Bank, bankAccount: BankAccount) : Box[ModeratedBankAccount] = {
    val viewPermissions = getViewPermissions

    if(viewPermissions.exists(_ == CAN_SEE_TRANSACTION_THIS_BANK_ACCOUNT))
    {
      val owners : Set[User] = if(viewPermissions.exists(_ == CAN_SEE_BANK_ACCOUNT_OWNERS)) bankAccount.userOwners else Set()
      val balance = if(viewPermissions.exists(_ == CAN_SEE_BANK_ACCOUNT_BALANCE) && bankAccount.balance != null) bankAccount.balance.toString else ""
      val accountType = if(viewPermissions.exists(_ == CAN_SEE_BANK_ACCOUNT_TYPE)) Some(bankAccount.accountType) else None
      val currency = if(viewPermissions.exists(_ == CAN_SEE_BANK_ACCOUNT_CURRENCY)) Some(bankAccount.currency) else None
      val label = if (viewPermissions.exists(_ == CAN_SEE_BANK_ACCOUNT_LABEL)) Some(bankAccount.label) else None
      val iban = if (viewPermissions.exists(_ == CAN_SEE_BANK_ACCOUNT_IBAN)) bankAccount.accountRoutings.find(_.scheme == AccountRoutingScheme.IBAN.toString).map(_.address) else None
      val number = if (viewPermissions.exists(_ == CAN_SEE_BANK_ACCOUNT_NUMBER)) Some(bankAccount.number) else None
      //From V300, use scheme and address stuff...
      val accountRoutingScheme = if (viewPermissions.exists(_ == CAN_SEE_BANK_ACCOUNT_ROUTING_SCHEME)) bankAccount.accountRoutings.headOption.map(_.scheme) else None
      val accountRoutingAddress = if (viewPermissions.exists(_ == CAN_SEE_BANK_ACCOUNT_ROUTING_ADDRESS)) bankAccount.accountRoutings.headOption.map(_.address) else None
      val accountRoutings = if (viewPermissions.exists(_ == CAN_SEE_BANK_ACCOUNT_ROUTING_SCHEME) && viewPermissions.exists(_ == CAN_SEE_BANK_ACCOUNT_ROUTING_ADDRESS)) bankAccount.accountRoutings else Nil
      val accountRules = if (viewPermissions.exists(_ == CAN_SEE_BANK_ACCOUNT_CREDIT_LIMIT)) bankAccount.accountRules else Nil

      //followings are from the bank object.
      val bankId = bank.bankId
      val bankName = if (viewPermissions.exists(_ == CAN_SEE_BANK_ACCOUNT_BANK_NAME)) Some(bank.fullName) else None
      val nationalIdentifier = if (viewPermissions.exists(_ == CAN_SEE_BANK_ACCOUNT_NATIONAL_IDENTIFIER)) Some(bank.nationalIdentifier) else None
      val bankRoutingScheme = if (viewPermissions.exists(_ == CAN_SEE_BANK_ROUTING_SCHEME)) Some(bank.bankRoutingScheme) else None
      val bankRoutingAddress = if (viewPermissions.exists(_ == CAN_SEE_BANK_ROUTING_ADDRESS)) Some(bank.bankRoutingAddress) else None

      Some(
        new ModeratedBankAccount(
          accountId = bankAccount.accountId,
          owners = Some(owners),
          accountType = accountType,
          balance = balance,
          currency = currency,
          label = label,
          nationalIdentifier = nationalIdentifier,
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
      Failure(s"${ErrorMessages.ViewDoesNotPermitAccess} You need the `${(CAN_SEE_TRANSACTION_THIS_BANK_ACCOUNT)}` permission on the view(${view.viewId.value})")
  }



  @deprecated("This have the performance issue, call `Connector.connector.vend.getBankLegacy` four times in the backend. use @moderateAccount instead ","08-01-2020")
  def moderateAccountLegacy(bankAccount: BankAccount) : Box[ModeratedBankAccount] = {
    val viewPermissions = getViewPermissions

    if(viewPermissions.exists(_ == CAN_SEE_TRANSACTION_THIS_BANK_ACCOUNT))
    {
      val owners : Set[User] = if(viewPermissions.exists(_ == CAN_SEE_BANK_ACCOUNT_OWNERS)) bankAccount.userOwners else Set()
      val balance = if(viewPermissions.exists(_ == CAN_SEE_BANK_ACCOUNT_BALANCE) && bankAccount.balance !=null) bankAccount.balance.toString else ""
      val accountType = if(viewPermissions.exists(_ == CAN_SEE_BANK_ACCOUNT_TYPE)) Some(bankAccount.accountType) else None
      val currency = if(viewPermissions.exists(_ == CAN_SEE_BANK_ACCOUNT_CURRENCY)) Some(bankAccount.currency) else None
      val label = if(viewPermissions.exists(_ == CAN_SEE_BANK_ACCOUNT_LABEL)) Some(bankAccount.label) else None
      val nationalIdentifier = if(viewPermissions.exists(_ == CAN_SEE_BANK_ACCOUNT_NATIONAL_IDENTIFIER)) Some(bankAccount.nationalIdentifier) else None
      val iban = if(viewPermissions.exists(_ == CAN_SEE_BANK_ACCOUNT_IBAN)) bankAccount.accountRoutings.find(_.scheme == AccountRoutingScheme.IBAN.toString).map(_.address) else None
      val number = if(viewPermissions.exists(_ == CAN_SEE_BANK_ACCOUNT_NUMBER)) Some(bankAccount.number) else None
      val bankName = if(viewPermissions.exists(_ == CAN_SEE_BANK_ACCOUNT_BANK_NAME)) Some(bankAccount.bankName) else None
      val bankId = bankAccount.bankId
      //From V300, use scheme and address stuff...
      val bankRoutingScheme = if(viewPermissions.exists(_ == CAN_SEE_BANK_ROUTING_SCHEME)) Some(bankAccount.bankRoutingScheme) else None
      val bankRoutingAddress = if(viewPermissions.exists(_ == CAN_SEE_BANK_ROUTING_ADDRESS)) Some(bankAccount.bankRoutingAddress) else None
      val accountRoutingScheme = if(viewPermissions.exists(_ == CAN_SEE_BANK_ACCOUNT_ROUTING_SCHEME)) bankAccount.accountRoutings.headOption.map(_.scheme) else None
      val accountRoutingAddress = if(viewPermissions.exists(_ == CAN_SEE_BANK_ACCOUNT_ROUTING_ADDRESS)) bankAccount.accountRoutings.headOption.map(_.address) else None
      val accountRoutings = if(viewPermissions.exists(_ == CAN_SEE_BANK_ACCOUNT_ROUTING_SCHEME) && viewPermissions.exists(_ == CAN_SEE_BANK_ACCOUNT_ROUTING_ADDRESS)) bankAccount.accountRoutings else Nil
      val accountRules = if(viewPermissions.exists(_ == CAN_SEE_BANK_ACCOUNT_CREDIT_LIMIT)) bankAccount.accountRules else Nil

      Some(
        new ModeratedBankAccount(
          accountId = bankAccount.accountId,
          owners = Some(owners),
          accountType = accountType,
          balance = balance,
          currency = currency,
          label = label,
          nationalIdentifier = nationalIdentifier,
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
      Failure(s"${ErrorMessages.ViewDoesNotPermitAccess} You need the `${(CAN_SEE_TRANSACTION_THIS_BANK_ACCOUNT)}` permission on the view(${view.viewId.value})")
  }

  def moderateAccountCore(bankAccount: BankAccount) : Box[ModeratedBankAccountCore] = {
    val viewPermissions = getViewPermissions

    if(viewPermissions.exists(_ == CAN_SEE_TRANSACTION_THIS_BANK_ACCOUNT))
    {
      val owners : Set[User] = if(viewPermissions.exists(_ == CAN_SEE_BANK_ACCOUNT_OWNERS)) bankAccount.userOwners else Set()
      val balance = if(viewPermissions.exists(_ == CAN_SEE_BANK_ACCOUNT_BALANCE) && bankAccount.balance != null) Some(bankAccount.balance.toString) else None
      val accountType = if(viewPermissions.exists(_ == CAN_SEE_BANK_ACCOUNT_TYPE)) Some(bankAccount.accountType) else None
      val currency = if(viewPermissions.exists(_ == CAN_SEE_BANK_ACCOUNT_CURRENCY)) Some(bankAccount.currency) else None
      val label = if(viewPermissions.exists(_ == CAN_SEE_BANK_ACCOUNT_LABEL)) Some(bankAccount.label) else None
      val number = if(viewPermissions.exists(_ == CAN_SEE_BANK_ACCOUNT_NUMBER)) Some(bankAccount.number) else None
      val bankId = bankAccount.bankId
      //From V300, use scheme and address stuff...
      val accountRoutings = if(viewPermissions.exists(_ == CAN_SEE_BANK_ACCOUNT_ROUTING_SCHEME) && viewPermissions.exists(_ == CAN_SEE_BANK_ACCOUNT_ROUTING_ADDRESS)) bankAccount.accountRoutings else Nil
      val accountRules = if(viewPermissions.exists(_ == CAN_SEE_BANK_ACCOUNT_CREDIT_LIMIT)) bankAccount.accountRules else Nil

      Some(
        ModeratedBankAccountCore(
          accountId = bankAccount.accountId,
          owners = Some(owners),
          accountType = accountType,
          balance = balance,
          currency = currency,
          label = label,
          number = number,
          bankId = bankId,
          accountRoutings = accountRoutings,
          accountRules = accountRules
        )
      )
    }
    else
      Failure(s"${ErrorMessages.ViewDoesNotPermitAccess} You need the `${(CAN_SEE_TRANSACTION_THIS_BANK_ACCOUNT)}` permission on the view(${view.viewId.value})")
  }

  // Moderate the Counterparty side of the Transaction (i.e. the Other Account involved in the transaction)
  def moderateOtherAccount(otherBankAccount : Counterparty) : Box[ModeratedOtherBankAccount] = {
    val viewPermissions = getViewPermissions

    if (viewPermissions.exists(_ == CAN_SEE_TRANSACTION_OTHER_BANK_ACCOUNT))
    {
      //other account data
      val otherAccountId = otherBankAccount.counterpartyId
      val otherAccountLabel: AccountName = {
        val realName = otherBankAccount.counterpartyName

        if (view.usePublicAliasIfOneExists) {

          val publicAlias = Counterparties.counterparties.vend.getPublicAlias(otherBankAccount.counterpartyId).getOrElse("Unknown")

          if (! publicAlias.isEmpty ) AccountName(publicAlias, PublicAlias)
          else AccountName(realName, NoAlias)

        } else if (view.usePrivateAliasIfOneExists) {

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
        if(isAlias & view.hideOtherAccountMetadataIfAlias)
          None
        else
          if(canSeeField)
            Some(field)
          else
            None
      }

      implicit def optionStringToString(x : Option[String]) : String = x.getOrElse("")
      val otherAccountNationalIdentifier = if(viewPermissions.exists(_ == CAN_SEE_OTHER_ACCOUNT_NATIONAL_IDENTIFIER)) Some(otherBankAccount.nationalIdentifier) else None
      val otherAccountSWIFT_BIC = if(viewPermissions.exists(_ == CAN_SEE_OTHER_ACCOUNT_SWIFT_BIC)) otherBankAccount.otherBankRoutingAddress else None
      val otherAccountIBAN = if(viewPermissions.exists(_ == CAN_SEE_OTHER_ACCOUNT_IBAN)) otherBankAccount.otherAccountRoutingAddress else None
      val otherAccountBankName = if(viewPermissions.exists(_ == CAN_SEE_OTHER_ACCOUNT_BANK_NAME)) Some(otherBankAccount.thisBankId.value) else None
      val otherAccountNumber = if(viewPermissions.exists(_ == CAN_SEE_OTHER_ACCOUNT_NUMBER)) Some(otherBankAccount.thisAccountId.value) else None
      val otherAccountKind = if(viewPermissions.exists(_ == CAN_SEE_OTHER_ACCOUNT_KIND)) Some(otherBankAccount.kind) else None
      val otherBankRoutingScheme = if(viewPermissions.exists(_ == CAN_SEE_OTHER_BANK_ROUTING_SCHEME)) Some(otherBankAccount.otherBankRoutingScheme) else None
      val otherBankRoutingAddress = if(viewPermissions.exists(_ == CAN_SEE_OTHER_BANK_ROUTING_ADDRESS)) otherBankAccount.otherBankRoutingAddress else None
      val otherAccountRoutingScheme = if(viewPermissions.exists(_ == CAN_SEE_OTHER_ACCOUNT_ROUTING_SCHEME)) Some(otherBankAccount.otherAccountRoutingScheme) else None
      val otherAccountRoutingAddress = if(viewPermissions.exists(_ == CAN_SEE_OTHER_ACCOUNT_ROUTING_ADDRESS)) otherBankAccount.otherAccountRoutingAddress else None
      val otherAccountMetadata =
        if(viewPermissions.exists(_ == CAN_SEE_OTHER_ACCOUNT_METADATA)){
          //other bank account metadata
          val moreInfo = moderateField(viewPermissions.exists(_ == CAN_SEE_MORE_INFO), Counterparties.counterparties.vend.getMoreInfo(otherBankAccount.counterpartyId).getOrElse("Unknown"))
          val url = moderateField(viewPermissions.exists(_ == CAN_SEE_URL), Counterparties.counterparties.vend.getUrl(otherBankAccount.counterpartyId).getOrElse("Unknown"))
          val imageUrl = moderateField(viewPermissions.exists(_ == CAN_SEE_IMAGE_URL), Counterparties.counterparties.vend.getImageURL(otherBankAccount.counterpartyId).getOrElse("Unknown"))
          val openCorporatesUrl = moderateField (viewPermissions.exists(_ == CAN_SEE_OPEN_CORPORATES_URL), Counterparties.counterparties.vend.getOpenCorporatesURL(otherBankAccount.counterpartyId).getOrElse("Unknown"))
          val corporateLocation : Option[Option[GeoTag]] = moderateField(viewPermissions.exists(_ == CAN_SEE_CORPORATE_LOCATION), Counterparties.counterparties.vend.getCorporateLocation(otherBankAccount.counterpartyId).toOption)
          val physicalLocation : Option[Option[GeoTag]] = moderateField(viewPermissions.exists(_ == CAN_SEE_PHYSICAL_LOCATION), Counterparties.counterparties.vend.getPhysicalLocation(otherBankAccount.counterpartyId).toOption)
          val addMoreInfo = moderateField(viewPermissions.exists(_ == CAN_ADD_MORE_INFO), otherBankAccount.metadata.addMoreInfo)
          val addURL = moderateField(viewPermissions.exists(_ == CAN_ADD_URL), otherBankAccount.metadata.addURL)
          val addImageURL = moderateField(viewPermissions.exists(_ == CAN_ADD_IMAGE_URL), otherBankAccount.metadata.addImageURL)
          val addOpenCorporatesUrl = moderateField(viewPermissions.exists(_ == CAN_ADD_OPEN_CORPORATES_URL), otherBankAccount.metadata.addOpenCorporatesURL)
          val addCorporateLocation = moderateField(viewPermissions.exists(_ == CAN_ADD_CORPORATE_LOCATION), otherBankAccount.metadata.addCorporateLocation)
          val addPhysicalLocation = moderateField(viewPermissions.exists(_ == CAN_ADD_PHYSICAL_LOCATION), otherBankAccount.metadata.addPhysicalLocation)
          val publicAlias = moderateField(viewPermissions.exists(_ == CAN_SEE_PUBLIC_ALIAS), Counterparties.counterparties.vend.getPublicAlias(otherBankAccount.counterpartyId).getOrElse("Unknown"))
          val privateAlias = moderateField(viewPermissions.exists(_ == CAN_SEE_PRIVATE_ALIAS), Counterparties.counterparties.vend.getPrivateAlias(otherBankAccount.counterpartyId).getOrElse("Unknown"))
          val addPublicAlias = moderateField(viewPermissions.exists(_ == CAN_ADD_PUBLIC_ALIAS), otherBankAccount.metadata.addPublicAlias)
          val addPrivateAlias = moderateField(viewPermissions.exists(_ == CAN_ADD_PRIVATE_ALIAS), otherBankAccount.metadata.addPrivateAlias)
          val deleteCorporateLocation = moderateField(viewPermissions.exists(_ == CAN_DELETE_CORPORATE_LOCATION), otherBankAccount.metadata.deleteCorporateLocation)
          val deletePhysicalLocation= moderateField(viewPermissions.exists(_ == CAN_DELETE_PHYSICAL_LOCATION), otherBankAccount.metadata.deletePhysicalLocation)

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
          bankRoutingScheme = otherBankRoutingScheme ,
          bankRoutingAddress = otherBankRoutingAddress,
          accountRoutingScheme = otherAccountRoutingScheme,
          accountRoutingAddress = otherAccountRoutingAddress
        )
      )
    }
    else
      Failure(s"${ErrorMessages.ViewDoesNotPermitAccess} You need the `${(CAN_SEE_TRANSACTION_OTHER_BANK_ACCOUNT)}` permission on the view(${view.viewId.value})")
  }

  def moderateCore(counterpartyCore : CounterpartyCore) : Box[ModeratedOtherBankAccountCore] = {
    val viewPermissions = getViewPermissions

    if (viewPermissions.exists(_ == CAN_SEE_TRANSACTION_OTHER_BANK_ACCOUNT))
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
      val otherAccountSWIFT_BIC = if(viewPermissions.exists(_ == CAN_SEE_OTHER_ACCOUNT_SWIFT_BIC)) counterpartyCore.otherBankRoutingAddress else None
      val otherAccountIBAN = if(viewPermissions.exists(_ == CAN_SEE_OTHER_ACCOUNT_IBAN)) counterpartyCore.otherAccountRoutingAddress else None
      val otherAccountBankName = if(viewPermissions.exists(_ == CAN_SEE_OTHER_ACCOUNT_BANK_NAME)) Some(counterpartyCore.thisBankId.value) else None
      val otherAccountNumber = if(viewPermissions.exists(_ == CAN_SEE_OTHER_ACCOUNT_NUMBER)) Some(counterpartyCore.thisAccountId.value) else None
      val otherAccountKind = if(viewPermissions.exists(_ == CAN_SEE_OTHER_ACCOUNT_KIND)) Some(counterpartyCore.kind) else None
      val otherBankRoutingScheme = if(viewPermissions.exists(_ == CAN_SEE_OTHER_BANK_ROUTING_SCHEME)) Some(counterpartyCore.otherBankRoutingScheme) else None
      val otherBankRoutingAddress = if(viewPermissions.exists(_ == CAN_SEE_OTHER_BANK_ROUTING_ADDRESS)) counterpartyCore.otherBankRoutingAddress else None
      val otherAccountRoutingScheme = if(viewPermissions.exists(_ == CAN_SEE_OTHER_ACCOUNT_ROUTING_SCHEME)) Some(counterpartyCore.otherAccountRoutingScheme) else None
      val otherAccountRoutingAddress = if(viewPermissions.exists(_ == CAN_SEE_OTHER_ACCOUNT_ROUTING_ADDRESS)) counterpartyCore.otherAccountRoutingAddress else None
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
      Failure(s"${ErrorMessages.ViewDoesNotPermitAccess} You need the `${(CAN_SEE_TRANSACTION_OTHER_BANK_ACCOUNT)}` permission on the view(${view.viewId.value})")
  }
}
