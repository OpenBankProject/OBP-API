package code.sandbox

import code.atms.Atms
import code.branches.MappedBranch
import code.crm.DoobieCrmEventProvider
import code.metadata.counterparties.MappedCounterpartyMetadata
import code.bankconnectors.DoobieBankAccountRoutingQueries
import code.model.dataAccess.{MappedBank, MappedBankAccount}
import code.products.MappedProduct
import code.transaction.MappedTransaction
import code.views.Views
import com.openbankproject.commons.model.enums.AccountRoutingScheme
import com.openbankproject.commons.model.{AccountId, Address, AtmId, AtmT, BankId, License, Location, Meta, View}

// , MappedDataLicense
import code.util.Helper.convertToSmallestCurrencyUnits
import net.liftweb.common.{Box, Failure, Full}
import net.liftweb.util.Helpers._

// Branch persistence goes through the Doobie store, for the same reason as SaveableAtm below.
case class SaveableBranch(branchId: String, bankId: String, name: String, line1: String,
                          line2: String, line3: String, city: String, county: String,
                          state: String, postCode: String, countryCode: String, latitude: Double,
                          longitude: Double, licenseId: String, licenseName: String,
                          lobbyHours: String, driveUpHours: String) extends Saveable[MappedBranch] {
  lazy val value: MappedBranch = MappedBranch.find(bankId, branchId)
    .openOrThrowException("the branch just saved must be readable")
  def save(): Unit = {
    // The importer supplies no opening times, routing, accessibility or contact details. Lobby
    // times default to "00:00" as the connector does; everything else is left null, which is what
    // Mapper's untouched fields stored.
    MappedBranch.createOrUpdate(
      branchIdRaw = branchId, bankIdRaw = bankId, nameRaw = name,
      line1 = line1, line2 = line2, line3 = line3, city = city, county = county, state = state,
      postCode = postCode, countryCode = countryCode, latitude = latitude, longitude = longitude,
      licenseId = licenseId, licenseName = licenseName,
      lobbyHours = lobbyHours, driveUpHours = driveUpHours,
      branchRoutingSchemeRaw = null, branchRoutingAddressRaw = null,
      lobbyOpenMonday = "00:00", lobbyCloseMonday = "00:00",
      lobbyOpenTuesday = "00:00", lobbyCloseTuesday = "00:00",
      lobbyOpenWednesday = "00:00", lobbyCloseWednesday = "00:00",
      lobbyOpenThursday = "00:00", lobbyCloseThursday = "00:00",
      lobbyOpenFriday = "00:00", lobbyCloseFriday = "00:00",
      lobbyOpenSaturday = "00:00", lobbyCloseSaturday = "00:00",
      lobbyOpenSunday = "00:00", lobbyCloseSunday = "00:00",
      driveUpOpenMonday = null, driveUpCloseMonday = null,
      driveUpOpenTuesday = null, driveUpCloseTuesday = null,
      driveUpOpenWednesday = null, driveUpCloseWednesday = null,
      driveUpOpenThursday = null, driveUpCloseThursday = null,
      driveUpOpenFriday = null, driveUpCloseFriday = null,
      driveUpOpenSaturday = null, driveUpCloseSaturday = null,
      driveUpOpenSunday = null, driveUpCloseSunday = null,
      isAccessibleRaw = "", accessibleFeaturesRaw = null, branchTypeRaw = null,
      moreInfoRaw = null, phoneNumberRaw = null, isDeletedRaw = false)
    ()
  }
}

// Product persistence goes through the Doobie store, for the same reason as SaveableAtm below.
case class SaveableProduct(bankId: String, code: String, name: String, category: String,
                           family: String, superFamily: String, moreInfoUrl: String,
                           licenseId: String, licenseName: String) extends Saveable[MappedProduct] {
  lazy val value: MappedProduct = MappedProduct.find(bankId, code)
    .openOrThrowException("the product just saved must be readable")
  def save(): Unit = {
    MappedProduct.createOrUpdate(bankId, code, parentProductCode = None, name = name,
      category = category, family = family, superFamily = superFamily, moreInfoUrl = moreInfoUrl,
      termsAndConditionsUrl = "", details = "", description = "", licenseId = licenseId,
      licenseName = licenseName)
    ()
  }
}

// ATM persistence goes through the active AtmsProvider (Doobie): the sandbox import must not
// write the row with Mapper while every read of it comes back through the provider.
case class SaveableAtm(valueParam : AtmT) extends Saveable[AtmT] {
  lazy val value: AtmT = valueParam
  def save() = Atms.atmsProvider.vend.createOrUpdateAtm(value)
}

