package code.bankconnectors.vMay2019

import java.io.File
import java.util.Date
import java.util.regex.Matcher

import code.api.util.ApiTag.ResourceDocTag
import code.api.util.{ApiTag, CallContext, OBPQueryParam}
import code.bankconnectors.{Connector, ConnectorBuilderUtil}
import com.openbankproject.commons.util.ReflectUtils
import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils._

import scala.collection.immutable.List
import scala.language.postfixOps
import scala.reflect.runtime.universe._
import scala.reflect.runtime.{universe => ru}
import code.api.util.CodeGenerateUtils.createDocExample
import code.bankconnectors.vSept2018.KafkaConnectorBuilder.genMethodNames
import javassist.ClassPool

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

  ConnectorBuilderUtil.generateMethods(genMethodNames,
    "src/main/scala/code/bankconnectors/vMay2019/KafkaMappedConnector_vMay2019.scala",
    "processRequest[InBound](req)", true)
}




