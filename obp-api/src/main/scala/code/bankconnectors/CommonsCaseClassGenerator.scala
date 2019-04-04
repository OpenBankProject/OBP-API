package code.bankconnectors

import scala.concurrent.Future
import scala.reflect.runtime.{universe => ru}

object CommonsCaseClassGenerator extends App {

  def extractReturnModel(tp: ru.Type): ru.Type = {
    if (tp.typeArgs.isEmpty) {
      tp
    } else {
      extractReturnModel(tp.typeArgs(0))
    }
  }

  private val mirror: ru.Mirror = ru.runtimeMirror(this.getClass.getClassLoader)
  private val clazz: ru.ClassSymbol = mirror.typeOf[Connector].typeSymbol.asClass
  private val retureFutureMethods: Iterable[ru.Type] = mirror.typeOf[Connector].decls.filter(symbol => {
    val isMethod = symbol.isMethod && !symbol.asMethod.isVal && !symbol.asMethod.isVar && !symbol.asMethod.isConstructor
    isMethod
  }).map(it => it.asMethod.returnType)
    .filter(it => it <:< ru.typeOf[Future[_]])

  val returnModels: Iterable[ru.Type] = retureFutureMethods
    .map(extractReturnModel)
    .filter(it => {
      val symbol = it.typeSymbol
//      val isAbstract = symbol.isAbstract
      val isOurClass = symbol.fullName.matches("(code\\.|com.openbankproject\\.).+")
      //isAbstract &&
        isOurClass
    }).toSet

  returnModels.map(_.typeSymbol.fullName).foreach(it => println(s"import $it"))

  def mkClass(tp: ru.Type) = {
    val varibles = tp.decls.map(it => s"${it.name} :${it.typeSignature.typeSymbol.name}").mkString(", \n    ")

      s"""
         |case class ${tp.typeSymbol.name}Commons(
         |    $varibles) extends ${tp.typeSymbol.name}
       """.stripMargin
  }
 // private val str: String = ru.typeOf[Bank].decls.map(it => s"${it.name} :${it.typeSignature.typeSymbol.name}").mkString(", \n")
  returnModels.map(mkClass).foreach(println)
  println()

}
