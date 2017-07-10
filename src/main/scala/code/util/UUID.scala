package code.util

import net.liftweb.mapper.{MappedString, Mapper}
import net.liftweb.util.Props

/**
 * Enforces a default max length.
 */
class UUID [T <: Mapper[T]](override val fieldOwner : T) extends MappedString(fieldOwner, UUID.MaxLength)

object UUID {
  val MaxLength = Props.getInt("uuid.length", 50)
}