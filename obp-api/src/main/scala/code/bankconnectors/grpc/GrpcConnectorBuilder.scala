package code.bankconnectors.grpc

import code.bankconnectors.generator.ConnectorBuilderUtil._
import net.liftweb.util.StringHelpers

import scala.language.postfixOps

object GrpcConnectorBuilder extends App {

  buildMethods(commonMethodNames.diff(omitMethods),
    "src/main/scala/code/bankconnectors/grpc/GrpcConnector_vFeb2026.scala",
     methodName => s"""sendRequest[InBound]("obp_${StringHelpers.snakify(methodName)}", req, callContext)""")
}
