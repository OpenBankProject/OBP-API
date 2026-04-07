package code.chat

import java.util.Date
import code.util.MappedUUID
import net.liftweb.common.Box
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

object MappedParticipantProvider extends ParticipantProvider {

  override def addParticipant(
    chatRoomId: String,
    userId: String,
    consumerId: String,
    permissions: List[String],
    webhookUrl: String
  ): Box[ParticipantTrait] = {
    tryo {
      Participant.create
        .ChatRoomId(chatRoomId)
        .UserId(userId)
        .ConsumerId(consumerId)
        .Permissions(permissions.mkString(","))
        .WebhookUrl(webhookUrl)
        .JoinedAt(new Date())
        .LastReadAt(new Date())
        .IsMuted(false)
        .saveMe()
    }
  }

  override def getParticipant(chatRoomId: String, userId: String): Box[ParticipantTrait] = {
    Participant.find(
      By(Participant.ChatRoomId, chatRoomId),
      By(Participant.UserId, userId)
    )
  }

  override def getParticipantByConsumerId(chatRoomId: String, consumerId: String): Box[ParticipantTrait] = {
    Participant.find(
      By(Participant.ChatRoomId, chatRoomId),
      By(Participant.ConsumerId, consumerId)
    )
  }

  override def getParticipants(chatRoomId: String): Box[List[ParticipantTrait]] = {
    tryo {
      Participant.findAll(By(Participant.ChatRoomId, chatRoomId))
    }
  }

  override def getParticipantRoomsByUserId(userId: String): Box[List[ParticipantTrait]] = {
    tryo {
      Participant.findAll(By(Participant.UserId, userId))
    }
  }

  override def updateParticipantPermissions(
    chatRoomId: String,
    userId: String,
    permissions: List[String]
  ): Box[ParticipantTrait] = {
    Participant.find(
      By(Participant.ChatRoomId, chatRoomId),
      By(Participant.UserId, userId)
    ).flatMap { p =>
      tryo {
        p.Permissions(permissions.mkString(",")).saveMe()
      }
    }
  }

  override def updateWebhookUrl(
    chatRoomId: String,
    userId: String,
    webhookUrl: String
  ): Box[ParticipantTrait] = {
    Participant.find(
      By(Participant.ChatRoomId, chatRoomId),
      By(Participant.UserId, userId)
    ).flatMap { p =>
      tryo {
        p.WebhookUrl(webhookUrl).saveMe()
      }
    }
  }

  override def updateLastReadAt(chatRoomId: String, userId: String): Box[ParticipantTrait] = {
    Participant.find(
      By(Participant.ChatRoomId, chatRoomId),
      By(Participant.UserId, userId)
    ).flatMap { p =>
      tryo {
        p.LastReadAt(new Date()).saveMe()
      }
    }
  }

  override def updateMuted(chatRoomId: String, userId: String, isMuted: Boolean): Box[ParticipantTrait] = {
    Participant.find(
      By(Participant.ChatRoomId, chatRoomId),
      By(Participant.UserId, userId)
    ).flatMap { p =>
      tryo {
        p.IsMuted(isMuted).saveMe()
      }
    }
  }

  override def removeParticipant(chatRoomId: String, userId: String): Box[Boolean] = {
    Participant.find(
      By(Participant.ChatRoomId, chatRoomId),
      By(Participant.UserId, userId)
    ).flatMap { p =>
      tryo {
        p.delete_!
      }
    }
  }
}

class Participant extends ParticipantTrait with LongKeyedMapper[Participant] with IdPK {

  def getSingleton = Participant

  object ParticipantId extends MappedUUID(this)
  object ChatRoomId extends MappedString(this, 36)
  object UserId extends MappedString(this, 36)
  object ConsumerId extends MappedString(this, 36)
  object Permissions extends MappedText(this)
  object WebhookUrl extends MappedString(this, 1024)
  object JoinedAt extends MappedDateTime(this)
  object LastReadAt extends MappedDateTime(this)
  object IsMuted extends MappedBoolean(this)

  override def participantId: String = ParticipantId.get
  override def chatRoomId: String = ChatRoomId.get
  override def userId: String = UserId.get
  override def consumerId: String = ConsumerId.get
  override def permissions: List[String] = {
    val permsStr = Permissions.get
    if (permsStr == null || permsStr.isEmpty) List.empty
    else permsStr.split(",").map(_.trim).filter(_.nonEmpty).toList
  }
  override def webhookUrl: String = WebhookUrl.get
  override def joinedAt: Date = JoinedAt.get
  override def lastReadAt: Date = LastReadAt.get
  override def isMuted: Boolean = IsMuted.get
}

object Participant extends Participant with LongKeyedMetaMapper[Participant] {
  override def dbTableName = "Participant"
  override def dbIndexes = UniqueIndex(ParticipantId) :: Index(ChatRoomId) :: Index(UserId) :: UniqueIndex(ChatRoomId, UserId) :: super.dbIndexes
}