// CrmEvent persistence goes through DoobieCrmEventProvider: the sandbox import must not write
// the row with Mapper while every read of it comes back through the provider. This is an
// unsaved, transient representation (mirroring MappedCrmEvent.create before .save()), so `user`
// throws if accessed - the Mapper version would have thrown too, since mUserId was never set
// on the sandbox-import path (see the "Note: We are not saving API User..." warning below).
case class CrmEventCreateParams(
  bankIdValue: String,
  crmEventIdValue: String,
  category: String,
  detail: String,
  channel: String,
  actualDate: java.util.Date,
  customerName: String,
  customerNumber: String
) extends code.crm.CrmEvent.CrmEvent {
  override def crmEventId: code.crm.CrmEvent.CrmEventId = code.crm.CrmEvent.CrmEventId(crmEventIdValue)
  override def bankId: BankId = BankId(bankIdValue)
  override def user: code.model.dataAccess.ResourceUser = throw new UnsupportedOperationException("user is not available before this CrmEvent is saved")
  override def scheduledDate: java.util.Date = new java.util.Date(0L)
  override def result: String = ""
}
case class SaveableCrmEvent(valueParam : CrmEventCreateParams) extends Saveable[CrmEventCreateParams] {
  lazy val value: CrmEventCreateParams = valueParam
  def save() = DoobieCrmEventProvider.createEvent(
    bankId = value.bankIdValue,
    crmEventId = value.crmEventIdValue,
    category = value.category,
    detail = value.detail,
    channel = value.channel,
    actualDate = value.actualDate,
    customerName = value.customerName,
    customerNumber = value.customerNumber
  )
}

case class SaveableBank(bankId: String, fullBankName: String, shortBankName: String,
                        logoURL: String, websiteURL: String) extends Saveable[MappedBank] {
  // Read before save() runs - createAccountsAndViews needs the bank ids while the rows are still
  // unwritten - so this is the transient row the import is about to store, not a row read back.
  // The now-removed MappedSaveable handed out the unsaved Mapper entity in exactly the same way.
  lazy val value: MappedBank = MappedBank(BankId(bankId), fullBankName, shortBankName, logoURL, websiteURL,
    swiftBic = "", nationalIdentifier = "", bankRoutingScheme = "", bankRoutingAddress = "",
    createdByUserId = "")
  def save(): Unit = {
    MappedBank.findByBankId(BankId(bankId)) match {
      case Full(_) =>
        MappedBank.updateByBankId(bankId, fullBankName, shortBankName, logoURL, websiteURL,
          swiftBIC = "", nationalIdentifier = "", bankRoutingScheme = "", bankRoutingAddress = "")
      case _ =>
        MappedBank.insert(bankId, fullBankName, shortBankName, logoURL, websiteURL,
          swiftBIC = "", nationalIdentifier = "", bankRoutingScheme = "", bankRoutingAddress = "",
          createdByUserId = "")
    }
    ()
  }
}

case class SaveableTransaction(bank: String, account: String, transactionId: String,
                               transactionType: String, amount: Long, newAccountBalance: Long,
                               currency: String, tStartDate: java.util.Date,
                               tFinishDate: java.util.Date, description: String,
                               counterpartyAccountHolder: String,
                               counterpartyAccountNumber: String)
  extends Saveable[MappedTransaction] {
  // Read both before and after save() runs, so this is the transient row the import is about to
  // store rather than a row read back - the same thing the now-removed MappedSaveable handed out. The
  // transactionUUID the store generates on write is not needed by any importer caller.
  lazy val value: MappedTransaction = MappedTransaction(bank, account, transactionId,
    transactionUUID = "", transactionType, amount, newAccountBalance, currency, tStartDate,
    tFinishDate, description, chargePolicy = "", counterpartyAccountHolder,
    counterpartyAccountKind = "", counterpartyBankName = "", counterpartyNationalId = "",
    counterpartyAccountNumber, counterpartyIban = "", CPCounterPartyId = "",
    CPOtherAccountProvider = "", CPOtherAccountRoutingScheme = "",
    CPOtherAccountRoutingAddress = "", CPOtherAccountSecondaryRoutingScheme = "",
    CPOtherAccountSecondaryRoutingAddress = "", CPOtherBankRoutingScheme = "",
    CPOtherBankRoutingAddress = "", status = "")
  def save(): Unit = {
    MappedTransaction.insert(bank = bank, account = account, transactionId = transactionId,
      transactionType = transactionType, amount = amount, newAccountBalance = newAccountBalance,
      currency = currency, tStartDate = tStartDate, tFinishDate = tFinishDate,
      description = description, counterpartyAccountHolder = counterpartyAccountHolder,
      counterpartyAccountNumber = counterpartyAccountNumber)
    ()
  }
}

