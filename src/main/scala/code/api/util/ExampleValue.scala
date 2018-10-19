package code.api.util

object ExampleValue {

  case class ConnectorField(value: String, description: String) {

    def verbose: String = {

      s"${value} :: ${description}".toString
    }

  }

  val bankIdExample = ConnectorField("bankId1", "bankId is a string that MUST uniquely identify the bank on this OBP instance. It COULD be a UUID but is generally a short string that easily identifies the bank / brand it represents.")


}



