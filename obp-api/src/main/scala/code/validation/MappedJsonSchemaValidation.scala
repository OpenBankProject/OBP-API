package code.validation

import net.liftweb.mapper.{MappedText, _}

class JsonSchemaValidation extends LongKeyedMapper[JsonSchemaValidation] with IdPK {

  override def getSingleton = JsonSchemaValidation


  object OperationId extends MappedString(this, 200)
  object JsonSchema extends MappedText(this)

  def operationId: String = OperationId.get
  def jsonSchema: String = JsonSchema.get
}


object JsonSchemaValidation extends JsonSchemaValidation with LongKeyedMetaMapper[JsonSchemaValidation] {
  override def dbIndexes: List[BaseIndex[JsonSchemaValidation]] = UniqueIndex(OperationId) :: super.dbIndexes
}

