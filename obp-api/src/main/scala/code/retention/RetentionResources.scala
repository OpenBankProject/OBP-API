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

import cats.data.NonEmptyList
import code.CustomerDependants.MappedCustomerDependant
import code.api.util.DoobieUtil
import code.customer.MappedCustomer
import code.customeraddress.MappedCustomerAddress
import code.files.{File, FileAccess, FileAttachment, FileContent}
import code.retention.Retention._
import code.retention.RetentionSql.{readDate, ts}
import com.openbankproject.commons.util.JsonAliases
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._
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

  private[retention] val PageSize = 500

  /**
   * Read a table in pages ordered by primary key, so that rows deleted while the scan runs never make
   * it skip or repeat one. `page(lastId)` reads the next at most PageSize rows whose id is above
   * `lastId`, in id order (see `afterId`). `visit` returns false to stop.
   */
  private[retention] def scanById[T](idOf: T => Long)(page: Long => List[T])(visit: T => Boolean): Unit = {
    var lastId = -1L
    var go = true
    while (go) {
      val rows = page(lastId)
      val it = rows.iterator
      while (go && it.hasNext) {
        val row = it.next()
        lastId = idOf(row)
        go = visit(row)
      }
      if (rows.size < PageSize) go = false
    }
  }

  /** The tail of a page query: the rows after `lastId`, in id order, one page of them. */
  private[retention] def afterId(lastId: Long): Fragment =
    fr"AND id > $lastId ORDER BY id ASC" ++ DoobieUtil.pagination(PageSize, 0)

  /** `column IN (values)`; an empty list matches nothing, as Mapper's ByList did. */
  private[retention] def in(column: String, values: List[String]): Fragment =
    values.filter(_ != null).distinct match {
      case Nil => fr"1 = 0"
      case vs => Fragments.in(Fragment.const(column), NonEmptyList.fromListUnsafe(vs))
    }

  private[retention] def update(statement: Fragment): Int = DoobieUtil.runUpdate(statement.update.run)

  private[retention] def now(): java.sql.Timestamp = new java.sql.Timestamp(System.currentTimeMillis())

  private[retention] def parse(json: String): JValue =
    Try(JsonAliases.parse(Option(json).getOrElse(""))).getOrElse(JNothing)

  private[retention] def nonEmpty(values: String*): Option[String] = values.find(v => v != null && v.nonEmpty)
}

import code.retention.RetentionResources._

/**
 * The rows the handlers below read, as the Mapper entities upstream read them handed them out: each
 * string is the column as it is (NULL as null), a flag NULL as false, a number NULL as 0. The
 * handlers read the tables with SQL of their own because they walk them by primary key, which the
 * stores of those tables do not offer.
 */
private[retention] case class DynamicRecordRow(id: Long, dynamicDataId: String, dynamicEntityName: String,
                                               dataJson: String, bankId: String, userId: String,
                                               isPersonalEntity: Boolean, createdAt: Date, updatedAt: Date,
                                               createdByUserId: String, createdByOnBehalfOfUserId: String,
                                               updatedByUserId: String, updatedByOnBehalfOfUserId: String)

private[retention] case class ChatMessageRow(id: Long, chatMessageId: String, chatRoomId: String, senderUserId: String,
                                             senderConsumerId: String, content: String, messageType: String,
                                             mentionedUserIds: String, replyToMessageId: String, threadId: String,
                                             isDeleted: Boolean, createdAt: Date, updatedAt: Date)

private[retention] case class ChatRoomRow(id: Long, chatRoomId: String, bankId: String, name: String,
                                          description: String, createdByUserId: String, isOpenRoom: Boolean,
                                          isArchived: Boolean, lastMessageAt: Date, createdAt: Date, updatedAt: Date)

private[retention] case class CustomerRow(id: Long, createdAt: Date, updatedAt: Date, customer: Option[MappedCustomer])

private[retention] case class ConsentRow(id: Long, consentId: String, userId: String, consumerId: String, status: String,
                                         apiStandard: String, apiVersion: String, createdAt: Date, updatedAt: Date,
                                         jwtExpiresAt: Date, statusUpdateDateTime: Date, validUntil: Date,
                                         expirationDateTime: Date, recurringIndicator: Boolean, frequencyPerDay: Int,
                                         note: String, consentReferenceId: String)

private[retention] case class ConsentItemRow(itemType: String, bankId: String, accountId: String, viewId: String,
                                             roleName: String)

object DynamicEntityRecordRetention extends RetentionResourceHandler {
  val resourceType: String = ResourceDynamicEntityRecord

  private type Row = (Long, Option[String], Option[String], Option[String], Option[String], Option[String],
    Option[Boolean], Option[java.sql.Timestamp], Option[java.sql.Timestamp], Option[String], Option[String],
    Option[String], Option[String])

