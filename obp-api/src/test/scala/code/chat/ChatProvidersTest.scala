package code.chat

import java.util.{Date, UUID}

import code.setup.ServerSetup

/**
 * Characterization test for the four chat stores: rooms, participants, messages, reactions.
 *
 * The group had no direct coverage — the endpoints above it are commented out, so nothing exercised
 * these providers at all. Written against the Lift Mapper implementation first and confirmed green
 * there, so it pins existing behaviour rather than describing the Doobie rewrite.
 *
 * Deliberately uses only the provider interfaces, which both implementations share. A test that
 * reached for an entity method one implementation lacks could not have been run against both, and
 * so could not have served as a baseline.
 *
 * What it pins, beyond plain round-tripping:
 *   - the unique indexes that carry behaviour: repeated reaction, repeated participant, and
 *     get-or-create of the default room;
 *   - the comma-joined permission / mention columns, including the empty case, which is stored as
 *     an empty string and must read back as Nil rather than List("");
 *   - the room list for a user being the union of explicitly-joined rooms and open rooms, deduped;
 *   - the 100-character truncation of the denormalised last-message preview;
 *   - soft deletion leaving the message row in place.
 */
class ChatProvidersTest extends ServerSetup {

  private val rooms = MappedChatRoomProvider
  private val participants = MappedParticipantProvider
  private val messages = MappedChatMessageProvider
  private val reactions = MappedReactionProvider

  private def uniq(prefix: String): String = prefix + "_" + UUID.randomUUID.toString.take(8)

  // Helpers live at class level: a def inside a feature block is not valid Scala.
  private def messageIn(room: ChatRoomTrait): String =
    messages.createMessage(room.chatRoomId, uniq("sender"), uniq("consumer"), "reactable",
      "text", Nil, "", "").openOrThrowException("expected the message").chatMessageId

  private def newRoom(bankId: String, name: String): ChatRoomTrait =
    rooms.createChatRoom(bankId, name, "a room", uniq("creator"))
      .openOrThrowException("expected the room just created")

