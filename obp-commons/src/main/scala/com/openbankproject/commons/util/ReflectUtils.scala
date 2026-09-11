package com.openbankproject.commons.util

import org.json4s._
import java.lang.reflect.{Field, Modifier}

import net.liftweb.common.{Box, Empty, Failure, Full}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.annotation.tailrec
import scala.collection.immutable.List
import scala.concurrent.Future
import scala.language.postfixOps
import scala.reflect.runtime.universe._
import scala.reflect.runtime.{universe => ru}
import scala.util.Success
import org.json4s.JValue
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.reflect.FieldUtils

import scala.collection.mutable
import scala.reflect.ClassTag

object ReflectUtils {
  private[this] val mirror: ru.Mirror = ru.runtimeMirror(getClass().getClassLoader)

  private val OBP_TYPE_REGEX = """^(com\.openbankproject\.commons\.|code\.).+""".r

  def isObpObject(any: Any): Boolean = any != null && OBP_TYPE_REGEX.findFirstIn(any.getClass.getName).isDefined

  def isObpType(tp: Type): Boolean = tp != null && tp.typeSymbol.isClass && OBP_TYPE_REGEX.findFirstIn(tp.typeSymbol.fullName).isDefined

  def isObpClass(clazz: Class[_]): Boolean = clazz != null && OBP_TYPE_REGEX.findFirstIn(clazz.getName).isDefined

  def isObpClass(clazzName: String): Boolean = clazzName != null && OBP_TYPE_REGEX.findFirstIn(clazzName).isDefined

  /**
   * get given instance FieldMirror, and operate it. this function is just for helper of getField and setField function
   * @param obj
   * @param fieldName
   * @param fn a callback to operate field, default value is do nothing
   * @return the given value given field original value
   */
  private def operateField[T](obj: AnyRef, fieldName: String)(fn: (InstanceMirror, TermSymbol) => Unit): T = {
    val instanceMirror: ru.InstanceMirror = mirror.reflect(obj)
    val tp = getType(obj)

    def isFieldOrCallByPath(term: ru.TermSymbol) = {
      term.name.decodedName.toString.trim == fieldName &&
        (term.isVal || term.isLazy || (term.isMethod && term.asMethod.paramLists.isEmpty))
    }

    val fields: Iterable[ru.TermSymbol] = tp.members.collect({
      case term: TermSymbol if isFieldOrCallByPath(term) => term
    })
    assert(fields.nonEmpty, s"${tp.typeSymbol.fullName} have not field kind member '$fieldName'")
    val field = fields.find(it => it.isVal || it.isVar).getOrElse(fields.head)

    val result: T = if(field.isVal || field.isVar) {
      val fieldMirror: ru.FieldMirror = instanceMirror.reflectField(field)
      val originValue = fieldMirror.get
      originValue.asInstanceOf[T]
    } else {// the field is a lazy val or call by name or empty param list method
      val method = field.asMethod
      instanceMirror.reflectMethod(method).apply().asInstanceOf[T]
    }
    fn(instanceMirror, field)
    result
  }

  def getInstanceMirror(any: Any): ru.InstanceMirror = mirror.reflect(any)