  private def page(space: String, entityName: String, dateFilter: Fragment)(lastId: Long): List[DynamicRecordRow] =
    DoobieUtil.runQuery((
      fr"""SELECT id, dynamicdataid, dynamicentityname, datajson, bankid, userid, ispersonalentity, createdat,
                  updatedat, createdbyuserid, createdbyonbehalfofuserid, updatedbyuserid, updatedbyonbehalfofuserid
           FROM dynamicdata
           WHERE bankid = ${Option(space)} AND dynamicentityname = ${Option(entityName)}""" ++ dateFilter ++
        afterId(lastId)).query[Row].to[List]).map {
      case (id, dataId, name, json, bankId, userId, personal, createdAt, updatedAt, createdBy, createdByOnBehalfOf,
            updatedBy, updatedByOnBehalfOf) =>
        DynamicRecordRow(id, dataId.orNull, name.orNull, json.orNull, bankId.orNull, userId.orNull,
          personal.getOrElse(false), readDate(createdAt), readDate(updatedAt), createdBy.orNull,
          createdByOnBehalfOf.orNull, updatedBy.orNull, updatedByOnBehalfOf.orNull)
    }

  def scan(policy: RetentionPolicy, cutoff: Date)(visit: RetentionCandidate => Boolean): Unit = {
    val space = policy.bankId
    val entityName = policy.entityName.getOrElse("")
    val dateFilter: Fragment = policy.dateBasis match {
      case BasisCreated => fr"AND createdat <= ${ts(cutoff)}"
      case BasisUpdated => fr"AND updatedat <= ${ts(cutoff)}"
      case _ => Fragment.empty
    }
    scanById[DynamicRecordRow](_.id)(page(space, entityName, dateFilter)) { row =>
      val data = parse(row.dataJson)
      val trigger: Option[Date] = policy.dateBasis match {
        case BasisCreated => Option(row.createdAt)
        case BasisUpdated => Option(row.updatedAt)
        case basis => dateIn(data, basis.stripPrefix(BasisFieldPrefix).trim)
      }
      trigger.filter(t => !t.after(cutoff) && conditionMatches(policy.condition, data)) match {
        case Some(t) => visit(candidate(policy, row, data, t))
        case None => true
      }
    }
  }

