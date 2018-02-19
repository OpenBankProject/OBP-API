package code.sandbox

import code.atms.MappedAtm
import code.branches.MappedBranch
import code.crm.MappedCrmEvent
import code.metadata.counterparties.MappedCounterpartyMetadata
import code.model.dataAccess.{MappedBank, MappedBankAccount}
import code.model.{AccountId, BankId, View}
import code.products.MappedProduct
import code.transaction.MappedTransaction
import code.views.Views

// , MappedDataLicense
import code.util.Helper.convertToSmallestCurrencyUnits
import net.liftweb.common.{Box, Failure, Full}
import net.liftweb.mapper.Mapper
import net.liftweb.util.Helpers._

case class MappedSaveable[T <: Mapper[_]](value : T) extends Saveable[T] {
  def save() = value.save()
}

object LocalMappedConnectorDataImport extends OBPDataImport with CreateAuthUsers {

  // Rename these types as MappedCrmEventType etc? Else can get confused with other types of same name

  type BankType = MappedBank
  type AccountType = MappedBankAccount
  type MetadataType = MappedCounterpartyMetadata
  type TransactionType = MappedTransaction
  type BranchType = MappedBranch
  type AtmType = MappedAtm
  type ProductType = MappedProduct
  type CrmEventType = MappedCrmEvent

  protected def createSaveableBanks(data : List[SandboxBankImport]) : Box[List[Saveable[BankType]]] = {
    val mappedBanks = data.map(bank => {
      MappedBank.create
        .permalink(bank.id)
        .fullBankName(bank.full_name)
        .shortBankName(bank.short_name)
        .logoURL(bank.logo)
        .websiteURL(bank.website)
    })

    val validationErrors = mappedBanks.flatMap(_.validate)

    if(validationErrors.nonEmpty) {
      Failure(s"Errors: ${validationErrors.map(_.msg)}")
    } else {
      Full(mappedBanks.map(MappedSaveable(_)))
    }
  }

  protected def createSaveableBranches(data : List[SandboxBranchImport]) : Box[List[Saveable[BranchType]]] = {
    val mappedBranches = data.map(branch => {

      val lobbyHours =  if (branch.lobby.isDefined) {branch.lobby.get.hours.toString} else ""
      val driveUpHours =  if (branch.driveUp.isDefined) {branch.driveUp.get.hours.toString} else ""

      MappedBranch.create
        .mBranchId(branch.id)
        .mBankId(branch.bank_id)
        .mName(branch.name)
        // Note: address fields are returned in meta.address
        // but are stored flat as fields / columns in the table
        .mLine1(branch.address.line_1)
        .mLine2(branch.address.line_2)
        .mLine3(branch.address.line_3)
        .mCity(branch.address.city)
        .mCounty(branch.address.county)
        .mState(branch.address.state)
        .mPostCode(branch.address.post_code)
        .mCountryCode(branch.address.country_code)
        .mlocationLatitude(branch.location.latitude)
        .mlocationLongitude(branch.location.longitude)
        .mLicenseId(branch.meta.license.id)
        .mLicenseName(branch.meta.license.name)
        .mLobbyHours(lobbyHours)
        .mDriveUpHours(driveUpHours)
    })

    val validationErrors = mappedBranches.flatMap(_.validate)

    if(validationErrors.nonEmpty) {
      Failure(s"Errors: ${validationErrors.map(_.msg)}")
    } else {
      Full(mappedBranches.map(MappedSaveable(_)))
    }
  }



/////

