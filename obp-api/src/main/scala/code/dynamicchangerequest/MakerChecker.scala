package code.dynamicchangerequest

import java.net.URLDecoder
import java.util.Date

import code.abacrule.{AbacRule, AbacRuleEngine, MappedAbacRuleProvider}
import code.api.Constant
import code.api.dynamic.endpoint.helper.CompiledObjects
import code.api.util.APIUtil.{getPropsAsBoolValue, getPropsAsIntValue, getPropsValue, sha256Hex}
import code.api.util.DynamicUtil.Validation
import code.api.util.{CallContext, ErrorMessages}
import code.api.v6_0_0.{CreateAbacRuleJsonV600, UpdateAbacRuleJsonV600}
import code.bankconnectors.{DynamicConnector, InternalConnector}
import code.connectormethod.{ConnectorMethod, ConnectorMethodProvider, JsonConnectorMethod, JsonConnectorMethodMethodBody}
import code.dynamicMessageDoc.{DynamicMessageDoc, DynamicMessageDocProvider, JsonDynamicMessageDoc}
import code.dynamicResourceDoc.{DynamicResourceDoc, DynamicResourceDocProvider, JsonDynamicResourceDoc}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.enums.DynamicChangeRequestOperation._
import com.openbankproject.commons.model.enums.DynamicChangeRequestStatus
import com.openbankproject.commons.model.enums.DynamicChangeRequestTargetType._
import com.openbankproject.commons.model.enums.{DynamicChangeRequestOperation, DynamicChangeRequestTargetType}
import com.openbankproject.commons.util.JsonAliases.{compactRender, parse}
import net.liftweb.common.{Box, Empty, Failure, Full}
import org.json4s.Formats
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.tryo
import org.apache.commons.lang3.StringUtils
import org.json4s.JsonAST.{JArray, JNothing, JObject, JValue}

/**
 * Maker/checker for runtime-supplied code and configuration. Design: MAKER_CHECKER_DYNAMIC_CODE_DESIGN.md.
 *
 * Three responsibilities:
 *  - configuration + hashing (what is managed, what "the same content" means);
 *  - the execution guard: the runtime loads a row only when it is active and, for a managed type,
 *    when its body hash equals the hash a checker approved (enforced at the load site, so it holds
 *    against direct database edits and stale compile memos);
 *  - the request lifecycle: intercept a v4/v6 write into the queue, approve (re-validate + apply
 *    through the same providers the v4/v6 handlers use), reject, withdraw, expire, deactivate.
 */
object MakerChecker extends MdcLoggable {

  implicit private val formats: Formats = code.api.util.CustomJsonFormats.formats

  // ─── configuration ─────────────────────────────────────────────────────────

  /** dynamic_code_requires_approval: when true, writes to the managed target types are queued for a second
    * user's approval and the runtime executes only code whose body hash a checker approved. */
  def enabled: Boolean = getPropsAsBoolValue("dynamic_code_requires_approval", false)

  private val defaultTargetTypes = List(DYNAMIC_RESOURCE_DOC, DYNAMIC_MESSAGE_DOC, CONNECTOR_METHOD, ABAC_RULE).map(_.toString)

  def managedTargetTypes: Set[String] =
    getPropsValue("dynamic_code_approval_target_types", defaultTargetTypes.mkString(","))
      .split(",").map(_.trim).filter(_.nonEmpty).toSet

  def isManaged(targetType: DynamicChangeRequestTargetType): Boolean = enabled && managedTargetTypes.contains(targetType.toString)

  def requireApprovalForDelete: Boolean = getPropsAsBoolValue("dynamic_code_delete_requires_approval", true)

  def requestTtlHours: Int = getPropsAsIntValue("dynamic_code_approval_request_ttl_hours", 168)

  /** Only these types can be applied by this phase; the enum lists the later phases too. */
  val applicableTargetTypes: Set[DynamicChangeRequestTargetType] = Set(DYNAMIC_RESOURCE_DOC, DYNAMIC_MESSAGE_DOC, CONNECTOR_METHOD, ABAC_RULE)

  // ─── hashing ───────────────────────────────────────────────────────────────