  Feature("chat room storage") {

    Scenario("a created room round-trips and is reachable by id, by (bank, name) and by joining key") {
      val bankId = uniq("bank")
      val name = uniq("room")
      val created = newRoom(bankId, name)

      created.bankId should equal(bankId)
      created.name should equal(name)
      created.description should equal("a room")
      created.isArchived should equal(false)
      created.isOpenRoom should equal(false)
      withClue("a joining key is generated on create, not supplied: ") {
        created.joiningKey.nonEmpty should equal(true)
      }
      withClue("no message has arrived yet, so there is no last-message timestamp: ") {
        created.lastMessageAt should equal(None)
      }

      rooms.getChatRoom(created.chatRoomId)
        .openOrThrowException("expected lookup by id").name should equal(name)
      rooms.getChatRoomByBankIdAndName(bankId, name)
        .openOrThrowException("expected lookup by bank and name").chatRoomId should equal(created.chatRoomId)
      rooms.getChatRoomByJoiningKey(created.joiningKey)
        .openOrThrowException("expected lookup by joining key").chatRoomId should equal(created.chatRoomId)
    }

    Scenario("name and description update independently of each other") {
      val room = newRoom(uniq("bank"), uniq("room"))

      val renamed = rooms.updateChatRoom(room.chatRoomId, Some("new name"), None)
        .openOrThrowException("expected the updated room")
      renamed.name should equal("new name")
      withClue("description was not supplied, so it must be left alone: ") {
        renamed.description should equal("a room")
      }

      val redescribed = rooms.updateChatRoom(room.chatRoomId, None, Some("new description"))
        .openOrThrowException("expected the updated room")
      redescribed.name should equal("new name")
      redescribed.description should equal("new description")
    }

    Scenario("the last-message preview is truncated to 100 characters") {
      val room = newRoom(uniq("bank"), uniq("room"))
      val longPreview = "x" * 250
      val at = new Date()

      val updated = rooms.updateLastMessageInfo(room.chatRoomId, at, longPreview, "sender-name")
        .openOrThrowException("expected the updated room")

      withClue("the preview column is 100 wide, so the write must truncate rather than fail: ") {
        updated.lastMessagePreview.length should equal(100)
      }
      updated.lastMessageSenderUsername should equal("sender-name")
      updated.lastMessageAt.isDefined should equal(true)
    }

    Scenario("archiving and refreshing the joining key each change exactly one field") {
      val room = newRoom(uniq("bank"), uniq("room"))
      val originalKey = room.joiningKey

      val archived = rooms.archiveChatRoom(room.chatRoomId)
        .openOrThrowException("expected the archived room")
      archived.isArchived should equal(true)
      archived.joiningKey should equal(originalKey)

      val refreshed = rooms.refreshJoiningKey(room.chatRoomId)
        .openOrThrowException("expected the room with a new joining key")
      refreshed.joiningKey should not equal originalKey
      withClue("the old key must stop resolving once it is replaced: ") {
        rooms.getChatRoomByJoiningKey(originalKey).isDefined should equal(false)
      }
    }

    Scenario("a deleted room stops resolving") {
      val room = newRoom(uniq("bank"), uniq("room"))
      rooms.deleteChatRoom(room.chatRoomId).openOrThrowException("expected the delete to report")
      rooms.getChatRoom(room.chatRoomId).isDefined should equal(false)
    }

    Scenario("a user's room list is the union of joined rooms and open rooms, deduped") {
      val bankId = uniq("bank")
      val userId = uniq("user")

      val joined = newRoom(bankId, uniq("joined"))
      val open = newRoom(bankId, uniq("open"))
      val other = newRoom(bankId, uniq("other"))
      rooms.setIsOpenRoom(open.chatRoomId, true).openOrThrowException("expected the open room")
      participants.addParticipant(joined.chatRoomId, userId, uniq("consumer"), List("read"), "")
        .openOrThrowException("expected the participant")
      // Also join the open room, so the union has an overlap to dedupe.
      participants.addParticipant(open.chatRoomId, userId, uniq("consumer"), List("read"), "")
        .openOrThrowException("expected the participant")

      val visible = rooms.getChatRoomsByBankIdForUser(bankId, userId)
        .openOrThrowException("expected the room list").map(_.chatRoomId)

      visible should contain(joined.chatRoomId)
      visible should contain(open.chatRoomId)
      withClue("a room the user never joined and which is not open must not appear: ") {
        visible should not contain other.chatRoomId
      }
      withClue("the open room is reachable two ways but must be listed once: ") {
        visible.count(_ == open.chatRoomId) should equal(1)
      }
    }

    Scenario("a user with no rooms at all gets an empty list rather than every room") {
      val bankId = uniq("bank")
      newRoom(bankId, uniq("room"))
      // Pins the empty-id-list case: Mapper's ByList with no ids rendered "0 = 1", i.e. no rows —
      // not "no filter", which would have returned every room in the bank.
      rooms.getChatRoomsByBankIdForUser(bankId, uniq("stranger"))
        .openOrThrowException("expected an empty room list") should equal(Nil)
    }

    Scenario("searching by exact participant set excludes open rooms") {
      val bankId = uniq("bank")
      val me = uniq("me")
      val you = uniq("you")

      val pair = newRoom(bankId, uniq("pair"))
      val openPair = newRoom(bankId, uniq("openpair"))
      rooms.setIsOpenRoom(openPair.chatRoomId, true).openOrThrowException("expected the open room")
      List(pair, openPair).foreach { room =>
        participants.addParticipant(room.chatRoomId, me, uniq("consumer"), Nil, "")
          .openOrThrowException("expected the participant")
        participants.addParticipant(room.chatRoomId, you, uniq("consumer"), Nil, "")
          .openOrThrowException("expected the participant")
      }

      val exact = rooms.searchChatRoomsForUserWithParticipants(me, List(you), exactParticipants = true)
        .openOrThrowException("expected the search result").map(_.chatRoomId)
      exact should contain(pair.chatRoomId)
      withClue("an open room's participant set is 'everyone', so an exact match is meaningless: ") {
        exact should not contain openPair.chatRoomId
      }

      val inexact = rooms.searchChatRoomsForUserWithParticipants(me, List(you), exactParticipants = false)
        .openOrThrowException("expected the search result").map(_.chatRoomId)
      inexact should contain(pair.chatRoomId)
      inexact should contain(openPair.chatRoomId)
    }

    Scenario("the default room is created once and then returned") {
      val first = rooms.getOrCreateDefaultRoom().openOrThrowException("expected the default room")
      val second = rooms.getOrCreateDefaultRoom().openOrThrowException("expected the default room")
      first.name should equal("general")
      withClue("get-or-create must resolve to the same row rather than creating a second: ") {
        second.chatRoomId should equal(first.chatRoomId)
      }
    }
  }

