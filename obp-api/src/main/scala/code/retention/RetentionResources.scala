/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */
package code.retention

import java.util.Date

import code.CustomerDependants.MappedCustomerDependant
import code.DynamicData.{DynamicData, DynamicDataAccess}
import code.chat.{ChatMessage, ChatRoom, Participant, Reaction}
import code.consent.{ConsentItem, MappedConsent}
import code.context.MappedConsentAuthContext
import code.customer.MappedCustomer
import code.customeraccountlinks.CustomerAccountLink
import code.customeraddress.MappedCustomerAddress
import code.customerattribute.MappedCustomerAttribute
import code.files.{File, FileAccess, FileAttachment, FileContent}
import code.retention.Retention._
import code.taxresidence.MappedTaxResidence
import code.usercustomerlinks.MappedUserCustomerLink
import com.openbankproject.commons.util.JsonAliases
import net.liftweb.mapper._
import org.json4s._
import org.json4s.JsonDSL._

import scala.util.Try

/**
 * One resource a policy has found due: its identity, the date its retention period is counted from,
 * and how to dispose of it.
 *
 * `conditionView` is what the policy's condition is checked against, and is cheap to build.
 * `jsonCopy` is the full copy written to the retention archive, built only when the resource is
 * archived. `dispose` carries out an action on the live tables and returns false when the resource
 * was left in place because something it would take with it is on hold.
 */
case class RetentionCandidate(resourceType: String,
                              resourceId: String,
                              bankId: String,
                              entityName: Option[String],
                              ownerUserId: Option[String],
                              triggerDate: Date,
                              conditionView: JValue)(val jsonCopy: () => JObject,
                                                     val dispose: (String, Disposal) => Boolean)

/** How one resource type is found and disposed of. */
trait RetentionResourceHandler {
  def resourceType: String

  /**
   * Hand each resource of the policy's space whose trigger date is on or before `cutoff`, and which
   * matches the policy's condition, to `visit`, oldest row first, until `visit` returns false.
   * Resources may be disposed of from inside `visit`.
   */
  def scan(policy: RetentionPolicy, cutoff: Date)(visit: RetentionCandidate => Boolean): Unit
}

object RetentionResources {

  val handlers: Map[String, RetentionResourceHandler] = List(
    DynamicEntityRecordRetention, FileRetention, ChatMessageRetention, ChatRoomRetention, CustomerRetention, ConsentRetention
  ).map(h => h.resourceType -> h).toMap

  private val PageSize = 500

  /**
   * Read a table in pages ordered by primary key, so that rows deleted while the scan runs never make
   * it skip or repeat one. `visit` returns false to stop.
   */
  private[retention] def scanById[T <: LongKeyedMapper[T]](meta: LongKeyedMetaMapper[T], idField: MappedField[Long, T],
                                                          params: List[QueryParam[T]])(visit: T => Boolean): Unit = {
    var lastId = -1L
    var go = true
    while (go) {
      val page = meta.findAll((params ::: List(By_>(idField, lastId), OrderBy(idField, Ascending), MaxRows[T](PageSize))): _*)
      val rows = page.iterator
      while (go && rows.hasNext) {
        val row = rows.next()
        lastId = row.primaryKeyField.get
        go = visit(row)
      }
      if (page.size < PageSize) go = false
    }
  }

  private[retention] def parse(json: String): JValue =
    Try(JsonAliases.parse(Option(json).getOrElse(""))).getOrElse(JNothing)

  private[retention] def nonEmpty(values: String*): Option[String] = values.find(v => v != null && v.nonEmpty)
}

import code.retention.RetentionResources._

object DynamicEntityRecordRetention extends RetentionResourceHandler {
  val resourceType: String = ResourceDynamicEntityRecord