  /** Sorted keys, no insignificant whitespace, so a client that reorders fields hashes the same. */
  def canonicalJson(jv: JValue): JValue = jv match {
    case JObject(fields) => JObject(fields.sortBy(_._1).map { case (k, v) => (k, canonicalJson(v)) })
    case JArray(items)   => JArray(items.map(canonicalJson))
    case other           => other
  }

  /** SHA-256 hex of the canonical form of a JSON payload (falls back to the raw text when it is not JSON). */
  def payloadHash(rawPayload: String): String = {
    val raw = Option(rawPayload).getOrElse("")
    // A DELETE has no body: parse("") yields JNothing, which cannot be rendered, so hash the raw text.
    tryo(parse(raw)).filter(_ != JNothing).map(jv => sha256Hex(compactRender(canonicalJson(jv)))).getOrElse(sha256Hex(raw))
  }

  /** Accept "sha256:<hex>" as well as bare hex from clients. */
  def normaliseHash(h: String): String = Option(h).map(_.trim).map(_.stripPrefix("sha256:")).getOrElse("")

  private def blank(s: String): Boolean = StringUtils.isBlank(s)

  private def bodyHashOf(storedHash: String, encodedBody: String): String =
    if (!blank(storedHash)) storedHash
    else sha256Hex(URLDecoder.decode(Option(encodedBody).getOrElse(""), "UTF-8"))

  /** The live target's body hash, Empty when the target does not exist. */
  def currentBodyHash(targetType: DynamicChangeRequestTargetType, targetId: String): Box[String] = targetType match {
    case DYNAMIC_RESOURCE_DOC => DynamicResourceDoc.find(By(DynamicResourceDoc.DynamicResourceDocId, targetId)).map(r => bodyHashOf(r.MethodBodyHash.get, r.MethodBody.get))
    case DYNAMIC_MESSAGE_DOC  => DynamicMessageDoc.find(By(DynamicMessageDoc.DynamicMessageDocId, targetId)).map(r => bodyHashOf(r.MethodBodyHash.get, r.MethodBody.get))
    case CONNECTOR_METHOD     => ConnectorMethod.find(By(ConnectorMethod.ConnectorMethodId, targetId)).map(r => bodyHashOf(r.MethodBodyHash.get, r.MethodBody.get))
    case ABAC_RULE            => AbacRule.find(By(AbacRule.AbacRuleId, targetId)).map(r => sha256Hex(Option(r.RuleCode.get).getOrElse("")))
    case _                    => Empty
  }

  // ─── execution guard ───────────────────────────────────────────────────────

  private def executable(isActive: Boolean, bodyHash: String, approvedHash: String, targetType: DynamicChangeRequestTargetType): Boolean =
    isActive && (!isManaged(targetType) || (!blank(approvedHash) && bodyHash == approvedHash))

  // Connector methods and message docs are looked up per API call, so the per-row guard is memoised
  // briefly (0 in test mode, like the providers' own caches). Approval / deactivation therefore take
  // up to this long to propagate to those two families; the resource doc group is already TTL-cached.
  private val guardTtl: Int = if (net.liftweb.util.Props.testMode) 0 else getPropsAsIntValue("dynamic_code_approval_guard_cache_ttl_seconds", 10)
  private def memoGuard(key: String)(check: => Boolean): Boolean =
    code.api.cache.Caching.memoizeSyncWithImMemory(Some(("maker_checker_guard_" + key).intern()))(scala.concurrent.duration.Duration(guardTtl, "seconds"))(check)

  def isExecutableDynamicResourceDoc(dynamicResourceDocId: String): Boolean =
    DynamicResourceDoc.find(By(DynamicResourceDoc.DynamicResourceDocId, dynamicResourceDocId))
      .map(r => executable(r.IsActive.get, bodyHashOf(r.MethodBodyHash.get, r.MethodBody.get), r.ApprovedHash.get, DYNAMIC_RESOURCE_DOC))
      .getOrElse(false)

  def isExecutableDynamicMessageDoc(dynamicMessageDocId: String): Boolean = memoGuard("dmd_" + dynamicMessageDocId) {
    DynamicMessageDoc.find(By(DynamicMessageDoc.DynamicMessageDocId, dynamicMessageDocId))
      .map(r => executable(r.IsActive.get, bodyHashOf(r.MethodBodyHash.get, r.MethodBody.get), r.ApprovedHash.get, DYNAMIC_MESSAGE_DOC))
      .getOrElse(false)
  }

