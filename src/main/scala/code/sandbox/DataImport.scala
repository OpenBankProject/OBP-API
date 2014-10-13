package code.sandbox

import net.liftweb.common.{Full, Failure, Box}


case class SandboxBankImport(
  id : String,
  short_name : String,
  full_name : String,
  logo : String,
  website : String)

case class SandboxUserImport(
  email : String,
  password : String,
  display_name : String)

case class SandboxAccountImport(
  id : String,
  bank : String,
  label : String,
  number : String,
  `type` : String,
  balance : SandboxBalanceImport,
  IBAN : String,
  owners : List[String],
  generate_public_view : Boolean)

case class SandboxBalanceImport(
  currency : String,
  amount : Double)

case class SandboxTransactionImport(
  id : String,
  this_account : SandboxAccountIdImport,
  counterparty : Option[SandboxAccountIdImport],
  details : SandboxAccountDetailsImport)

case class SandboxAccountIdImport(
  id : String,
  bank : String)

case class SandboxAccountDetailsImport(
  `type` : String,
  description : String,
  posted : String,
  completed : String,
  new_balance : String,
  value : String)

case class SandboxDataImport(
  banks : List[SandboxBankImport],
  users : List[SandboxUserImport],
  accounts : List[SandboxAccountImport],
  transactions : List[SandboxTransactionImport])

object DataImport {

  /**
   * @param data
   * @return A full box if the import worked, or else a failure describing what went wrong
   */
  def importData(data : SandboxDataImport) : Box[Unit] = {

    def createBanks() : Box[Unit] = {
      Failure("TODO")
    }

    def createUsers() : Box[Unit] = {
      Failure("TODO")
    }

    def createAccounts() : Box[Unit] = {
      Failure("TODO")
    }

    def createTransactions() : Box[Unit] = {
      Failure("TODO")
    }

    for {
      banks <- createBanks()
      users <- createUsers()
      accounts <- createAccounts()
      transactions <- createTransactions()
    } yield {
      Full(Unit)
    }
  }

}
