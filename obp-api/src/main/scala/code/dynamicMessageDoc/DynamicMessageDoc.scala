package code.dynamicMessageDoc

import code.api.util.DoobieUtil
import com.openbankproject.commons.util.json
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}

import java.util.Date

import scala.collection.immutable.List

/**
 * One connector process definition, uploaded at runtime.
 *
 * `process` is unique globally rather than per bank, so a bank-level and a system-level doc cannot
 * share a process name.
 *
 * `bankId` genuinely holds NULL for system-level docs and is bound as Option throughout — the
 * provider writes bankId.getOrElse(null).
 */
case class DynamicMessageDoc(
  dynamicMessageDocId: String,
  bankId: Option[String],
  process: String,
  messageFormat: String,
  description: String,
  outboundTopic: String,
  inboundTopic: String,
  exampleOutboundMessage: String,
  exampleInboundMessage: String,
  outboundAvroSchema: String,
  inboundAvroSchema: String,
  adapterImplementation: String,
  methodBody: String,
  programmingLang: String,
  // Provenance, carried across from the Mapper entity upstream extended. Written server-side from
  // the CallContext user and a hash computed here, never from the request body.
  createdByUserId: Option[String],
  updatedByUserId: Option[String],
  methodBodyHash: Option[String],
  createdAt: Option[Date],
  updatedAt: Option[Date]
)

object DynamicMessageDoc {

  private val selectColumns =
    fr"""SELECT dynamicmessagedocid, bankid, process, messageformat, description, outboundtopic,
                inboundtopic, exampleoutboundmessage, exampleinboundmessage, outboundavroschema,
                inboundavroschema, adapterimplementation, methodbody, lang,
                createdbyuserid, updatedbyuserid, methodbodyhash, createdat, updatedat
         FROM dynamicmessagedoc"""

