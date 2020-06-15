package code.bankconnectors.vSept2018

import code.bankconnectors.ConnectorBuilderUtil

import scala.collection.immutable.List
import scala.language.postfixOps

object KafkaConnectorBuilder extends App {

  val genMethodNames = List(
//    "getKycChecks",
//    "getKycDocuments",
//    "getKycMedias",
//    "getKycStatuses",
//    "createOrUpdateKycCheck",
//    "createOrUpdateKycDocument",
//    "createOrUpdateKycMedia",
//    "createOrUpdateKycStatus",
//    "createCustomer",
    "createBankAccount",
  )

  ConnectorBuilderUtil.generateMethods(genMethodNames,
    "src/main/scala/code/bankconnectors/vSept2018/KafkaMappedConnector_vSept2018.scala",
     "processRequest[InBound](req)", true)
}





