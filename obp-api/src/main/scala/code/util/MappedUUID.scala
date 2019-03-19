package code.util

import code.api.util.APIUtil.generateUUID
import net.liftweb.mapper.{MappedString, Mapper}

class MappedUUID[T <: Mapper[T]] (override val fieldOwner : T) extends MappedString(fieldOwner, 36) {
  override def defaultValue = generateUUID()
}