  def isExecutableConnectorMethod(connectorMethodId: String): Boolean = memoGuard("cm_" + connectorMethodId) {
    ConnectorMethod.find(By(ConnectorMethod.ConnectorMethodId, connectorMethodId))
      .map(r => executable(r.IsActive.get, bodyHashOf(r.MethodBodyHash.get, r.MethodBody.get), r.ApprovedHash.get, CONNECTOR_METHOD))
      .getOrElse(false)
  }

  /** AbacRuleEngine already checks IsActive; this adds the approved-hash check for managed instances. */
  def isApprovedAbacRule(abacRuleId: String): Boolean =
    if (!isManaged(ABAC_RULE)) true
    else AbacRule.find(By(AbacRule.AbacRuleId, abacRuleId))
      .map(r => !blank(r.ApprovedHash.get) && sha256Hex(Option(r.RuleCode.get).getOrElse("")) == r.ApprovedHash.get)
      .getOrElse(false)

  // ─── submission ────────────────────────────────────────────────────────────

  private def provider = DynamicChangeRequestTrait.dynamicChangeRequest.vend

  private def expiry(): Option[Date] = {
    val hours = requestTtlHours
    if (hours <= 0) None else Some(new Date(System.currentTimeMillis() + hours.toLong * 3600L * 1000L))
  }

  /**
   * Called by a v4/v6 create/update/delete handler AFTER it has parsed, validated and compiled the
   * body exactly as it does today. Some(request) means the write was queued and the handler must
   * answer 202 with it; None means maker/checker does not apply and the handler proceeds as before.
   */
  def intercept(
    targetType: DynamicChangeRequestTargetType,
    operation: DynamicChangeRequestOperation,
    targetId: Option[String],
    cc: CallContext
  ): Box[Option[DynamicChangeRequestTrait]] = {
    val deleteBypass = operation == DELETE && !requireApprovalForDelete
    if (!isManaged(targetType) || deleteBypass) Full(None)
    else {
      val requestor = cc.user.map(_.userId).openOr("")
      if (blank(requestor)) Failure(ErrorMessages.AuthenticatedUserIsRequired)
      else submit(targetType, operation, targetId, cc.verb, cc.url, cc.httpBody.getOrElse(""), requestor, "").map(Some(_))
    }
  }

  def submit(
    targetType: DynamicChangeRequestTargetType,
    operation: DynamicChangeRequestOperation,
    targetId: Option[String],
    requestVerb: String,
    requestPath: String,
    proposedPayload: String,
    requestorUserId: String,
    businessJustification: String
  ): Box[DynamicChangeRequestTrait] = {
    val id = targetId.getOrElse("")
    val current: Box[String] = if (operation == CREATE) Full("") else currentBodyHash(targetType, id)
    current match {
      case Full(currentHash) =>
        provider.create(
          targetType = targetType.toString,
          targetId = id,
          operation = operation.toString,
          requestVerb = requestVerb,
          requestPath = requestPath,
          proposedPayload = proposedPayload,
          payloadHash = payloadHash(proposedPayload),
          currentPayloadHash = currentHash,
          requestorUserId = requestorUserId,
          businessJustification = businessJustification,
          expiresAt = expiry()
        )
      case _ => Failure(s"${ErrorMessages.DynamicChangeRequestTargetNotFound} ${targetType} $id")
    }
  }

  // ─── lifecycle ─────────────────────────────────────────────────────────────

  /** Lazily move an overdue INITIATED request to EXPIRED and return the refreshed row. */
  def expireIfDue(request: DynamicChangeRequestTrait): DynamicChangeRequestTrait =
    if (request.status == DynamicChangeRequestStatus.INITIATED.toString && request.expiresAt.exists(_.before(new Date())))
      provider.updateStatus(request.dynamicChangeRequestId, DynamicChangeRequestStatus.EXPIRED.toString, "", "expired").openOr(request)
    else request