  def getFieldValues(obj: AnyRef)(predicate: TermSymbol => Boolean = _=>true): Map[String, Any] = {
    val instanceMirror = mirror.reflect(obj)
    val tp: ru.Type = instanceMirror.symbol.info
    // Scala 3's LazyVals compiles `lazy val x` to a backing field named `x$lzy1` (verified via
    // javap), not `x` - so a plain isVal/isVar/isLazy-style name match against getDeclaredFields
    // would miss every lazy val. Accept either spelling.
    //
    // runtimeClass(tp) can itself fail - e.g. for a path-dependent inner class, `tp` resolves to
    // a refinement type mirror.runtimeClass has no single java.lang.Class for, and throws
    // NoClassDefFoundError (a LinkageError - NOT caught by NonFatal, which treats LinkageError as
    // fatal) rather than returning one. That is a shape this function never used to touch (the
    // pre-fix code never called runtimeClass at all), so falling through uncaught would make
    // getFieldValues newly crash on inputs it used to handle. Fall back to the old permissive
    // behaviour - treat the candidate as field-backed - rather than letting a disambiguation aid
    // break the thing it is meant to refine.
    // runtimeClass(tp) itself, not instanceMirror.symbol.toType: `tp` is `.info`, the class
    // symbol's own ClassInfoType (a template - parents + decls), and mirror.runtimeClass can't
    // resolve that back to a java.lang.Class the way it resolves an ordinary TypeRef; `.toType`
    // (as getType(obj) elsewhere in this file uses) is the reference form runtimeClass expects.
    lazy val declaredFieldNames: Option[Set[String]] =
      try Some(runtimeClass(instanceMirror.symbol.toType).getDeclaredFields.map(_.getName).toSet) catch { case _: Throwable => None }
    def isFieldBacked(name: String): Boolean = declaredFieldNames match {
      case Some(names) => names.contains(name) || names.exists(_.startsWith(s"$name$$lzy"))
      case None => true
    }
    (tp.members ++ tp.decls).toSet
      .withFilter(_.isTerm)
      .map(_.asTerm)
      .withFilter(!_.isImplicit)
      // isLazy/isVal/isVar answer from Scala's own declaration metadata (ScalaSig for Scala 2,
      // TASTy for Scala 3). scala.reflect.runtime.universe - the Scala 2.13 reflection library
      // obp-commons is pinned to - has no TASTy reader, so all three come back false for every
      // member of a Scala-3-compiled class; only the bytecode-level shape (a zero-arg method with
      // a return type) survives. The extra clause recovers that shape.
      //
      // Restricted to it.owner == tp.typeSymbol (declared directly on the target's own class,
      // not inherited) rather than trying to exclude bad owners by name one at a time: a zero-arg
      // method can be inherited from java.lang.Object (notify/wait - reflectMethod on those
      // outside a synchronized block throws IllegalMonitorStateException), from scala.Any
      // (asInstanceOf/isInstanceOf - compiler-magic, reflectMethod refuses to invoke them at
      // all), or even from a JDK-internal interface an unrelated object's runtime class happens
      // to implement (hit via JSONFactory1_4_0's unfiltered fallback branch on values it doesn't
      // otherwise know how to schema - a java.lang.reflect.InaccessibleObjectException on some
      // jdk.internal.constant.* method). None of that is ever something a genuine val/lazy val/
      // case-class field owns; requiring same-class ownership excludes all of it at once, and
      // every actual caller's target (ExampleValue/ApiRole/ApiTag's own lazy vals, a case class's
      // own constructor-derived accessors) declares its members directly, never by inheritance.
      //
      // Same-class ownership alone still can't tell a val-accessor from an ordinary zero-arg def
      // declared directly on the class (both compile to that identical shape); isFieldBacked
      // closes that gap with the one signal that does survive - whether a matching backing field
      // actually exists - so a genuine helper method (e.g. a custom toString) isn't reported as
      // a schema field just because it happens to take no arguments.
      .withFilter(it => it.isLazy || it.isVal || it.isVar ||
        (it.isMethod && !it.asMethod.isConstructor && it.asMethod.paramLists.forall(_.isEmpty) &&
          it.owner == tp.typeSymbol && isFieldBacked(it.name.decodedName.toString.trim)))
      .withFilter(predicate)
      .map(it => {
        val fieldName = it.name.decodedName.toString.trim
        if(it.isLazy || (it.isMethod && !it.isVal && !it.isVar)) {
          // get lazy value, or invoke the zero-arg-method-shaped accessor recovered above
          fieldName -> instanceMirror.reflectMethod(it.asMethod)()
        } else {
          fieldName -> instanceMirror.reflectField(it).get
        }
      })
      .toMap
  }

  /**
   * get all field name to value of object
   * @param obj
   * @tparam T field type
   * @return
   */
  // Callers pass T's Type explicitly rather than via a TypeTag context bound: T is frequently an
  // obp-api type (e.g. ApiRole, ResourceDocTag), and typeTag[T] needs the Scala 2 compiler's
  // TypeTag synthesis at the call site, which Scala 3 does not implement.
  def getFieldsNameToValue[T](obj: AnyRef, tpe: ru.Type): Map[String, T] = {
    getFieldValues(obj){it =>
    if(it.isMethod) {
        it.asMethod.returnType <:< tpe
      } else {
        it.info <:< tpe
      }
    }.asInstanceOf[Map[String, T]]
  }

