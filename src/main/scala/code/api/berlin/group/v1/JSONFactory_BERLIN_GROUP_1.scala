package code.api.berlin.group.v1

import code.model.CoreAccount

import scala.collection.immutable.List

object JSONFactory_BERLIN_GROUP_1 {

  implicit val formats = net.liftweb.json.DefaultFormats

  trait links
  case class Balances(balances: String) extends links
  case class Transactions(trasactions: String) extends links
  case class CoreAccountJson_v1(
                                 id: String,
                                 iban: String,
                                 currency: String,
                                 accountType: String,
                                 cashAccountType: String,
                                 _links: List[links],
                                 name: String
                               )

  case class CoreAccountsJson_v1(`account-list`: List[CoreAccountJson_v1])

  def createCoreAccountsByCoreAccountsJSON(coreAccounts: List[CoreAccount]) = {
    CoreAccountsJson_v1(coreAccounts.map(
      x => CoreAccountJson_v1(
        id = x.id,
        iban = if (x.account_routing.scheme == "IBAN") x.account_routing.address else "",
        currency = "",
        accountType = "",
        cashAccountType = "",
        _links = Nil,
        name = x.label)
       )
    )
  }


}
