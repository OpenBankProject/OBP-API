package code.util

import net.liftweb.mapper.{Mapper, MappedString}

/**
 * For string fields that an api user can set. It enforces a max length.
 */
class MappedUserInputString [T <: Mapper[T]] (override val fieldOwner : T) extends MappedString(fieldOwner, MappedUserInputString.MaxLength)

object MappedUserInputString {
  val MaxLength = 2000
}