  def scan(policy: RetentionPolicy, cutoff: Date)(visit: RetentionCandidate => Boolean): Unit = {
    val space = policy.bankId
    val entityName = policy.entityName.getOrElse("")
    val dateFilter: List[QueryParam[DynamicData]] = policy.dateBasis match {
      case BasisCreated => List(By_<=(DynamicData.createdAt, cutoff))
      case BasisUpdated => List(By_<=(DynamicData.updatedAt, cutoff))
      case _ => Nil
    }
    scanById(DynamicData, DynamicData.id,
      By(DynamicData.BankId, space) :: By(DynamicData.DynamicEntityName, entityName) :: dateFilter) { row =>
      val data = parse(row.DataJson.get)
      val trigger: Option[Date] = policy.dateBasis match {
        case BasisCreated => Option(row.createdAt.get)
        case BasisUpdated => Option(row.updatedAt.get)
        case basis => dateIn(data, basis.stripPrefix(BasisFieldPrefix).trim)
      }
      trigger.filter(t => !t.after(cutoff) && conditionMatches(policy.condition, data)) match {
        case Some(t) => visit(candidate(policy, row, data, t))
        case None => true
      }
    }
  }

  private def candidate(policy: RetentionPolicy, row: DynamicData, data: JValue, trigger: Date): RetentionCandidate = {
    val space = policy.bankId
    val entityName = row.DynamicEntityName.get
    val recordId = row.DynamicDataId.get
    val owner =
      if (row.IsPersonalEntity.get) nonEmpty(row.UserId.get)
      else nonEmpty(row.CreatedByOnBehalfOfUserId.get, row.CreatedByUserId.get, row.UserId.get)
    lazy val self: RetentionCandidate = RetentionCandidate(resourceType, recordId, space, Some(entityName), owner, trigger, data)(
      jsonCopy = () =>
        ("dynamic_data_id" -> recordId) ~
          ("entity_name" -> entityName) ~
          ("bank_id" -> space) ~
          ("user_id" -> text(row.UserId.get)) ~
          ("is_personal_entity" -> row.IsPersonalEntity.get) ~
          ("created_at" -> iso(row.createdAt.get)) ~
          ("updated_at" -> iso(row.updatedAt.get)) ~
          ("created_by_user_id" -> text(row.CreatedByUserId.get)) ~
          ("created_by_on_behalf_of_user_id" -> text(row.CreatedByOnBehalfOfUserId.get)) ~
          ("updated_by_user_id" -> text(row.UpdatedByUserId.get)) ~
          ("updated_by_on_behalf_of_user_id" -> text(row.UpdatedByOnBehalfOfUserId.get)) ~
          ("file_attachments" -> FileRetention.attachmentsJson(space, entityName, recordId)) ~
          ("data" -> data),
      dispose = (action, disposal) => {
        val archiveId = if (action == ActionArchive) Some(disposal.archive(self, None, None)) else None
        FileRetention.disposeAttached(space, entityName, recordId, policy.cascade, action, disposal, resourceType)
        DynamicDataAccess.bulkDelete_!!(By(DynamicDataAccess.BankId, space), By(DynamicDataAccess.EntityName, entityName),
          By(DynamicDataAccess.DynamicDataId, recordId))
        row.delete_!
        // Keep the SQL projection of the entity in step, as the record endpoints do. No webhook is sent:
        // a retention run is not a write through the endpoints.
        code.api.dynamic.entity.projection.ProjectionDualWrite.onDelete(
          Option(space).filterNot(_ == SystemSpace), entityName, recordId)
        disposal.register(self, action, archiveId, None)
        true
      }
    )
    self
  }
}

object FileRetention extends RetentionResourceHandler {
  val resourceType: String = ResourceFile

  def scan(policy: RetentionPolicy, cutoff: Date)(visit: RetentionCandidate => Boolean): Unit =
    scanById(File, File.id, List(ByList(File.BankId, storedBankIds(policy.bankId)), By_<=(File.CreatedAt, cutoff))) { file =>
      val c = candidate(file, None)
      if (conditionMatches(policy.condition, c.conditionView)) visit(c) else true
    }

  def attachmentsJson(bankId: String, recordType: String, recordId: String): JValue =
    JArray(FileAttachment.findAll(ByList(FileAttachment.BankId, storedBankIds(bankId)), By(FileAttachment.RecordType, recordType),
      By(FileAttachment.RecordId, recordId)).map(a =>
      ("file_attachment_id" -> a.fileAttachmentId) ~ ("file_id" -> a.fileId): JValue))

