package code.bankconnectors

import scala.concurrent.Future
import scala.reflect.runtime.{universe => ru}

object InOutCaseClassGenerator extends App {

  def extractReturnModel(tp: ru.Type): ru.Type = {
    if (tp.typeArgs.isEmpty) {
      tp
    } else {
      extractReturnModel(tp.typeArgs(0))
    }
  }

  private val mirror: ru.Mirror = ru.runtimeMirror(this.getClass.getClassLoader)
  private val clazz: ru.ClassSymbol = mirror.typeOf[Connector].typeSymbol.asClass
  private val retureFutureMethods: Iterable[ru.MethodSymbol] = mirror.typeOf[Connector].decls.filter(symbol => {
    val isMethod = symbol.isMethod && !symbol.asMethod.isVal && !symbol.asMethod.isVar && !symbol.asMethod.isConstructor
    isMethod
  }).map(it => it.asMethod)
    .filterNot(it => it.returnType <:< ru.typeOf[Future[_]])
    .filter(it => {
      extractReturnModel(it.returnType).typeSymbol.fullName.matches("(code\\.|com.openbankproject\\.).+")
    })


  val code = retureFutureMethods.map(it => {
    val returnType = it.returnType
    val tp = extractReturnModel(returnType)
    val isCaseClass = tp.typeSymbol.asClass.isCaseClass
    var payload = returnType.toString
      .replaceAll("([\\w\\.]+\\.)", "")
      .replaceFirst("OBPReturnType\\[Box\\[(.*)\\]\\]$", "$1")
      .replaceFirst("Future\\[Box\\[\\((.*), Option\\[CallContext\\]\\)\\]\\]$", "$1")
    if(!isCaseClass) {
      val name = tp.typeSymbol.name.toString
      println(name)
      payload = payload.replace(name, name+"Commons")
    }
    var parameters = it.asMethod.typeSignature.toString.replaceAll("([\\w\\.]+\\.)", "")
    if(parameters.startsWith("(callContext: Option[CallContext])")) {
      parameters = ""
    } else {
      parameters = parameters.replaceFirst("^\\(", ", ").replaceFirst(", callContext: Option.*$", "").replace(",", ",\n")
    }
    s"""
       |case class OutBound${it.name.toString.capitalize} (adapterCallContext: AdapterCallContext$parameters)
       |case class InBound${it.name.toString.capitalize} (adapterCallContext: OutboundAdapterCallContext, data: $payload)
     """.stripMargin
  })
  code.foreach(println)
  println()

}
