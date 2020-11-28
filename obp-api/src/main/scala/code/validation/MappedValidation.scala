package code.validation

import net.liftweb.mapper.{MappedText, _}

class Validation extends LongKeyedMapper[Validation] with IdPK {

  override def getSingleton = Validation


  object OperationId extends MappedString(this, 200)
  object JsonSchema extends MappedText(this)

  def operationId: String = OperationId.get
  def jsonSchema: String = JsonSchema.get
}


object Validation extends Validation with LongKeyedMetaMapper[Validation] {
  override def dbIndexes: List[BaseIndex[Validation]] = UniqueIndex(OperationId) :: super.dbIndexes
}