case class SaveableAccount(accountId: String, bankId: String, accountLabel: String,
                           accountNumber: String, kind: String, accountCurrency: String,
                           accountBalance: Long) extends Saveable[MappedBankAccount] {
  // Read before save() runs - createTransactions needs the account ids while the rows are still
  // unwritten - so this is the transient row the import is about to store, as the now-removed MappedSaveable did.
  lazy val value: MappedBankAccount = MappedBankAccount(0L, bankId, accountId, accountCurrency,
    accountNumber, holder = "", accountBalance, accountName = "", kind, accountLabel,
    accountLastUpdate = null, branchId = "", accountRuleScheme1 = "", accountRuleValue1 = 0L,
    accountRuleScheme2 = "", accountRuleValue2 = 0L)
  def save(): Unit = {
    MappedBankAccount.insert(bankId = bankId, accountId = accountId, accountLabel = accountLabel,
      accountNumber = accountNumber, kind = kind, accountCurrency = accountCurrency,
      accountBalance = accountBalance)
    ()
  }
}

object LocalMappedConnectorDataImport extends OBPDataImport with CreateAuthUsers {

  // Rename these types as MappedCrmEventType etc? Else can get confused with other types of same name

  type BankType = MappedBank
  type AccountType = MappedBankAccount
  type MetadataType = MappedCounterpartyMetadata
  type TransactionType = MappedTransaction
  type BranchType = MappedBranch
  type AtmType = AtmT
  type ProductType = MappedProduct
  type CrmEventType = CrmEventCreateParams

  protected def createSaveableBanks(data : List[SandboxBankImport]) : Box[List[Saveable[BankType]]] = {
    // Bank persistence goes through the Doobie store, as with branches, products and ATMs: the
    // import must not write the row with Mapper while every read comes back through the store.
    // The importer supplies no BIC, national identifier or routing, and no creating user - the
    // same fields Mapper left at their defaults.
    Full(data.map(bank => SaveableBank(
      bankId = bank.id,
      fullBankName = bank.full_name,
      shortBankName = bank.short_name,
      logoURL = bank.logo,
      websiteURL = bank.website)))
  }

  protected def createSaveableBranches(data : List[SandboxBranchImport]) : Box[List[Saveable[BranchType]]] = {
    // Branch persistence goes through the Doobie store, as with products and ATMs: the import must
    // not write the row with Mapper while every read comes back through the store. The fields the
    // importer does not supply keep the defaults the store writes for them.
    val saveableBranches = data.map(branch => {

      val lobbyHours =  if (branch.lobby.isDefined) {branch.lobby.get.hours.toString} else ""
      val driveUpHours =  if (branch.driveUp.isDefined) {branch.driveUp.get.hours.toString} else ""

      SaveableBranch(
        branchId = branch.id,
        bankId = branch.bank_id,
        name = branch.name,
        // Note: address fields are returned in meta.address
        // but are stored flat as fields / columns in the table
        line1 = branch.address.line_1,
        line2 = branch.address.line_2,
        line3 = branch.address.line_3,
        city = branch.address.city,
        county = branch.address.county,
        state = branch.address.state,
        postCode = branch.address.post_code,
        countryCode = branch.address.country_code,
        latitude = branch.location.latitude,
        longitude = branch.location.longitude,
        licenseId = branch.meta.license.id,
        licenseName = branch.meta.license.name,
        lobbyHours = lobbyHours,
        driveUpHours = driveUpHours)
    })

    // Mapper ran field validation here; no validator was ever declared on the branch entity, so it
    // always passed and the column widths are what reject an over-long value.
    Full(saveableBranches)
  }



/////

  protected def createSaveableAtms(data : List[SandboxAtmImport]) : Box[List[Saveable[AtmType]]] = {
    val atms: List[AtmT] = data.map(atm =>
      Atms.Atm(
        atmId  = AtmId(atm.id),
        bankId = BankId(atm.bank_id),
        name   = atm.name,
        // Note: address fields are returned in meta.address but are stored flat as columns in the table
        address = Address(
          line1       = atm.address.line_1,
          line2       = atm.address.line_2,
          line3       = atm.address.line_3,
          city        = atm.address.city,
          county      = Some(atm.address.county),
          state       = atm.address.state,
          postCode    = atm.address.post_code,
          countryCode = atm.address.country_code
        ),
        location = Location(atm.location.latitude, atm.location.longitude, None, None),
        meta     = Meta(License(id = atm.meta.license.id, name = atm.meta.license.name)),
        OpeningTimeOnMonday = None, ClosingTimeOnMonday = None,
        OpeningTimeOnTuesday = None, ClosingTimeOnTuesday = None,
        OpeningTimeOnWednesday = None, ClosingTimeOnWednesday = None,
        OpeningTimeOnThursday = None, ClosingTimeOnThursday = None,
        OpeningTimeOnFriday = None, ClosingTimeOnFriday = None,
        OpeningTimeOnSaturday = None, ClosingTimeOnSaturday = None,
        OpeningTimeOnSunday = None, ClosingTimeOnSunday = None,
        isAccessible = None, locatedAt = None, moreInfo = None, hasDepositCapability = None
      )
    )

    Full(atms.map(SaveableAtm(_)))
  }