  /**
   * get given object given field value
   * @param obj
   * @param fieldName field name
   * @return the field value of obj
   */
  def getField(obj: AnyRef, fieldName: String): Any = operateField[Any](obj, fieldName)(Functions.doNothingFn)
  /**
   * get given object nested field value
   * @param obj
   * @param fieldName field name
   * @return the field value of obj
   */
  def getNestedField(obj: AnyRef, rootField: String, nestedFields: String*): Any = {
    nestedFields.foldLeft(getField(obj, rootField)) { (parentObject, field) =>
      assert(parentObject != null, s"Can't read `$field` value from null.")
      assert(parentObject.isInstanceOf[AnyRef], s"Value $parentObject must be AnyRef type.")

      getField(parentObject.asInstanceOf[AnyRef], field)
    }
  }

  /**
   * according object name get corresponding field value
   * @param objName object name
   * @param fieldName field name
   * @return field value
   */
  def getField(objName: String, fieldName: String): Any = getField(getObject(objName), fieldName)


  def getFieldByType[T](obj: AnyRef, fieldName: String): T = getField(obj, fieldName).asInstanceOf[T]

  def getFieldByType[T](objName: String, fieldName: String): T = getField(objName, fieldName).asInstanceOf[T]

  /**
   * get given instance by full name.
   * example:
   * {{{
   *  package com.foo.bar
   *  object Hello {}
   *  getObject("com.foo.bar.Hello") == Hello
   * }}}
   * @param fullName full name of object
   * @return object value
   */
  def getObject(fullName: String): AnyRef = {
    val regex = "(.+?)(\\.type)?".r
    val regex(typeName, _) = fullName
    val objClazz = Class.forName(typeName + "$")
    val instanceField: Field = objClazz.getDeclaredField("MODULE$")
    instanceField.get(null)
  }

  /**
   * set given instance given field to a new value, and return the original value
   * @param obj
   * @param fieldName
   * @param fieldValue
   * @tparam T field type
   * @return the original field value
   */
  def setField[T](obj: AnyRef, fieldName: String, fieldValue: T): T = operateField[T](obj, fieldName) { (instanceMirror, term) =>
    assert(term.isVal || term.isVar, s"${obj.getClass.getName} have no field name is '$fieldName'")
    instanceMirror.reflectField(term).set(fieldValue)
  }

  /**
   * modify given instance nested fields value
   * @param obj given instance to modify
   * @param predicate check whether current field value need to modify
   * @param fn modify function, signature is (fieldName: String, fieldType: Type, fieldValue: Any, ownerType: Type): Any
   *           fn result is calculated new value
   * @return modified instance
   *
   * @note be carefully, this method will modify immutable object state, before you call this method, you should very sure it is safe for your logic.
   */
  def resetNestedFields(obj: Any, predicate: Any => Boolean = isObpObject)(fn: PartialFunction[(String, Type, Any, Type), Any]): Any = {
    val recurseCallback = resetNestedFields(_: Any, predicate)(fn)
    obj match {
      case null | None | Empty | Nil => obj
      case _: Unit => obj
      case _: Boolean => obj
      case _: Byte => obj
      case _: Char => obj
      case _: Short => obj
      case _: Int => obj
      case _: Long => obj
      case _: Float => obj
      case _: Double => obj
      case it: Iterable[_] if(it.isEmpty) => obj
      case v: Some[_] => v.map(recurseCallback)
      case v: Full[_] => v.map(recurseCallback)
      case (k, v) => (recurseCallback(k), recurseCallback(v))
      case v: Future[_] => v.map(recurseCallback)
      case v: Right[_, _] => v.map(recurseCallback)
      case v: Success[_] => v.map(recurseCallback)
      case v: Array[_] => v.map(recurseCallback)
      case v: Map[_, _] => v.values.map(recurseCallback)
      case v: Iterable[_] => v.map(recurseCallback)
      case v if !predicate(v) => v
      case _ => {
        val tp = this.getType(obj)
        val clazz = obj.getClass
        val instanceMirror: ru.InstanceMirror = mirror.reflect(obj)

        val constructFieldNames: Seq[String] = ReflectUtils.getPrimaryConstructor(tp)
          .paramLists
          .headOption
          .getOrElse(Nil)
          .map(_.name.toString.trim)
        val fieldNames = clazz.getFields.map(_.getName).toSet
        constructFieldNames.foreach(fieldName => {
          // if case class constructor args have 'override val', this class have no this field, need find field from parent class
          if(fieldNames.contains(fieldName)) {
            val fieldSymbol: ru.TermSymbol = tp.member(ru.TermName(fieldName)).asTerm.accessed.asTerm
            val fieldMirror: ru.FieldMirror = instanceMirror.reflectField(fieldSymbol)
            val fieldValue: Any = fieldMirror.get
            recurseCallback(fieldValue)

            //check whether field should modify, if PartialFunction check result is true, just modify it with new Value
            val fieldType: Type = fieldSymbol.info
            val ownerType: Type = fieldSymbol.owner.asType.toType

            if(fn.isDefinedAt(fieldName, fieldType, fieldValue, ownerType)) {
              val newValue = fn(fieldName, fieldType, fieldValue, ownerType)
              fieldMirror.set(newValue)
            }
          } else {
            val field = FieldUtils.getField(clazz, fieldName, true)
            val fieldValue = field.get(obj)
            recurseCallback(fieldValue)

            val fieldType: Type = classToType(field.getType)
            val ownerType: Type = classToType(field.getDeclaringClass)

            if(fn.isDefinedAt(fieldName, fieldType, fieldValue, ownerType)) {
              val newValue = fn(fieldName, fieldType, fieldValue, ownerType)
              field.set(obj, newValue)
            }
          }

        })
        obj
      }
    }
  }

