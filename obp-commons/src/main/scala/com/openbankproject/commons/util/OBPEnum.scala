package com.openbankproject.commons.util

import scala.reflect.runtime.universe._
import scala.reflect.runtime.{universe => ru}
import Functions.Implicits._

/**
 * OBP enumeration
 * For example:
 * {{{
 *  sealed trait EnumShape extends EnumValue
 *
 *  object EnumShape extends OBPEnumeration[EnumShape]{
 *    object Circle extends EnumShape
 *    object Square extends EnumShape
 *    object Other extends EnumShape
 *  }
 * }}}
 */
trait EnumValue{
  override def toString: String = this.getClass.getSimpleName.replaceFirst("\\$$", "")
}

// Shared by OBPEnumeration and OBPEnumerationWithType: everything about walking the enclosing
// object's nested modules only needs tpe as a plain value, never the TypeTag itself.
abstract class OBPEnumerationBase[T <: EnumValue](tpe: ru.Type) {
  type Value = T // just keep the same usage with scala enumeration

  private val mirror: ru.Mirror = ru.runtimeMirror(this.getClass.getClassLoader) // classloader

  private val clazz: Class[_] = mirror.runtimeClass(tpe)

  // Deliberately lazy, not eager: an eager val here runs during OBPEnumerationBase's own
  // constructor, i.e. while the concrete companion object (e.g. AuthenticationType$) is still in
  // the middle of its own <clinit> - the JVM has not yet marked the class "initialized". Scala's
  // runtime reflection, asked to inspect that same not-yet-initialized class from inside its own
  // construction, silently returns member symbols with every declaration flag - isModule
  // included - false, so decls.filter(_.isModule) found nothing and the assertion below threw at
  // <clinit> time for AuthenticationType (obp-api, Scala 3) and reproduced identically for
  // AttributeType (obp-commons, Scala 2) once isolated, so this is a self-reflection-during-own-
  // <clinit> problem, not a Scala-3/TASTy one. Deferring to first external access - after the
  // class is fully initialized - gets decls.filter(_.isModule) back to finding the right symbols.
  // Order is a separate, narrower caveat: decls preserves source declaration order for a
  // Scala-2-compiled companion (verified by OBPEnumerationTest, which asserts on it), but not for
  // a Scala-3-compiled one, where it comes back in some other deterministic (observed:
  // alphabetical) order instead. AuthenticationType, the only Scala-3-compiled subclass today,
  // uses values only as an unordered set (filterNot on it, joined into an error message) - if a
  // future subclass needs withIndex/example/values.head to mean "as declared", that will need a
  // proper fix here.
  //
  // The symbol -> runtime instance step still can't go through mirror.reflectModule(sym).instance
  // though: for a Scala-3-compiled nested module, ModuleMirror's own name resolution reconstructs
  // the wrong binary name and reflectModule throws ClassNotFoundException. Do that step by hand
  // instead - the binary name of a nested object is always "<outer's binary name><simple name>$"
  // regardless of which Scala version compiled it, and loading it plus reading its MODULE$ field
  // is the same reliable, version-agnostic mechanism used elsewhere in this class.
  private lazy val modules: List[Class[_]] = {
    val instanceMirror = mirror.reflect(this)
    val outerBinaryName = this.getClass.getName // e.g. "code.api.util.AuthenticationType$"
    instanceMirror.symbol.toType.decls.filter(_.isPublic).filter(_.isModule)
      .map(_.asModule)
      .flatMap { sym =>
        // A ModuleSymbol's decodedName already carries the trailing "$" (unlike a val/def's), so
        // strip it before rebuilding the binary name rather than appending a second one.
        val simpleName = sym.name.decodedName.toString.trim.stripSuffix("$")
        try Some(Class.forName(s"$outerBinaryName$simpleName$$", false, mirror.classLoader)) catch { case _: Throwable => None }
      }
      .toList
  }

  lazy val values: List[T] = {
    val result = modules.flatMap { nestedClass =>
      try {
        val instance = nestedClass.getField("MODULE$").get(null)
        if (clazz.isInstance(instance)) Some(instance.asInstanceOf[T]) else None
      } catch {
        case _: NoSuchFieldException => None
      }
    }
    assert(result.nonEmpty, s"enumeration must at least have one value, please check ${tpe}")
    result
  }

  lazy val nameToValue: Map[String, T] = values.toMapByKey(_.toString)

  def withNameOption(name: String): Option[T] = nameToValue.get(name)
  def withIndexOption(index: Int): Option[T] = values.lift(index)

  def withName(name: String): T = nameToValue.get(name).get
  def withIndex(index: Int): T = values.lift(index).get
  def example: T = values.head
}

abstract class OBPEnumeration[T <: EnumValue: ru.TypeTag] extends OBPEnumerationBase[T](typeTag[T].tpe) // trait not support context bounded type