  private def summary(file: File): JObject =
    ("file_id" -> file.fileId) ~
      ("bank_id" -> file.bankId) ~
      ("file_name" -> text(file.FileName.get)) ~
      ("media_type" -> text(file.MediaType.get)) ~
      ("size_in_bytes" -> file.SizeInBytes.get) ~
      ("sha256" -> text(file.Sha256.get)) ~
      ("user_id" -> text(file.UserId.get)) ~
      ("on_behalf_of_user_id" -> text(file.OnBehalfOfUserId.get)) ~
      ("created_at" -> iso(file.CreatedAt.get))

  private def candidate(file: File, parent: Option[(String, String)]): RetentionCandidate =
    RetentionCandidate(resourceType, file.fileId, file.bankId, None, nonEmpty(file.ownerUserId), file.CreatedAt.get, summary(file))(
      jsonCopy = () => summary(file) ~
        ("attachments" -> JArray(FileAttachment.findAll(By(FileAttachment.FileId, file.fileId)).map(a =>
          ("file_attachment_id" -> a.fileAttachmentId) ~ ("record_type" -> a.recordType) ~ ("record_id" -> a.recordId): JValue))) ~
        ("access" -> JArray(FileAccess.findAll(By(FileAccess.FileId, file.fileId)).map(a =>
          ("grantee_user_id" -> a.granteeUserId) ~ ("granted_by_user_id" -> a.grantedByUserId): JValue))),
      dispose = (action, disposal) => {
        disposeFile(file, action, disposal, parent)
        true
      }
    )

  /** Archive or delete one file with its bytes, its attachments and its access list. */
  private def disposeFile(file: File, action: String, disposal: Disposal, parent: Option[(String, String)]): Unit = {
    val self = candidate(file, parent)
    val fileAction = if (action == ActionArchive) ActionArchive else ActionDelete
    val archiveId =
      if (fileAction == ActionArchive) {
        val bytes = FileContent.find(By(FileContent.FileId, file.fileId)).toOption.flatMap(row => Option(row.Content.get))
        Some(disposal.archive(self, bytes, parent))
      } else None
    FileAccess.bulkDelete_!!(By(FileAccess.FileId, file.fileId))
    FileAttachment.bulkDelete_!!(By(FileAttachment.FileId, file.fileId))
    FileContent.bulkDelete_!!(By(FileContent.FileId, file.fileId))
    file.delete_!
    disposal.register(self, fileAction, archiveId, parent)
  }

  /**
   * The files attached to a record that is being disposed of. The record's attachments always go.
   * With `cascade`, each file goes too, with the record's action (archive stays archive; delete and
   * anonymise delete the file) — unless it is still attached to another record, which keeps it, or
   * it is on hold, which keeps it and is counted as held.
   */
  def disposeAttached(bankId: String, recordType: String, recordId: String, cascade: Boolean, action: String,
                      disposal: Disposal, parentType: String): Unit = {
    val attachments = FileAttachment.findAll(ByList(FileAttachment.BankId, storedBankIds(bankId)),
      By(FileAttachment.RecordType, recordType), By(FileAttachment.RecordId, recordId))
    attachments.foreach { attachment =>
      attachment.delete_!
      if (cascade && FileAttachment.count(By(FileAttachment.FileId, attachment.fileId)) == 0) {
        File.find(By(File.FileId, attachment.fileId)).foreach { file =>
          if (disposal.isHeld(resourceType, file.fileId, None)) disposal.countHeld()
          else disposeFile(file, action, disposal, Some(parentType -> recordId))
        }
      }
    }
  }
}

object ChatMessageRetention extends RetentionResourceHandler {
  val resourceType: String = ResourceChatMessage