  protected def createSaveableAtms(data : List[SandboxAtmImport]) : Box[List[Saveable[AtmType]]] = {
    val mappedAtms = data.map(atm => {


      MappedAtm.create
        .mAtmId(atm.id)
        .mBankId(atm.bank_id)
        .mName(atm.name)
        // Note: address fields are returned in meta.address
        // but are stored flat as fields / columns in the table
        .mLine1(atm.address.line_1)
        .mLine2(atm.address.line_2)
        .mLine3(atm.address.line_3)
        .mCity(atm.address.city)
        .mCounty(atm.address.county)
        .mState(atm.address.state)
        .mPostCode(atm.address.post_code)
        .mCountryCode(atm.address.country_code)
        .mlocationLatitude(atm.location.latitude)
        .mlocationLongitude(atm.location.longitude)
        .mLicenseId(atm.meta.license.id)
        .mLicenseName(atm.meta.license.name)
    })

    val validationErrors = mappedAtms.flatMap(_.validate)

    if (validationErrors.nonEmpty) {
      Failure(s"Errors: ${validationErrors.map(_.msg)}")
    } else {
      Full(mappedAtms.map(MappedSaveable(_)))
    }

  }


  protected def createSaveableProducts(data : List[SandboxProductImport]) : Box[List[Saveable[ProductType]]] = {
    val mappedProducts = data.map(product => {
      MappedProduct.create
        .mBankId(product.bank_id)
        .mCode(product.code)
        .mName(product.name)
        .mCategory(product.category)
        .mFamily(product.family)
        .mSuperFamily(product.super_family)
        .mMoreInfoUrl(product.more_info_url)
        .mLicenseId(product.meta.license.id)
        .mLicenseName(product.meta.license.name)
    })

    val validationErrors = mappedProducts.flatMap(_.validate)

    if (validationErrors.nonEmpty) {
      logger.error(s"Problem saving ${mappedProducts.flatMap(_.code.value)}")
      Failure(s"Errors: ${validationErrors.map(_.msg)}")
    } else {
      Full(mappedProducts.map(MappedSaveable(_)))
    }

  }


  protected def createSaveableCrmEvents(data : List[SandboxCrmEventImport]) : Box[List[Saveable[CrmEventType]]] = {


      val mappedEvents = data.map(event => {
        // TODO Make so we can return any boxed error as below
        //scheduledDate <- tryo{dateFormat.parse(crmEvent.scheduled_date)} ?~ s"Invalid date format: ${crmEvent.scheduled_date}. Expected pattern $datePattern"
        //actualDate <- tryo{dateFormat.parse(crmEvent.actual_date)} ?~ s"Invalid date format: ${crmEvent.actual_date}. Expected pattern $datePattern"
        //val scheduledDate = dateFormat.parse(event.scheduled_date)
        val actualDate = dateFormat.parse(event.actual_date)


        logger.warn(s"Note: We are not saving API User, Result or Scheduled Date")

        val crmEvent = MappedCrmEvent.create
            .mBankId(event.bank_id)
            .mCrmEventId(event.id)
            //.mUserId(event.customer.number) // UserId is a long
            .mActualDate(actualDate)
            .mCategory(event.category)
            .mChannel(event.channel)
            .mDetail(event.detail)
            .mCustomerName(event.customer.name)
            .mCustomerNumber(event.customer.number)
            //.mResult("")
            //.mScheduledDate(event.scheduled_date)

        logger.debug(s"Saved CrmEvent id: ${crmEvent.crmEventId} customer name: ${crmEvent.customerName}")

        crmEvent
        }
      )

    val validationErrors = mappedEvents.flatMap(_.validate)

    if (validationErrors.nonEmpty) {
      logger.error(s"Problem saving ${mappedEvents.flatMap(_.category)}")
      Failure(s"Errors: ${validationErrors.map(_.msg)}")
    } else {
      Full(mappedEvents.map(MappedSaveable(_)))
    }

  }