// Same as OBPEnumeration, but for obp-api's one subclass (AuthenticationType) rather than the many
// declared here: T's Type is a constructor parameter instead of a TypeTag context bound, because
// typeTag[T] needs the Scala 2 compiler's TypeTag synthesis at the `extends` clause itself, and
// obp-api compiles under Scala 3. The subclass passes ReflectUtils.forType("fully.qualified.T")
// instead - a pure string-based class lookup needing no compiler synthesis.
abstract class OBPEnumerationWithType[T <: EnumValue](tpe: ru.Type) extends OBPEnumerationBase[T](tpe)

object OBPEnumeration {
  private def getEnumContainer(tp: Type): OBPEnumerationBase[_] = {
    require(tp <:< typeOf[EnumValue], s"parameter must be sub-type of ${typeOf[EnumValue]}")
    val mirror = ru.runtimeMirror(this.getClass.getClassLoader)
    getEnumContainer(mirror.runtimeClass(tp).asInstanceOf[Class[EnumValue]])
  }

  // knownDirectSubclasses.head - walking from any one known subclass (an enum value object) back
  // up to its owner (the companion object holding it) - is another knownDirectSubclasses call
  // reading Scala's own declaration metadata (see OBPEnumerationBase's values, which hit the
  // identical gap): scala.reflect.runtime.universe has no TASTy reader, so a Scala-3-compiled
  // sealed trait (e.g. TransactionRequestStatus) reports zero known subclasses and .head throws
  // NoSuchElementException. This function doesn't actually need any subclass, only the companion
  // itself - and a companion object's binary name is always "<trait's binary name>$", regardless
  // of which Scala version compiled it (same technique OBPEnumerationBase.modules uses).
  //
  // Returns OBPEnumerationBase[T], not OBPEnumeration[T]: OBPEnumerationWithType[T] (obp-api's
  // AuthenticationType, Scala 3) is a sibling of OBPEnumeration[T], not a subtype of it - both
  // just extend OBPEnumerationBase - so a hardcoded OBPEnumeration[T] return type here made the
  // final .asInstanceOf throw ClassCastException for AuthenticationType specifically. Every
  // caller below only ever uses values/withNameOption/withIndexOption/example, all declared on
  // the shared base, so nothing downstream needed the narrower type in the first place.
  private def getEnumContainer[T <: EnumValue](clazz: Class[T]): OBPEnumerationBase[T] = {
    require(clazz != classOf[EnumValue], s"parameter must be sub-class of ${classOf[EnumValue]}")
    val companionClass = Class.forName(clazz.getName + "$", false, clazz.getClassLoader)
    companionClass.getField("MODULE$").get(null).asInstanceOf[OBPEnumerationBase[T]]
  }

  def getValuesByType(tp: Type): List[EnumValue] = getEnumContainer(tp).values.map(_.asInstanceOf[EnumValue])

  def getValuesByClass[T <: EnumValue](clazz: Class[T]): List[T] = getEnumContainer(clazz).values

  def getValuesByInstance[T <: EnumValue](instance: T): List[T] = {
    val clazz = instance.getClass
    // Not just clazz.getInterfaces.headOption: for a Scala-3-compiled enum value, EnumValue
    // itself can be the first interface JVM-side (interface linearization order isn't the same
    // between Scala 2 and Scala 3), so blindly taking index 0 sometimes returns EnumValue rather
    // than the intermediate sealed trait (e.g. TransactionRequestStatus) - getEnumContainer then
    // rejects it outright ("parameter must be sub-class of interface EnumValue", since it
    // literally *is* EnumValue). Find the interface that extends EnumValue without being it.
    val enumValueClass = classOf[EnumValue]
    val enumType = clazz.getInterfaces
      .find(i => i != enumValueClass && enumValueClass.isAssignableFrom(i))
      .orElse(clazz.getInterfaces.headOption)
      .getOrElse(clazz.getSuperclass)
    getValuesByClass(enumType.asInstanceOf[Class[T]])
  }

  def withNameOption(tp: Type, name: String): Option[EnumValue] = getEnumContainer(tp).withNameOption(name).map(_.asInstanceOf[EnumValue])

  def withNameOption[T <: EnumValue](clazz: Class[T], name: String): Option[T] = getEnumContainer(clazz).withNameOption(name)

  def withName(tp: Type, name: String): EnumValue = withNameOption(tp, name).get

  def withName[T <: EnumValue](clazz: Class[T], name: String): T = withNameOption[T](clazz, name).get

  def withIndexOption(tp: Type, index: Int): Option[EnumValue] = getEnumContainer(tp).withIndexOption(index).map(_.asInstanceOf[EnumValue])

  def withIndexOption[T <: EnumValue](clazz: Class[T], index: Int): Option[T] = getEnumContainer(clazz).withIndexOption(index)

  def withIndex(tp: Type, index: Int): EnumValue = withIndexOption(tp, index).get

  def withIndex[T <: EnumValue](clazz: Class[T], index: Int): T = withIndexOption[T](clazz, index).get

  def getExampleByType(tp: Type): EnumValue = getEnumContainer(tp).example.asInstanceOf[EnumValue]

  def getExampleByClass[T <: EnumValue](clazz: Class[T]): T = getEnumContainer(clazz).example
}
