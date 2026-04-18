package code.chat

import java.util.Date
import code.api.util.APIUtil.generateUUID
import code.util.MappedUUID
import net.liftweb.common.{Box, Full}
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

object MappedChatRoomProvider extends ChatRoomProvider {

  override def createChatRoom(
    bankId: String,
    name: String,
    description: String,
    createdByUserId: String
  ): Box[ChatRoomTrait] = {
    tryo {
      ChatRoom.create
        .BankId(bankId)
        .Name(name)
        .Description(description)
        .CreatedByUserId(createdByUserId)
        .IsArchived(false)
        .saveMe()
    }
  }

  override def getChatRoom(chatRoomId: String): Box[ChatRoomTrait] = {
    ChatRoom.find(By(ChatRoom.ChatRoomId, chatRoomId))
  }

  override def getChatRoomByBankIdAndName(bankId: String, name: String): Box[ChatRoomTrait] = {
    ChatRoom.find(
      By(ChatRoom.BankId, bankId),
      By(ChatRoom.Name, name)
    )
  }

  override def getChatRoomsByBankId(bankId: String): Box[List[ChatRoomTrait]] = {
    tryo {
      ChatRoom.findAll(By(ChatRoom.BankId, bankId))
    }
  }

  override def getChatRoomsByBankIdForUser(bankId: String, userId: String): Box[List[ChatRoomTrait]] = {
    tryo {
      val participantRoomIds = Participant.findAll(By(Participant.UserId, userId))
        .map(_.chatRoomId)
      val explicitRooms = ChatRoom.findAll(
        By(ChatRoom.BankId, bankId),
        ByList(ChatRoom.ChatRoomId, participantRoomIds)
      )
      val openRooms = ChatRoom.findAll(
        By(ChatRoom.BankId, bankId),
        By(ChatRoom.IsOpenRoom, true)
      )
      (explicitRooms ++ openRooms).groupBy(_.chatRoomId).values.map(_.head).toList
    }
  }

  override def getChatRoomByJoiningKey(joiningKey: String): Box[ChatRoomTrait] = {
    ChatRoom.find(By(ChatRoom.JoiningKey, joiningKey))
  }

  override def searchChatRoomsForUserWithParticipants(
    userId: String,
    requiredParticipantUserIds: List[String],
    exactParticipants: Boolean
  ): Box[List[ChatRoomTrait]] = {
    tryo {
      // 1. Find every room where the current user is an explicit participant.
      val myRoomIds = Participant.findAll(By(Participant.UserId, userId))
        .map(_.chatRoomId)
        .distinct
      val myRooms = if (myRoomIds.isEmpty) Nil
        else ChatRoom.findAll(ByList(ChatRoom.ChatRoomId, myRoomIds))

      // 2. For each candidate room, fetch the full participant set and apply
      //    the requested filters.
      val requiredSet = requiredParticipantUserIds.toSet
      val expectedExactSize = requiredSet.size + 1 // +1 for the current user

      myRooms.filter { room =>
        // Open rooms have implicit participants ("everyone"), so an exact-match
        // query is meaningless against them — exclude them in that case.
        if (exactParticipants && room.isOpenRoom) {
          false
        } else {
          val participantUserIds = Participant.findAll(By(Participant.ChatRoomId, room.chatRoomId))
            .map(_.userId)
            .toSet
          val containsAllRequired = requiredSet.subsetOf(participantUserIds)
          if (!containsAllRequired) {
            false
          } else if (exactParticipants) {
            participantUserIds.size == expectedExactSize
          } else {
            true
          }
        }
      }
    }
  }

  override def updateChatRoom(
    chatRoomId: String,
    name: Option[String],
    description: Option[String]
  ): Box[ChatRoomTrait] = {
    ChatRoom.find(By(ChatRoom.ChatRoomId, chatRoomId)).flatMap { room =>
      tryo {
        name.foreach(n => room.Name(n))
        description.foreach(d => room.Description(d))
        room.saveMe()
      }
    }
  }

