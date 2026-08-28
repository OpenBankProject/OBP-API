package code.dynamicMessageDoc

import code.api.cache.Caching
import code.api.util.APIUtil
import code.util.Helper
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.Props

import scala.concurrent.duration.DurationInt

object MappedDynamicMessageDocProvider extends DynamicMessageDocProvider {

  private val getDynamicMessageDocTTL : Int = {
    if(Props.testMode) 0
    else APIUtil.getPropsValue(s"dynamicMessageDoc.cache.ttl.seconds", "40").toInt
  }

  override def getById(bankId: Option[String], dynamicMessageDocId: String): Box[JsonDynamicMessageDoc] =
    DynamicMessageDoc.findById(bankId, dynamicMessageDocId).map(DynamicMessageDoc.getJsonDynamicMessageDoc)

  override def getByProcess(bankId: Option[String], process: String): Box[JsonDynamicMessageDoc] =
    DynamicMessageDoc.findByProcess(bankId, process).map(DynamicMessageDoc.getJsonDynamicMessageDoc)

  override def getAll(bankId: Option[String]): List[JsonDynamicMessageDoc] = {
    val cacheKey = ("code.dynamicMessageDoc.MappedDynamicMessageDocProvider", "getAll", List(bankId).mkString("_"))
    Caching.memoizeSyncWithProvider (Some(cacheKey.toString())) (getDynamicMessageDocTTL.second) {
      DynamicMessageDoc.findAll(bankId).map(DynamicMessageDoc.getJsonDynamicMessageDoc)
    }
  }

  override def create(bankId: Option[String], entity: JsonDynamicMessageDoc,
                      createdByUserId: Option[String]): Box[JsonDynamicMessageDoc] =
    tryo {
      DynamicMessageDoc.insert(
        dynamicMessageDocId = APIUtil.generateUUID(),
        bankId = bankId,
        process = entity.process,
        messageFormat = entity.messageFormat,
        description = entity.description,
        outboundTopic = entity.outboundTopic,
        inboundTopic = entity.inboundTopic,
        exampleOutboundMessage = Helper.prettyJson(entity.exampleOutboundMessage),
        exampleInboundMessage = Helper.prettyJson(entity.exampleInboundMessage),
        outboundAvroSchema = entity.outboundAvroSchema,
        inboundAvroSchema = entity.inboundAvroSchema,
        adapterImplementation = entity.adapterImplementation,
        methodBody = entity.methodBody,
        programmingLang = entity.programmingLang,
        // Provenance from the authenticated user and a hash computed here, never from the body.
        createdByUserId = createdByUserId,
        methodBodyHash = Some(APIUtil.sha256Hex(entity.decodedMethodBody)))
    }.map(DynamicMessageDoc.getJsonDynamicMessageDoc)

  override def update(bankId: Option[String], entity: JsonDynamicMessageDoc,
                      updatedByUserId: Option[String]): Box[JsonDynamicMessageDoc] = {
    val currentId = entity.dynamicMessageDocId.getOrElse("")
    DynamicMessageDoc.findById(bankId, currentId) match {
      case Full(_) =>
        tryo {
          // bankId is not written here — the Mapper update did not set it either, so a doc cannot
          // move between system and bank scope once created.
          DynamicMessageDoc.update(
            currentDynamicMessageDocId = currentId,
            dynamicMessageDocId = currentId,
            process = entity.process,
            messageFormat = entity.messageFormat,
            description = entity.description,
            outboundTopic = entity.outboundTopic,
            inboundTopic = entity.inboundTopic,
            exampleOutboundMessage = Helper.prettyJson(entity.exampleOutboundMessage),
            exampleInboundMessage = Helper.prettyJson(entity.exampleInboundMessage),
            outboundAvroSchema = entity.outboundAvroSchema,
            inboundAvroSchema = entity.inboundAvroSchema,
            adapterImplementation = entity.adapterImplementation,
            methodBody = entity.methodBody,
            programmingLang = entity.programmingLang,
            updatedByUserId = updatedByUserId,
            methodBodyHash = Some(APIUtil.sha256Hex(entity.decodedMethodBody)))
        }.flatMap(box => box).map(DynamicMessageDoc.getJsonDynamicMessageDoc)
      case _ => Empty
    }
  }

  override def deleteById(bankId: Option[String], id: String): Box[Boolean] =
    tryo(DynamicMessageDoc.delete(bankId, id))
}
