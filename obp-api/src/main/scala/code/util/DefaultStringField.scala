package code.util

import net.liftweb.mapper.{Mapper, MappedString}

/**
 * Enforces a default max length.
 */
class DefaultStringField [T <: Mapper[T]] (override val fieldOwner : T) extends MappedString(fieldOwner, DefaultStringField.MaxLength)

object DefaultStringField {
  val MaxLength = 2000
}