  protected def createSaveableAccount(acc : SandboxAccountImport, banks : List[BankType]) : Box[Saveable[AccountType]] = {

    val mappedAccount = for {
      balance <- tryo{BigDecimal(acc.balance.amount)} ?~ s"Invalid balance: ${acc.balance.amount}"
      currency = acc.balance.currency
    } yield {
      MappedBankAccount.create
        .theAccountId(acc.id)
        .bank(acc.bank)
        .accountLabel(acc.label)
        .accountNumber(acc.number)
        .kind(acc.`type`)
        .accountIban(acc.IBAN)
        .accountCurrency(currency)
        .accountBalance(convertToSmallestCurrencyUnits(balance, currency))
    }

    val validationErrors = mappedAccount.map(_.validate).getOrElse(Nil)

    if(validationErrors.nonEmpty) {
      Failure(s"Errors: ${validationErrors.map(_.msg)}")
    } else {
      mappedAccount.map(MappedSaveable(_))
    }
  }


  override protected def createSaveableTransaction(t : SandboxTransactionImport, createdBanks : List[BankType], createdAccounts : List[AccountType]):
  Box[Saveable[TransactionType]] = {

    for {
      createdAcc <- Box(createdAccounts.find(acc => acc.accountId == AccountId(t.this_account.id) && acc.bankId == BankId(t.this_account.bank))) ?~ {
        logger.warn("Data import failed because a created account was not found for a transaction when it should have been")
        "Server Error"
      }
      currency = createdAcc.currency
      newBalanceValueAsBigDecimal <- tryo(List(classOf[NumberFormatException])){BigDecimal(t.details.new_balance)} ?~ s"Invalid new balance: ${t.details.new_balance}"
      tValueAsBigDecimal <- tryo(List(classOf[NumberFormatException])){BigDecimal(t.details.value)} ?~ s"Invalid transaction value: ${t.details.value}"
      postedDate <- tryo{dateFormat.parse(t.details.posted)} ?~ s"Invalid date format: ${t.details.posted}. Expected pattern $datePattern"
      completedDate <-tryo{dateFormat.parse(t.details.completed)} ?~ s"Invalid date format: ${t.details.completed}. Expected pattern $datePattern"
    } yield {

      logger.info(s"About to create the following MappedTransaction: ${t}")

      val mappedTransaction = MappedTransaction.create
        .bank(t.this_account.bank)
        .account(t.this_account.id)
        .transactionId(t.id)
        .transactionType(t.details.`type`)
        .amount(convertToSmallestCurrencyUnits(tValueAsBigDecimal, currency))
        .newAccountBalance(convertToSmallestCurrencyUnits(newBalanceValueAsBigDecimal, currency))
        .currency(currency)
        .tStartDate(postedDate)
        .tFinishDate(completedDate)
        .description(t.details.description)
        .counterpartyAccountHolder(t.counterparty.flatMap(_.name).getOrElse(""))
        .counterpartyAccountNumber(t.counterparty.flatMap(_.account_number).getOrElse(""))

      MappedSaveable(mappedTransaction)
    }
  }

  protected def createFirehoseView(bankId : BankId, accountId : AccountId, description: String) : Box[ViewType] = {
    Views.views.vend.getOrCreateFirehoseView(bankId, accountId, description).asInstanceOf[Box[ViewType]]
  }
  
  protected def createOwnerView(bankId : BankId, accountId : AccountId, description: String) : Box[ViewType] = {
    Views.views.vend.getOrCreateOwnerView(bankId, accountId, description).asInstanceOf[Box[ViewType]]
  }

  protected def createPublicView(bankId : BankId, accountId : AccountId, description: String) : Box[ViewType] = {
    Views.views.vend.getOrCreatePublicView(bankId, accountId, description).asInstanceOf[Box[ViewType]]
  }

  protected def createAccountantsView(bankId : BankId, accountId : AccountId, description: String) : Box[ViewType] = {
    Views.views.vend.getOrCreateAccountantsView(bankId, accountId, description).asInstanceOf[Box[ViewType]]
  }

  protected def createAuditorsView(bankId : BankId, accountId : AccountId, description: String) : Box[ViewType] = {
    Views.views.vend.getOrCreateAuditorsView(bankId, accountId, description).asInstanceOf[Box[ViewType]]
  }

}