  def approve(request: DynamicChangeRequestTrait, checkerUserId: String, payloadHashFromChecker: String, comment: String): Box[DynamicChangeRequestTrait] = {
    val targetType = tryo(DynamicChangeRequestTargetType.withName(request.targetType)).toOption
    val operation = tryo(DynamicChangeRequestOperation.withName(request.operation)).toOption
    for {
      _ <- boolBox(request.status == DynamicChangeRequestStatus.INITIATED.toString, ErrorMessages.DynamicChangeRequestNotInitiated)
      _ <- boolBox(checkerUserId != request.requestorUserId, ErrorMessages.MakerCheckerSameUser)
      _ <- boolBox(normaliseHash(payloadHashFromChecker) == request.payloadHash, ErrorMessages.DynamicChangeRequestHashMismatch)
      tt <- Box(targetType) ?~! s"${ErrorMessages.DynamicChangeRequestTargetTypeNotManaged} ${request.targetType}"
      op <- Box(operation) ?~! s"${ErrorMessages.InvalidJsonFormat} operation ${request.operation}"
      _ <- boolBox(applicableTargetTypes.contains(tt), s"${ErrorMessages.DynamicChangeRequestTargetTypeNotManaged} ${request.targetType}")
      _ <- if (op == CREATE) Full(()) else currentBodyHash(tt, request.targetId) match {
        case Full(h) if h == request.currentPayloadHash => Full(())
        case Full(_) => Failure(ErrorMessages.DynamicChangeRequestStale)
        case _ => Failure(s"${ErrorMessages.DynamicChangeRequestTargetNotFound} ${request.targetType} ${request.targetId}")
      }
      // Win the INITIATED -> APPROVED transition BEFORE applying, so a concurrent approve/reject
      // cannot apply twice; if apply then fails the row is moved to FAILED with the reason.
      approved <- provider.updateStatus(request.dynamicChangeRequestId, DynamicChangeRequestStatus.APPROVED.toString, checkerUserId, comment)
      result <- applyRequest(approved, tt, op) match {
        case Full(appliedTargetId) =>
          // A CREATE only knows its target after apply: record it so the request points at the row it made.
          if (blank(approved.targetId) && !blank(appliedTargetId)) setTargetId(approved, appliedTargetId)
          Full(approved)
        case fail: Failure =>
          markFailed(approved, s"${Option(comment).getOrElse("")} | apply failed: ${fail.messageChain}".trim)
          Failure(s"${ErrorMessages.DynamicChangeRequestApplyFailed} ${fail.messageChain}")
        case _ =>
          markFailed(approved, s"${Option(comment).getOrElse("")} | apply failed".trim)
          Failure(ErrorMessages.DynamicChangeRequestApplyFailed)
      }
      refreshed <- provider.getById(result.dynamicChangeRequestId)
    } yield refreshed
  }

  def reject(request: DynamicChangeRequestTrait, checkerUserId: String, comment: String): Box[DynamicChangeRequestTrait] =
    for {
      _ <- boolBox(request.status == DynamicChangeRequestStatus.INITIATED.toString, ErrorMessages.DynamicChangeRequestNotInitiated)
      _ <- boolBox(checkerUserId != request.requestorUserId, ErrorMessages.MakerCheckerSameUser)
      updated <- provider.updateStatus(request.dynamicChangeRequestId, DynamicChangeRequestStatus.REJECTED.toString, checkerUserId, comment)
    } yield updated

  def withdraw(request: DynamicChangeRequestTrait, requestorUserId: String, comment: String): Box[DynamicChangeRequestTrait] =
    for {
      _ <- boolBox(request.status == DynamicChangeRequestStatus.INITIATED.toString, ErrorMessages.DynamicChangeRequestNotInitiated)
      _ <- boolBox(requestorUserId == request.requestorUserId, ErrorMessages.DynamicChangeRequestNotRequestor)
      updated <- provider.updateStatus(request.dynamicChangeRequestId, DynamicChangeRequestStatus.WITHDRAWN.toString, requestorUserId, comment)
    } yield updated

