package code.bankconnectors.akka

import code.bankconnectors.ConnectorBuilderUtil._

import scala.language.postfixOps

object AkkaConnectorBuilder extends App {

  generateMethods(commonMethodNames,
    "src/main/scala/code/bankconnectors/akka/AkkaConnector_vDec2018.scala",
    "(southSideActor ? req).mapTo[InBound].map(Box !! _)")
}
