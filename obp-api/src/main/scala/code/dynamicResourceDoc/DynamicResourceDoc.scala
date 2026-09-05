package code.dynamicResourceDoc

import code.api.util.DoobieUtil
import com.openbankproject.commons.util.json
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
import net.liftweb.common.{Box, Empty, Full}
import org.apache.commons.lang3.StringUtils

import java.util.Date

import scala.collection.immutable.List

/**
 * One runtime-defined endpoint.
 *
 * `bankId`, `exampleRequestBody` and `successResponseBody` genuinely hold NULL — the provider
 * writes bankId.getOrElse(null) and maps the two optional JSON bodies through orNull — so all three
 * are bound as Option. An absent body must not become "" either, since the reader distinguishes
 * blank from present.
 */
case class DynamicResourceDoc(
  dynamicResourceDocId: String,
  bankId: Option[String],
  partialFunctionName: String,
  requestVerb: String,
  requestUrl: String,
  summary: String,
  description: String,
  exampleRequestBody: Option[String],
  successResponseBody: Option[String],
  errorResponseBodies: String,
  tags: String,
  roles: String,
  methodBody: String,
  // Provenance, added upstream on the Mapper entity and carried across: who created / last updated
  // this runtime-compiled endpoint, and a SHA-256 of the decoded method body so drift is
  // detectable. Written server-side from the CallContext user, never from the request body.
  createdByUserId: Option[String],
  updatedByUserId: Option[String],
  methodBodyHash: Option[String],
  // CreatedUpdated's two columns. Read as java.util.Date, not the java.sql.Timestamp the driver
  // hands back: json4s serialises the subclass as an empty JSON object.
  createdAt: Option[Date],
  updatedAt: Option[Date]
)

object DynamicResourceDoc {

  // roles is stored as roles_c: ROLES collides with a SQL reserved word.
  private val selectColumns =
    fr"""SELECT dynamicresourcedocid, bankid, partialfunctionname, requestverb, requesturl, summary,
                description, examplerequestbody, successresponsebody, errorresponsebodies, tags,
                roles_c, methodbody, createdbyuserid, updatedbyuserid, methodbodyhash,
                createdat, updatedat
         FROM dynamicresourcedoc"""

