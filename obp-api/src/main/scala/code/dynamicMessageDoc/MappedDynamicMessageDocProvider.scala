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

  override def getById(bankId: Option[String], dynamicMessageDocId: String): Box[JsonDynamicMessageDoc] =
    if(bankId.isEmpty) {
      DynamicMessageDoc.find(By(DynamicMessageDoc.DynamicMessageDocId, dynamicMessageDocId)).map(DynamicMessageDoc.getJsonDynamicMessageDoc)
    }else{
      DynamicMessageDoc.find(
        By(DynamicMessageDoc.DynamicMessageDocId, dynamicMessageDocId),
        By(DynamicMessageDoc.BankId, bankId.getOrElse("")
      )).map(DynamicMessageDoc.getJsonDynamicMessageDoc)
    }

  override def getByProcess(bankId: Option[String], process: String): Box[JsonDynamicMessageDoc] =
    if(bankId.isEmpty) {
      DynamicMessageDoc.find(By(DynamicMessageDoc.Process, process)).map(DynamicMessageDoc.getJsonDynamicMessageDoc)
    }else{
      DynamicMessageDoc.find(
        By(DynamicMessageDoc.Process, process),
        By(DynamicMessageDoc.BankId, bankId.getOrElse("")
      )).map(DynamicMessageDoc.getJsonDynamicMessageDoc)
    }

  
  override def getAll(bankId: Option[String]): List[JsonDynamicMessageDoc] = {
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider (Some(cacheKey.toString())) (getDynamicMessageDocTTL second) {
        if(bankId.isEmpty){
          DynamicMessageDoc.findAll().map(DynamicMessageDoc.getJsonDynamicMessageDoc)
        } else {
          DynamicMessageDoc.findAll(By(DynamicMessageDoc.BankId, bankId.getOrElse(""))).map(DynamicMessageDoc.getJsonDynamicMessageDoc)
        }
      }}
  }

  override def create(bankId: Option[String], entity: JsonDynamicMessageDoc): Box[JsonDynamicMessageDoc]= {
    tryo {
      DynamicMessageDoc.create
        .BankId(bankId.getOrElse(null))
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
        .Lang(entity.programmingLang)
        .saveMe()
    }.map(DynamicMessageDoc.getJsonDynamicMessageDoc)
  }


  override def update(bankId: Option[String], entity: JsonDynamicMessageDoc): Box[JsonDynamicMessageDoc] = {
    val dynamicMessageDocBox = if(bankId.isDefined){
      DynamicMessageDoc.find(
        By(DynamicMessageDoc.DynamicMessageDocId, entity.dynamicMessageDocId.getOrElse("")),
        By(DynamicMessageDoc.BankId, bankId.head)
      )
    } else {
      DynamicMessageDoc.find(
        By(DynamicMessageDoc.DynamicMessageDocId, entity.dynamicMessageDocId.getOrElse(""))
      )
    }
    dynamicMessageDocBox match {
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
            .Lang(entity.programmingLang)
            .saveMe()
        }.map(DynamicMessageDoc.getJsonDynamicMessageDoc)
      case _ => Empty
    }
  }

  override def deleteById(bankId: Option[String], id: String): Box[Boolean] = tryo {
    if(bankId.isEmpty) {
      DynamicMessageDoc.bulkDelete_!!(By(DynamicMessageDoc.DynamicMessageDocId, id))
    }else{
      DynamicMessageDoc.bulkDelete_!!(
        By(DynamicMessageDoc.BankId, bankId.getOrElse("")),
        By(DynamicMessageDoc.DynamicMessageDocId, id)
      )
    }
  }
}