  protected def createSaveableProducts(data : List[SandboxProductImport]) : Box[List[Saveable[ProductType]]] = {
    // Product persistence goes through the Doobie store: the sandbox import must not write the row
    // with Mapper while every read of it comes back through the store. The fields the importer does
    // not supply keep the "" the store writes for them.
    val saveableProducts = data.map(product =>
      SaveableProduct(
        bankId = product.bank_id,
        code = product.code,
        name = product.name,
        category = product.category,
        family = product.family,
        superFamily = product.super_family,
        moreInfoUrl = product.more_info_url,
        licenseId = product.meta.license.id,
        licenseName = product.meta.license.name
      )
    )
    // Mapper ran field validation here; no validator was ever declared on the product entity, so
    // the check always passed and the column widths are what reject an over-long value.
    Full(saveableProducts)

  }


  protected def createSaveableCrmEvents(data : List[SandboxCrmEventImport]) : Box[List[Saveable[CrmEventType]]] = {


      val events = data.map(event => {
        // TODO Make so we can return any boxed error as below
        //scheduledDate <- tryo{dateFormat.parse(crmEvent.scheduled_date)} ?~ s"Invalid date format: ${crmEvent.scheduled_date}. Expected pattern $datePattern"
        //actualDate <- tryo{dateFormat.parse(crmEvent.actual_date)} ?~ s"Invalid date format: ${crmEvent.actual_date}. Expected pattern $datePattern"
        //val scheduledDate = dateFormat.parse(event.scheduled_date)
        val actualDate = dateFormat.parse(event.actual_date)

        logger.warn(s"Note: We are not saving API User, Result or Scheduled Date")

        val crmEvent = CrmEventCreateParams(
          bankIdValue = event.bank_id,
          crmEventIdValue = event.id,
          // UserId is a long - not set here, same as the Mapper version
          category = event.category,
          detail = event.detail,
          channel = event.channel,
          actualDate = actualDate,
          customerName = event.customer.name,
          customerNumber = event.customer.number
        )

        logger.debug(s"Saved CrmEvent id: ${crmEvent.crmEventId} customer name: ${crmEvent.customerName}")

        crmEvent
        }
      )

    Full(events.map(SaveableCrmEvent(_)))

  }


  protected def createSaveableAccount(acc : SandboxAccountImport, banks : List[BankType]) : Box[Saveable[AccountType]] = {

    val mappedAccount = for {
      balance <- tryo{BigDecimal(acc.balance.amount)} ?~ s"Invalid balance: ${acc.balance.amount}"
      currency = acc.balance.currency
    } yield {
      DoobieBankAccountRoutingQueries.create(BankId(acc.bank), AccountId(acc.id), AccountRoutingScheme.IBAN.toString, acc.IBAN)
      SaveableAccount(
        accountId = acc.id,
        bankId = acc.bank,
        accountLabel = acc.label,
        accountNumber = acc.number,
        kind = acc.`type`,
        accountCurrency = currency.toUpperCase,
        accountBalance = convertToSmallestCurrencyUnits(balance, currency))
    }

    mappedAccount
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

      SaveableTransaction(
        bank = t.this_account.bank,
        account = t.this_account.id,
        transactionId = t.id,
        transactionType = t.details.`type`,
        amount = convertToSmallestCurrencyUnits(tValueAsBigDecimal, currency),
        newAccountBalance = convertToSmallestCurrencyUnits(newBalanceValueAsBigDecimal, currency),
        currency = currency,
        tStartDate = postedDate,
        tFinishDate = completedDate,
        description = t.details.description,
        counterpartyAccountHolder = t.counterparty.flatMap(_.name).getOrElse(""),
        counterpartyAccountNumber = t.counterparty.flatMap(_.account_number).getOrElse(""))
    }
  }
  protected def createPublicView(bankId : BankId, accountId : AccountId, description: String) : Box[ViewType] = {
    Views.views.vend.getOrCreateCustomPublicView(bankId, accountId, description).asInstanceOf[Box[ViewType]]
  }
}
