package code.bankconnectors.rest

import code.bankconnectors.ConnectorBuilderUtil._

import scala.language.postfixOps

object RestConnectorBuilder extends App {

  buildMethods(commonMethodNames,
    "src/main/scala/code/bankconnectors/rest/RestConnector_vMar2019.scala",
    methodName => s"""sendRequest[InBound](getUrl(callContext, "$methodName"), HttpMethods.POST, req, callContext)""")
}

