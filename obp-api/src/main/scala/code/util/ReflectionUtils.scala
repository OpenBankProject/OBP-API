package code.util

import java.util.Date

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
    if (tp.typeSymbol.fullName.startsWith("com.openbankproject.commons.")) {
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
        val valueName = symbol.name.toString.replaceFirst("^type$", "`type`")
        s"""$str,
           |${valueName}=${value}""".stripMargin
      }).substring(2)
      val withNew = if(!tp.typeSymbol.asClass.isCaseClass) "new" else ""
      s"$withNew ${tp.typeSymbol.name}($fields)"
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


  /**
    * convert a object to it's sibling, please have a loot the example:
    * trait Base {
    *     def value: String
    *     def size: Long
    * }
    * class SomeImp extends Base {
    *     override def value: String = "some value"
    *     override def size: Long = 123L
    * }
    * case class BaseCommons(value: String, size: Long) extends Base
    *
    * val base: Base = new SomeImp()
    *
    * val commons: BaseCommons = toOther[BaseCommons](base)
    *
    * So in this way, we can get the sibling object of SomeImp.
    *
    * @param t will do convert object
    * @tparam T expected type, it should have no default constructor
    * @return the expected value
    */
  def toOther[T: TypeTag](t: Any): T = {
    val expectType: ru.Type = typeTag[T].tpe
    if(expectType.typeSymbol.isAbstract) {
      throw new IllegalArgumentException(s"expected type is abstract: $expectType")
    }
    val constructor: ru.MethodSymbol = expectType.decl(ru.termNames.CONSTRUCTOR).asMethod
    val mirrorClass: ru.ClassMirror = mirror.reflectClass(expectType.typeSymbol.asClass)

    val paramNames = constructor.paramLists(0).map(_.name.toString)
    val mirrorObj = mirror.reflect(t)
    val methodSymbols = paramNames.map(name => mirrorObj.symbol.info.decl(ru.TermName(name)).asMethod)
    val methodMirrors: Seq[ru.MethodMirror] = methodSymbols.map(mirrorObj.reflectMethod(_))
    val seq = methodMirrors.map(_())

    mirrorClass.reflectConstructor(constructor).apply(seq :_*).asInstanceOf[T]
  }

  /**
    * convert a group of object to it's siblings
    * @param items will do convert
    * @tparam T expected type
    * @return expected values
    */
  def toOther[T: TypeTag](items: List[_]): List[T] = items.map(toOther[T](_))
}
