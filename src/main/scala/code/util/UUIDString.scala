package code.util

import net.liftweb.mapper.{MappedString, Mapper}
import net.liftweb.util.Props

/**
 * Enforces a default max length.
 */
class UUIDString [T <: Mapper[T]](override val fieldOwner : T) extends MappedString(fieldOwner, UUIDString.MaxLength)

object UUIDString {
  val MaxLength = Props.getInt("uuid_string.length", 36)
}


class MediumString [T <: Mapper[T]](override val fieldOwner : T) extends MappedString(fieldOwner, MediumString.MaxLength)

object MediumString {
  val MaxLength = Props.getInt("medium_string.length", 20)
}