  def scan(policy: RetentionPolicy, cutoff: Date)(visit: RetentionCandidate => Boolean): Unit = {
    val rooms = ChatRoom.findAll(ByList(ChatRoom.BankId, storedBankIds(policy.bankId)))
    val dateFilter: QueryParam[ChatMessage] =
      if (policy.dateBasis == BasisUpdated) By_<=(ChatMessage.updatedAt, cutoff) else By_<=(ChatMessage.createdAt, cutoff)
    var go = true
    rooms.map(_.chatRoomId).grouped(500).foreach { roomIds =>
      if (go) scanById(ChatMessage, ChatMessage.id, List(ByList(ChatMessage.ChatRoomId, roomIds), dateFilter)) { message =>
        val trigger = if (policy.dateBasis == BasisUpdated) message.updatedAt.get else message.createdAt.get
        val c = candidate(policy.bankId, message, trigger)
        go = if (conditionMatches(policy.condition, c.conditionView)) visit(c) else true
        go
      }
    }
  }

  private[retention] def summary(message: ChatMessage): JObject =
    ("chat_message_id" -> message.chatMessageId) ~
      ("chat_room_id" -> message.chatRoomId) ~
      ("sender_user_id" -> text(message.SenderUserId.get)) ~
      ("sender_consumer_id" -> text(message.SenderConsumerId.get)) ~
      ("message_type" -> text(message.MessageType.get)) ~
      ("reply_to_message_id" -> text(message.ReplyToMessageId.get)) ~
      ("thread_id" -> text(message.ThreadId.get)) ~
      ("is_deleted" -> message.IsDeleted.get) ~
      ("created_at" -> iso(message.createdAt.get)) ~
      ("updated_at" -> iso(message.updatedAt.get))

  private[retention] def fullJson(message: ChatMessage): JObject =
    summary(message) ~
      ("content" -> text(message.Content.get)) ~
      ("mentioned_user_ids" -> text(message.MentionedUserIds.get)) ~
      ("reactions" -> JArray(Reaction.findAll(By(Reaction.ChatMessageId, message.chatMessageId)).map(r =>
        ("reaction_id" -> r.reactionId) ~ ("user_id" -> r.userId) ~ ("emoji" -> r.emoji) ~ ("created_at" -> iso(r.createdAt.get)): JValue)))

  private[retention] def candidate(bankId: String, message: ChatMessage, trigger: Date): RetentionCandidate = {
    lazy val self: RetentionCandidate = RetentionCandidate(resourceType, message.chatMessageId, bankId, None,
      nonEmpty(message.SenderUserId.get), trigger, summary(message))(
      jsonCopy = () => fullJson(message),
      dispose = (action, disposal) => {
        val archiveId = if (action == ActionArchive) Some(disposal.archive(self, None, None)) else None
        Reaction.bulkDelete_!!(By(Reaction.ChatMessageId, message.chatMessageId))
        message.delete_!
        // The room shows a preview of its newest message; it must not keep the text of one disposed of.
        ChatRoom.find(By(ChatRoom.ChatRoomId, message.chatRoomId)).foreach { room =>
          val lastAt = Option(room.LastMessageAt.get)
          if (lastAt.forall(at => Option(message.createdAt.get).forall(created => !at.after(created))))
            room.LastMessagePreview("").LastMessageSenderUsername("").saveMe()
        }
        disposal.register(self, action, archiveId, None)
        true
      }
    )
    self
  }
}

object ChatRoomRetention extends RetentionResourceHandler {
  val resourceType: String = ResourceChatRoom

  def scan(policy: RetentionPolicy, cutoff: Date)(visit: RetentionCandidate => Boolean): Unit = {
    val dateFilter: List[QueryParam[ChatRoom]] = policy.dateBasis match {
      case BasisCreated => List(By_<=(ChatRoom.createdAt, cutoff))
      case BasisUpdated => List(By_<=(ChatRoom.updatedAt, cutoff))
      case _ => Nil
    }
    scanById(ChatRoom, ChatRoom.id, ByList(ChatRoom.BankId, storedBankIds(policy.bankId)) :: dateFilter) { room =>
      val trigger: Option[Date] = policy.dateBasis match {
        case BasisCreated => Option(room.createdAt.get)
        case BasisUpdated => Option(room.updatedAt.get)
        case _ => Option(room.LastMessageAt.get).orElse(Option(room.createdAt.get))
      }
      val c = trigger.filterNot(_.after(cutoff)).map(t => candidate(policy.bankId, room, t))
      c.filter(c => conditionMatches(policy.condition, c.conditionView)).map(visit).getOrElse(true)
    }
  }

