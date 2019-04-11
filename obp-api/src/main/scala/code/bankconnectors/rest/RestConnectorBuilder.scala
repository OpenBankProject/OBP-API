package code.bankconnectors.rest

import code.bankconnectors.Connector
import scala.language.postfixOps
import scala.reflect.runtime.universe._
import scala.collection.immutable.List
import scala.reflect.runtime.{universe => ru}

object RestConnectorBuilder extends App{

  //  val value  = this.getBanksFuture(None)
  //  val value2  = this.getBankFuture(BankId("hello-bank-id"), None)
  //  Thread.sleep(10000)
  val genMethodNames = List("getAdapterInfoFuture", "getBanksFuture", "getBankFuture", "checkBankAccountExistsFuture", "getBankAccountFuture", "getCoreBankAccountsFuture", "getCustomersByUserIdFuture",
    "getCounterpartiesFuture", "getTransactionsFuture", "getTransactionFuture", "getAdapterInfoFuture"
  )

  private val mirror: ru.Mirror = ru.runtimeMirror(getClass().getClassLoader)
  private val clazz: ru.ClassSymbol = ru.typeOf[Connector].typeSymbol.asClass
  private val classMirror: ru.ClassMirror = mirror.reflectClass(clazz)
  private val nameSignature = ru.typeOf[Connector].decls
    .filter(_.isMethod)
    .filter(it => genMethodNames.contains(it.name.toString))
    .map(it => Generator(it.name.toString, it.typeSignature))


  //  private val types: Iterable[ru.Type] = symbols.map(_.typeSignature)
  //  println(symbols)
  println("-------------------")
  nameSignature.map(_.methodCode).foreach(println(_))
  println("===================")
}

case class Generator(methodName: String, tp: Type) {
  private[this] def paramAnResult = tp.toString.replaceAll("(\\w+\\.)+", "").replaceFirst("\\)", "): ")
  private[this] val params = tp.paramLists(0).dropRight(1).map(_.name.toString)
  private[this] val name = methodName.replaceFirst("Future$", "")
  private[this] val resultType = tp.resultType.toString.replaceAll("(\\w+\\.)+", "")

  val signature = s"$methodName$paramAnResult"
  val pathVariables = params.map(it => s""", ("$it", $it)""").mkString
  val urlDemo = s"/$name" + params.map(it => s"/$it/{$it}").mkString
  val jsonType = "InBound" + methodName.capitalize
  val lastMapStatement = if(resultType.startsWith("Future[Box[")) {
   """|                    boxedResult.map { result =>
      |                         (result.data, buildCallContext(result.authInfo, callContext))
      |                    }
    """.stripMargin
  } else {
   """|                    boxedResult match {
      |                        case Full(result) => (Full(result.data), buildCallContext(result.authInfo, callContext))
      |                        case result: EmptyBox => (result, callContext) // Empty and Failure all match this case
      |                    }
    """.stripMargin
  }
  val methodCode =
    s"""
       |  // url example: $urlDemo
       |  override def $signature = saveConnectorMetric {
       |    /**
       |      * Please note that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
       |      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
       |      * The real value will be assigned by Macro during compile time at this line of a code:
       |      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
       |      */
       |    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
       |    CacheKeyFromArguments.buildCacheKey {
       |      Caching.memoizeWithProvider(Some(cacheKey.toString()))(banksTTL second){
       |        val url = getUrl("$name" $pathVariables)
       |        sendGetRequest[$jsonType](url, callContext)
       |          .map { boxedResult =>
       |             $lastMapStatement
       |          }
       |      }
       |    }
       |  }("$name")
    """.stripMargin
}