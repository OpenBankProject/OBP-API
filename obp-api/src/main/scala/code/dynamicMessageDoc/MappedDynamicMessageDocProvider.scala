package code.dynamicMessageDoc

import code.api.cache.Caching
import code.api.util.APIUtil
import com.tesobe.CacheKeyFromArguments
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.Props
import java.util.UUID.randomUUID
import code.util.Helper

import scala.concurrent.duration.DurationInt

object MappedDynamicMessageDocProvider extends DynamicMessageDocProvider {

  private val getDynamicMessageDocTTL : Int = {
    if(Props.testMode) 0
    else APIUtil.getPropsValue(s"dynamicMessageDoc.cache.ttl.seconds", "40").toInt
  }

  override def getById(dynamicMessageDocId: String): Box[JsonDynamicMessageDoc] = 
    DynamicMessageDoc.find(By(DynamicMessageDoc.DynamicMessageDocId, dynamicMessageDocId))
    .map(DynamicMessageDoc.getJsonDynamicMessageDoc)

  override def getByProcess(process: String): Box[JsonDynamicMessageDoc] = 
    DynamicMessageDoc.find(By(DynamicMessageDoc.Process, process))
    .map(DynamicMessageDoc.getJsonDynamicMessageDoc)

  
  override def getAll(): List[JsonDynamicMessageDoc] = {
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider (Some(cacheKey.toString())) (getDynamicMessageDocTTL second) {
        DynamicMessageDoc.findAll()
          .map(DynamicMessageDoc.getJsonDynamicMessageDoc)
      }}
  }

  override def create(entity: JsonDynamicMessageDoc): Box[JsonDynamicMessageDoc]=
    tryo {
      DynamicMessageDoc.create
        .DynamicMessageDocId(APIUtil.generateUUID())
        .Process(entity.process)
        .MessageFormat(entity.messageFormat)
        .Description(entity.description)
        .OutboundTopic(entity.outboundTopic)
        .InboundTopic(entity.inboundTopic)
        .ExampleOutboundMessage(Helper.prettyJson(entity.exampleOutboundMessage))
        .ExampleInboundMessage(Helper.prettyJson(entity.exampleInboundMessage))
        .OutboundAvroSchema(entity.outboundAvroSchema)
        .InboundAvroSchema(entity.inboundAvroSchema)
        .AdapterImplementation(entity.adapterImplementation)
        .MethodBody(entity.methodBody)
        .saveMe()
    }.map(DynamicMessageDoc.getJsonDynamicMessageDoc)


  override def update(entity: JsonDynamicMessageDoc): Box[JsonDynamicMessageDoc] = {
    DynamicMessageDoc.find(By(DynamicMessageDoc.DynamicMessageDocId, entity.dynamicMessageDocId.getOrElse(""))) match {
      case Full(v) =>
        tryo {
          v.DynamicMessageDocId(entity.dynamicMessageDocId.getOrElse(""))
            .Process(entity.process)
            .MessageFormat(entity.messageFormat)
            .Description(entity.description)
            .OutboundTopic(entity.outboundTopic)
            .InboundTopic(entity.inboundTopic)
            .ExampleOutboundMessage(Helper.prettyJson(entity.exampleOutboundMessage))
            .ExampleInboundMessage(Helper.prettyJson(entity.exampleInboundMessage))
            .OutboundAvroSchema(entity.outboundAvroSchema)
            .InboundAvroSchema(entity.inboundAvroSchema)
            .AdapterImplementation(entity.adapterImplementation)
            .MethodBody(entity.methodBody)
            .saveMe()
        }.map(DynamicMessageDoc.getJsonDynamicMessageDoc)
      case _ => Empty
    }
  }

  override def deleteById(id: String): Box[Boolean] = tryo {
    DynamicMessageDoc.bulkDelete_!!(By(DynamicMessageDoc.DynamicMessageDocId, id))
  }
}