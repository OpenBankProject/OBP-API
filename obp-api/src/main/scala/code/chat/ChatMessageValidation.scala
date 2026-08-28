package code.chat

/**
 * Referential integrity checks for chat message input — the ids and enums a
 * message carries must make sense within the room it is posted to. See also
 * ChatContentPolicy (length, characters) and ChatLinkPolicy (link hosts).
 */
object ChatMessageValidation {

  /** message_type values the chat endpoints accept. */
  val AllowedMessageTypes: Set[String] = Set("text", "system")

  def isAllowedMessageType(messageType: Option[String]): Boolean =
    messageType.forall(AllowedMessageTypes.contains)

  /**
   * Mentioned user ids that are not participants of the room. In open rooms
   * ChatPermissions treats any user as an implicit participant, so mentions
   * are unrestricted there by design.
   */
  def nonParticipantMentions(chatRoomId: String, mentionedUserIds: List[String]): List[String] =
    mentionedUserIds.distinct.filter(userId =>
      ChatPermissions.isParticipant(chatRoomId, userId).isEmpty)

  /** True when the referenced message id is empty or names a message in this room. */
  def referenceInRoom(chatRoomId: String, messageId: String): Boolean =
    messageId.isEmpty ||
      ChatMessageTrait.chatMessageProvider.vend.getMessage(messageId).exists(_.chatRoomId == chatRoomId)
}
