package code.chat

import code.api.util.APIUtil

import scala.util.Try

/**
 * Content policy for chat messages, applied wherever message content enters
 * (create, edit, thread reply) — see also ChatLinkPolicy for the link-host
 * whitelist.
 */
object ChatContentPolicy {

  /** Maximum accepted content length in characters (prop chat.max_message_length). */
  def maxContentLength: Int =
    APIUtil.getPropsValue("chat.max_message_length")
      .flatMap(v => Try(v.trim.toInt).toOption.filter(_ > 0))
      .getOrElse(10000)

  // Character class shared with SignalContentPolicy — see
  // code.util.DangerousCharacters for the rationale and the strip-vs-reject
  // asymmetry between chat and signal.
  def stripDangerousCharacters(content: String): String =
    code.util.DangerousCharacters.strip(content)
}
