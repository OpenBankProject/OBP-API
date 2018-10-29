package code.api.util


import code.api.util.Glossary.glossaryItems

object ExampleValue {

  case class ConnectorField(value: String, description: String) {

    def valueAndDescription: String = {
      s"${value} : ${description}".toString
    }

  }

  val bankIdGlossary = glossaryItems.find(_.title == "Bank.bank_id").map(_.textDescription)

  val bankIdExample = ConnectorField("bankId1", s"A string that MUST uniquely identify the bank on this OBP instance. It COULD be a UUID but is generally a short string that easily identifies the bank / brand it represents.")

  val accountIdExample = ConnectorField("8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0", s"A string that MUST uniquely identify the account on this OBP instance. A non reversible hash of the human readable account number.")



}