  Feature("participant storage") {

    Scenario("a participant round-trips, including the comma-joined permission column") {
      val room = newRoom(uniq("bank"), uniq("room"))
      val userId = uniq("user")
      val consumerId = uniq("consumer")

      val added = participants.addParticipant(room.chatRoomId, userId, consumerId,
        List("read", "write"), "https://example.com/hook")
        .openOrThrowException("expected the participant just added")

      added.userId should equal(userId)
      added.consumerId should equal(consumerId)
      added.permissions should equal(List("read", "write"))
      added.webhookUrl should equal("https://example.com/hook")
      added.isMuted should equal(false)

      val fetched = participants.getParticipant(room.chatRoomId, userId)
        .openOrThrowException("expected lookup by room and user")
      withClue("permissions are stored comma-joined in one column and must survive the round trip: ") {
        fetched.permissions should equal(List("read", "write"))
      }
      participants.getParticipantByConsumerId(room.chatRoomId, consumerId)
        .openOrThrowException("expected lookup by consumer id").userId should equal(userId)
    }

    Scenario("an empty permission list reads back as empty, not as one blank permission") {
      val room = newRoom(uniq("bank"), uniq("room"))
      val userId = uniq("user")
      participants.addParticipant(room.chatRoomId, userId, uniq("consumer"), Nil, "")
        .openOrThrowException("expected the participant")

      withClue("joining Nil gives \"\", and splitting \"\" must not yield List(\"\"): ") {
        participants.getParticipant(room.chatRoomId, userId)
          .openOrThrowException("expected the participant").permissions should equal(Nil)
      }
    }

    Scenario("permissions, webhook, last-read and muted each update in place") {
      val room = newRoom(uniq("bank"), uniq("room"))
      val userId = uniq("user")
      participants.addParticipant(room.chatRoomId, userId, uniq("consumer"), List("read"), "")
        .openOrThrowException("expected the participant")

      participants.updateParticipantPermissions(room.chatRoomId, userId, List("read", "write", "admin"))
        .openOrThrowException("expected the updated participant")
        .permissions should equal(List("read", "write", "admin"))

      participants.updateWebhookUrl(room.chatRoomId, userId, "https://example.com/new")
        .openOrThrowException("expected the updated participant")
        .webhookUrl should equal("https://example.com/new")

      participants.updateMuted(room.chatRoomId, userId, true)
        .openOrThrowException("expected the updated participant").isMuted should equal(true)

      participants.updateLastReadAt(room.chatRoomId, userId)
        .openOrThrowException("expected the updated participant")

      withClue("updating one field must not disturb the others: ") {
        val after = participants.getParticipant(room.chatRoomId, userId)
          .openOrThrowException("expected the participant")
        after.permissions should equal(List("read", "write", "admin"))
        after.webhookUrl should equal("https://example.com/new")
        after.isMuted should equal(true)
      }
    }

    Scenario("joining the same room twice is rejected") {
      val room = newRoom(uniq("bank"), uniq("room"))
      val userId = uniq("user")
      participants.addParticipant(room.chatRoomId, userId, uniq("consumer"), Nil, "")
        .openOrThrowException("expected the first join to succeed")

      withClue("the unique index on (chatroomid, userid) is what keeps membership single-valued: ") {
        participants.addParticipant(room.chatRoomId, userId, uniq("consumer"), Nil, "")
          .isDefined should equal(false)
      }
      participants.getParticipants(room.chatRoomId)
        .openOrThrowException("expected the participant list").size should equal(1)
    }

    Scenario("removing a participant leaves the other rooms alone") {
      val roomA = newRoom(uniq("bank"), uniq("rooma"))
      val roomB = newRoom(uniq("bank"), uniq("roomb"))
      val userId = uniq("user")
      participants.addParticipant(roomA.chatRoomId, userId, uniq("consumer"), Nil, "")
        .openOrThrowException("expected the participant")
      participants.addParticipant(roomB.chatRoomId, userId, uniq("consumer"), Nil, "")
        .openOrThrowException("expected the participant")

      participants.removeParticipant(roomA.chatRoomId, userId)
        .openOrThrowException("expected the removal to report")

      participants.getParticipant(roomA.chatRoomId, userId).isDefined should equal(false)
      participants.getParticipant(roomB.chatRoomId, userId).isDefined should equal(true)
      participants.getParticipantRoomsByUserId(userId)
        .openOrThrowException("expected the room list").map(_.chatRoomId) should equal(List(roomB.chatRoomId))
    }
  }

