package code.dynamicMessageDoc

import code.util.UUIDString
import net.liftweb.mapper._
import scala.collection.immutable.List

class DynamicMessageDoc extends LongKeyedMapper[DynamicMessageDoc] with IdPK {

  override def getSingleton = DynamicMessageDoc

  object DynamicMessageDocId extends UUIDString(this)     
  object Process extends MappedString(this, 255)   
  object MessageFormat extends MappedString(this, 255)   
  object Description extends MappedString(this, 255)   
  object OutboundTopic extends MappedString(this, 255)   
  object InboundTopic extends MappedString(this, 255)   
  object ExampleOutboundMessage extends MappedString(this, 255)   
  object ExampleInboundMessage extends MappedString(this, 255)   
  object OutboundAvroSchema extends MappedString(this, 255)   
  object InboundAvroSchema extends MappedString(this, 255)   
  object AdapterImplementation  extends MappedString(this, 255)   
  object MethodBody  extends MappedText(this)
}


object DynamicMessageDoc extends DynamicMessageDoc with LongKeyedMetaMapper[DynamicMessageDoc] {
  override def dbIndexes: List[BaseIndex[DynamicMessageDoc]] = UniqueIndex(DynamicMessageDocId) :: super.dbIndexes
  def getJsonDynamicMessageDoc(dynamicMessageDoc: DynamicMessageDoc) = JsonDynamicMessageDoc(
    dynamicMessageDocId = Some(dynamicMessageDoc.DynamicMessageDocId.get),
    process = dynamicMessageDoc.Process.get,
    messageFormat = dynamicMessageDoc.MessageFormat.get,
    description = dynamicMessageDoc.Description.get,
    outboundTopic = dynamicMessageDoc.OutboundTopic.get,
    inboundTopic = dynamicMessageDoc.InboundTopic.get,
    exampleOutboundMessage = dynamicMessageDoc.ExampleOutboundMessage.get,
    exampleInboundMessage = dynamicMessageDoc.ExampleInboundMessage.get,
    outboundAvroSchema = dynamicMessageDoc.OutboundAvroSchema.get,
    inboundAvroSchema = dynamicMessageDoc.InboundAvroSchema.get,
    adapterImplementation = dynamicMessageDoc.AdapterImplementation.get,
    methodBody = dynamicMessageDoc.MethodBody.get,
  )
}