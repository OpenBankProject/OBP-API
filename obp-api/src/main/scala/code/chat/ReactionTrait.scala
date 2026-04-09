package code.chat

import java.util.Date
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

object ReactionTrait extends SimpleInjector {
  val reactionProvider = new Inject(buildOne _) {}
  def buildOne: ReactionProvider = MappedReactionProvider
}

trait ReactionProvider {
  def addReaction(chatMessageId: String, userId: String, emoji: String): Box[ReactionTrait]
  def removeReaction(chatMessageId: String, userId: String, emoji: String): Box[Boolean]
  def getReactions(chatMessageId: String): Box[List[ReactionTrait]]
  def getReactionsForMessages(chatMessageIds: List[String]): Box[Map[String, List[ReactionTrait]]]
  def getReaction(chatMessageId: String, userId: String, emoji: String): Box[ReactionTrait]
}

trait ReactionTrait {
  def reactionId: String
  def chatMessageId: String
  def userId: String
  def emoji: String
  def createdDate: Date
}
