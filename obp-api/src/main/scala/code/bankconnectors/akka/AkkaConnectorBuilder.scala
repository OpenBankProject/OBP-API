package code.bankconnectors.akka

import code.bankconnectors.ConnectorBuilderUtil._

import scala.language.postfixOps

object AkkaConnectorBuilder extends App {

  val createManually = List(
    "getAdapterInfo",
    "getBanks",
    "getBank",
    "getBankAccountsForUser",
    "checkBankAccountExists",
    "getBankAccount",
    "getCoreBankAccounts",
    "getCustomersByUserId",
    "getTransactions",
    "getTransaction",
  )
  // exclude manually created methods
  val toGenerateMethodNames = commonMethodNames.filterNot(createManually.contains)

  generateMethods(toGenerateMethodNames,
    "src/main/scala/code/bankconnectors/akka/AkkaConnector_vDec2018.scala",
    "(southSideActor ? req).mapTo[InBound].recoverWith(recoverFunction).map(Box !! _) ")
}