  Feature("chat message storage") {

    Scenario("a created message round-trips, including the comma-joined mention column") {
      val room = newRoom(uniq("bank"), uniq("room"))
      val sender = uniq("sender")
      val mentioned = uniq("mentioned")

      val created = messages.createMessage(room.chatRoomId, sender, uniq("consumer"), "hello",
        "text", List(mentioned), "", "")
        .openOrThrowException("expected the message just created")

      created.chatRoomId should equal(room.chatRoomId)
      created.senderUserId should equal(sender)
      created.content should equal("hello")
      created.messageType should equal("text")
      created.mentionedUserIds should equal(List(mentioned))
      created.isDeleted should equal(false)
      created.chatMessageId.nonEmpty should equal(true)

      messages.getMessage(created.chatMessageId)
        .openOrThrowException("expected lookup by message id").content should equal("hello")
    }

    Scenario("no mentions reads back as empty, not as one blank mention") {
      val room = newRoom(uniq("bank"), uniq("room"))
      val created = messages.createMessage(room.chatRoomId, uniq("sender"), uniq("consumer"),
        "no mentions", "text", Nil, "", "")
        .openOrThrowException("expected the message")

      messages.getMessage(created.chatMessageId)
        .openOrThrowException("expected the message").mentionedUserIds should equal(Nil)
    }

    Scenario("messages come back oldest first and honour limit and offset") {
      val room = newRoom(uniq("bank"), uniq("room"))
      val sender = uniq("sender")
      val contents = List("one", "two", "three", "four")
      contents.foreach { c =>
        messages.createMessage(room.chatRoomId, sender, uniq("consumer"), c, "text", Nil, "", "")
          .openOrThrowException("expected the message")
      }
      val wide = new Date(0)
      val far = new Date(System.currentTimeMillis() + 60000)

      val all = messages.getMessages(room.chatRoomId, 100, 0, wide, far)
        .openOrThrowException("expected the message page").map(_.content)
      all should equal(contents)

      val page = messages.getMessages(room.chatRoomId, 2, 1, wide, far)
        .openOrThrowException("expected the message page").map(_.content)
      page should equal(List("two", "three"))
    }

    Scenario("the date window excludes messages outside it") {
      val room = newRoom(uniq("bank"), uniq("room"))
      messages.createMessage(room.chatRoomId, uniq("sender"), uniq("consumer"), "now", "text",
        Nil, "", "").openOrThrowException("expected the message")

      val longAgoStart = new Date(0)
      val longAgoEnd = new Date(1000)
      messages.getMessages(room.chatRoomId, 100, 0, longAgoStart, longAgoEnd)
        .openOrThrowException("expected an empty page") should equal(Nil)
    }

    Scenario("thread replies are scoped to their thread") {
      val room = newRoom(uniq("bank"), uniq("room"))
      val threadId = uniq("thread")
      val otherThreadId = uniq("otherthread")
      messages.createMessage(room.chatRoomId, uniq("sender"), uniq("consumer"), "reply one",
        "text", Nil, "", threadId).openOrThrowException("expected the message")
      messages.createMessage(room.chatRoomId, uniq("sender"), uniq("consumer"), "reply two",
        "text", Nil, "", threadId).openOrThrowException("expected the message")
      messages.createMessage(room.chatRoomId, uniq("sender"), uniq("consumer"), "elsewhere",
        "text", Nil, "", otherThreadId).openOrThrowException("expected the message")

      messages.getThreadReplies(threadId)
        .openOrThrowException("expected the thread").map(_.content) should equal(List("reply one", "reply two"))
    }

    Scenario("mentions for a user come back newest first") {
      val room = newRoom(uniq("bank"), uniq("room"))
      val mentioned = uniq("mentioned")
      messages.createMessage(room.chatRoomId, uniq("sender"), uniq("consumer"), "first",
        "text", List(mentioned), "", "").openOrThrowException("expected the message")
      messages.createMessage(room.chatRoomId, uniq("sender"), uniq("consumer"), "second",
        "text", List(mentioned), "", "").openOrThrowException("expected the message")
      messages.createMessage(room.chatRoomId, uniq("sender"), uniq("consumer"), "unrelated",
        "text", Nil, "", "").openOrThrowException("expected the message")

      messages.getMentionsForUser(mentioned, 100, 0)
        .openOrThrowException("expected the mentions").map(_.content) should equal(List("second", "first"))
    }

    Scenario("unread counts exclude the reader's own messages") {
      val room = newRoom(uniq("bank"), uniq("room"))
      val me = uniq("me")
      val them = uniq("them")
      val since = new Date(System.currentTimeMillis() - 1000)

      messages.createMessage(room.chatRoomId, them, uniq("consumer"), "theirs one", "text",
        Nil, "", "").openOrThrowException("expected the message")
      messages.createMessage(room.chatRoomId, them, uniq("consumer"), "theirs two", "text",
        List(me), "", "").openOrThrowException("expected the message")
      messages.createMessage(room.chatRoomId, me, uniq("consumer"), "mine", "text",
        List(me), "", "").openOrThrowException("expected the message")

      withClue("a reader's own messages are never unread to them: ") {
        messages.getUnreadCount(room.chatRoomId, me, since)
          .openOrThrowException("expected the unread count") should equal(2L)
      }
      messages.getUnreadMentionCount(room.chatRoomId, me, since)
        .openOrThrowException("expected the unread mention count") should equal(1L)
    }

    Scenario("editing changes the content and soft deletion keeps the row") {
      val room = newRoom(uniq("bank"), uniq("room"))
      val created = messages.createMessage(room.chatRoomId, uniq("sender"), uniq("consumer"),
        "original", "text", Nil, "", "").openOrThrowException("expected the message")

      messages.updateMessage(created.chatMessageId, "edited")
        .openOrThrowException("expected the edited message").content should equal("edited")

      val deleted = messages.softDeleteMessage(created.chatMessageId)
        .openOrThrowException("expected the deleted message")
      deleted.isDeleted should equal(true)
      withClue("soft deletion must leave the row so threads and reactions keep their anchor: ") {
        messages.getMessage(created.chatMessageId)
          .openOrThrowException("expected the row to still exist").isDeleted should equal(true)
      }
    }
  }

