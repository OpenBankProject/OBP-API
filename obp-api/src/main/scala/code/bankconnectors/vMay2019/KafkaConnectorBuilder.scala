package code.bankconnectors.vMay2019

import code.bankconnectors.ConnectorBuilderUtil._

import scala.collection.immutable.List
import scala.language.postfixOps

object KafkaConnectorBuilder extends App {

  val genMethodNames = List(
    "getAdapterInfo",
    "getBank",
    "getBanks",
    "getBankAccountsBalances",
    "getBranch",
    "getBranches",
    "getAtm",
    "getAtms",
    "getCustomersByUserId",
    "getCustomerByCustomerId",
    "getCustomerByCustomerNumber"
  )

  generateMethods(commonMethodNames,
    "src/main/scala/code/bankconnectors/vMay2019/KafkaMappedConnector_vMay2019.scala",
    "processRequest[InBound](req)", true)
}




