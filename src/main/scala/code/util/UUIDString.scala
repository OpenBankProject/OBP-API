package code.util

import net.liftweb.mapper.{MappedString, Mapper}
import net.liftweb.util.Props

/**
 * Enforces a default max length.
 */
class UUIDString [T <: Mapper[T]](override val fieldOwner : T) extends MappedString(fieldOwner, UUIDString.MaxLength)

object UUIDString {
  val MaxLength = Props.getInt("uuid.length", 50)
}