  private def candidate(policy: RetentionPolicy, row: DynamicRecordRow, data: JValue, trigger: Date): RetentionCandidate = {
    val space = policy.bankId
    val entityName = row.dynamicEntityName
    val recordId = row.dynamicDataId
    val owner =
      if (row.isPersonalEntity) nonEmpty(row.userId)
      else nonEmpty(row.createdByOnBehalfOfUserId, row.createdByUserId, row.userId)
    lazy val self: RetentionCandidate = RetentionCandidate(resourceType, recordId, space, Some(entityName), owner, trigger, data)(
      jsonCopy = () =>
        ("dynamic_data_id" -> recordId) ~
          ("entity_name" -> entityName) ~
          ("bank_id" -> space) ~
          ("user_id" -> text(row.userId)) ~
          ("is_personal_entity" -> row.isPersonalEntity) ~
          ("created_at" -> iso(row.createdAt)) ~
          ("updated_at" -> iso(row.updatedAt)) ~
          ("created_by_user_id" -> text(row.createdByUserId)) ~
          ("created_by_on_behalf_of_user_id" -> text(row.createdByOnBehalfOfUserId)) ~
          ("updated_by_user_id" -> text(row.updatedByUserId)) ~
          ("updated_by_on_behalf_of_user_id" -> text(row.updatedByOnBehalfOfUserId)) ~
          ("file_attachments" -> FileRetention.attachmentsJson(space, entityName, recordId)) ~
          ("data" -> data),
      dispose = (action, disposal) => {
        val archiveId = if (action == ActionArchive) Some(disposal.archive(self, None, None)) else None
        FileRetention.disposeAttached(space, entityName, recordId, policy.cascade, action, disposal, resourceType)
        update(fr"""DELETE FROM dynamicdataaccess
                    WHERE bankid = ${Option(space)} AND entityname = ${Option(entityName)}
                      AND dynamicdataid = ${Option(recordId)}""")
        update(fr"DELETE FROM dynamicdata WHERE id = ${row.id}")
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
    scanById[File](_.id)(lastId => File.findBatch(storedBankIds(policy.bankId), cutoff, lastId, PageSize)) { file =>
      val c = candidate(file, None)
      if (conditionMatches(policy.condition, c.conditionView)) visit(c) else true
    }

  def attachmentsJson(bankId: String, recordType: String, recordId: String): JValue =
    JArray(FileAttachment.findByRecord(storedBankIds(bankId), recordType, recordId).map(a =>
      ("file_attachment_id" -> a.fileAttachmentId) ~ ("file_id" -> a.fileId): JValue))

  private def summary(file: File): JObject =
    ("file_id" -> file.fileId) ~
      ("bank_id" -> file.bankId) ~
      ("file_name" -> text(file.fileName)) ~
      ("media_type" -> text(file.mediaType)) ~
      ("size_in_bytes" -> file.sizeInBytes) ~
      ("sha256" -> text(file.sha256)) ~
      ("user_id" -> text(file.userId)) ~
      ("on_behalf_of_user_id" -> text(file.storedOnBehalfOfUserId)) ~
      ("created_at" -> iso(file.createdAt))

  private def candidate(file: File, parent: Option[(String, String)]): RetentionCandidate =
    RetentionCandidate(resourceType, file.fileId, file.bankId, None, nonEmpty(file.ownerUserId), file.createdAt, summary(file))(
      jsonCopy = () => summary(file) ~
        ("attachments" -> JArray(FileAttachment.findByFileId(file.fileId).map(a =>
          ("file_attachment_id" -> a.fileAttachmentId) ~ ("record_type" -> a.recordType) ~ ("record_id" -> a.recordId): JValue))) ~
        ("access" -> JArray(FileAccess.findByFileId(file.fileId).map(a =>
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
        val bytes = FileContent.findContent(file.fileId)
        Some(disposal.archive(self, bytes, parent))
      } else None
    FileAccess.deleteByFileId(file.fileId)
    FileAttachment.deleteByFileId(file.fileId)
    FileContent.deleteByFileId(file.fileId)
    File.delete(file)
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
    val attachments = FileAttachment.findByRecord(storedBankIds(bankId), recordType, recordId)
    attachments.foreach { attachment =>
      FileAttachment.delete(attachment)
      if (cascade && FileAttachment.countByFileId(attachment.fileId) == 0) {
        File.findByFileId(attachment.fileId).foreach { file =>
          if (disposal.isHeld(resourceType, file.fileId, None)) disposal.countHeld()
          else disposeFile(file, action, disposal, Some(parentType -> recordId))
        }
      }
    }
  }
}

object ChatMessageRetention extends RetentionResourceHandler {
  val resourceType: String = ResourceChatMessage

  private type Row = (Long, Option[String], Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[String], Option[String], Option[String], Option[Boolean], Option[java.sql.Timestamp],
    Option[java.sql.Timestamp])

  private val selectColumns =
    fr"""SELECT id, chatmessageid, chatroomid, senderuserid, senderconsumerid, content, messagetype, mentioneduserids,
                replytomessageid, threadid, isdeleted, createdat, updatedat
         FROM chatmessage"""

  /** The messages matching `condition`, as Mapper's ChatMessage read them. */
  private[retention] def messages(condition: Fragment): List[ChatMessageRow] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[Row].to[List]).map {
      case (id, messageId, roomId, senderUserId, senderConsumerId, content, messageType, mentioned, replyTo, threadId,
            isDeleted, createdAt, updatedAt) =>
        ChatMessageRow(id, messageId.orNull, roomId.orNull, senderUserId.orNull, senderConsumerId.orNull, content.orNull,
          messageType.orNull, mentioned.orNull, replyTo.orNull, threadId.orNull, isDeleted.getOrElse(false),
          readDate(createdAt), readDate(updatedAt))
    }

  def scan(policy: RetentionPolicy, cutoff: Date)(visit: RetentionCandidate => Boolean): Unit = {
    val rooms = DoobieUtil.runQuery((fr"SELECT chatroomid FROM chatroom WHERE" ++ in("bankid", storedBankIds(policy.bankId)) ++
      fr"ORDER BY id ASC").query[Option[String]].to[List]).map(_.orNull)
    val dateFilter: Fragment =
      if (policy.dateBasis == BasisUpdated) fr"AND updatedat <= ${ts(cutoff)}" else fr"AND createdat <= ${ts(cutoff)}"
    var go = true
    rooms.grouped(500).foreach { roomIds =>
      if (go) scanById[ChatMessageRow](_.id)(lastId => messages(fr"WHERE" ++ in("chatroomid", roomIds) ++ dateFilter ++
        afterId(lastId))) { message =>
        val trigger = if (policy.dateBasis == BasisUpdated) message.updatedAt else message.createdAt
        val c = candidate(policy.bankId, message, trigger)
        go = if (conditionMatches(policy.condition, c.conditionView)) visit(c) else true
        go
      }
    }
  }

  private[retention] def summary(message: ChatMessageRow): JObject =
    ("chat_message_id" -> message.chatMessageId) ~
      ("chat_room_id" -> message.chatRoomId) ~
      ("sender_user_id" -> text(message.senderUserId)) ~
      ("sender_consumer_id" -> text(message.senderConsumerId)) ~
      ("message_type" -> text(message.messageType)) ~
      ("reply_to_message_id" -> text(message.replyToMessageId)) ~
      ("thread_id" -> text(message.threadId)) ~
      ("is_deleted" -> message.isDeleted) ~
      ("created_at" -> iso(message.createdAt)) ~
      ("updated_at" -> iso(message.updatedAt))

  private[retention] def fullJson(message: ChatMessageRow): JObject =
    summary(message) ~
      ("content" -> text(message.content)) ~
      ("mentioned_user_ids" -> text(message.mentionedUserIds)) ~
      ("reactions" -> JArray(DoobieUtil.runQuery(
        sql"""SELECT reactionid, userid, emoji, createdat FROM reaction
              WHERE chatmessageid = ${Option(message.chatMessageId)} ORDER BY id ASC"""
          .query[(Option[String], Option[String], Option[String], Option[java.sql.Timestamp])].to[List]).map {
        case (reactionId, userId, emoji, createdAt) =>
          ("reaction_id" -> reactionId.orNull) ~ ("user_id" -> userId.orNull) ~ ("emoji" -> emoji.orNull) ~
            ("created_at" -> iso(readDate(createdAt))): JValue
      }))

  /** Delete a message with its reactions, by the message row's primary key as Mapper's delete_! did. */
  private[retention] def deleteMessage(message: ChatMessageRow): Unit = {
    update(fr"DELETE FROM reaction WHERE chatmessageid = ${Option(message.chatMessageId)}")
    update(fr"DELETE FROM chatmessage WHERE id = ${message.id}")
    ()
  }

  private[retention] def candidate(bankId: String, message: ChatMessageRow, trigger: Date): RetentionCandidate = {
    lazy val self: RetentionCandidate = RetentionCandidate(resourceType, message.chatMessageId, bankId, None,
      nonEmpty(message.senderUserId), trigger, summary(message))(
      jsonCopy = () => fullJson(message),
      dispose = (action, disposal) => {
        val archiveId = if (action == ActionArchive) Some(disposal.archive(self, None, None)) else None
        deleteMessage(message)
        // The room shows a preview of its newest message; it must not keep the text of one disposed of.
        DoobieUtil.runQuery((fr"SELECT id, lastmessageat FROM chatroom WHERE chatroomid = ${Option(message.chatRoomId)}" ++
          fr"ORDER BY id ASC" ++ DoobieUtil.pagination(1, 0)).query[(Long, Option[java.sql.Timestamp])].option).foreach {
          case (roomPk, lastMessageAt) =>
            val lastAt = Option(readDate(lastMessageAt))
            if (lastAt.forall(at => Option(message.createdAt).forall(created => !at.after(created))))
              update(fr"""UPDATE chatroom SET lastmessagepreview = '', lastmessagesenderusername = '',
                          updatedat = ${now()} WHERE id = $roomPk""")
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

  private type Row = (Long, Option[String], Option[String], Option[String], Option[String], Option[String],
    Option[Boolean], Option[Boolean], Option[java.sql.Timestamp], Option[java.sql.Timestamp], Option[java.sql.Timestamp])

  private def page(bankIds: List[String], dateFilter: Fragment)(lastId: Long): List[ChatRoomRow] =
    DoobieUtil.runQuery((
      fr"""SELECT id, chatroomid, bankid, name, description, createdbyuserid, isopenroom, isarchived, lastmessageat,
                  createdat, updatedat
           FROM chatroom WHERE""" ++ in("bankid", bankIds) ++ dateFilter ++ afterId(lastId)).query[Row].to[List]).map {
      case (id, roomId, bankId, name, description, createdBy, isOpen, isArchived, lastMessageAt, createdAt, updatedAt) =>
        ChatRoomRow(id, roomId.orNull, bankId.orNull, name.orNull, description.orNull, createdBy.orNull,
          isOpen.getOrElse(false), isArchived.getOrElse(false), readDate(lastMessageAt), readDate(createdAt),
          readDate(updatedAt))
    }

  def scan(policy: RetentionPolicy, cutoff: Date)(visit: RetentionCandidate => Boolean): Unit = {
    val dateFilter: Fragment = policy.dateBasis match {
      case BasisCreated => fr"AND createdat <= ${ts(cutoff)}"
      case BasisUpdated => fr"AND updatedat <= ${ts(cutoff)}"
      case _ => Fragment.empty
    }
    scanById[ChatRoomRow](_.id)(page(storedBankIds(policy.bankId), dateFilter)) { room =>
      val trigger: Option[Date] = policy.dateBasis match {
        case BasisCreated => Option(room.createdAt)
        case BasisUpdated => Option(room.updatedAt)
        case _ => Option(room.lastMessageAt).orElse(Option(room.createdAt))
      }
      val c = trigger.filterNot(_.after(cutoff)).map(t => candidate(policy.bankId, room, t))
      c.filter(c => conditionMatches(policy.condition, c.conditionView)).map(visit).getOrElse(true)
    }
  }

  private def summary(room: ChatRoomRow): JObject =
    ("chat_room_id" -> room.chatRoomId) ~
      ("bank_id" -> text(room.bankId)) ~
      ("name" -> text(room.name)) ~
      ("created_by_user_id" -> text(room.createdByUserId)) ~
      ("is_open_room" -> room.isOpenRoom) ~
      ("is_archived" -> room.isArchived) ~
      ("last_message_at" -> iso(room.lastMessageAt)) ~
      ("created_at" -> iso(room.createdAt)) ~
      ("updated_at" -> iso(room.updatedAt))

  private def roomMessages(room: ChatRoomRow): List[ChatMessageRow] =
    ChatMessageRetention.messages(fr"WHERE chatroomid = ${Option(room.chatRoomId)} ORDER BY id ASC")

  private def candidate(bankId: String, room: ChatRoomRow, trigger: Date): RetentionCandidate = {
    lazy val self: RetentionCandidate = RetentionCandidate(resourceType, room.chatRoomId, bankId, None, nonEmpty(room.createdByUserId), trigger, summary(room))(
      // The joining key is left out: it is how people get into the room, not a record of it.
      jsonCopy = () => summary(room) ~
        ("description" -> text(room.description)) ~
        ("participants" -> JArray(DoobieUtil.runQuery(
          sql"""SELECT participantid, userid, consumerid, permissions, joinedat FROM participant
                WHERE chatroomid = ${Option(room.chatRoomId)} ORDER BY id ASC"""
            .query[(Option[String], Option[String], Option[String], Option[String], Option[java.sql.Timestamp])].to[List]).map {
          case (participantId, userId, consumerId, permissions, joinedAt) =>
            ("participant_id" -> participantId.orNull) ~ ("user_id" -> text(userId.orNull)) ~
              ("consumer_id" -> text(consumerId.orNull)) ~ ("permissions" -> text(permissions.orNull)) ~
              ("joined_at" -> iso(readDate(joinedAt))): JValue
        })) ~
        ("messages" -> JArray(roomMessages(room).map(m => ChatMessageRetention.fullJson(m): JValue))),
      dispose = (action, disposal) => {
        val messages = roomMessages(room)
        // A room goes with all its messages, so a room holding a message on hold stays whole.
        if (messages.exists(m => disposal.isHeld(ResourceChatMessage, m.chatMessageId, None))) false
        else {
          val archiveId = if (action == ActionArchive) Some(disposal.archive(self, None, None)) else None
          messages.foreach { m =>
            ChatMessageRetention.deleteMessage(m)
            disposal.register(ChatMessageRetention.candidate(bankId, m, m.createdAt), action, archiveId, Some(resourceType -> room.chatRoomId))
          }
          update(fr"DELETE FROM participant WHERE chatroomid = ${Option(room.chatRoomId)}")
          update(fr"DELETE FROM chatroom WHERE id = ${room.id}")
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

  /**
   * One page of the space's customers: their keys and dates, read here, and the customer itself from
   * its store. A row deleted between the two reads has no customer, and is passed over.
   */
  private def page(bankId: String, dateFilter: Fragment)(lastId: Long): List[CustomerRow] =
    DoobieUtil.runQuery((fr"SELECT id, createdat, updatedat FROM mappedcustomer WHERE mbank = ${Option(bankId)}" ++
      dateFilter ++ afterId(lastId)).query[(Long, Option[java.sql.Timestamp], Option[java.sql.Timestamp])].to[List]).map {
      case (id, createdAt, updatedAt) =>
        CustomerRow(id, readDate(createdAt), readDate(updatedAt), MappedCustomer.findByPrimaryKey(id).toOption)
    }

  def scan(policy: RetentionPolicy, cutoff: Date)(visit: RetentionCandidate => Boolean): Unit = {
    val dateFilter: Fragment =
      if (policy.dateBasis == BasisUpdated) fr"AND updatedat <= ${ts(cutoff)}" else fr"AND createdat <= ${ts(cutoff)}"
    scanById[CustomerRow](_.id)(page(policy.bankId, dateFilter)) { row =>
      row.customer match {
        case None => true
        case Some(customer) =>
          // A Customer already anonymised is not anonymised again on every run.
          val alreadyDone = policy.action == ActionAnonymise && customer.legalName == AnonymisedLegalName
          val trigger = Option(if (policy.dateBasis == BasisUpdated) row.updatedAt else row.createdAt)
          val c = trigger.filterNot(_ => alreadyDone).map(t => candidate(policy, row, customer, t))
          c.filter(c => conditionMatches(policy.condition, c.conditionView)).map(visit).getOrElse(true)
      }
    }
  }

  private def summary(row: CustomerRow, customer: MappedCustomer): JObject =
    ("customer_id" -> customer.customerId) ~
      ("bank_id" -> customer.bankId) ~
      ("customer_number" -> text(customer.number)) ~
      ("customer_type" -> text(customer.customerTypeValue)) ~
      ("branch_id" -> text(customer.branchId)) ~
      ("kyc_status" -> customer.kycStatusValue) ~
      ("created_at" -> iso(row.createdAt)) ~
      ("updated_at" -> iso(row.updatedAt))

  private def fullJson(row: CustomerRow, customer: MappedCustomer): JObject = {
    val pk = customer.customerPrimaryKey
    summary(row, customer) ~
      ("legal_name" -> text(customer.legalName)) ~
      ("mobile_phone_number" -> text(customer.mobileNumber)) ~
      ("email" -> text(customer.email)) ~
      ("face_image_url" -> text(customer.faceImageUrl)) ~
      ("date_of_birth" -> iso(customer.dateOfBirthValue)) ~
      ("relationship_status" -> text(customer.relationshipStatus)) ~
      ("dependants" -> customer.dependentsValue) ~
      ("highest_education_attained" -> text(customer.highestEducationAttained)) ~
      ("employment_status" -> text(customer.employmentStatus)) ~
      ("credit_rating" -> (("rating" -> text(customer.creditRatingValue)) ~ ("source" -> text(customer.creditSource)))) ~
      ("credit_limit" -> (("currency" -> text(customer.creditLimitCurrency)) ~ ("amount" -> text(customer.creditLimitAmount)))) ~
      ("last_ok_date" -> iso(customer.lastOkDate)) ~
      ("title" -> text(customer.title)) ~
      ("name_suffix" -> text(customer.nameSuffix)) ~
      ("parent_customer_id" -> text(customer.parentCustomerIdValue)) ~
      ("customer_attributes" -> JArray(DoobieUtil.runQuery(
        sql"""SELECT mcustomerattributeid, mname, mtype, mvalue FROM mappedcustomerattribute
              WHERE mcustomerid = ${Option(customer.customerId)} ORDER BY id ASC"""
          .query[(Option[String], Option[String], Option[String], Option[String])].to[List]).map {
        case (attributeId, name, attributeType, value) =>
          ("customer_attribute_id" -> attributeId.orNull) ~ ("name" -> text(name.orNull)) ~
            ("type" -> text(attributeType.orNull)) ~ ("value" -> text(value.orNull)): JValue
      })) ~
      ("addresses" -> JArray(DoobieUtil.runQuery(
        sql"""SELECT mcustomeraddressid, mline1, mline2, mline3, mcity, mcounty, mstate, mpostcode, mcountrycode,
                     mtags, mstatus
              FROM mappedcustomeraddress WHERE mcustomerid = $pk ORDER BY id ASC"""
          .query[(Option[String], Option[String], Option[String], Option[String], Option[String], Option[String],
            Option[String], Option[String], Option[String], Option[String], Option[String])].to[List]).map {
        case (addressId, line1, line2, line3, city, county, state, postcode, countryCode, tags, status) =>
          ("customer_address_id" -> addressId.orNull) ~ ("line_1" -> text(line1.orNull)) ~ ("line_2" -> text(line2.orNull)) ~
            ("line_3" -> text(line3.orNull)) ~ ("city" -> text(city.orNull)) ~ ("county" -> text(county.orNull)) ~
            ("state" -> text(state.orNull)) ~ ("postcode" -> text(postcode.orNull)) ~
            ("country_code" -> text(countryCode.orNull)) ~ ("tags" -> text(tags.orNull)) ~
            ("status" -> text(status.orNull)): JValue
      })) ~
      ("dependant_dates_of_birth" -> JArray(MappedCustomerDependant.findAllByCustomerKey(pk)
        .map(d => iso(d.dateOfBirth)))) ~
      ("tax_residences" -> JArray(DoobieUtil.runQuery(
        sql"""SELECT mtaxresidenceid, mdomain, mtaxnumber FROM mappedtaxresidence
              WHERE mcustomerid = $pk ORDER BY id ASC"""
          .query[(Option[String], Option[String], Option[String])].to[List]).map {
        case (taxResidenceId, domain, taxNumber) =>
          ("tax_residence_id" -> taxResidenceId.orNull) ~ ("domain" -> text(domain.orNull)) ~
            ("tax_number" -> text(taxNumber.orNull)): JValue
      })) ~
      ("user_customer_links" -> JArray(userLinks(customer).map { case (linkId, userId, isActive, dateInserted) =>
        ("user_customer_link_id" -> linkId) ~ ("user_id" -> userId) ~ ("is_active" -> isActive) ~
          ("date_inserted" -> iso(dateInserted)): JValue
      })) ~
      ("customer_account_links" -> JArray(DoobieUtil.runQuery(
        sql"""SELECT customeraccountlinkid, bankid, accountid, relationshiptype FROM customeraccountlink
              WHERE customerid = ${Option(customer.customerId)} ORDER BY id ASC"""
          .query[(Option[String], Option[String], Option[String], Option[String])].to[List]).map {
        case (linkId, bankId, accountId, relationshipType) =>
          ("customer_account_link_id" -> linkId.orNull) ~ ("bank_id" -> text(bankId.orNull)) ~
            ("account_id" -> text(accountId.orNull)) ~ ("relationship_type" -> text(relationshipType.orNull)): JValue
      })) ~
      ("file_attachments" -> FileRetention.attachmentsJson(customer.bankId, FileRecordType, customer.customerId))
  }

  /** The customer's links to Users: id, user id, whether active, and when inserted. */
  private def userLinks(customer: MappedCustomer): List[(String, String, Boolean, Date)] =
    DoobieUtil.runQuery(
      sql"""SELECT musercustomerlinkid, muserid, misactive, mdateinserted FROM mappedusercustomerlink
            WHERE mcustomerid = ${Option(customer.customerId)} ORDER BY id ASC"""
        .query[(Option[String], Option[String], Option[Boolean], Option[java.sql.Timestamp])].to[List]).map {
      case (linkId, userId, isActive, dateInserted) =>
        (linkId.orNull, userId.orNull, isActive.getOrElse(false), readDate(dateInserted))
    }

  private def candidate(policy: RetentionPolicy, row: CustomerRow, customer: MappedCustomer, trigger: Date): RetentionCandidate = {
    val owner = userLinks(customer).sortBy(l => !l._3).headOption.map(_._2).filter(u => u != null && u.nonEmpty)
    lazy val self: RetentionCandidate = RetentionCandidate(resourceType, customer.customerId, customer.bankId, None, owner, trigger, summary(row, customer))(
      jsonCopy = () => fullJson(row, customer),
      dispose = (action, disposal) => {
        val archiveId = if (action == ActionArchive) Some(disposal.archive(self, None, None)) else None
        if (policy.cascade || action != ActionAnonymise)
          FileRetention.disposeAttached(customer.bankId, FileRecordType, customer.customerId, policy.cascade, action, disposal, resourceType)
        removePersonalRecords(customer)
        if (action == ActionAnonymise) anonymise(customer)
        else {
          update(fr"DELETE FROM customeraccountlink WHERE customerid = ${Option(customer.customerId)}")
          update(fr"DELETE FROM mappedcustomer WHERE id = ${customer.customerPrimaryKey}")
        }
        disposal.register(self, action, archiveId, None)
        true
      }
    )
    self
  }

  /** The records that describe the person: attributes, addresses, dependants, tax residences and the links to Users. */
  private def removePersonalRecords(customer: MappedCustomer): Unit = {
    val pk = customer.customerPrimaryKey
    update(fr"DELETE FROM mappedcustomerattribute WHERE mcustomerid = ${Option(customer.customerId)}")
    MappedCustomerAddress.deleteByCustomerKey(pk)
    MappedCustomerDependant.deleteByCustomerKey(pk)
    update(fr"DELETE FROM mappedtaxresidence WHERE mcustomerid = $pk")
    update(fr"DELETE FROM mappedusercustomerlink WHERE mcustomerid = ${Option(customer.customerId)}")
    ()
  }

  /**
   * Clear the personal fields and keep the row, so that the customer id, the bank, the customer
   * number and the links to accounts stay valid for whatever refers to them. updatedat is stamped,
   * as Mapper's saveMe did.
   */
  private def anonymise(customer: MappedCustomer): Unit = {
    update(fr"""UPDATE mappedcustomer SET mlegalname = $AnonymisedLegalName, mmobilenumber = '', memail = '',
                  mfaceimageurl = '', mfaceimagetime = NULL, mdateofbirth = NULL, mrelationshipstatus = '',
                  mdependents = 0, mhighesteducationattained = '', memploymentstatus = '',
                  mcreditrating = '', mcreditsource = '', mcreditlimitcurrency = '',
                  mcreditlimitamount = '', mtitle = '', mnamesuffix = '', updatedat = ${now()}
                WHERE id = ${customer.customerPrimaryKey}""")
    ()
  }
}

object ConsentRetention extends RetentionResourceHandler {
  val resourceType: String = ResourceConsent

  /**
   * A Consent belongs to the banks its items name. A policy of a bank governs the Consents that name
   * that bank and no other; a policy of SYS governs the Consents that name no bank at all.
   */
  private def belongsTo(bankId: String, items: List[ConsentItemRow]): Boolean = {
    val banks = items.map(i => Option(i.bankId).getOrElse("")).filter(b => b.nonEmpty && b != SystemSpace).distinct
    if (bankId == SystemSpace) banks.isEmpty else banks == List(bankId)
  }

  private type Row = (Long, Option[String], Option[String], Option[String], Option[String], Option[String],
    Option[String], Option[java.sql.Timestamp], Option[java.sql.Timestamp], Option[java.sql.Timestamp],
    Option[java.sql.Timestamp], Option[java.sql.Date], Option[java.sql.Timestamp], Option[Boolean], Option[Int],
    Option[String], Option[String])

  private def page(dateFilter: Fragment)(lastId: Long): List[ConsentRow] =
    DoobieUtil.runQuery((
      fr"""SELECT id, mconsentid, muserid, mconsumerid, mstatus, mapistandard, mapiversion, createdat, updatedat,
                  jwt_expires_at, mstatusupdatedatetime, mvaliduntil, mexpirationdatetime, mrecurringindicator,
                  mfrequencyperday, mnote, consent_reference_id
           FROM mappedconsent WHERE""" ++ dateFilter ++ afterId(lastId)).query[Row].to[List]).map {
      case (id, consentId, userId, consumerId, status, apiStandard, apiVersion, createdAt, updatedAt, jwtExpiresAt,
            statusUpdatedAt, validUntil, expirationDateTime, recurring, frequency, note, referenceId) =>
        ConsentRow(id, consentId.orNull, userId.orNull, consumerId.orNull, status.orNull, apiStandard.orNull,
          apiVersion.orNull, readDate(createdAt), readDate(updatedAt), readDate(jwtExpiresAt), readDate(statusUpdatedAt),
          validUntil.map(d => new Date(d.getTime)).orNull, readDate(expirationDateTime), recurring.getOrElse(false),
          frequency.getOrElse(0), note.orNull, referenceId.orNull)
    }

  private def itemsOf(referenceId: String): List[ConsentItemRow] =
    DoobieUtil.runQuery(
      sql"""SELECT item_type, bank_id, account_id, view_id, role_name FROM consent_item
            WHERE consent_reference_id = ${Option(referenceId)} ORDER BY id ASC"""
        .query[(Option[String], Option[String], Option[String], Option[String], Option[String])].to[List]).map {
      case (itemType, bankId, accountId, viewId, roleName) =>
        ConsentItemRow(itemType.orNull, bankId.orNull, accountId.orNull, viewId.orNull, roleName.orNull)
    }

  def scan(policy: RetentionPolicy, cutoff: Date)(visit: RetentionCandidate => Boolean): Unit = {
    val dateFilter: Fragment = policy.dateBasis match {
      case BasisCreated => fr"createdat <= ${ts(cutoff)}"
      case BasisUpdated => fr"updatedat <= ${ts(cutoff)}"
      case _ => fr"jwt_expires_at <= ${ts(cutoff)}"
    }
    scanById[ConsentRow](_.id)(page(dateFilter)) { consent =>
      val referenceId = Option(consent.consentReferenceId).getOrElse("")
      lazy val items = itemsOf(referenceId)
      val trigger: Option[Date] = policy.dateBasis match {
        case BasisCreated => Option(consent.createdAt)
        case BasisUpdated => Option(consent.updatedAt)
        case _ => Option(consent.jwtExpiresAt)
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
  private def summary(consent: ConsentRow, referenceId: String): JObject =
    ("consent_reference_id" -> referenceId) ~
      ("user_id" -> text(consent.userId)) ~
      ("consumer_id" -> text(consent.consumerId)) ~
      ("status" -> text(consent.status)) ~
      ("api_standard" -> text(consent.apiStandard)) ~
      ("api_version" -> text(consent.apiVersion)) ~
      ("created_at" -> iso(consent.createdAt)) ~
      ("updated_at" -> iso(consent.updatedAt)) ~
      ("jwt_expires_at" -> iso(consent.jwtExpiresAt))

  private def candidate(bankId: String, consent: ConsentRow, referenceId: String, items: List[ConsentItemRow], trigger: Date): RetentionCandidate = {
    lazy val self: RetentionCandidate = RetentionCandidate(resourceType, referenceId, bankId, None, nonEmpty(consent.userId), trigger, summary(consent, referenceId))(
      jsonCopy = () => summary(consent, referenceId) ~
        ("status_updated_at" -> iso(consent.statusUpdateDateTime)) ~
        ("valid_until" -> iso(consent.validUntil)) ~
        ("expiration_date_time" -> iso(consent.expirationDateTime)) ~
        ("recurring_indicator" -> consent.recurringIndicator) ~
        ("frequency_per_day" -> consent.frequencyPerDay) ~
        ("note" -> text(consent.note)) ~
        ("consent_items" -> JArray(items.map(i =>
          ("item_type" -> text(i.itemType)) ~ ("bank_id" -> text(i.bankId)) ~ ("account_id" -> text(i.accountId)) ~
            ("view_id" -> text(i.viewId)) ~ ("role_name" -> text(i.roleName)): JValue))),
      dispose = (action, disposal) => {
        val archiveId = if (action == ActionArchive) Some(disposal.archive(self, None, None)) else None
        update(fr"DELETE FROM consent_item WHERE consent_reference_id = ${Option(referenceId)}")
        update(fr"DELETE FROM consentauthcontext WHERE consentid = ${Option(consent.consentId)}")
        update(fr"DELETE FROM mappedconsent WHERE id = ${consent.id}")
        disposal.register(self, action, archiveId, None)
        true
      }
    )
    self
  }
}
