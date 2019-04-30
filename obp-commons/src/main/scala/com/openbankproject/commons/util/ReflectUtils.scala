package com.openbankproject.commons.util

import java.util.Date

import com.openbankproject.commons.model.{AccountAttributeType, ProductAttributeType}
import net.liftweb.common.Box

import scala.collection.immutable.List
import scala.language.postfixOps
import scala.reflect.runtime.universe._
import scala.reflect.runtime.{universe => ru}

object ReflectUtils {
  private[this] val mirror: ru.Mirror = ru.runtimeMirror(getClass().getClassLoader)

  private[this] def genericSymbolToString(tp: ru.Type): String = {
    if (tp.typeArgs.isEmpty) {
      createDocExample(tp)
    } else {
      val value = tp.typeArgs.map(genericSymbolToString).mkString(",")
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
          val commonClass = ReflectUtils.getTypeByName(s"com.openbankproject.commons.model.${sym.name}Commons")
            createDocExample(commonClass)
        } else if (args.isEmpty) {
          createDocExample(sym.asType.toType)
        } else {
          val typeParamStr = args.map(genericSymbolToString).mkString(",")
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
    case _ => false
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


  def invokeMethod(obj: AnyRef, methodName: String, args: Any*): Any = {
    val objMirror = mirror.reflect(obj)
    val methodSymbol: Option[ru.MethodSymbol] = findMethod(obj, methodName) { nameToType => {
        args.size == args.size && nameToType.values.zip(args).forall(it => isTypeOf(it._1, it._2))
      }
    }

    if (methodSymbol.isEmpty) {
      throw new IllegalArgumentException(s"not found method $methodName match the parameters: $args")
    }
    objMirror.reflectMethod(methodSymbol.get).apply(args: _*)
  }

  def invokeConstructor(tp: ru.Type)(fn: (Seq[ru.Type]) => Seq[Any]): Any = {
    val classMirror = mirror.reflectClass(tp.typeSymbol.asClass)
    val constructor = tp.decl(ru.termNames.CONSTRUCTOR).asMethod
    val paramTypes: Seq[ru.Type] = constructor.paramLists.headOption.getOrElse(Nil).map(_.info.typeSymbol.asType.toType)
    val params: Seq[Any] = fn.apply(paramTypes)
    classMirror.reflectConstructor(constructor).apply(params :_*)
  }

  def invokeConstructor(tp: ru.Type, args: Any*): Any = invokeConstructor(tp)(_ => args.toList)

  def isTypeOf(tp: ru.Type, obj: Any):Boolean = {
    if(obj == null || mirror.classSymbol(obj.getClass).toType <:< tp) {
      true
    } else {
      obj match {
        case _: java.lang.Byte => tp =:= typeOf[Byte]
        case _: java.lang.Short => tp =:= typeOf[Short]
        case _: java.lang.Character => tp =:= typeOf[Char]
        case _: java.lang.Integer => tp =:= typeOf[Int]
        case _: java.lang.Long => tp =:= typeOf[Long]
        case _: java.lang.Float => tp =:= typeOf[Float]
        case _: java.lang.Double => tp =:= typeOf[Double]
        case _: java.lang.Boolean => tp =:= typeOf[Boolean]
        case _ => false
      }
    }
  }

  def findMethod(tp: ru.Type, methodName: String)(predicate: Map[String, ru.Type] => Boolean): Option[MethodSymbol] = {
    tp.members.filter(it => it.isMethod && it.name.toString == methodName)
        .map(_.asMethod)
        .find(it => {
          val paramNameToType = it.paramLists.headOption.getOrElse(Nil).map(i => (i.name.toString, i.info)).toMap
          predicate(paramNameToType)
        })
  }

  def findMethod(obj: Any, methodName: String)(predicate: Map[String, ru.Type] => Boolean): Option[MethodSymbol] = findMethod(getType(obj), methodName)(predicate)

  def getType(obj: Any): ru.Type = mirror.reflect(obj).symbol.toType


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
  def toOther[T](t: Any, expectType: ru.Type): T = {
    if(isTypeOf(expectType, t)) {
      return t.asInstanceOf[T]
    }
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

  def toOther[T: TypeTag](t: Any): T = toOther[T](t, typeTag[T].tpe)

  def toOther[T](t: Any, typeName: String): T = {
    val tp: ru.Type = mirror.staticClass(typeName).toType
    toOther[T](t, tp)
  }

  /**
    * some object can't be serialized to json, because some value is method.
    * this function to convert object to value object, e.g:
    * class Foo{
    *   def foo:String = "hello"
    * }
    * -->
    * Map(("foo": "hello"))
    *
    * List(Foo())
    * -->
    * List(Map(("foo": "hello")))
    * @param t
    * @return
    */
  def toValueObject(t: Any): Any = {
    t match {
      case null => null
      case it: Iterable[_] => it.map(toValueObject)
      case array: Array[_] => array.map(toValueObject)
      case v if(getType(v).typeSymbol.asClass.isCaseClass) => v
      case other => {
        val mirrorObj = mirror.reflect(other)
        mirrorObj.symbol.info.decls
          .filter(it => it.isMethod && it.isPublic && it.name.toString != "getSingleton")
          .filterNot(_.isConstructor)
          .map(_.asMethod)
          .filter(_.paramLists.headOption.getOrElse(Nil).isEmpty)
          .map(method => {
            var value = mirrorObj.reflectMethod(method).apply()
            if(value != null) {
              val clazz = getType(value).typeSymbol.asClass
              if(clazz.fullName.matches("(com.openbankproject.commons|code).*") && !clazz.isCaseClass) {
                value = toValueObject(value)
              }
            }
            (method.name.toString, value)
          })
          .toMap
      }
    }
  }


  /**
    * convert a group of object to it's siblings
    * @param items will do convert
    * @tparam T expected type
    * @return expected values
    */
  def toOthers[T: TypeTag](items: List[_]): List[T] = items.map(toOther[T](_))

  // the follow four currying function is for implicit usage, to convert trait type to commons case class
  def toSibling[T, D <% T: TypeTag] = (t: T) => toOther[D](t)


  def toSiblings[T, D <% T: TypeTag] = (items: List[T]) => toOthers[D](items)


  def toSiblingBox[T, D <% T: TypeTag] = (box: Box[T]) => box.map(toOther[D](_))

  def toSiblingsBox[T, D <% T: TypeTag] = (boxItems: Box[List[T]]) => boxItems.map(toOthers[D](_))

}
