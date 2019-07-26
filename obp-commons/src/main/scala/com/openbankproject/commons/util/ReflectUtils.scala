package com.openbankproject.commons.util

import net.liftweb.common.Box

import scala.annotation.tailrec
import scala.collection.immutable.List
import scala.language.postfixOps
import scala.reflect.runtime.universe._
import scala.reflect.runtime.{universe => ru}

object ReflectUtils {
  private[this] val mirror: ru.Mirror = ru.runtimeMirror(getClass().getClassLoader)

  private val OBP_TYPE_REGEX = """^(com\.openbankproject\.commons\.|code\.).+""".r

  def isObpObject(any: Any): Boolean = any != null && OBP_TYPE_REGEX.findFirstIn(any.getClass.getName).isDefined

  def isObpType(tp: Type): Boolean = tp != null && OBP_TYPE_REGEX.findFirstIn(tp.typeSymbol.fullName).isDefined

  /**
    * get all val and var name to values of given object
    * @param obj to do extract object
    * @param excludes excluded var or val names
    * @param includeVar whether include var values
    * @return map of val or var name to value
    */
  def getNameToValues(obj: AnyRef, excludes: Seq[String] = Nil, includeVar: Boolean = true): Map[String, Any] = {
    obj match {
      case null => Map.empty[String, Any]
      case _ => getType(obj).decls
        .filter(_.isTerm)
        .map(_.asTerm)
        .filterNot(it => excludes.contains(it.name.toString))
        .filter(it => it.isVal || (includeVar && it.isVar))
        .map(it => (it.name.toString, invokeMethod(obj, it.getter.asMethod)))
        .toMap
    }
  }
  /**
    * get all val and var values of given object
    * @param obj to do extract object
    * @param excludes excluded var or val names
    * @param includeVar whether include var values
    * @return List of val or var values
    */
  def getValues(obj: AnyRef, excludes: Seq[String] = Nil, includeVar: Boolean = true): List[Any] = getNameToValues(obj, excludes, includeVar).values.toList

  def getTypeByName(typeName: String, mirror: ru.Mirror = this.mirror): ru.Type = mirror.staticClass(typeName).asType.toType

  def isTypeExists(typeName: String): Boolean = try {
    getTypeByName(typeName)
    true
  } catch {
    case _: Throwable => false
  }

  /**
    * get a nested type parameter of given type, according the indexes, example:
    *
    * > val tp = typeOf[List[(Int, String)]]
    * > getNestTypeArg(tp, 0, 1)
    * > String
    *
    * > val tp = typeOf[List[(Optional[Int], String)]]
    * > getNestTypeArg(tp, 0, 0, 0)
    * > Int
    *
    * @param tp tp to do parsed type
    * @param typeArgIndexes indexes of type arg
    * @return the nested type parameter
    */
  @tailrec
  def getNestTypeArg(tp: ru.Type, typeArgIndexes: Int*): ru.Type = {
    (typeArgIndexes.toList, tp.typeArgs) match {
      case (Nil, _) => tp
      case (head :: tail, args) => {
        assume(head < args.size, s"index $head is too big for $args")
        getNestTypeArg(args(head), tail:_*)
      }
    }
  }

  /**
    * get a nested type parameter of given type, only get the first one of every nested args, example:
    * > val tp = typeOf[List[(Int, String)]]
    * > getNestFirstTypeArg(tp)
    * > Int
    *
    * > val tp = typeOf[List[(Optional[Int], String)]]
    * > getNestFirstTypeArg(tp)
    * > Int
    *
    * @param tp to do parsed type
    * @return the nested type parameter
    */
  @tailrec
  def getNestFirstTypeArg(tp: ru.Type): ru.Type = {
    tp.typeArgs match {
      case Nil => tp
      case head :: _ => getNestFirstTypeArg(head)
    }
  }

  /**
    * get all nested type, e.g:
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


  def invokeMethod(obj: Any, methodName: String, args: Any*): Any = {
    val objMirror = mirror.reflect(obj)
    val methodSymbol: Option[ru.MethodSymbol] = findMethodByArgs(obj, methodName, args:_*)

    if (methodSymbol.isEmpty) {
      throw new IllegalArgumentException(s"not found method $methodName match the parameters: $args")
    }
    objMirror.reflectMethod(methodSymbol.get).apply(args: _*)
  }

  def invokeMethod(obj: Any, method: ru.MethodSymbol, args: Any*): Any = {
    val objMirror = mirror.reflect(obj)
    objMirror.reflectMethod(method).apply(args: _*)
  }

  /**
    * invoke given object "call by name" methods or val values, to get methodName to value
    * @param obj to get values object
    * @param methodNames call by name method names or val names
    * @return name to values get from obj
    */
  def getCallByNameValues(obj: Any, methodNames: String*): Map[String, Any] = {
    val objMirror = mirror.reflect(obj)
    val tp = objMirror.symbol.toType
    methodNames
      .map(methodName => tp.member(ru.TermName(methodName)))
      .map { methodSymbol=>
          assume(methodSymbol.isMethod, s"${methodSymbol.name} is not method in Object ${obj}")
          val method = methodSymbol.asMethod
          val callByNameMethod = method.alternatives.find(it => it.asMethod.paramLists == Nil).map(_.asMethod)
          assume(callByNameMethod.isDefined, s"there is no call by name method or val of name ${methodSymbol.name} in Object ${obj}")

          callByNameMethod.get
        }
      .map {method =>
        val paramName = method.name.toString
        val paramValue =objMirror.reflectMethod(method).apply()
        (paramName, paramValue)
      } .toMap
  }

