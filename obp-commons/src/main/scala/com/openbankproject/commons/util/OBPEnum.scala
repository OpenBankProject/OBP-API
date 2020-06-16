package com.openbankproject.commons.util

import scala.reflect.runtime.universe._
import scala.reflect.runtime.{universe => ru}

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

abstract class OBPEnumeration[T <: EnumValue: ru.TypeTag] { // trait not support context bounded type
  type Value = T // just keep the same usage with scala enumeration
  private val tpe: ru.Type = typeTag[T].tpe

  private val mirror: ru.Mirror = ru.runtimeMirror(this.getClass.getClassLoader) // classloader
  private val instanceMirror: ru.InstanceMirror = mirror.reflect(this)

  private val clazz: Class[_] = mirror.runtimeClass(tpe)
  private val modules: List[ru.ModuleMirror] = instanceMirror.symbol.toType.decls.filter(_.isPublic).filter(_.isModule)
    .map(_.asModule)
    .map(it => mirror.reflectModule(it))
    .filter(it => clazz.isInstance(it.instance))
    .toList

  val values: List[T] = modules.map(_.instance.asInstanceOf[T])

  assert(values.nonEmpty, s"enumeration must at least have one value, please check ${tpe}")

  val nameToValue: Map[String, T] = modules.map(it => (it.symbol.name.toString, it.instance.asInstanceOf[T])).toMap

  def withNameOption(name: String): Option[T] = nameToValue.get(name)
  def withIndexOption(index: Int): Option[T] = values.lift(index)

  def withName(name: String): T = nameToValue.get(name).get
  def withIndex(index: Int): T = values.lift(index).get
  def example: T = values.head
}

object OBPEnumeration {
  private def getEnumContainer(tp: Type): OBPEnumeration[_] = {
    require(tp <:< typeOf[EnumValue], s"parameter must be sub-type of ${typeOf[EnumValue]}")

    val mirror = ru.runtimeMirror(this.getClass.getClassLoader)
    val anyImplementation: ru.Symbol = tp.typeSymbol.asClass.knownDirectSubclasses.head
    val enumContainer: ru.ModuleSymbol = anyImplementation.owner.asClass.module.asModule
    mirror.reflectModule(enumContainer).instance.asInstanceOf[OBPEnumeration[_]]
  }

  private def getEnumContainer[T <: EnumValue](clazz: Class[T]): OBPEnumeration[T] = {
    require(clazz != classOf[EnumValue], s"parameter must be sub-class of ${classOf[EnumValue]}")

    val mirror = ru.runtimeMirror(this.getClass.getClassLoader)
    val anyImplementation = mirror.classSymbol(clazz).knownDirectSubclasses.head
    val enumContainer = anyImplementation.owner.asClass.module.asModule
    mirror.reflectModule(enumContainer).instance.asInstanceOf[OBPEnumeration[T]]
  }

  def getValuesByType(tp: Type): List[EnumValue] = getEnumContainer(tp).values.map(_.asInstanceOf[EnumValue])

  def getValuesByClass[T <: EnumValue](clazz: Class[T]): List[T] = getEnumContainer(clazz).values

  def getValuesByInstance[T <: EnumValue](instance: T): List[T] = {
    val clazz = instance.getClass
    val enumType = clazz.getInterfaces.headOption.getOrElse(clazz.getSuperclass)
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