  /**
    * get all val and var name to values of given object
    * @param obj to do extract object
    * @param excludes excluded var or val names
    * @param includeVar whether include var values
    * @return map of val or var name to value
    */
  /**
   * Every val/var of `obj`, by name.
   *
   * `isVal`/`isVar` alone are not enough. They answer from Scala's own declaration metadata -
   * ScalaSig for Scala 2, TASTy for Scala 3 - and scala.reflect.runtime.universe, the Scala 2.13
   * reflection library this module is pinned to, has no TASTy reader: for a Scala-3-compiled class
   * both come back false for every member. This function then returned an empty map, and the
   * `allFields` collectors built on it (SwaggerDefinitionsJSON, MessageDocsSwaggerDefinitions,
   * JSONFactoryCustom300, SandboxData in OBPDataImport) each collected nothing - silently, since
   * an empty list is a legal result and nothing asserted otherwise. SwaggerDefinitionsJSON declares
   * 777 lazy vals and produced 0.
   *
   * The recovery is the same one getFieldValues already uses, shared here rather than copied: what
   * does survive into bytecode is the shape - a zero-arg method declared on this very class, with a
   * backing field of the same name (or `name$lzy…`, which is how Scala 3 spells a lazy val's
   * field). `isFieldBacked` is what separates such an accessor from an ordinary zero-arg def.
   *
   * `includeVar = false` cannot filter Scala 3 vars for the same reason `isVar` fails there; on
   * Scala 2 it behaves as before. Documented rather than silently approximated.
   */
  def getNameToValues(obj: AnyRef, excludes: Seq[String] = Nil, includeVar: Boolean = true): Map[String, Any] = {
    obj match {
      case null => Map.empty[String, Any]
      case _ =>
        val tp = getType(obj)
        val isFieldBacked = fieldBackedPredicate(obj, tp)
        tp.decls
          .filter(_.isTerm)
          .map(_.asTerm)
          .filterNot(it => excludes.contains(it.name.decodedName.toString.trim))
          .filter(it => it.isVal || (includeVar && it.isVar) ||
            (it.isMethod && !it.asMethod.isConstructor && it.asMethod.paramLists.forall(_.isEmpty) &&
              it.owner == tp.typeSymbol && isFieldBacked(it.name.decodedName.toString.trim)))
          .map(it => {
            val name = it.name.decodedName.toString.trim
            // getter is NoSymbol for the zero-arg-method shape recovered above - it IS the getter.
            val accessor = if (it.isMethod) it.asMethod else it.getter.asMethod
            (name, invokeMethod(obj, accessor))
          })
          .toMap
    }
  }

