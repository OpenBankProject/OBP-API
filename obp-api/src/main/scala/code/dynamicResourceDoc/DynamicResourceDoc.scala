package code.dynamicResourceDoc

import code.api.util.DoobieUtil
import com.openbankproject.commons.util.json
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import org.apache.commons.lang3.StringUtils

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
  methodBody: String
)

object DynamicResourceDoc {

  // roles is stored as roles_c: ROLES collides with a SQL reserved word.
  private val selectColumns =
    fr"""SELECT dynamicresourcedocid, bankid, partialfunctionname, requestverb, requesturl, summary,
                description, examplerequestbody, successresponsebody, errorresponsebodies, tags,
                roles_c, methodbody
         FROM dynamicresourcedoc"""

  private type Row = (String, Option[String], String, String, String, String, String,
    Option[String], Option[String], String, String, String, String)

  private def fromRow(row: Row): DynamicResourceDoc = row match {
    case (dynamicResourceDocId, bankId, partialFunctionName, requestVerb, requestUrl, summary,
          description, exampleRequestBody, successResponseBody, errorResponseBodies, tags, roles,
          methodBody) =>
      DynamicResourceDoc(dynamicResourceDocId, bankId, partialFunctionName, requestVerb, requestUrl,
        summary, description, exampleRequestBody, successResponseBody, errorResponseBodies, tags,
        roles, methodBody)
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
             methodBody: String): DynamicResourceDoc = {
    DoobieUtil.runUpdate(
      sql"""INSERT INTO dynamicresourcedoc
            (dynamicresourcedocid, bankid, partialfunctionname, requestverb, requesturl, summary,
             description, examplerequestbody, successresponsebody, errorresponsebodies, tags,
             roles_c, methodbody)
            VALUES ($dynamicResourceDocId, $bankId, ${Option(partialFunctionName)},
             ${Option(requestVerb)}, ${Option(requestUrl)}, ${Option(summary)},
             ${Option(description)}, $exampleRequestBody, $successResponseBody,
             ${Option(errorResponseBodies)}, ${Option(tags)}, ${Option(roles)},
             ${Option(methodBody)})"""
        .update.run)
    findById(None, dynamicResourceDocId)
      .openOrThrowException("the resource doc just inserted must be readable")
  }

  /** Unlike the message-doc update, this one DOES write bankId — the Mapper path did too. */
  def update(dynamicResourceDocId: String, bankId: Option[String], partialFunctionName: String,
             requestVerb: String, requestUrl: String, summary: String, description: String,
             exampleRequestBody: Option[String], successResponseBody: Option[String],
             errorResponseBodies: String, tags: String, roles: String,
             methodBody: String): Box[DynamicResourceDoc] = {
    DoobieUtil.runUpdate(
      sql"""UPDATE dynamicresourcedoc SET bankid = $bankId,
              partialfunctionname = ${Option(partialFunctionName)},
              requestverb = ${Option(requestVerb)}, requesturl = ${Option(requestUrl)},
              summary = ${Option(summary)}, description = ${Option(description)},
              examplerequestbody = $exampleRequestBody,
              successresponsebody = $successResponseBody,
              errorresponsebodies = ${Option(errorResponseBodies)}, tags = ${Option(tags)},
              roles_c = ${Option(roles)}, methodbody = ${Option(methodBody)}
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