  // Every column the insert below binds through Option is read as one too. A doc posted with a
  // null tags or summary really does store SQL NULL - Mapper did the same - and reading it as a
  // bare String throws NonNullableColumnRead, which fails the whole query rather than the one row.
  private type Row = (Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String],
    Option[java.sql.Timestamp], Option[java.sql.Timestamp])

  /** java.sql.Timestamp is a java.util.Date subclass, but json4s renders it as {} - convert. */
  private def readDate(value: Option[java.sql.Timestamp]): Option[Date] =
    value.map(t => new Date(t.getTime))

  private def fromRow(row: Row): DynamicResourceDoc = row match {
    case (dynamicResourceDocId, bankId, partialFunctionName, requestVerb, requestUrl, summary,
          description, exampleRequestBody, successResponseBody, errorResponseBodies, tags, roles,
          methodBody, createdByUserId, updatedByUserId, methodBodyHash, createdAt, updatedAt) =>
      // orNull, not "": MappedString handed a NULL column back as null and the JSON showed null.
      DynamicResourceDoc(dynamicResourceDocId.orNull, bankId, partialFunctionName.orNull,
        requestVerb.orNull, requestUrl.orNull, summary.orNull, description.orNull,
        exampleRequestBody, successResponseBody, errorResponseBodies.orNull, tags.orNull,
        roles.orNull, methodBody.orNull,
        createdByUserId, updatedByUserId, methodBodyHash,
        readDate(createdAt), readDate(updatedAt))
  }

  private def query(condition: Fragment): List[DynamicResourceDoc] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map(fromRow)

  private def one(condition: Fragment): Box[DynamicResourceDoc] =
    query(condition ++ fr"ORDER BY id ASC LIMIT 1").headOption match {
      case Some(row) => Full(row)
      case None => Empty
    }

  /**
   * A supplied bank id narrows the match; an absent one does NOT constrain bankid, so a
   * system-level lookup also matches bank-level rows. Pre-existing provider behaviour.
   */
  private def bankFilter(bankId: Option[String]): Fragment =
    bankId.map(b => fr"AND bankid = $b").getOrElse(Fragment.empty)

  def findById(bankId: Option[String], dynamicResourceDocId: String): Box[DynamicResourceDoc] =
    one(fr"WHERE dynamicresourcedocid = $dynamicResourceDocId" ++ bankFilter(bankId))

  def findByVerbAndUrl(bankId: Option[String], requestVerb: String,
                       requestUrl: String): Box[DynamicResourceDoc] =
    one(fr"WHERE requestverb = $requestVerb AND requesturl = $requestUrl" ++ bankFilter(bankId))

  def findAll(bankId: Option[String]): List[DynamicResourceDoc] = bankId match {
    case None => query(fr"ORDER BY id ASC")
    case Some(b) => query(fr"WHERE bankid = $b ORDER BY id ASC")
  }

  def insert(dynamicResourceDocId: String, bankId: Option[String], partialFunctionName: String,
             requestVerb: String, requestUrl: String, summary: String, description: String,
             exampleRequestBody: Option[String], successResponseBody: Option[String],
             errorResponseBodies: String, tags: String, roles: String,
             methodBody: String, createdByUserId: Option[String],
             methodBodyHash: Option[String]): DynamicResourceDoc = {
    // CreatedUpdated set both on create; the row is never written without them.
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""INSERT INTO dynamicresourcedoc
            (dynamicresourcedocid, bankid, partialfunctionname, requestverb, requesturl, summary,
             description, examplerequestbody, successresponsebody, errorresponsebodies, tags,
             roles_c, methodbody, createdbyuserid, methodbodyhash, createdat, updatedat)
            VALUES ($dynamicResourceDocId, $bankId, ${Option(partialFunctionName)},
             ${Option(requestVerb)}, ${Option(requestUrl)}, ${Option(summary)},
             ${Option(description)}, $exampleRequestBody, $successResponseBody,
             ${Option(errorResponseBodies)}, ${Option(tags)}, ${Option(roles)},
             ${Option(methodBody)}, $createdByUserId, $methodBodyHash, $now, $now)"""
        .update.run)
    findById(None, dynamicResourceDocId)
      .openOrThrowException("the resource doc just inserted must be readable")
  }

  /** Unlike the message-doc update, this one DOES write bankId — the Mapper path did too. */
  def update(dynamicResourceDocId: String, bankId: Option[String], partialFunctionName: String,
             requestVerb: String, requestUrl: String, summary: String, description: String,
             exampleRequestBody: Option[String], successResponseBody: Option[String],
             errorResponseBodies: String, tags: String, roles: String,
             methodBody: String, updatedByUserId: Option[String],
             methodBodyHash: Option[String]): Box[DynamicResourceDoc] = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runUpdate(
      sql"""UPDATE dynamicresourcedoc SET bankid = $bankId,
              partialfunctionname = ${Option(partialFunctionName)},
              requestverb = ${Option(requestVerb)}, requesturl = ${Option(requestUrl)},
              summary = ${Option(summary)}, description = ${Option(description)},
              examplerequestbody = $exampleRequestBody,
              successresponsebody = $successResponseBody,
              errorresponsebodies = ${Option(errorResponseBodies)}, tags = ${Option(tags)},
              roles_c = ${Option(roles)}, methodbody = ${Option(methodBody)},
              updatedbyuserid = $updatedByUserId, methodbodyhash = $methodBodyHash,
              updatedat = $now
            WHERE dynamicresourcedocid = $dynamicResourceDocId""".update.run)
    findById(None, dynamicResourceDocId)
  }

  def delete(bankId: Option[String], dynamicResourceDocId: String): Boolean = {
    DoobieUtil.runUpdate(
      (fr"DELETE FROM dynamicresourcedoc WHERE dynamicresourcedocid = $dynamicResourceDocId" ++
        bankFilter(bankId)).update.run)
    true
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM dynamicresourcedoc".update.run)
    ()
  }

  def getJsonDynamicResourceDoc(dynamicResourceDoc: DynamicResourceDoc): JsonDynamicResourceDoc =
    JsonDynamicResourceDoc(
      bankId = dynamicResourceDoc.bankId,
      dynamicResourceDocId = Some(dynamicResourceDoc.dynamicResourceDocId),
      methodBody = dynamicResourceDoc.methodBody,
      partialFunctionName = dynamicResourceDoc.partialFunctionName,
      requestVerb = dynamicResourceDoc.requestVerb,
      requestUrl = dynamicResourceDoc.requestUrl,
      summary = dynamicResourceDoc.summary,
      description = dynamicResourceDoc.description,
      exampleRequestBody = dynamicResourceDoc.exampleRequestBody.filter(StringUtils.isNotBlank).map(json.parse),
      successResponseBody = dynamicResourceDoc.successResponseBody.filter(StringUtils.isNotBlank).map(json.parse),
      errorResponseBodies = dynamicResourceDoc.errorResponseBodies,
      tags = dynamicResourceDoc.tags,
      roles = dynamicResourceDoc.roles
    )
}