  /**
   * Four eyes to enable, one pair to disable: a single approver deactivates directly. Audited as a
   * DEACTIVATE row written straight through to APPROVED with requestor = checker.
   */
  def deactivate(targetType: DynamicChangeRequestTargetType, targetId: String, checkerUserId: String, comment: String): Box[DynamicChangeRequestTrait] =
    for {
      _ <- boolBox(applicableTargetTypes.contains(targetType), s"${ErrorMessages.DynamicChangeRequestTargetTypeNotManaged} $targetType")
      currentHash <- currentBodyHash(targetType, targetId) ?~! s"${ErrorMessages.DynamicChangeRequestTargetNotFound} $targetType $targetId"
      _ <- setActive(targetType, targetId, active = false)
      audit <- provider.create(targetType.toString, targetId, DEACTIVATE.toString, "POST", "", """{"is_active":false}""",
        payloadHash("""{"is_active":false}"""), currentHash, checkerUserId, comment, None)
      done <- provider.updateStatus(audit.dynamicChangeRequestId, DynamicChangeRequestStatus.APPROVED.toString, checkerUserId, comment)
    } yield done

  private def setTargetId(request: DynamicChangeRequestTrait, targetId: String): Unit =
    DynamicChangeRequest.find(By(DynamicChangeRequest.DynamicChangeRequestId, request.dynamicChangeRequestId)).foreach { row =>
      row.TargetId(targetId).save
    }

  private def markFailed(request: DynamicChangeRequestTrait, comment: String): Unit =
    DynamicChangeRequest.find(By(DynamicChangeRequest.DynamicChangeRequestId, request.dynamicChangeRequestId)).foreach { row =>
      row.Status(DynamicChangeRequestStatus.FAILED.toString).CheckerComment(comment.take(4000)).save
    }

  private def boolBox(condition: Boolean, failMsg: => String): Box[Unit] = if (condition) Full(()) else Failure(failMsg)

  // ─── apply ─────────────────────────────────────────────────────────────────

  private val bankInPath = """.*/banks/([^/]+)/.*""".r

  /** The maker's original call encodes the scope: /management/banks/BANK_ID/... vs /management/... */
  def bankIdFromPath(requestPath: String): Option[String] = requestPath match {
    case bankInPath(bankId) if !blank(bankId) => Some(bankId)
    case _ => None
  }

  /** Applies the request and returns the id of the target it acted on (the new id for a CREATE). */
  private def applyRequest(request: DynamicChangeRequestTrait, targetType: DynamicChangeRequestTargetType, operation: DynamicChangeRequestOperation): Box[String] = {
    val result: Box[String] = tryo {
      targetType match {
        case DYNAMIC_RESOURCE_DOC => applyDynamicResourceDoc(request, operation)
        case DYNAMIC_MESSAGE_DOC  => applyDynamicMessageDoc(request, operation)
        case CONNECTOR_METHOD     => applyConnectorMethod(request, operation)
        case ABAC_RULE            => applyAbacRule(request, operation)
        case other                => Failure(s"${ErrorMessages.DynamicChangeRequestTargetTypeNotManaged} $other")
      }
    }.flatMap(identity)
    result.foreach(id => invalidateCaches(targetType, id))
    result
  }

  private def invalidateCaches(targetType: DynamicChangeRequestTargetType, targetId: String): Unit = targetType match {
    case DYNAMIC_RESOURCE_DOC =>
      Constant.incrementCacheNamespaceVersion(Constant.RD_DYNAMIC_NAMESPACE)
      Constant.incrementCacheNamespaceVersion(Constant.RD_ALL_NAMESPACE)
    case ABAC_RULE => AbacRuleEngine.clearRuleFromCache(targetId)
    case _ => ()
  }

  private def parseAs[T: Manifest](payload: String): Box[T] =
    tryo(parse(payload).extract[T]) ?~! s"${ErrorMessages.InvalidJsonFormat} The stored payload is not a ${manifest[T].runtimeClass.getSimpleName}"

  private def compileBox(description: String)(block: => Box[_]): Box[Unit] = {
    val compiled: Box[Any] = tryo(block) match {
      case Full(inner) => inner
      case f: Failure  => f
      case _           => Empty
    }
    compiled match {
      case Full(_) => Full(())
      case f: Failure => Failure(s"${ErrorMessages.DynamicCodeCompileFail} $description: ${f.messageChain}")
      case _ => Failure(s"${ErrorMessages.DynamicCodeCompileFail} $description")
    }
  }

