package code.bankaccountcreation

import code.api.test.ServerSetup
import code.api.{DefaultConnectorTestSetup, DefaultUsers}
import code.bankconnectors.Connector
import code.model.{AccountId, BankId}
import org.scalatest.Tag

class BankAccountCreationTest extends ServerSetup with DefaultUsers with DefaultConnectorTestSetup {

  object BankAccountCreation extends Tag("account_creation")

  override def beforeEach() = {
    super.beforeEach()
    wipeTestData()
  }

  override def afterEach() = {
    super.afterEach()
    wipeTestData()
  }

  feature("Bank and bank account creation") {

    val accountNumber = "12313213"
    val accountHolderName = "Rolf Rolfson"

    scenario("Creating an account for a bank that does not exist yet") {

      val bankNationalIdentifier = "bank-identifier"
      val bankName = "A Bank"
      Given("A bank that does not exist")
      Connector.connector.vend.getBanks.size should equal(0)

      When("We create an account at that bank")
      val (_, returnedAccount) = Connector.connector.vend.createBankAndAccount(bankName, bankNationalIdentifier, accountNumber, accountHolderName)

      Then("A bank should now exist, with the correct parameters")
      val allBanks = Connector.connector.vend.getBanks
      allBanks.size should equal(1)
      val newBank = allBanks(0)
      newBank.fullName should equal(bankName)
      newBank.nationalIdentifier should equal(bankNationalIdentifier)

      And("An account should now exist, with the correct parameters")
      val foundAccountBox = Connector.connector.vend.getBankAccount(newBank.bankId, returnedAccount.accountId)
      foundAccountBox.isDefined should equal(true)
      val foundAccount = foundAccountBox.get

      foundAccount.number should equal(accountNumber)
      foundAccount.accountHolder should equal(accountHolderName)
    }

    scenario("Creating an account for a bank that already exists") {
      val existingBank = createBank("some-bank")

      Given("A bank that does exist")
      val allBanksBefore = Connector.connector.vend.getBanks
      allBanksBefore.size should equal(1)
      allBanksBefore(0).bankId should equal(existingBank.bankId)


      When("We create an account at that bank")
      val (_, returnedAccount) = Connector.connector.vend.createBankAndAccount(existingBank.fullName, existingBank.nationalIdentifier, accountNumber, accountHolderName)

      Then("No new bank should be created")
      val allBanksAfter = Connector.connector.vend.getBanks
      allBanksAfter.size should equal(1)
      allBanksAfter(0).fullName should equal(existingBank.fullName)
      allBanksAfter(0).nationalIdentifier should equal(existingBank.nationalIdentifier)

      And("An account should now exist, with the correct parameters")
      val foundAccountBox = Connector.connector.vend.getBankAccount(existingBank.bankId, returnedAccount.accountId)
      foundAccountBox.isDefined should equal(true)
      val foundAccount = foundAccountBox.get

      foundAccount.number should equal(accountNumber)
      foundAccount.accountHolder should equal(accountHolderName)
    }

  }

  feature("Bank account creation that fails if the associated bank doesn't exist") {

    val bankId = BankId("some-bank")
    val accountId = AccountId("some-account")
    val currency = "EUR"
    val initialBalance = BigDecimal("1000.00")
    val accountHolderName = "Some Person"
    val defaultAccountNumber = "1231213213"

    scenario("Creating a bank account when the associated bank does not exist") {
      Given("A bank that doesn't exist")
      Connector.connector.vend.getBank(bankId).isDefined should equal(false)

      When("We try to create an account at that bank")
      Connector.connector.vend.createSandboxBankAccount(bankId, accountId, defaultAccountNumber, currency, initialBalance, accountHolderName)

      Then("No account is created")
      Connector.connector.vend.getBankAccount(bankId, accountId).isDefined should equal(false)

    }

    scenario("Creating a bank account with an account number") {
      Given("A bank that does exist")
      createBank(bankId.value)
      Connector.connector.vend.getBank(bankId).isDefined should equal(true)

      When("We try to create an account at that bank")
      Connector.connector.vend.createSandboxBankAccount(bankId, accountId, defaultAccountNumber, currency, initialBalance, accountHolderName)

      Then("An account with the proper parameters should be created")
      val createdAccBox = Connector.connector.vend.getBankAccount(bankId, accountId)
      createdAccBox.isDefined should be(true)
      val createdAcc = createdAccBox.get

      createdAcc.bankId should equal(bankId)
      createdAcc.accountId should equal(accountId)
      createdAcc.balance should equal(initialBalance)
      createdAcc.currency should equal(currency)
      createdAcc.number should equal(defaultAccountNumber)
      createdAcc.accountHolder should equal(accountHolderName)
    }

    scenario("Creating a bank account without an account number") {
      Given("A bank that does exist")
      createBank(bankId.value)
      Connector.connector.vend.getBank(bankId).isDefined should equal(true)

      When("We try to create an account at that bank")
      Connector.connector.vend.createSandboxBankAccount(bankId, accountId, currency, initialBalance, accountHolderName)

      Then("An account with the proper parameters should be created")
      val createdAccBox = Connector.connector.vend.getBankAccount(bankId, accountId)
      createdAccBox.isDefined should be(true)
      val createdAcc = createdAccBox.get

      createdAcc.bankId should equal(bankId)
      createdAcc.accountId should equal(accountId)
      createdAcc.balance should equal(initialBalance)
      createdAcc.currency should equal(currency)
      createdAcc.accountHolder should equal(accountHolderName)

      //Account number should be autogenerated
      createdAcc.number.nonEmpty should equal(true)

    }

  }



}
