package code.sandbox

import java.text.SimpleDateFormat
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

object OBPDataImport extends SimpleInjector {

  val importer =  new Inject(buildOne _) {}

  def buildOne : OBPDataImport = LocalConnectorDataImport

}

trait OBPDataImport {
  val datePattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
  val dateFormat = new SimpleDateFormat(datePattern)

  /**
   * @param data
   * @return A full box if the import worked, or else a failure describing what went wrong
   */
  //TODO: might be nice to use something like scalaz's validations here
  def importData(data : SandboxDataImport) : Box[Unit]
}


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
  amount : String)

case class SandboxTransactionImport(
  id : String,
  this_account : SandboxAccountIdImport,
  counterparty : Option[SandboxTransactionCounterparty],
  details : SandboxAccountDetailsImport)

case class SandboxTransactionCounterparty(
  name : Option[String],
  account_number : Option[String])

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