  private def applyDynamicResourceDoc(request: DynamicChangeRequestTrait, operation: DynamicChangeRequestOperation): Box[String] = {
    val bankId = bankIdFromPath(request.requestPath)
    val p = DynamicResourceDocProvider.provider.vend
    operation match {
      case CREATE | UPDATE =>
        for {
          body <- parseAs[JsonDynamicResourceDoc](request.proposedPayload)
          _ <- compileBox("dynamic resource doc") {
            val compiled = CompiledObjects(body.exampleRequestBody, body.successResponseBody, body.methodBody)
            compiled.validateDependency()
            Full(compiled)
          }
          saved <- if (operation == CREATE) {
            for {
              _ <- boolBox(p.getByVerbAndUrl(bankId, body.requestVerb, body.requestUrl).isEmpty,
                s"${ErrorMessages.DynamicResourceDocAlreadyExists} ${body.requestVerb} ${body.requestUrl}")
              created <- p.create(bankId, body, Some(request.requestorUserId))
            } yield created
          } else p.update(bankId, body.copy(dynamicResourceDocId = Some(request.targetId)), Some(request.requestorUserId))
          id = saved.dynamicResourceDocId.getOrElse("")
          _ <- markApproved(DYNAMIC_RESOURCE_DOC, id)
        } yield id
      case DELETE   => p.deleteById(bankId, request.targetId).map(_ => request.targetId)
      case ACTIVATE => markApproved(DYNAMIC_RESOURCE_DOC, request.targetId).map(_ => request.targetId)
      case other    => Failure(s"${ErrorMessages.InvalidJsonFormat} operation $other is not a request operation")
    }
  }

  private def applyDynamicMessageDoc(request: DynamicChangeRequestTrait, operation: DynamicChangeRequestOperation): Box[String] = {
    val bankId = bankIdFromPath(request.requestPath)
    val p = DynamicMessageDocProvider.provider.vend
    operation match {
      case CREATE | UPDATE =>
        for {
          body <- parseAs[JsonDynamicMessageDoc](request.proposedPayload)
          _ <- compileBox("dynamic message doc") {
            val fn = DynamicConnector.createFunction(body.programmingLang, body.decodedMethodBody)
            fn.foreach(Validation.validateDependency(_))
            fn
          }
          saved <- if (operation == CREATE) {
            for {
              _ <- boolBox(p.getByProcess(bankId, body.process).isEmpty, s"${ErrorMessages.DynamicMessageDocAlreadyExists} ${body.process}")
              created <- p.create(bankId, body, Some(request.requestorUserId))
            } yield created
          } else p.update(bankId, body.copy(dynamicMessageDocId = Some(request.targetId)), Some(request.requestorUserId))
          id = saved.dynamicMessageDocId.getOrElse("")
          _ <- markApproved(DYNAMIC_MESSAGE_DOC, id)
        } yield id
      case DELETE   => p.deleteById(bankId, request.targetId).map(_ => request.targetId)
      case ACTIVATE => markApproved(DYNAMIC_MESSAGE_DOC, request.targetId).map(_ => request.targetId)
      case other    => Failure(s"${ErrorMessages.InvalidJsonFormat} operation $other is not a request operation")
    }
  }

  private def applyConnectorMethod(request: DynamicChangeRequestTrait, operation: DynamicChangeRequestOperation): Box[String] = {
    val p = ConnectorMethodProvider.provider.vend
    operation match {
      case CREATE =>
        for {
          body <- parseAs[JsonConnectorMethod](request.proposedPayload)
          _ <- boolBox(p.getByMethodNameWithoutCache(body.methodName).isEmpty, s"${ErrorMessages.ConnectorMethodAlreadyExists} ${body.methodName}")
          _ <- compileBox("connector method") {
            val fn = InternalConnector.createFunction(body.methodName, body.decodedMethodBody, body.programmingLang)
            fn.foreach(Validation.validateDependency(_))
            fn
          }
          created <- p.create(body, Some(request.requestorUserId))
          id = created.connectorMethodId.getOrElse("")
          _ <- markApproved(CONNECTOR_METHOD, id)
        } yield id
      case UPDATE =>
        for {
          body <- parseAs[JsonConnectorMethodMethodBody](request.proposedPayload)
          existing <- p.getById(request.targetId) ?~! s"${ErrorMessages.ConnectorMethodNotFound} ${request.targetId}"
          _ <- compileBox("connector method") {
            val fn = InternalConnector.createFunction(existing.methodName, body.decodedMethodBody, body.programmingLang)
            fn.foreach(Validation.validateDependency(_))
            fn
          }
          _ <- p.update(request.targetId, body.methodBody, body.programmingLang, Some(request.requestorUserId))
          _ <- markApproved(CONNECTOR_METHOD, request.targetId)
        } yield request.targetId
      case DELETE   => p.deleteById(request.targetId).map(_ => request.targetId)
      case ACTIVATE => markApproved(CONNECTOR_METHOD, request.targetId).map(_ => request.targetId)
      case other    => Failure(s"${ErrorMessages.InvalidJsonFormat} operation $other is not a request operation")
    }
  }

