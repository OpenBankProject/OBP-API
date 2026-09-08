package code.dynamicchangerequest

import java.util.Date
import code.api.util.ErrorMessages
import code.util.MappedUUID
import com.openbankproject.commons.model.enums.DynamicChangeRequestStatus
import net.liftweb.common.{Box, Failure, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

object MappedDynamicChangeRequestProvider extends DynamicChangeRequestProvider {

  override def create(
    targetType: String,
    targetId: String,
    operation: String,
    requestVerb: String,
    requestPath: String,
    proposedPayload: String,
    payloadHash: String,
    currentPayloadHash: String,
    requestorUserId: String,
    businessJustification: String,
    expiresAt: Option[Date]
  ): Box[DynamicChangeRequestTrait] = tryo {
    DynamicChangeRequest.create
      .TargetType(targetType)
      .TargetId(targetId)
      .Operation(operation)
      .RequestVerb(requestVerb)
      .RequestPath(requestPath)
      .ProposedPayload(proposedPayload)
      .PayloadHash(payloadHash)
      .CurrentPayloadHash(currentPayloadHash)
      .Status(DynamicChangeRequestStatus.INITIATED.toString)
      .RequestorUserId(requestorUserId)
      .BusinessJustification(businessJustification)
      .CheckerUserId("")
      .CheckerComment("")
      .ExpiresAt(expiresAt.orNull)
      .saveMe()
  }

  override def getById(dynamicChangeRequestId: String): Box[DynamicChangeRequestTrait] =
    DynamicChangeRequest.find(By(DynamicChangeRequest.DynamicChangeRequestId, dynamicChangeRequestId))

  override def getAll(
    status: Option[String],
    targetType: Option[String],
    targetId: Option[String],
    requestorUserId: Option[String]
  ): List[DynamicChangeRequestTrait] = {
    val filters: List[QueryParam[DynamicChangeRequest]] =
      status.map(By(DynamicChangeRequest.Status, _)).toList :::
      targetType.map(By(DynamicChangeRequest.TargetType, _)).toList :::
      targetId.map(By(DynamicChangeRequest.TargetId, _)).toList :::
      requestorUserId.map(By(DynamicChangeRequest.RequestorUserId, _)).toList :::
      List(OrderBy(DynamicChangeRequest.id, Descending))
    DynamicChangeRequest.findAll(filters: _*)
  }

  override def getByRequestorUserId(requestorUserId: String): List[DynamicChangeRequestTrait] =
    DynamicChangeRequest.findAll(
      By(DynamicChangeRequest.RequestorUserId, requestorUserId),
      OrderBy(DynamicChangeRequest.id, Descending))

  override def updateStatus(
    dynamicChangeRequestId: String,
    status: String,
    checkerUserId: String,
    checkerComment: String
  ): Box[DynamicChangeRequestTrait] = {
    getById(dynamicChangeRequestId).flatMap { request =>
      // Atomic guarded transition: a request is actioned once, from INITIATED. The loser of a
      // concurrent approve/reject gets 0 rows -> Failure, instead of silently overwriting the decision.
      val rows = code.bankconnectors.DoobieBusinessStatusQueries.conditionalDynamicChangeRequestStatus(
        request.asInstanceOf[DynamicChangeRequest].id.get,
        DynamicChangeRequestStatus.INITIATED.toString, status, checkerUserId, checkerComment)
      if (rows == 1) getById(dynamicChangeRequestId)
      else Failure(ErrorMessages.DynamicChangeRequestNotInitiated)
    }
  }
}

class DynamicChangeRequest extends DynamicChangeRequestTrait with LongKeyedMapper[DynamicChangeRequest] with IdPK with CreatedUpdated {

  def getSingleton = DynamicChangeRequest

  object DynamicChangeRequestId extends MappedUUID(this)
  object TargetType extends MappedString(this, 64)
  object TargetId extends MappedString(this, 255)
  object Operation extends MappedString(this, 32)
  object RequestVerb extends MappedString(this, 16)
  object RequestPath extends MappedString(this, 1024)
  object ProposedPayload extends MappedText(this)
  object PayloadHash extends MappedString(this, 64)
  object CurrentPayloadHash extends MappedString(this, 64)
  object Status extends MappedString(this, 32)
  object RequestorUserId extends MappedString(this, 255)
  object BusinessJustification extends MappedText(this)
  object CheckerUserId extends MappedString(this, 255)
  object CheckerComment extends MappedText(this)
  object ActionedAt extends MappedDateTime(this)
  object ExpiresAt extends MappedDateTime(this)

  override def dynamicChangeRequestId: String = DynamicChangeRequestId.get
  override def targetType: String = TargetType.get
  override def targetId: String = Option(TargetId.get).getOrElse("")
  override def operation: String = Operation.get
  override def requestVerb: String = Option(RequestVerb.get).getOrElse("")
  override def requestPath: String = Option(RequestPath.get).getOrElse("")
  override def proposedPayload: String = Option(ProposedPayload.get).getOrElse("")
  override def payloadHash: String = Option(PayloadHash.get).getOrElse("")
  override def currentPayloadHash: String = Option(CurrentPayloadHash.get).getOrElse("")
  override def status: String = Status.get
  override def requestorUserId: String = RequestorUserId.get
  override def businessJustification: String = Option(BusinessJustification.get).getOrElse("")
  override def checkerUserId: String = Option(CheckerUserId.get).getOrElse("")
  override def checkerComment: String = Option(CheckerComment.get).getOrElse("")
  override def created: Date = createdAt.get
  override def updated: Date = updatedAt.get
  override def actionedAt: Option[Date] = Option(ActionedAt.get)
  override def expiresAt: Option[Date] = Option(ExpiresAt.get)
}

object DynamicChangeRequest extends DynamicChangeRequest with LongKeyedMetaMapper[DynamicChangeRequest] {
  override def dbTableName = "DynamicChangeRequest"
  override def dbIndexes = UniqueIndex(DynamicChangeRequestId) :: Index(TargetType, TargetId) :: Index(RequestorUserId) :: Index(Status) :: super.dbIndexes
}
