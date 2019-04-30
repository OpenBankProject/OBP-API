/**
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd.

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
TESOBE Ltd.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */


package code.model

import java.util.Date

import code.api.util.ErrorMessages
import code.metadata.counterparties.Counterparties
import com.openbankproject.commons.model._
import net.liftweb.common._

case class ViewExtended(val view: View) {

  val viewLogger = Logger(classOf[ViewExtended])

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
        if(view.canSeeTransactionMetadata)
        {
          val ownerComment = if (view.canSeeOwnerComment) Some(transaction.metadata.ownerComment()) else None
          val comments =
            if (view.canSeeComments)
              Some(transaction.metadata.comments(view.viewId))
            else None
          val addCommentFunc= if(view.canAddComment) Some(transaction.metadata.addComment) else None
          val deleteCommentFunc =
            if(view.canDeleteComment)
              Some(transaction.metadata.deleteComment)
            else
              None
          val addOwnerCommentFunc:Option[String=> Boolean] = if (view.canEditOwnerComment) Some(transaction.metadata.addOwnerComment) else None
          val tags =
            if(view.canSeeTags)
              Some(transaction.metadata.tags(view.viewId))
            else None
          val addTagFunc =
            if(view.canAddTag)
              Some(transaction.metadata.addTag)
            else
              None
          val deleteTagFunc =
            if(view.canDeleteTag)
              Some(transaction.metadata.deleteTag)
            else
              None
          val images =
            if(view.canSeeImages) Some(transaction.metadata.images(view.viewId))
            else None

          val addImageFunc =
            if(view.canAddImage) Some(transaction.metadata.addImage)
            else None

          val deleteImageFunc =
            if(view.canDeleteImage) Some(transaction.metadata.deleteImage)
            else None

          val whereTag =
            if(view.canSeeWhereTag)
              Some(transaction.metadata.whereTags(view.viewId))
            else
              None

          val addWhereTagFunc : Option[(UserPrimaryKey, ViewId, Date, Double, Double) => Boolean] =
            if(view.canAddWhereTag)
              Some(transaction.metadata.addWhereTag)
            else
              Empty

          val deleteWhereTagFunc : Option[(ViewId) => Boolean] =
            if (view.canDeleteWhereTag)
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
        if (view.canSeeTransactionType) Some(transaction.transactionType)
        else None

      val transactionAmount =
        if (view.canSeeTransactionAmount) Some(transaction.amount)
        else None

      val transactionCurrency =
        if (view.canSeeTransactionCurrency) Some(transaction.currency)
        else None

      val transactionDescription =
        if (view.canSeeTransactionDescription) transaction.description
        else None

      val transactionStartDate =
        if (view.canSeeTransactionStartDate) Some(transaction.startDate)
        else None

      val transactionFinishDate =
        if (view.canSeeTransactionFinishDate) Some(transaction.finishDate)
        else None

      val transactionBalance =
        if (view.canSeeTransactionBalance) transaction.balance.toString()
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
      view.viewLogger.warn(failMsg)
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
        if (view.canSeeTransactionType) Some(transactionCore.transactionType)
        else None

      val transactionAmount =
        if (view.canSeeTransactionAmount) Some(transactionCore.amount)
        else None

      val transactionCurrency =
        if (view.canSeeTransactionCurrency) Some(transactionCore.currency)
        else None

      val transactionDescription =
        if (view.canSeeTransactionDescription) transactionCore.description
        else None

      val transactionStartDate =
        if (view.canSeeTransactionStartDate) Some(transactionCore.startDate)
        else None

      val transactionFinishDate =
        if (view.canSeeTransactionFinishDate) Some(transactionCore.finishDate)
        else None

      val transactionBalance =
        if (view.canSeeTransactionBalance) transactionCore.balance.toString()
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
      view.viewLogger.warn(failMsg)
      Failure(failMsg)
    } else {
      Full(moderatedTransaction)
    }

  }


  def moderateTransactionsWithSameAccount(transactions : List[Transaction]) : Box[List[ModeratedTransaction]] = {

    val accountUids = transactions.map(t => BankIdAccountId(t.bankId, t.accountId))

    // This function will only accept transactions which have the same This Account.
    if(accountUids.toSet.size > 1) {
      view.viewLogger.warn("Attempted to moderate transactions not belonging to the same account in a call where they should")
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
      view.viewLogger.warn("Attempted to moderate transactions not belonging to the same account in a call where they should")
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
    if(view.canSeeTransactionThisBankAccount)
    {
      val owners : Set[User] = if(view.canSeeBankAccountOwners) bankAccount.userOwners else Set()
      val balance = if(view.canSeeBankAccountBalance) bankAccount.balance.toString else ""
      val accountType = if(view.canSeeBankAccountType) Some(bankAccount.accountType) else None
      val currency = if(view.canSeeBankAccountCurrency) Some(bankAccount.currency) else None
      val label = if(view.canSeeBankAccountLabel) Some(bankAccount.label) else None
      val nationalIdentifier = if(view.canSeeBankAccountNationalIdentifier) Some(bankAccount.nationalIdentifier) else None
      val iban = if(view.canSeeBankAccountIban) bankAccount.iban else None
      val number = if(view.canSeeBankAccountNumber) Some(bankAccount.number) else None
      val bankName = if(view.canSeeBankAccountBankName) Some(bankAccount.bankName) else None
      val bankId = bankAccount.bankId
      //From V300, use scheme and address stuff...
      val bankRoutingScheme = if(view.canSeeBankRoutingScheme) Some(bankAccount.bankRoutingScheme) else None
      val bankRoutingAddress = if(view.canSeeBankRoutingAddress) Some(bankAccount.bankRoutingAddress) else None
      val accountRoutingScheme = if(view.canSeeBankAccountRoutingScheme) Some(bankAccount.accountRoutingScheme) else None
      val accountRoutingAddress = if(view.canSeeBankAccountRoutingAddress) Some(bankAccount.accountRoutingAddress) else None
      val accountRoutings = if(view.canSeeBankAccountRoutingScheme && view.canSeeBankAccountRoutingAddress) bankAccount.accountRoutings else Nil
      val accountRules = if(view.canSeeBankAccountCreditLimit) bankAccount.accountRules else Nil

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
      Failure(s"${ErrorMessages.ViewDoesNotPermitAccess} You need the `canSeeTransactionThisBankAccount` access for the view(${view.viewId.value})")
  }

  // Moderate the Counterparty side of the Transaction (i.e. the Other Account involved in the transaction)
  def moderateOtherAccount(otherBankAccount : Counterparty) : Box[ModeratedOtherBankAccount] = {
    if (view.canSeeTransactionOtherBankAccount)
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
      val otherAccountNationalIdentifier = if(view.canSeeOtherAccountNationalIdentifier) Some(otherBankAccount.nationalIdentifier) else None
      val otherAccountSWIFT_BIC = if(view.canSeeOtherAccountSWIFT_BIC) otherBankAccount.otherBankRoutingAddress else None
      val otherAccountIBAN = if(view.canSeeOtherAccountIBAN) otherBankAccount.otherAccountRoutingAddress else None
      val otherAccountBankName = if(view.canSeeOtherAccountBankName) Some(otherBankAccount.thisBankId.value) else None
      val otherAccountNumber = if(view.canSeeOtherAccountNumber) Some(otherBankAccount.thisAccountId.value) else None
      val otherAccountKind = if(view.canSeeOtherAccountKind) Some(otherBankAccount.kind) else None
      val otherBankRoutingScheme = if(view.canSeeOtherBankRoutingScheme) Some(otherBankAccount.otherBankRoutingScheme) else None
      val otherBankRoutingAddress = if(view.canSeeOtherBankRoutingAddress) otherBankAccount.otherBankRoutingAddress else None
      val otherAccountRoutingScheme = if(view.canSeeOtherAccountRoutingScheme) Some(otherBankAccount.otherAccountRoutingScheme) else None
      val otherAccountRoutingAddress = if(view.canSeeOtherAccountRoutingAddress) otherBankAccount.otherAccountRoutingAddress else None
      val otherAccountMetadata =
        if(view.canSeeOtherAccountMetadata){
          //other bank account metadata
          val moreInfo = moderateField(view.canSeeMoreInfo, Counterparties.counterparties.vend.getMoreInfo(otherBankAccount.counterpartyId).getOrElse("Unknown"))
          val url = moderateField(view.canSeeUrl, Counterparties.counterparties.vend.getUrl(otherBankAccount.counterpartyId).getOrElse("Unknown"))
          val imageUrl = moderateField(view.canSeeImageUrl, Counterparties.counterparties.vend.getImageURL(otherBankAccount.counterpartyId).getOrElse("Unknown"))
          val openCorporatesUrl = moderateField (view.canSeeOpenCorporatesUrl, Counterparties.counterparties.vend.getOpenCorporatesURL(otherBankAccount.counterpartyId).getOrElse("Unknown"))
          val corporateLocation : Option[Option[GeoTag]] = moderateField(view.canSeeCorporateLocation, Counterparties.counterparties.vend.getCorporateLocation(otherBankAccount.counterpartyId).toOption)
          val physicalLocation : Option[Option[GeoTag]] = moderateField(view.canSeePhysicalLocation, Counterparties.counterparties.vend.getPhysicalLocation(otherBankAccount.counterpartyId).toOption)
          val addMoreInfo = moderateField(view.canAddMoreInfo, otherBankAccount.metadata.addMoreInfo)
          val addURL = moderateField(view.canAddURL, otherBankAccount.metadata.addURL)
          val addImageURL = moderateField(view.canAddImageURL, otherBankAccount.metadata.addImageURL)
          val addOpenCorporatesUrl = moderateField(view.canAddOpenCorporatesUrl, otherBankAccount.metadata.addOpenCorporatesURL)
          val addCorporateLocation = moderateField(view.canAddCorporateLocation, otherBankAccount.metadata.addCorporateLocation)
          val addPhysicalLocation = moderateField(view.canAddPhysicalLocation, otherBankAccount.metadata.addPhysicalLocation)
          val publicAlias = moderateField(view.canSeePublicAlias, Counterparties.counterparties.vend.getPublicAlias(otherBankAccount.counterpartyId).getOrElse("Unknown"))
          val privateAlias = moderateField(view.canSeePrivateAlias, Counterparties.counterparties.vend.getPrivateAlias(otherBankAccount.counterpartyId).getOrElse("Unknown"))
          val addPublicAlias = moderateField(view.canAddPublicAlias, otherBankAccount.metadata.addPublicAlias)
          val addPrivateAlias = moderateField(view.canAddPrivateAlias, otherBankAccount.metadata.addPrivateAlias)
          val deleteCorporateLocation = moderateField(view.canDeleteCorporateLocation, otherBankAccount.metadata.deleteCorporateLocation)
          val deletePhysicalLocation= moderateField(view.canDeletePhysicalLocation, otherBankAccount.metadata.deletePhysicalLocation)

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
      Failure(s"${ErrorMessages.ViewDoesNotPermitAccess} You need the `canSeeTransactionOtherBankAccount` access for the view(${view.viewId.value})")
  }

  def moderateCore(counterpartyCore : CounterpartyCore) : Box[ModeratedOtherBankAccountCore] = {
    if (view.canSeeTransactionOtherBankAccount)
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
      val otherAccountSWIFT_BIC = if(view.canSeeOtherAccountSWIFT_BIC) counterpartyCore.otherBankRoutingAddress else None
      val otherAccountIBAN = if(view.canSeeOtherAccountIBAN) counterpartyCore.otherAccountRoutingAddress else None
      val otherAccountBankName = if(view.canSeeOtherAccountBankName) Some(counterpartyCore.thisBankId.value) else None
      val otherAccountNumber = if(view.canSeeOtherAccountNumber) Some(counterpartyCore.thisAccountId.value) else None
      val otherAccountKind = if(view.canSeeOtherAccountKind) Some(counterpartyCore.kind) else None
      val otherBankRoutingScheme = if(view.canSeeOtherBankRoutingScheme) Some(counterpartyCore.otherBankRoutingScheme) else None
      val otherBankRoutingAddress = if(view.canSeeOtherBankRoutingAddress) counterpartyCore.otherBankRoutingAddress else None
      val otherAccountRoutingScheme = if(view.canSeeOtherAccountRoutingScheme) Some(counterpartyCore.otherAccountRoutingScheme) else None
      val otherAccountRoutingAddress = if(view.canSeeOtherAccountRoutingAddress) counterpartyCore.otherAccountRoutingAddress else None
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
      Failure(s"${ErrorMessages.ViewDoesNotPermitAccess} You need the `canSeeTransactionOtherBankAccount` access for the view(${view.viewId.value})")
  }
}
