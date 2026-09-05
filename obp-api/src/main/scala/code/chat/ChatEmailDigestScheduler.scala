package code.chat

import code.actorsystem.ObpActorSystem
import code.api.util.{APIUtil, CommonsEmailWrapper}
import code.model.dataAccess.AuthUser
import code.users.Users
import code.util.Helper.MdcLoggable

import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Emails each user an occasional digest of chat messages they have not read.
 *
 * Design (deliberately NOT the transactional message outbox): an outbox row
 * freezes a payload that must be delivered at-least-once. A digest is the
 * opposite — the email is COMPUTED AT SEND TIME from current state (unread
 * counts vs read markers), coalescing everything pending into one mail, and a
 * missed tick heals itself on the next one because the state is still there.
 *
 * Per user, per pass:
 *  - skip when they read anything recently (active users never get emailed);
 *  - skip when the last digest was less than the minimum interval ago;
 *  - collect rooms with unread messages, but send only when something is NEW
 *    since the last digest — an unchanged backlog is never re-notified;
 *  - the email carries metadata only (room names and counts, no message
 *    content) — email is the least-controlled channel this data would touch.
 *
 * Props:
 *  - chat.email_digest_enabled (default false) — master switch, read in Boot.
 *  - chat.email_digest_tick_seconds (default 300) — pass frequency.
 *  - chat.email_digest_min_interval_minutes (default 60) — per-user email cap.
 *  - chat.email_digest_active_grace_minutes (default 10) — "recently active"
 *    window; reading any room within it suppresses the digest.
 */
object ChatEmailDigestScheduler extends MdcLoggable {

  private def minIntervalMillis: Long =
    APIUtil.getPropsAsLongValue("chat.email_digest_min_interval_minutes", 60L) * 60L * 1000L
  private def activeGraceMillis: Long =
    APIUtil.getPropsAsLongValue("chat.email_digest_active_grace_minutes", 10L) * 60L * 1000L

  def start(tickSeconds: Long): Unit = {
    implicit val executor = ObpActorSystem.localActorSystem.dispatcher
    ObpActorSystem.localActorSystem.scheduler.schedule(
      initialDelay = scala.concurrent.duration.Duration(tickSeconds, TimeUnit.SECONDS),
      interval = scala.concurrent.duration.Duration(tickSeconds, TimeUnit.SECONDS),
      runnable = new Runnable {
        def run(): Unit =
          try digestOnePass()
          catch { case e: Throwable => logger.error("chat email digest pass failed", e) }
      }
    )
    logger.info(s"chat email digest scheduler started (tick ${tickSeconds}s)")
  }

  private case class RoomDigest(name: String, totalUnread: Long, newSinceLastDigest: Long)

  def digestOnePass(): Unit = {
    val now = new Date()
    Participant.findAll().groupBy(_.userId).foreach { case (userId, memberships) =>
      try maybeSendDigest(userId, memberships, now)
      catch { case e: Throwable => logger.error(s"chat email digest for user $userId failed", e) }
    }
  }

  private def maybeSendDigest(userId: String, memberships: List[Participant], now: Date): Unit = {
    // Reading anywhere recently is our presence proxy — active users get nothing.
    val lastReadTimes = memberships.flatMap(m => Option(m.lastReadAt)).map(_.getTime)
    if (lastReadTimes.nonEmpty && now.getTime - lastReadTimes.max < activeGraceMillis) return

    val lastNotified = ChatEmailDigestState.lastNotifiedAt(userId)
    if (lastNotified.exists(at => now.getTime - at.getTime < minIntervalMillis)) return

    val provider = ChatMessageTrait.chatMessageProvider.vend
    val rooms = memberships.flatMap { membership =>
      val readAt = Option(membership.lastReadAt).getOrElse(APIUtil.theEpochTime)
      val totalUnread = provider.getUnreadCount(membership.chatRoomId, userId, readAt).openOr(0L)
      if (totalUnread <= 0) None
      else {
        // "New" means since whichever is later: the last digest or the last read.
        val freshSince = lastNotified.filter(_.after(readAt)).getOrElse(readAt)
        val fresh = provider.getUnreadCount(membership.chatRoomId, userId, freshSince).openOr(0L)
        val roomName = ChatRoomTrait.chatRoomProvider.vend
          .getChatRoom(membership.chatRoomId).map(_.name).openOr(membership.chatRoomId)
        Some(RoomDigest(roomName, totalUnread, fresh))
      }
    }
    // Unread rooms exist but nothing is new since the last digest → stay silent.
    if (rooms.isEmpty || rooms.forall(_.newSinceLastDigest <= 0)) return

    Users.users.vend.getUserByUserId(userId).foreach { user =>
      val toAddress = user.emailAddress
      if (toAddress == null || toAddress.trim.isEmpty) return

      // Only email addresses their owner has confirmed. Local accounts record
      // this on AuthUser.validated. Accounts from an external identity
      // provider (Google, Keycloak, ...) are assumed validated — the provider
      // verified the email before ever issuing tokens for it.
      val isLocalUser = user.provider == code.api.Constant.localIdentityProvider
      val emailValidated = !isLocalUser || AuthUser
        .findByResourceUserPrimaryKey(user.userPrimaryKey.value)
        .map(_.validated)
        .getOrElse(false)
      if (!emailValidated) {
        logger.debug(s"chat digest skipped for user $userId: email not validated")
        return
      }

      val totalCount = rooms.map(_.totalUnread).sum
      val roomLines = rooms
        .sortBy(-_.totalUnread)
        .map(r => s"  - ${r.name}: ${r.totalUnread} unread")
        .mkString("\n")
      val portalLink = APIUtil.getPortalUrl.map(url => s"\nRead them at $url/user/chat\n").openOr("")
      val body =
        s"""You have $totalCount unread chat message(s):
           |
           |$roomLines
           |$portalLink
           |You receive at most one of these emails per ${minIntervalMillis / 60000} minutes,
           |and none while you are actively using the chat.
           |""".stripMargin

      val fromAddress = APIUtil.getPropsValue("mail.users.userinfo.sender.address", "noreply@example.com")
      CommonsEmailWrapper.sendTextEmailEither(
        CommonsEmailWrapper.EmailContent(
          from = fromAddress,
          to = List(toAddress),
          subject = s"You have $totalCount unread chat message(s)",
          textContent = Some(body)
        )
      ) match {
        case Right(_) =>
          ChatEmailDigestState.recordNotified(userId, now)
          logger.debug(s"chat digest sent to user $userId ($totalCount unread in ${rooms.size} rooms)")
        case Left(e) =>
          // last_notified_at is NOT stamped — the next pass retries.
          logger.error(s"chat digest email to user $userId failed: ${e.getMessage}")
      }
    }
  }
}