  // Read as Option wherever the insert binds Option: a doc stored with a null topic or schema is
  // SQL NULL, and a bare String mapping would throw NonNullableColumnRead for the whole query.
  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String],
    Option[java.sql.Timestamp], Option[java.sql.Timestamp])

  /** java.sql.Timestamp is a java.util.Date subclass, but json4s renders it as {} - convert. */
  private def readDate(value: Option[java.sql.Timestamp]): Option[Date] =
    value.map(t => new Date(t.getTime))

  private def fromRow(row: Row): DynamicMessageDoc = row match {
    case (dynamicMessageDocId, bankId, process, messageFormat, description, outboundTopic,
          inboundTopic, exampleOutboundMessage, exampleInboundMessage, outboundAvroSchema,
          inboundAvroSchema, adapterImplementation, methodBody, programmingLang,
          createdByUserId, updatedByUserId, methodBodyHash, createdAt, updatedAt) =>
      // orNull, as MappedString did on read.
      DynamicMessageDoc(dynamicMessageDocId.orNull, bankId, process.orNull, messageFormat.orNull,
        description.orNull, outboundTopic.orNull, inboundTopic.orNull,
        exampleOutboundMessage.orNull, exampleInboundMessage.orNull, outboundAvroSchema.orNull,
        inboundAvroSchema.orNull, adapterImplementation.orNull, methodBody.orNull,
        programmingLang.orNull,
        createdByUserId, updatedByUserId, methodBodyHash,
        readDate(createdAt), readDate(updatedAt))
  }

  private def query(condition: Fragment): List[DynamicMessageDoc] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def one(condition: Fragment): Box[DynamicMessageDoc] =
    query(condition ++ fr"ORDER BY id ASC LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  /**
   * A supplied bank id narrows the match; an absent one does NOT constrain bankid at all, so a
   * system-level lookup also matches bank-level rows. That asymmetry is the provider's existing
   * behaviour and is reproduced rather than tightened.
   */
  private def bankFilter(bankId: Option[String]): Fragment =
    bankId.map(b => fr"AND bankid = $b").getOrElse(Fragment.empty)

  def findById(bankId: Option[String], dynamicMessageDocId: String): Box[DynamicMessageDoc] =
    one(fr"WHERE dynamicmessagedocid = $dynamicMessageDocId" ++ bankFilter(bankId))

  def findByProcess(bankId: Option[String], process: String): Box[DynamicMessageDoc] =
    one(fr"WHERE process = $process" ++ bankFilter(bankId))

  def findAll(bankId: Option[String]): List[DynamicMessageDoc] = bankId match {
    case None => query(fr"ORDER BY id ASC")
    case Some(b) => query(fr"WHERE bankid = $b ORDER BY id ASC")
  }

  def insert(dynamicMessageDocId: String, bankId: Option[String], process: String,
             messageFormat: String, description: String, outboundTopic: String,
             inboundTopic: String, exampleOutboundMessage: String, exampleInboundMessage: String,
             outboundAvroSchema: String, inboundAvroSchema: String, adapterImplementation: String,
             methodBody: String, programmingLang: String,
             createdByUserId: Option[String],
             methodBodyHash: Option[String]): DynamicMessageDoc = {
    // CreatedUpdated set both on create; the row is never written without them.
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO dynamicmessagedoc
            (dynamicmessagedocid, bankid, process, messageformat, description, outboundtopic,
             inboundtopic, exampleoutboundmessage, exampleinboundmessage, outboundavroschema,
             inboundavroschema, adapterimplementation, methodbody, lang,
             createdbyuserid, methodbodyhash, createdat, updatedat)
            VALUES ($dynamicMessageDocId, $bankId, ${Option(process)}, ${Option(messageFormat)},
             ${Option(description)}, ${Option(outboundTopic)}, ${Option(inboundTopic)},
             ${Option(exampleOutboundMessage)}, ${Option(exampleInboundMessage)},
             ${Option(outboundAvroSchema)}, ${Option(inboundAvroSchema)},
             ${Option(adapterImplementation)}, ${Option(methodBody)}, ${Option(programmingLang)},
             $createdByUserId, $methodBodyHash, $now, $now)"""
        .update.run)
    findById(None, dynamicMessageDocId)
      .openOrThrowException("the message doc just inserted must be readable")
  }

  /** bankId is deliberately not written on update, matching the Mapper path. */
  def update(currentDynamicMessageDocId: String, dynamicMessageDocId: String, process: String,
             messageFormat: String, description: String, outboundTopic: String,
             inboundTopic: String, exampleOutboundMessage: String, exampleInboundMessage: String,
             outboundAvroSchema: String, inboundAvroSchema: String, adapterImplementation: String,
             methodBody: String, programmingLang: String,
             updatedByUserId: Option[String],
             methodBodyHash: Option[String]): Box[DynamicMessageDoc] = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""UPDATE dynamicmessagedoc SET dynamicmessagedocid = ${Option(dynamicMessageDocId)},
              process = ${Option(process)}, messageformat = ${Option(messageFormat)},
              description = ${Option(description)}, outboundtopic = ${Option(outboundTopic)},
              inboundtopic = ${Option(inboundTopic)},
              exampleoutboundmessage = ${Option(exampleOutboundMessage)},
              exampleinboundmessage = ${Option(exampleInboundMessage)},
              outboundavroschema = ${Option(outboundAvroSchema)},
              inboundavroschema = ${Option(inboundAvroSchema)},
              adapterimplementation = ${Option(adapterImplementation)},
              methodbody = ${Option(methodBody)}, lang = ${Option(programmingLang)},
              updatedbyuserid = $updatedByUserId, methodbodyhash = $methodBodyHash,
              updatedat = $now
            WHERE dynamicmessagedocid = $currentDynamicMessageDocId""".update.run)
    findById(None, dynamicMessageDocId)
  }

  def delete(bankId: Option[String], dynamicMessageDocId: String): Boolean = {
    DoobieUtil.runUpdate(
      (fr"DELETE FROM dynamicmessagedoc WHERE dynamicmessagedocid = $dynamicMessageDocId" ++
        bankFilter(bankId)).update.run)
    true
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM dynamicmessagedoc".update.run)
    ()
  }

  def getJsonDynamicMessageDoc(dynamicMessageDoc: DynamicMessageDoc): JsonDynamicMessageDoc =
    JsonDynamicMessageDoc(
      bankId = dynamicMessageDoc.bankId,
      dynamicMessageDocId = Some(dynamicMessageDoc.dynamicMessageDocId),
      process = dynamicMessageDoc.process,
      messageFormat = dynamicMessageDoc.messageFormat,
      description = dynamicMessageDoc.description,
      outboundTopic = dynamicMessageDoc.outboundTopic,
      inboundTopic = dynamicMessageDoc.inboundTopic,
      exampleOutboundMessage = json.parse(dynamicMessageDoc.exampleOutboundMessage),
      exampleInboundMessage = json.parse(dynamicMessageDoc.exampleInboundMessage),
      outboundAvroSchema = dynamicMessageDoc.outboundAvroSchema,
      inboundAvroSchema = dynamicMessageDoc.inboundAvroSchema,
      adapterImplementation = dynamicMessageDoc.adapterImplementation,
      methodBody = dynamicMessageDoc.methodBody,
      programmingLang = dynamicMessageDoc.programmingLang
    )
}
