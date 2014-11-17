package code.util

import java.util.UUID

import net.liftweb.mapper.{MappedString, Mapper}

class MappedUUID[T <: Mapper[T]] (override val fieldOwner : T) extends MappedString(fieldOwner, 36) {
  override def defaultValue = UUID.randomUUID().toString
}