  private def summary(room: ChatRoom): JObject =
    ("chat_room_id" -> room.chatRoomId) ~
      ("bank_id" -> text(room.BankId.get)) ~
      ("name" -> text(room.Name.get)) ~
      ("created_by_user_id" -> text(room.CreatedByUserId.get)) ~
      ("is_open_room" -> room.IsOpenRoom.get) ~
      ("is_archived" -> room.IsArchived.get) ~
      ("last_message_at" -> iso(room.LastMessageAt.get)) ~
      ("created_at" -> iso(room.createdAt.get)) ~
      ("updated_at" -> iso(room.updatedAt.get))

  private def candidate(bankId: String, room: ChatRoom, trigger: Date): RetentionCandidate = {
    lazy val self: RetentionCandidate = RetentionCandidate(resourceType, room.chatRoomId, bankId, None, nonEmpty(room.CreatedByUserId.get), trigger, summary(room))(
      // The joining key is left out: it is how people get into the room, not a record of it.
      jsonCopy = () => summary(room) ~
        ("description" -> text(room.Description.get)) ~
        ("participants" -> JArray(Participant.findAll(By(Participant.ChatRoomId, room.chatRoomId)).map(p =>
          ("participant_id" -> p.participantId) ~ ("user_id" -> text(p.UserId.get)) ~ ("consumer_id" -> text(p.ConsumerId.get)) ~
            ("permissions" -> text(p.Permissions.get)) ~ ("joined_at" -> iso(p.JoinedAt.get)): JValue))) ~
        ("messages" -> JArray(ChatMessage.findAll(By(ChatMessage.ChatRoomId, room.chatRoomId), OrderBy(ChatMessage.id, Ascending))
          .map(m => ChatMessageRetention.fullJson(m): JValue))),
      dispose = (action, disposal) => {
        val messages = ChatMessage.findAll(By(ChatMessage.ChatRoomId, room.chatRoomId))
        // A room goes with all its messages, so a room holding a message on hold stays whole.
        if (messages.exists(m => disposal.isHeld(ResourceChatMessage, m.chatMessageId, None))) false
        else {
          val archiveId = if (action == ActionArchive) Some(disposal.archive(self, None, None)) else None
          messages.foreach { m =>
            Reaction.bulkDelete_!!(By(Reaction.ChatMessageId, m.chatMessageId))
            m.delete_!
            disposal.register(ChatMessageRetention.candidate(bankId, m, m.createdAt.get), action, archiveId, Some(resourceType -> room.chatRoomId))
          }
          Participant.bulkDelete_!!(By(Participant.ChatRoomId, room.chatRoomId))
          room.delete_!
          disposal.register(self, action, archiveId, None)
          true
        }
      }
    )
    self
  }
}

object CustomerRetention extends RetentionResourceHandler {
  val resourceType: String = ResourceCustomer
  /** Files about a Customer are attached with this record type. */
  val FileRecordType = "customer"

  def scan(policy: RetentionPolicy, cutoff: Date)(visit: RetentionCandidate => Boolean): Unit = {
    val dateFilter: QueryParam[MappedCustomer] =
      if (policy.dateBasis == BasisUpdated) By_<=(MappedCustomer.updatedAt, cutoff) else By_<=(MappedCustomer.createdAt, cutoff)
    scanById(MappedCustomer, MappedCustomer.id, List(By(MappedCustomer.mBank, policy.bankId), dateFilter)) { customer =>
      // A Customer already anonymised is not anonymised again on every run.
      val alreadyDone = policy.action == ActionAnonymise && customer.mLegalName.get == AnonymisedLegalName
      val trigger = Option(if (policy.dateBasis == BasisUpdated) customer.updatedAt.get else customer.createdAt.get)
      val c = trigger.filterNot(_ => alreadyDone).map(t => candidate(policy, customer, t))
      c.filter(c => conditionMatches(policy.condition, c.conditionView)).map(visit).getOrElse(true)
    }
  }