  private def applyAbacRule(request: DynamicChangeRequestTrait, operation: DynamicChangeRequestOperation): Box[String] = operation match {
    case CREATE =>
      for {
        body <- parseAs[CreateAbacRuleJsonV600](request.proposedPayload)
        _ <- AbacRuleEngine.validateRuleCode(body.rule_code)
        rule <- MappedAbacRuleProvider.createAbacRule(body.rule_name, body.rule_code, body.description, body.policy, body.is_active, request.requestorUserId)
        _ <- markApproved(ABAC_RULE, rule.abacRuleId)
      } yield rule.abacRuleId
    case UPDATE =>
      for {
        body <- parseAs[UpdateAbacRuleJsonV600](request.proposedPayload)
        _ <- AbacRuleEngine.validateRuleCode(body.rule_code)
        _ <- MappedAbacRuleProvider.updateAbacRule(request.targetId, body.rule_name, body.rule_code, body.description, body.policy, body.is_active, request.requestorUserId)
        _ <- markApproved(ABAC_RULE, request.targetId)
      } yield request.targetId
    case DELETE   => MappedAbacRuleProvider.deleteAbacRule(request.targetId).map(_ => request.targetId)
    case ACTIVATE => markApproved(ABAC_RULE, request.targetId).map(_ => request.targetId)
    case other    => Failure(s"${ErrorMessages.InvalidJsonFormat} operation $other is not a request operation")
  }

  /** Record that the row's current body is the approved one, and make it active. */
  private def markApproved(targetType: DynamicChangeRequestTargetType, targetId: String): Box[Unit] = tryo {
    targetType match {
      case DYNAMIC_RESOURCE_DOC =>
        DynamicResourceDoc.find(By(DynamicResourceDoc.DynamicResourceDocId, targetId)).map { r =>
          val h = bodyHashOf(r.MethodBodyHash.get, r.MethodBody.get)
          r.MethodBodyHash(h).ApprovedHash(h).IsActive(true).save; ()
        }
      case DYNAMIC_MESSAGE_DOC =>
        DynamicMessageDoc.find(By(DynamicMessageDoc.DynamicMessageDocId, targetId)).map { r =>
          val h = bodyHashOf(r.MethodBodyHash.get, r.MethodBody.get)
          r.MethodBodyHash(h).ApprovedHash(h).IsActive(true).save; ()
        }
      case CONNECTOR_METHOD =>
        ConnectorMethod.find(By(ConnectorMethod.ConnectorMethodId, targetId)).map { r =>
          val h = bodyHashOf(r.MethodBodyHash.get, r.MethodBody.get)
          r.MethodBodyHash(h).ApprovedHash(h).IsActive(true).save; ()
        }
      case ABAC_RULE =>
        AbacRule.find(By(AbacRule.AbacRuleId, targetId)).map { r =>
          r.ApprovedHash(sha256Hex(Option(r.RuleCode.get).getOrElse(""))).IsActive(true).save; ()
        }
      case _ => Empty
    }
  }.flatMap(b => b ?~! s"${ErrorMessages.DynamicChangeRequestTargetNotFound} $targetType $targetId")

