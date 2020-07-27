package code.api.util.migration

import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}

import code.api.util.APIUtil
import code.api.util.migration.Migration.{DbFunction, saveLog}
import code.model.dataAccess.{BankAccountRouting, MappedBankAccount}
import net.liftweb.mapper.{DB, NotNullRef}
import net.liftweb.util.DefaultConnectionIdentifier

object MigrationOfAccountRoutings {

  val oneDayAgo = ZonedDateTime.now(ZoneId.of("UTC")).minusDays(1)
  val oneYearInFuture = ZonedDateTime.now(ZoneId.of("UTC")).plusYears(1)
  val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'")

  def populate(name: String): Boolean = {
    DbFunction.tableExists(BankAccountRouting, (DB.use(DefaultConnectionIdentifier) { conn => conn })) match {
      case true =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit

        val accountsIban = MappedBankAccount.findAll(
          NotNullRef(MappedBankAccount.accountIban)
        )

        val accountsOtherScheme = MappedBankAccount.findAll(
          NotNullRef(MappedBankAccount.mAccountRoutingScheme),
          NotNullRef(MappedBankAccount.mAccountRoutingAddress)
        )

        // Make back up
        DbFunction.makeBackUpOfTable(MappedBankAccount)

        // Add iban rows into table "BankAccountRouting"
        val addIbanRows: List[Boolean] = {
          val definedAccountsIban = accountsIban.filter(_.iban.getOrElse("").nonEmpty)
          for {
            accountIban <- definedAccountsIban
          } yield {
            BankAccountRouting.create
              .BankId(accountIban.bankId.value)
              .AccountId(accountIban.accountId.value)
              .AccountRoutingScheme("IBAN")
              .AccountRoutingAddress(accountIban.iban.get)
              .save()
          }
        }

        // Add other routing scheme rows into table "BankAccountRouting"
        val addOtherRoutingSchemeRows: List[Boolean] = {
          val accountsOtherSchemeNonDuplicatedIban = accountsOtherScheme
            .filterNot(a =>
              (a.iban.getOrElse("").nonEmpty && a.accountRoutingScheme == "IBAN")
                || a.accountRoutingScheme.isEmpty || a.accountRoutingAddress.isEmpty
            )
          for {
            accountOtherScheme <- accountsOtherSchemeNonDuplicatedIban
          } yield {
            BankAccountRouting.create
              .BankId(accountOtherScheme.bankId.value)
              .AccountId(accountOtherScheme.accountId.value)
              .AccountRoutingScheme(accountOtherScheme.accountRoutingScheme)
              .AccountRoutingAddress(accountOtherScheme.accountRoutingAddress)
              .save()
          }
        }


        val isSuccessful = addIbanRows.forall(_ == true) && addOtherRoutingSchemeRows.forall(_ == true)
        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""Number of iban rows inserted at table BankAccountRouting: ${addIbanRows.size}
             |Number of other routing scheme rows inserted at table BankAccountRouting: ${addOtherRoutingSchemeRows.size}
             |""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful

      case false =>
        val startDate = System.currentTimeMillis()
        val commitId: String = APIUtil.gitCommit
        val isSuccessful = false
        val endDate = System.currentTimeMillis()
        val comment: String =
          s"""BankAccountRouting table does not exist""".stripMargin
        saveLog(name, commitId, isSuccessful, startDate, endDate, comment)
        isSuccessful
    }
  }
}
