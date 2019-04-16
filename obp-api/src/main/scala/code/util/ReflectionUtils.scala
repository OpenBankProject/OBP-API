package code.util

import java.util.Date

import com.openbankproject.commons.dto.rest.OutBoundMakePaymentv210

import scala.language.postfixOps
import scala.reflect.runtime.universe._
import scala.reflect.runtime.{universe => ru}
import com.openbankproject.commons.model.{AccountAttributeType, ProductAttributeType}

import scala.collection.immutable.List

object reflectionUtils {
  private[this] val mirror: ru.Mirror = ru.runtimeMirror(getClass().getClassLoader)

  private[this] def genericSymboToString(tp: ru.Type): String = {
    if (tp.typeArgs.isEmpty) {
      createDocExample(tp)
    } else {
      val value = tp.typeArgs.map(genericSymboToString).mkString(",")
      s"${tp.typeSymbol.name}(${value})".replaceFirst("Tuple\\d*", "")
    }
  }

  def createDocExample(tp: ru.Type): String = {
    if (tp.typeSymbol.asClass.isCaseClass) {
      val fields = tp.decls.find(it => it.isConstructor).toList.flatMap(_.asMethod.paramLists(0)).foldLeft("")((str, symbol) => {
        val TypeRef(pre: Type, sym: Symbol, args: List[Type]) = symbol.info
        val value = if (pre <:< ru.typeOf[ProductAttributeType.type]) {
          "ProductAttributeType.STRING"
        } else if (pre <:< ru.typeOf[AccountAttributeType.type]) {
          "AccountAttributeType.INTEGER"
        } else if (args.isEmpty && sym.isClass && sym.asClass.isTrait) {
          val commonClass = reflectionUtils.getTypeByName(s"com.openbankproject.commons.model.${sym.name}Commons")
            createDocExample(commonClass)
        } else if (args.isEmpty) {
          createDocExample(sym.asType.toType)
        } else {
          val typeParamStr = args.map(genericSymboToString).mkString(",")
          s"${sym.name}($typeParamStr)"
        }
        val valueName = if(symbol.name.toString == "type") "`type`" else symbol.name.toString
        s"""$str,
           |${valueName}=${value}""".stripMargin
      }).substring(2)
      s"${tp.typeSymbol.name}($fields)"
    } else if (tp =:= ru.typeOf[String]) {
      """"string""""
    } else if (tp =:= ru.typeOf[Int] || tp =:= ru.typeOf[java.lang.Integer] || tp =:= ru.typeOf[Long] || tp =:= ru.typeOf[java.lang.Long]) {
      "123"
    } else if (tp =:= ru.typeOf[Float] || tp =:= ru.typeOf[Double] || tp =:= ru.typeOf[java.lang.Float] || tp =:= ru.typeOf[java.lang.Double] || tp =:= ru.typeOf[BigDecimal] || tp =:= ru.typeOf[java.math.BigDecimal]) {
      "123.123"
    } else if (tp =:= ru.typeOf[Date]) {
      "new Date()"
    } else if (tp =:= ru.typeOf[Boolean] || tp =:= ru.typeOf[java.lang.Boolean]) {
      "true"
    } else {
      throw new IllegalStateException(s"type $tp is not supported, please add this type to here.")
    }
  }

  def getTypeByName(typeName: String, mirror: ru.Mirror = this.mirror): ru.Type = mirror.staticClass(typeName).asType.toType

  def isTypeExists(typeName: String): Boolean = try {
    getTypeByName(typeName)
    true
  } catch {
    case e => false
  }

  /**
    * get all nest type, e.g:
    *     Future[Box[(CheckbookOrdersJson, Option[CallContext])]] -> List(CheckbookOrdersJson)
    *     OBPReturnType[Box[List[(ProductCollectionItem, Product, List[ProductAttribute])]]] -> List(ProductCollectionItem, Product, List[ProductAttribute])
    * @param tp a Type do check deep generic types
    * @return deep type of generic
    */
  def getDeepGenericType(tp: ru.Type): List[ru.Type] = {
    if (tp.typeArgs.isEmpty) {
      List(tp)
    } else {
      tp.typeArgs.flatMap(getDeepGenericType)
    }
  }

  /**
    * check whether symbol is case class
    * @param symbol
    * @return
    */
  def isCaseClass(symbol: Symbol): Boolean = symbol.isType && symbol.asType.isClass && symbol.asType.asClass.isCaseClass
}
