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
trait EnumValue {
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

  val nameToValue: Map[String, T] = modules.map(it => (it.symbol.name.toString, it.instance.asInstanceOf[T])).toMap

  def withNameOption(name: String): Option[T] = nameToValue.get(name)
  def withIndexOption(index: Int): Option[T] = values.lift(index)

  def withName(name: String): T = nameToValue.get(name).get
  def withIndex(index: Int): T = values.lift(index).get
  def example: T = values.head
}