  /**
   * Whether a name has a real backing field on `obj`'s runtime class - the one signal that a
   * val/lazy val leaves in bytecode and an ordinary def does not.
   *
   * Scala 3's LazyVals compiles `lazy val x` to a field named `x$lzy1`, so both spellings count.
   * When the runtime class cannot be resolved at all (a path-dependent inner class resolves to a
   * refinement type, and mirror.runtimeClass throws NoClassDefFoundError - a LinkageError, which
   * NonFatal does not catch), fall back to admitting the candidate: this predicate exists to
   * refine a selection, and must not make its callers fail on inputs they used to handle.
   */
  private def fieldBackedPredicate(obj: AnyRef, tp: ru.Type): String => Boolean = {
    lazy val declaredFieldNames: Option[Set[String]] =
      try Some(runtimeClass(mirror.reflect(obj).symbol.toType).getDeclaredFields.map(_.getName).toSet)
      catch { case _: Throwable => None }
    name => declaredFieldNames match {
      case Some(names) => names.contains(name) || names.exists(_.startsWith(s"$name$$lzy"))
      case None => true
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

  /**
   * 
   * @param fullName need the fully qualified class name: eg: com.openbankproject.commons.dto.OutBoundCreateBankAccount
   * @param mirror : has the default this.mirror
   * @return
   */
  def getTypeByName(fullName: String, mirror: ru.Mirror = this.mirror): ru.Type =
      mirror.staticClass(fullName).asType.toType

  /**
   * Check if the class is existing in the java path or not. 
   * @param fullName
   * @return
   */
  def isTypeExists(fullName: String): Boolean = try {
    getTypeByName(fullName)
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
        assume(head < args.size, s"$tp have no enough type parameters for index $head, it's type parameters: ${tp.typeArgs.mkString("[", ",", "]")}")
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
      .map { symbol =>
        // The docstring always promised "call by name methods OR val values", but the code only
        // ever handled the method shape - the Lift Mapper entities this was written for exposed
        // every column as a call-by-name accessor def. Post-Mapper-to-Doobie migration, an entity
        // like MappedBankAccount is a plain case class, so its fields (e.g. accountPrimaryKey)
        // are ordinary constructor vals: isMethod is correctly false for them (confirmed via an
        // isolated diagnostic, not a Scala-3-reflection gap like the isVal/isVar/isLazy ones
        // elsewhere in this file), and the old method-only assumption threw on every one of them.
        if (symbol.isMethod) {
          val method = symbol.asMethod
          val callByNameMethod = method.alternatives.find(it => it.asMethod.paramLists == Nil).map(_.asMethod)
          assume(callByNameMethod.isDefined, s"there is no call by name method or val of name ${symbol.name} in Object ${obj}")
          val resolved = callByNameMethod.get
          resolved.name.toString -> objMirror.reflectMethod(resolved).apply()
        } else if (symbol.isTerm && (symbol.asTerm.isVal || symbol.asTerm.isVar)) {
          symbol.name.toString.trim -> objMirror.reflectField(symbol.asTerm).get
        } else {
          assume(false, s"${symbol.name} is not a call by name method or val in Object ${obj}")
          throw new IllegalStateException("unreachable: assume(false, ...) always throws")
        }
      }
      .toMap
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
       .map(it => (it.name.toString.trim, it.info))
       .toMap


  def invokeConstructor(tp: ru.Type)(fn: (Seq[ru.Type]) => Seq[Any]): Any = {
    val classMirror = mirror.reflectClass(tp.typeSymbol.asClass)
    // tp.decl(CONSTRUCTOR).asMethod throws ScalaReflectionException when the class declares more
    // than one constructor (e.g. a case class with an auxiliary `def this(...)` for backward
    // compatibility, such as BankCommons) - decl returns an overloaded symbol in that case, which
    // .asMethod refuses to treat as a single method. getPrimaryConstructor already does the right
    // thing (picks .alternatives.head, the primary constructor) - reuse it instead of re-deriving
    // the constructor symbol here.
    val constructor = getPrimaryConstructor(tp)
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
    nameToType.values.zip(args).forall(it => isTypeOf(it._1, it._2))
  }

  def findMethodByArgs(obj: Any,  methodName: String, args: Any*): Option[ru.MethodSymbol] = findMethodByArgs(getType(obj), methodName, args:_*)


  def getType(obj: Any): ru.Type = mirror.reflect(obj).symbol.toType

  /**
   * get the java.lang.Class that backs a scala-reflect Type, e.g. the class for `Option[Boolean]`'s
   * type argument `Boolean` is `scala.Boolean` (JVM primitive `boolean`). Used to build a json4s
   * `TypeInfo` from a scala-reflect-derived type when the JVM's own generic signature can't be
   * trusted (see ObpCommonsProductDeserializer in JsonSerializers.scala).
   */
  def runtimeClass(tp: ru.Type): Class[_] = mirror.runtimeClass(tp)

  def forType(className: String): ru.Type = mirror.staticClass(className).toType

  def forTypeOption(className: String): Option[ru.Type] = try {
      Some(mirror.staticClass(className).toType)
    } catch {
      case _: ScalaReflectionException => None
    }

  def forClassOption(className: String): Option[Class[_]] = try {
      Some(Class.forName(className, false, getClass().getClassLoader))
    } catch {
      case _: ClassNotFoundException => None
    }

  private object ClassExtractor {
    // extract concrete class by class name
    def unapply(className: String): Option[Class[_]] = forClassOption(className).filterNot(clazz => Modifier.isAbstract(clazz.getModifiers))
  }

  /**
   * find one implement class in the package com.openbankproject.commons.model
   * @param clazz an abstract class
   * @return
   */
  def findImplementedClass(clazz: Class[_]): Option[Class[_]] = {
    if(clazz == null || !Modifier.isAbstract(clazz.getModifiers)) {
      None
    } else {
      val className = clazz.getSimpleName
      val fullClassName = clazz.getName

      val maybeImplementedClassNames = mutable.ListBuffer[String](s"com.openbankproject.commons.model.${className}Commons", s"${fullClassName}Commons")

      if(className.endsWith("Trait")) {
        val deletedSuffixName = StringUtils.substringBeforeLast(className, "Trait")
        maybeImplementedClassNames += s"com.openbankproject.commons.model.$deletedSuffixName"
        maybeImplementedClassNames += s"com.openbankproject.commons.model.${deletedSuffixName}Commons"

        val deleteSuffixFullName = StringUtils.substringBeforeLast(fullClassName, "Trait")
        maybeImplementedClassNames += deleteSuffixFullName
        maybeImplementedClassNames += s"${deleteSuffixFullName}Commons"
      }
      if(className.endsWith("T")) {
        val deletedSuffixName = StringUtils.substringBeforeLast(className, "T")
        maybeImplementedClassNames += s"com.openbankproject.commons.model.$deletedSuffixName"
        maybeImplementedClassNames += s"com.openbankproject.commons.model.${deletedSuffixName}Commons"

        val deleteSuffixFullName = StringUtils.substringBeforeLast(fullClassName, "T")
        maybeImplementedClassNames += deleteSuffixFullName
        maybeImplementedClassNames += s"${deleteSuffixFullName}Commons"
      }

      maybeImplementedClassNames collectFirst {
        case ClassExtractor(x) => x
      }

    }
  }
  /**
   * find one implement class in the package com.openbankproject.commons.model
   * @param className an abstract class
   * @return
   */
  def findImplementedClass(className: String): Option[Class[_]] = {
    if(StringUtils.isBlank(className)) {
      None
    } else {
      val clazz = forClassOption(className)
      clazz.flatMap(findImplementedClass(_))
    }
  }

  // .alternatives lists every overloaded constructor (primary and auxiliary, e.g. a class with a
  // convenience `def this(...)` alongside its case-class-generated one) in no order the language
  // spec guarantees - .head silently picked whichever came first, and for a Scala 3-compiled class
  // that order is not reliably source-declaration order (the reflect universe reading Scala 3
  // decls doesn't preserve it - see OBPEnumerationBase.modules elsewhere in this codebase for the
  // same observation). That let getPrimaryConstructor pick an auxiliary constructor over the real
  // primary one non-deterministically across JVM runs - reproduced for
  // code.methodrouting.MethodRoutingParam(key: String, value: String), which also declares
  // `def this(jObject: JObject)`: some runs read its primary constructor as (jObject: JObject)
  // instead, corrupting anything built from getConstructorParamInfo/invokeConstructor for it.
  //
  // isPrimaryConstructor looked like the fix - a real flag scala-reflect exposes for exactly this
  // - but it did not change CI's answer at all: like isVal/isVar/isImplicit elsewhere in this
  // migration, isPrimaryConstructor is itself source-level information scala.reflect.runtime.
  // universe cannot recover from a Scala 3-compiled class's TASTy-less classfile, so it was
  // silently false for both alternatives and the .getOrElse(.head) fallback fired every time -
  // functionally unchanged from the plain positional pick it was meant to replace.
  //
  // What actually distinguishes them is JVM-visible and needs no TASTy: a case class's primary
  // constructor parameters are exactly its declared instance fields (that's what `case class`
  // compiles to), while an auxiliary constructor's parameters generally are not - `jObject` above
  // is consumed to compute the real fields, not stored as one itself. Field names are ordinary
  // classfile metadata, so this reads identically regardless of which compiler or environment
  // produced the class.
  def getPrimaryConstructor(tp: ru.Type): MethodSymbol = {
    val alternatives = tp.decl(ru.termNames.CONSTRUCTOR).alternatives.map(_.asMethod)
    if (alternatives.size <= 1) alternatives.head
    else {
      val declaredFieldNames = runtimeClass(tp).getDeclaredFields.map(_.getName).toSet
      def paramNames(ctor: MethodSymbol): Set[String] =
        ctor.paramLists.headOption.getOrElse(Nil).map(_.name.decodedName.toString.trim).toSet
      val candidates = alternatives.filter(ctor => paramNames(ctor).nonEmpty && paramNames(ctor).subsetOf(declaredFieldNames))
      // More than one candidate is possible when an auxiliary constructor's parameters are a
      // strict subset of another candidate's - e.g. BankCommons has a 9-field primary
      // constructor and a 7-field auxiliary one whose names are all real fields too, so both
      // pass the filter above. `.find` (first match) then depended on `alternatives`' order,
      // which this whole fix exists because that order is not guaranteed.
      //
      // The primary constructor's parameters are exactly the case class's declared fields - not
      // merely the most of them among the candidates. Selecting by exact set equality rather
      // than by size means a class with no fields beyond its primary constructor's own (the
      // common case, true for BankCommons) has AT MOST ONE candidate that can ever match this -
      // two same-size auxiliary constructors, an ordering-dependent tie a size-only comparison
      // would have to break arbitrarily, can never satisfy it, since neither individually spans
      // every declared field. Only when the class has fields beyond any constructor's own (an
      // extra body-declared val) can no candidate match exactly; size is the closest fallback
      // signal for that narrower case, so it stays as a fallback rather than being replaced by it.
      //
      // Known residual gap: this compares parameter NAMES only, not types or order. Two
      // constructors whose parameter names are both exactly declaredFieldNames but differ in
      // type or position (a legal overload - e.g. `def this(a: String, b: Int) = this(b, a)`
      // alongside a primary `(a: Int, b: String)`) would both satisfy `==` here, so `.find`
      // would again depend on `alternatives`' order for that specific shape. No class in this
      // codebase does this (it is an unusual way to write an auxiliary constructor), and closing
      // it would mean comparing parameter types too - itself cross-compiler reflection this
      // migration keeps finding gaps in - so it is left as a known limitation rather than an
      // unverified fix, not silently assumed away.
      candidates.find(ctor => paramNames(ctor) == declaredFieldNames)
        .orElse(candidates.maxByOption(ctor => paramNames(ctor).size))
        .getOrElse(alternatives.head)
    }
  }

  def getPrimaryConstructor(obj: Any): MethodSymbol = this.getPrimaryConstructor(this.getType(obj))

  /**
   * get all sub type companions instance
   * @param tp type
   * @return
   */
  def getSubCompanions(tp: ru.Type): Set[Any] =
      tp
      .typeSymbol
      .asClass
      .knownDirectSubclasses
      .map(_.asClass.module.asModule)
      .map(mirror.reflectModule(_).instance)

  def getSubCompanions[T](clazz: Class[T]): Set[T] = getSubCompanions(classToType(clazz)).map(_.asInstanceOf[T])

  def classToType[A](clazz: Class[A]) : ru.Type = mirror.classSymbol(clazz).toType

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

  def typeTagToClass[T: TypeTag]: Class[_] = {
    val tt = implicitly[TypeTag[T]]
    tt.mirror.runtimeClass(tt.tpe.typeSymbol.asClass)
  }

  def classToSymbol(clazz: Class[_]): ru.ClassSymbol = ru.runtimeMirror(clazz.getClassLoader).classSymbol(clazz)

  /**
    * if type is concrete, get the constructor parameter name map type
    * @param tp to do extract type
    * @return a map of constructor parameter name to type, if tp is abstract, return empty amp
    */
  def getConstructorParamInfo(tp: ru.Type): Map[String, ru.Type] =
    tp.typeSymbol.isClass && !tp.typeSymbol.asClass.isTrait match {
    case false => Map.empty[String, ru.Type]
    case true => {
      import scala.collection.immutable.ListMap
      val paramNameToTypeList = getPrimaryConstructor(tp)
        .paramLists
        .headOption
        .getOrElse(Nil)
        .map(it => (it.name.toString, it.info))

      ListMap(paramNameToTypeList:_*)
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
    // getPrimaryConstructor, not a raw alternatives(0) pick - see its own doc for why: a type
    // with more than one constructor (e.g. BankCommons, whose 7-param auxiliary constructor's
    // names are all real fields too) has no guaranteed order to `alternatives`, so picking by
    // position silently returns the wrong constructor depending on the JVM/environment.
    val constructor: ru.MethodSymbol = getPrimaryConstructor(expectType)
    val mirrorClass: ru.ClassMirror = mirror.reflectClass(expectType.typeSymbol.asClass)

    val paramNames = constructor.paramLists(0).map(_.name.toString)
    val mirrorObj = mirror.reflect(t)
    val info = mirrorObj.symbol.info
    // A same-named source member that isn't a call-by-name method is usually a plain val/var
    // (case class constructor params reflect that way), not the mismatched "attributes" field
    // the previous code always fell back to for any non-method symbol - that fallback threw
    // ScalaReflectionException: <none> is not a method as soon as a source field it was pointed
    // at was a val rather than a def. Kept as the last resort, for whatever original case (some
    // dynamic/attribute-bag source shape) actually needed it.
    val seq = paramNames.map(name => {
      val nameSymbol = info.decl(ru.TermName(name))
      if (nameSymbol.isMethod) {
        mirrorObj.reflectMethod(nameSymbol.asMethod)()
      } else if (nameSymbol.isTerm && (nameSymbol.asTerm.isVal || nameSymbol.asTerm.isVar)) {
        mirrorObj.reflectField(nameSymbol.asTerm).get
      } else {
        mirrorObj.reflectMethod(info.member(ru.TermName("attributes")).asMethod)()
      }
    })

    mirrorClass.reflectConstructor(constructor).apply(seq :_*).asInstanceOf[T]
  }

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
      case v: JValue => v
      case Some(v) => toValueObject(v)
      case Full(v) => toValueObject(v)
      case None|Empty => null
      case v: Failure => v
      case Left(v) => Left(toValueObject(v))
      case v: Right[_, _] => v.map(toValueObject)
      case v: Success[_]=> v.map(toValueObject)
      case scala.util.Failure(v) => v
      case it: Iterable[_] => it.map(toValueObject)
      case array: Array[_] => array.map(toValueObject)
      case v if getType(v).typeSymbol.asClass.isCaseClass => v
      case obpObj if ReflectUtils.isObpObject(obpObj) => {
        val mirrorObj = mirror.reflect(obpObj)
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
      case x => x
    }
  }


  /**
   * get the value by the field name, see the usage :
   * eg: val value = ReflectUtils.getValueByFieldName(ExampleValue,"bankIdExample").asInstanceOf[ConnectorField].value
   */
  def getValueByFieldName[T: TypeTag : ClassTag](obj: T, memberName: String): Any = {
    val symbol = typeOf[T].member(TermName(memberName)).asMethod

    val m = runtimeMirror(obj.getClass.getClassLoader)
    val im = m.reflect(obj)

    im.reflectMethod(symbol).apply()
  }
}