  private def summary(customer: MappedCustomer): JObject =
    ("customer_id" -> customer.customerId) ~
      ("bank_id" -> customer.bankId) ~
      ("customer_number" -> text(customer.mNumber.get)) ~
      ("customer_type" -> text(customer.mCustomerType.get)) ~
      ("branch_id" -> text(customer.mBranchId.get)) ~
      ("kyc_status" -> customer.mKycStatus.get) ~
      ("created_at" -> iso(customer.createdAt.get)) ~
      ("updated_at" -> iso(customer.updatedAt.get))

  private def fullJson(customer: MappedCustomer): JObject = {
    val pk = customer.id.get
    summary(customer) ~
      ("legal_name" -> text(customer.mLegalName.get)) ~
      ("mobile_phone_number" -> text(customer.mMobileNumber.get)) ~
      ("email" -> text(customer.mEmail.get)) ~
      ("face_image_url" -> text(customer.mFaceImageUrl.get)) ~
      ("date_of_birth" -> iso(customer.mDateOfBirth.get)) ~
      ("relationship_status" -> text(customer.mRelationshipStatus.get)) ~
      ("dependants" -> customer.mDependents.get) ~
      ("highest_education_attained" -> text(customer.mHighestEducationAttained.get)) ~
      ("employment_status" -> text(customer.mEmploymentStatus.get)) ~
      ("credit_rating" -> (("rating" -> text(customer.mCreditRating.get)) ~ ("source" -> text(customer.mCreditSource.get)))) ~
      ("credit_limit" -> (("currency" -> text(customer.mCreditLimitCurrency.get)) ~ ("amount" -> text(customer.mCreditLimitAmount.get)))) ~
      ("last_ok_date" -> iso(customer.mLastOkDate.get)) ~
      ("title" -> text(customer.mTitle.get)) ~
      ("name_suffix" -> text(customer.mNameSuffix.get)) ~
      ("parent_customer_id" -> text(customer.mParentCustomerId.get)) ~
      ("customer_attributes" -> JArray(MappedCustomerAttribute.findAll(By(MappedCustomerAttribute.mCustomerId, customer.customerId)).map(a =>
        ("customer_attribute_id" -> a.customerAttributeId) ~ ("name" -> text(a.mName.get)) ~ ("type" -> text(a.mType.get)) ~
          ("value" -> text(a.mValue.get)): JValue))) ~
      ("addresses" -> JArray(MappedCustomerAddress.findAll(By(MappedCustomerAddress.mCustomerId, pk)).map(a =>
        ("customer_address_id" -> a.customerAddressId) ~ ("line_1" -> text(a.line1)) ~ ("line_2" -> text(a.line2)) ~
          ("line_3" -> text(a.line3)) ~ ("city" -> text(a.city)) ~ ("county" -> text(a.county)) ~ ("state" -> text(a.state)) ~
          ("postcode" -> text(a.postcode)) ~ ("country_code" -> text(a.countryCode)) ~ ("tags" -> text(a.tags)) ~
          ("status" -> text(a.status)): JValue))) ~
      ("dependant_dates_of_birth" -> JArray(MappedCustomerDependant.findAll(By(MappedCustomerDependant.mCustomer, pk))
        .map(d => iso(d.mDateOfBirth.get)))) ~
      ("tax_residences" -> JArray(MappedTaxResidence.findAll(By(MappedTaxResidence.mCustomerId, pk)).map(t =>
        ("tax_residence_id" -> t.taxResidenceId) ~ ("domain" -> text(t.domain)) ~ ("tax_number" -> text(t.taxNumber)): JValue))) ~
      ("user_customer_links" -> JArray(userLinks(customer).map(l =>
        ("user_customer_link_id" -> l.userCustomerLinkId) ~ ("user_id" -> l.userId) ~ ("is_active" -> l.isActive) ~
          ("date_inserted" -> iso(l.dateInserted)): JValue))) ~
      ("customer_account_links" -> JArray(CustomerAccountLink.findAll(By(CustomerAccountLink.CustomerId, customer.customerId)).map(l =>
        ("customer_account_link_id" -> l.customerAccountLinkId) ~ ("bank_id" -> text(l.BankId.get)) ~
          ("account_id" -> text(l.AccountId.get)) ~ ("relationship_type" -> text(l.RelationshipType.get)): JValue))) ~
      ("file_attachments" -> FileRetention.attachmentsJson(customer.bankId, FileRecordType, customer.customerId))
  }