  Feature("reaction storage") {

    Scenario("a reaction round-trips and is reachable by its triple") {
      val room = newRoom(uniq("bank"), uniq("room"))
      val messageId = messageIn(room)
      val userId = uniq("user")

      val added = reactions.addReaction(messageId, userId, ":thumbsup:")
        .openOrThrowException("expected the reaction just added")
      added.chatMessageId should equal(messageId)
      added.userId should equal(userId)
      added.emoji should equal(":thumbsup:")
      added.reactionId.nonEmpty should equal(true)

      reactions.getReaction(messageId, userId, ":thumbsup:")
        .openOrThrowException("expected lookup by message, user and emoji")
        .reactionId should equal(added.reactionId)
    }

    Scenario("the same reaction twice is rejected") {
      val room = newRoom(uniq("bank"), uniq("room"))
      val messageId = messageIn(room)
      val userId = uniq("user")
      reactions.addReaction(messageId, userId, ":thumbsup:")
        .openOrThrowException("expected the first reaction to succeed")

      withClue("the unique index on (chatmessageid, userid, emoji) is what makes reacting idempotent: ") {
        reactions.addReaction(messageId, userId, ":thumbsup:").isDefined should equal(false)
      }
      reactions.getReactions(messageId)
        .openOrThrowException("expected the reaction list").size should equal(1)
    }

    Scenario("a user may add different emoji to the same message") {
      val room = newRoom(uniq("bank"), uniq("room"))
      val messageId = messageIn(room)
      val userId = uniq("user")
      reactions.addReaction(messageId, userId, ":thumbsup:").openOrThrowException("expected the reaction")
      reactions.addReaction(messageId, userId, ":tada:").openOrThrowException("expected the reaction")

      reactions.getReactions(messageId)
        .openOrThrowException("expected the reaction list").map(_.emoji).toSet should
        equal(Set(":thumbsup:", ":tada:"))
    }

    Scenario("reactions for several messages come back grouped by message") {
      val room = newRoom(uniq("bank"), uniq("room"))
      val messageA = messageIn(room)
      val messageB = messageIn(room)
      val unreacted = messageIn(room)
      reactions.addReaction(messageA, uniq("user"), ":thumbsup:").openOrThrowException("expected the reaction")
      reactions.addReaction(messageA, uniq("user"), ":tada:").openOrThrowException("expected the reaction")
      reactions.addReaction(messageB, uniq("user"), ":eyes:").openOrThrowException("expected the reaction")

      val grouped = reactions.getReactionsForMessages(List(messageA, messageB, unreacted))
        .openOrThrowException("expected the grouped reactions")

      grouped(messageA).size should equal(2)
      grouped(messageB).map(_.emoji) should equal(List(":eyes:"))
      withClue("a message with no reactions is absent from the map rather than mapped to Nil: ") {
        grouped.contains(unreacted) should equal(false)
      }
    }

    Scenario("an empty message-id list short-circuits to an empty map") {
      reactions.getReactionsForMessages(Nil)
        .openOrThrowException("expected an empty map") should equal(Map.empty)
    }

    Scenario("removing a reaction leaves the others on the message") {
      val room = newRoom(uniq("bank"), uniq("room"))
      val messageId = messageIn(room)
      val userId = uniq("user")
      reactions.addReaction(messageId, userId, ":thumbsup:").openOrThrowException("expected the reaction")
      reactions.addReaction(messageId, userId, ":tada:").openOrThrowException("expected the reaction")

      reactions.removeReaction(messageId, userId, ":thumbsup:")
        .openOrThrowException("expected the removal to report")

      reactions.getReaction(messageId, userId, ":thumbsup:").isDefined should equal(false)
      reactions.getReactions(messageId)
        .openOrThrowException("expected the reaction list").map(_.emoji) should equal(List(":tada:"))
    }
  }
}
