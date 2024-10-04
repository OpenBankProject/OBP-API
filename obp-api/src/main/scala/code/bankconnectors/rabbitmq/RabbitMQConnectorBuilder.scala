package code.bankconnectors.rabbitmq

import code.bankconnectors.ConnectorBuilderUtil._
import net.liftweb.util.StringHelpers

import scala.language.postfixOps

object RabbitMQConnectorBuilder extends App {

  buildMethods(commonMethodNames,
    "src/main/scala/code/bankconnectors/storedprocedure/StoredProcedureConnector_vDec2019.scala",
     methodName => s"""sendRequest[InBound]("obp_${StringHelpers.snakify(methodName)}", req, callContext)""")
}

