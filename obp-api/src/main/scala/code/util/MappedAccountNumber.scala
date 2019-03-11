package code.util

import net.liftweb.mapper.{Mapper, MappedString}

// Enforces a default max length.
class MappedAccountNumber [T <: Mapper[T]] (override val fieldOwner : T) extends MappedString(fieldOwner, MappedAccountNumber.MaxLength)

object MappedAccountNumber {
  val MaxLength = 128
}