  /**
    * get given object val value or "call by name" method value
    * @param obj to do extract value object
    * @param methodName "call by name" method name or val name
    * @return value of given object through call "call by name" method or val
    */
  def getCallByNameValue(obj: Any, methodName: String): Any = getCallByNameValues(obj, methodName).headOption.get._2

  /**
    * extract object field values, like unapply method
    * for example:
    * val obj: Any = Foo(name = "ken", age = 12, email = "abc@tesobe.com")
    * getConstructValues(obj) == Map(("name", "ken"), ("age", 12), ("email", "abc@tesobe.com"))
    *
    * @param obj
    * @return
    */
  def getConstructorArgs(obj: Any): Map[String, Any] = {
    val constructorParamNames = getPrimaryConstructor(obj).paramLists.headOption.getOrElse(Nil).map(_.name.toString)
    getCallByNameValues(obj, constructorParamNames :_*)
  }

  /**
    * extract object constructor param name and types
    * for example:
    * val obj: Any = Foo(name = "ken", age = 12, email = "abc@tesobe.com")
    * getConstructValues(obj) == Map(("name", String), ("age", Int), ("email", String))
    *
    * @param obj
    * @return constructor param name to type
    */
  def getConstructorArgTypes(obj: Any): Map[String, ru.Type] =
     getPrimaryConstructor(obj)
       .paramLists.headOption
       .getOrElse(Nil)
       .map(it => (it.name.toString, it.info))
       .toMap


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
    tp.member(TermName(methodName)).alternatives match {
      case Nil => None
      case method::Nil => Some(method).filter(_.isMethod).map(_.asMethod)
      case list => list.filter(_.isMethod).map(_.asMethod).find { method =>
        val paramNameToType = method.paramLists.headOption.getOrElse(Nil).map(i => (i.name.toString, i.info)).toMap
        predicate(paramNameToType)
      }
    }
  }

  def findMethod(obj: Any, methodName: String)(predicate: Map[String, ru.Type] => Boolean): Option[MethodSymbol] = findMethod(getType(obj), methodName)(predicate)

  def findMethodByArgs(tp: ru.Type, methodName: String, args: Any*): Option[ru.MethodSymbol] = findMethod(tp, methodName) { nameToType =>
      args.size == args.size && nameToType.values.zip(args).forall(it => isTypeOf(it._1, it._2))
  }

  def findMethodByArgs(obj: Any,  methodName: String, args: Any*): Option[ru.MethodSymbol] = findMethodByArgs(getType(obj), methodName, args:_*)


  def getType(obj: Any): ru.Type = mirror.reflect(obj).symbol.toType

  def getPrimaryConstructor(tp: ru.Type): MethodSymbol = tp.decl(ru.termNames.CONSTRUCTOR).alternatives.head.asMethod

  def getPrimaryConstructor(obj: Any): MethodSymbol = this.getPrimaryConstructor(this.getType(obj))

  def classToTypeTag[A](clazz: Class[A], typeParams: Class[_]*): TypeTag[A] = {
    import scala.reflect.api
    val mirror: ru.Mirror = runtimeMirror(clazz.getClassLoader)
    val sym: ru.ClassSymbol = mirror.classSymbol(clazz)

    val tpe = if(typeParams.isEmpty) {
      sym.selfType
    } else {
      val typeParamList = typeParams.map(mirror.classSymbol(_).toType).toList
      ru.internal.typeRef(NoPrefix, sym, typeParamList)
    }

    // create a type tag which contains above type object
    TypeTag(mirror, new api.TypeCreator {
      def apply[U <: api.Universe with Singleton](m: api.Mirror[U]) =
        if (m eq mirror) tpe.asInstanceOf[U # Type]
        else throw new IllegalArgumentException(s"Type tag defined in $mirror cannot be migrated to other mirrors.")
    })
  }

  def classToSymbol(clazz: Class[_]): ru.ClassSymbol = ru.runtimeMirror(clazz.getClassLoader).classSymbol(clazz)

  /**
    * if type is concrete, get the constructor parameter name map type
    * @param tp to do extract type
    * @return a map of constructor parameter name to type, if tp is abstract, return empty amp
    */
  def getConstructorParamInfo(tp: ru.Type): Map[String, ru.Type] = tp.typeSymbol.isClass match {
    case false => Map.empty[String, ru.Type]
    case true => {
      ReflectUtils.getPrimaryConstructor(tp).paramLists.headOption.getOrElse(Nil).map(it => (it.name.toString, it.info)).toMap
    }
  }
  /**
    * if type is concrete, get the constructor parameter name map type
    * @param clazz to do extract class object
    * @return a map of constructor parameter name to type, if tp is abstract, return empty amp
    */
  def getConstructorInfo(clazz: Class[_]): Map[String, ru.Type] = getConstructorParamInfo(classToSymbol(clazz).toType)

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
    val constructor: ru.MethodSymbol = expectType.decl(ru.termNames.CONSTRUCTOR).alternatives(0).asMethod
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
  def toSibling[T, D <% T: TypeTag]: T => D = (t: T) => toOther[D](t)


  def toSiblings[T, D <% T: TypeTag]: List[T] => List[D] = (items: List[T]) => toOthers[D](items)


  def toSiblingBox[T, D <% T: TypeTag]: Box[T] => Box[D] = (box: Box[T]) => box.map(toOther[D](_))

  def toSiblingsBox[T, D <% T: TypeTag]: Box[List[T]] => Box[List[D]] = (boxItems: Box[List[T]]) => boxItems.map(toOthers[D](_))

  def toSiblingOption[T, D <% T: TypeTag]: Option[T] => Option[D] = (option: Option[T]) => option.map(toOther[D](_))

  def toSiblingsOption[T, D <% T: TypeTag]: Option[List[T]] => Option[List[D]] = (optionItems: Option[List[T]]) => optionItems.map(toOthers[D](_))
}
