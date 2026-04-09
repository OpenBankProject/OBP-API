package code.chat

import java.util.Date
import code.util.MappedUUID
import net.liftweb.common.Box
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

object MappedReactionProvider extends ReactionProvider {

  override def addReaction(chatMessageId: String, userId: String, emoji: String): Box[ReactionTrait] = {
    tryo {
      Reaction.create
        .ChatMessageId(chatMessageId)
        .UserId(userId)
        .Emoji(emoji)
        .saveMe()
    }
  }

  override def removeReaction(chatMessageId: String, userId: String, emoji: String): Box[Boolean] = {
    Reaction.find(
      By(Reaction.ChatMessageId, chatMessageId),
      By(Reaction.UserId, userId),
      By(Reaction.Emoji, emoji)
    ).flatMap { r =>
      tryo {
        r.delete_!
      }
    }
  }

  override def getReactions(chatMessageId: String): Box[List[ReactionTrait]] = {
    tryo {
      Reaction.findAll(By(Reaction.ChatMessageId, chatMessageId))
    }
  }

  override def getReactionsForMessages(chatMessageIds: List[String]): Box[Map[String, List[ReactionTrait]]] = {
    tryo {
      if (chatMessageIds.isEmpty) Map.empty[String, List[ReactionTrait]]
      else {
        Reaction.findAll(ByList(Reaction.ChatMessageId, chatMessageIds))
          .groupBy(_.chatMessageId)
      }
    }
  }

  override def getReaction(chatMessageId: String, userId: String, emoji: String): Box[ReactionTrait] = {
    Reaction.find(
      By(Reaction.ChatMessageId, chatMessageId),
      By(Reaction.UserId, userId),
      By(Reaction.Emoji, emoji)
    )
  }
}

class Reaction extends ReactionTrait with LongKeyedMapper[Reaction] with IdPK with CreatedUpdated {

  def getSingleton = Reaction

  object ReactionId extends MappedUUID(this)
  object ChatMessageId extends MappedString(this, 36)
  object UserId extends MappedString(this, 36)
  object Emoji extends MappedString(this, 64)

  override def reactionId: String = ReactionId.get
  override def chatMessageId: String = ChatMessageId.get
  override def userId: String = UserId.get
  override def emoji: String = Emoji.get
  override def createdDate: Date = createdAt.get
}

object Reaction extends Reaction with LongKeyedMetaMapper[Reaction] {
  override def dbTableName = "Reaction"
  override def dbIndexes = UniqueIndex(ReactionId) :: Index(ChatMessageId) :: UniqueIndex(ChatMessageId, UserId, Emoji) :: super.dbIndexes
}