  override def setIsOpenRoom(chatRoomId: String, isOpenRoom: Boolean): Box[ChatRoomTrait] = {
    ChatRoom.find(By(ChatRoom.ChatRoomId, chatRoomId)).flatMap { room =>
      tryo {
        room.IsOpenRoom(isOpenRoom).saveMe()
      }
    }
  }

  override def updateLastMessageInfo(chatRoomId: String, lastMessageAt: Date, preview: String, senderUsername: String): Box[ChatRoomTrait] = {
    ChatRoom.find(By(ChatRoom.ChatRoomId, chatRoomId)).flatMap { room =>
      tryo {
        room.LastMessageAt(lastMessageAt)
          .LastMessagePreview(if (preview.length > 100) preview.substring(0, 100) else preview)
          .LastMessageSenderUsername(senderUsername)
          .saveMe()
      }
    }
  }

  override def archiveChatRoom(chatRoomId: String): Box[ChatRoomTrait] = {
    ChatRoom.find(By(ChatRoom.ChatRoomId, chatRoomId)).flatMap { room =>
      tryo {
        room.IsArchived(true).saveMe()
      }
    }
  }

  override def deleteChatRoom(chatRoomId: String): Box[Boolean] = {
    ChatRoom.find(By(ChatRoom.ChatRoomId, chatRoomId)).flatMap { room =>
      tryo {
        room.delete_!
      }
    }
  }

  override def refreshJoiningKey(chatRoomId: String): Box[ChatRoomTrait] = {
    ChatRoom.find(By(ChatRoom.ChatRoomId, chatRoomId)).flatMap { room =>
      tryo {
        room.JoiningKey(generateUUID()).saveMe()
      }
    }
  }

  override def getOrCreateDefaultRoom(): Box[ChatRoomTrait] = {
    getChatRoomByBankIdAndName("", "general") match {
      case Full(room) => Full(room)
      case _ =>
        tryo {
          // "system" here is a sentinel, not a real user_id — the default room is
          // auto-provisioned and has no human creator. Every other caller passes a real user_id.
          ChatRoom.create
            .BankId("")
            .Name("general")
            .Description("Default system-wide chat room for all users")
            .CreatedByUserId("system")
            .IsOpenRoom(true)
            .IsArchived(false)
            .saveMe()
        }
    }
  }
}

class ChatRoom extends ChatRoomTrait with LongKeyedMapper[ChatRoom] with IdPK with CreatedUpdated {

  def getSingleton = ChatRoom

  object ChatRoomId extends MappedUUID(this)
  object BankId extends MappedString(this, 255)
  object Name extends MappedString(this, 255)
  object Description extends MappedText(this)
  object JoiningKey extends MappedUUID(this)
  object CreatedByUserId extends MappedString(this, 36)
  object IsOpenRoom extends MappedBoolean(this)
  object IsArchived extends MappedBoolean(this)
  object LastMessageAt extends MappedDateTime(this)
  object LastMessagePreview extends MappedString(this, 100)
  object LastMessageSenderUsername extends MappedString(this, 255)

  override def chatRoomId: String = ChatRoomId.get
  override def bankId: String = BankId.get
  override def name: String = Name.get
  override def description: String = Description.get
  override def joiningKey: String = JoiningKey.get
  override def createdByUserId: String = CreatedByUserId.get
  override def isOpenRoom: Boolean = IsOpenRoom.get
  override def isArchived: Boolean = IsArchived.get
  override def lastMessageAt: Option[Date] = Option(LastMessageAt.get)
  override def lastMessagePreview: String = LastMessagePreview.get
  override def lastMessageSenderUsername: String = LastMessageSenderUsername.get
  override def createdDate: Date = createdAt.get
  override def updatedDate: Date = updatedAt.get
}

object ChatRoom extends ChatRoom with LongKeyedMetaMapper[ChatRoom] {
  override def dbTableName = "ChatRoom"
  override def dbIndexes = UniqueIndex(ChatRoomId) :: UniqueIndex(BankId, Name) :: Index(BankId) :: super.dbIndexes
}