  private def userLinks(customer: MappedCustomer): List[MappedUserCustomerLink] =
    MappedUserCustomerLink.findAll(By(MappedUserCustomerLink.mCustomerId, customer.customerId))

  private def candidate(policy: RetentionPolicy, customer: MappedCustomer, trigger: Date): RetentionCandidate = {
    val owner = userLinks(customer).sortBy(l => !l.isActive).headOption.map(_.userId).filter(_.nonEmpty)
    lazy val self: RetentionCandidate = RetentionCandidate(resourceType, customer.customerId, customer.bankId, None, owner, trigger, summary(customer))(
      jsonCopy = () => fullJson(customer),
      dispose = (action, disposal) => {
        val archiveId = if (action == ActionArchive) Some(disposal.archive(self, None, None)) else None
        if (policy.cascade || action != ActionAnonymise)
          FileRetention.disposeAttached(customer.bankId, FileRecordType, customer.customerId, policy.cascade, action, disposal, resourceType)
        removePersonalRecords(customer)
        if (action == ActionAnonymise) anonymise(customer)
        else {
          CustomerAccountLink.bulkDelete_!!(By(CustomerAccountLink.CustomerId, customer.customerId))
          customer.delete_!
        }
        disposal.register(self, action, archiveId, None)
        true
      }
    )
    self
  }

  /** The records that describe the person: attributes, addresses, dependants, tax residences and the links to Users. */
  private def removePersonalRecords(customer: MappedCustomer): Unit = {
    val pk = customer.id.get
    MappedCustomerAttribute.bulkDelete_!!(By(MappedCustomerAttribute.mCustomerId, customer.customerId))
    MappedCustomerAddress.bulkDelete_!!(By(MappedCustomerAddress.mCustomerId, pk))
    MappedCustomerDependant.bulkDelete_!!(By(MappedCustomerDependant.mCustomer, pk))
    MappedTaxResidence.bulkDelete_!!(By(MappedTaxResidence.mCustomerId, pk))
    MappedUserCustomerLink.bulkDelete_!!(By(MappedUserCustomerLink.mCustomerId, customer.customerId))
  }

  /**
   * Clear the personal fields and keep the row, so that the customer id, the bank, the customer
   * number and the links to accounts stay valid for whatever refers to them.
   */
  private def anonymise(customer: MappedCustomer): Unit =
    customer
      .mLegalName(AnonymisedLegalName)
      .mMobileNumber("")
      .mEmail("")
      .mFaceImageUrl("")
      .mFaceImageTime(null)
      .mDateOfBirth(null)
      .mRelationshipStatus("")
      .mDependents(0)
      .mHighestEducationAttained("")
      .mEmploymentStatus("")
      .mCreditRating("")
      .mCreditSource("")
      .mCreditLimitCurrency("")
      .mCreditLimitAmount("")
      .mTitle("")
      .mNameSuffix("")
      .saveMe()
}

object ConsentRetention extends RetentionResourceHandler {
  val resourceType: String = ResourceConsent

  /**
   * A Consent belongs to the banks its items name. A policy of a bank governs the Consents that name
   * that bank and no other; a policy of SYS governs the Consents that name no bank at all.
   */
  private def belongsTo(bankId: String, items: List[ConsentItem]): Boolean = {
    val banks = items.map(i => Option(i.bankId.get).getOrElse("")).filter(b => b.nonEmpty && b != SystemSpace).distinct
    if (bankId == SystemSpace) banks.isEmpty else banks == List(bankId)
  }

