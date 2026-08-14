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

  // C0 controls except \t \n \r, DEL + C1 controls, and the Unicode bidi
  // override/isolate/mark characters ("Trojan Source" family): none have a
  // legitimate use in chat, and the bidi ones can visually reverse text to
  // disguise what a URL or name says.
  private val DangerousCharacters =
    "[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F-\\u009F\\u061C\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]"

  def stripDangerousCharacters(content: String): String =
    content.replaceAll(DangerousCharacters, "")
}
