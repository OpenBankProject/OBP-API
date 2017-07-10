package code.util

import net.liftweb.mapper.{MappedString, Mapper}

/**
 * Enforces a default max length.
 */
class DefaultStringId [T <: Mapper[T]](override val fieldOwner : T) extends MappedString(fieldOwner, DefaultStringId.MaxLength)

object DefaultStringId {
  val MaxLength = 50
}