  def scan(policy: RetentionPolicy, cutoff: Date)(visit: RetentionCandidate => Boolean): Unit = {
    val dateFilter: QueryParam[MappedConsent] = policy.dateBasis match {
      case BasisCreated => By_<=(MappedConsent.createdAt, cutoff)
      case BasisUpdated => By_<=(MappedConsent.updatedAt, cutoff)
      case _ => By_<=(MappedConsent.mJwtExpiresAt, cutoff)
    }
    scanById(MappedConsent, MappedConsent.id, List(dateFilter)) { consent =>
      val referenceId = Option(consent.mConsentReferenceId.get).getOrElse("")
      lazy val items = ConsentItem.findAll(By(ConsentItem.consentReferenceId, referenceId))
      val trigger: Option[Date] = policy.dateBasis match {
        case BasisCreated => Option(consent.createdAt.get)
        case BasisUpdated => Option(consent.updatedAt.get)
        case _ => Option(consent.mJwtExpiresAt.get)
      }
      if (referenceId.isEmpty || trigger.isEmpty || !belongsTo(policy.bankId, items)) true
      else {
        val c = candidate(policy.bankId, consent, referenceId, items, trigger.get)
        if (conditionMatches(policy.condition, c.conditionView)) visit(c) else true
      }
    }
  }

  // The consent id, the secret, the challenge and the JWT are credentials and are never copied: the
  // Consent is identified by its consent_reference_id everywhere in retention.
  private def summary(consent: MappedConsent, referenceId: String): JObject =
    ("consent_reference_id" -> referenceId) ~
      ("user_id" -> text(consent.mUserId.get)) ~
      ("consumer_id" -> text(consent.mConsumerId.get)) ~
      ("status" -> text(consent.mStatus.get)) ~
      ("api_standard" -> text(consent.mApiStandard.get)) ~
      ("api_version" -> text(consent.mApiVersion.get)) ~
      ("created_at" -> iso(consent.createdAt.get)) ~
      ("updated_at" -> iso(consent.updatedAt.get)) ~
      ("jwt_expires_at" -> iso(consent.mJwtExpiresAt.get))

  private def candidate(bankId: String, consent: MappedConsent, referenceId: String, items: List[ConsentItem], trigger: Date): RetentionCandidate = {
    lazy val self: RetentionCandidate = RetentionCandidate(resourceType, referenceId, bankId, None, nonEmpty(consent.mUserId.get), trigger, summary(consent, referenceId))(
      jsonCopy = () => summary(consent, referenceId) ~
        ("status_updated_at" -> iso(consent.mStatusUpdateDateTime.get)) ~
        ("valid_until" -> iso(consent.mValidUntil.get)) ~
        ("expiration_date_time" -> iso(consent.mExpirationDateTime.get)) ~
        ("recurring_indicator" -> consent.mRecurringIndicator.get) ~
        ("frequency_per_day" -> consent.mFrequencyPerDay.get) ~
        ("note" -> text(consent.mNote.get)) ~
        ("consent_items" -> JArray(items.map(i =>
          ("item_type" -> text(i.itemType.get)) ~ ("bank_id" -> text(i.bankId.get)) ~ ("account_id" -> text(i.accountId.get)) ~
            ("view_id" -> text(i.viewId.get)) ~ ("role_name" -> text(i.roleName.get)): JValue))),
      dispose = (action, disposal) => {
        val archiveId = if (action == ActionArchive) Some(disposal.archive(self, None, None)) else None
        ConsentItem.bulkDelete_!!(By(ConsentItem.consentReferenceId, referenceId))
        MappedConsentAuthContext.bulkDelete_!!(By(MappedConsentAuthContext.ConsentId, consent.mConsentId.get))
        consent.delete_!
        disposal.register(self, action, archiveId, None)
        true
      }
    )
    self
  }
}