  private def setActive(targetType: DynamicChangeRequestTargetType, targetId: String, active: Boolean): Box[Unit] = {
    val done: Box[Unit] = targetType match {
      case DYNAMIC_RESOURCE_DOC => DynamicResourceDoc.find(By(DynamicResourceDoc.DynamicResourceDocId, targetId)).map(r => { r.IsActive(active).save; () })
      case DYNAMIC_MESSAGE_DOC  => DynamicMessageDoc.find(By(DynamicMessageDoc.DynamicMessageDocId, targetId)).map(r => { r.IsActive(active).save; () })
      case CONNECTOR_METHOD     => ConnectorMethod.find(By(ConnectorMethod.ConnectorMethodId, targetId)).map(r => { r.IsActive(active).save; () })
      case ABAC_RULE            => AbacRule.find(By(AbacRule.AbacRuleId, targetId)).map(r => { r.IsActive(active).save; () })
      case _                    => Empty
    }
    done.foreach(_ => invalidateCaches(targetType, targetId))
    done ?~! s"${ErrorMessages.DynamicChangeRequestTargetNotFound} $targetType $targetId"
  }

  // ─── boot ──────────────────────────────────────────────────────────────────

  /** MigrationScriptLog entry that records the one-off seeding below; the seed never runs twice on a database. */
  val seedMigrationName = "seedDynamicCodeApprovedHashes"

  /**
   * Run ONCE per database, the first time the instance boots with dynamic_code_requires_approval=true.
   * Rows that predate the feature have no ApprovedHash and would stop executing, so their current body
   * is recorded as approved and the run is logged in MigrationScriptLog under `seedMigrationName`.
   * It is deliberately not repeated at later boots: after the seed, the only way a row gains an
   * ApprovedHash is a checker's approval, so a row written straight into the database (or created while
   * the feature was switched off) stays unexecutable until a second person ACTIVATEs it through a change
   * request. A pending UPDATE never modifies the live row, so it is unaffected by the seed.
   */
  def seedApprovedHashesIfEnabled(): Unit = if (enabled) {
    val logProvider = code.migration.MigrationScriptLogProvider.migrationScriptLogProvider.vend
    if (logProvider.isExecuted(seedMigrationName)) {
      logger.info(s"dynamic_code_requires_approval: ApprovedHash seeding ($seedMigrationName) already ran on this database; rows without an approved hash will not execute until approved")
    } else {
      val start = System.currentTimeMillis()
      def seed[T](name: String, rows: List[T])(hashOf: T => String, write: (T, String) => Unit): String = {
        rows.foreach(r => write(r, hashOf(r)))
        s"$name: ${rows.size}"
      }
      tryo {
        List(
          seed("DynamicResourceDoc", DynamicResourceDoc.findAll().filter(r => blank(r.ApprovedHash.get)))(
            r => bodyHashOf(r.MethodBodyHash.get, r.MethodBody.get), (r, h) => { r.MethodBodyHash(h).ApprovedHash(h).save; () }),
          seed("DynamicMessageDoc", DynamicMessageDoc.findAll().filter(r => blank(r.ApprovedHash.get)))(
            r => bodyHashOf(r.MethodBodyHash.get, r.MethodBody.get), (r, h) => { r.MethodBodyHash(h).ApprovedHash(h).save; () }),
          seed("ConnectorMethod", ConnectorMethod.findAll().filter(r => blank(r.ApprovedHash.get)))(
            r => bodyHashOf(r.MethodBodyHash.get, r.MethodBody.get), (r, h) => { r.MethodBodyHash(h).ApprovedHash(h).save; () }),
          seed("AbacRule", AbacRule.findAll().filter(r => blank(r.ApprovedHash.get)))(
            r => sha256Hex(Option(r.RuleCode.get).getOrElse("")), (r, h) => { r.ApprovedHash(h).save; () })
        ).mkString(", ")
      } match {
        case Full(summary) =>
          val comment = s"Seeded ApprovedHash from the current body on pre-existing rows ($summary); their current code is treated as approved"
          logger.warn(s"dynamic_code_requires_approval: $comment")
          logProvider.saveLog(seedMigrationName, code.api.util.APIUtil.gitCommit, true, start, System.currentTimeMillis(), comment)
        case f: Failure =>
          logger.error(s"dynamic_code_requires_approval: seeding ApprovedHash failed, will retry at next boot: ${f.messageChain}")
          logProvider.saveLog(seedMigrationName, code.api.util.APIUtil.gitCommit, false, start, System.currentTimeMillis(), f.messageChain)
        case _ => ()
      }
    }
  }
}
