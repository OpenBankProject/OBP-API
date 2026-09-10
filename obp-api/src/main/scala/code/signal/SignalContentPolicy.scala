package code.signal

import code.api.util.APIUtil
import code.util.DangerousCharacters
import org.json4s.JsonAST._

/**
 * Content policy for signal channel messages (Redis-backed agent-to-agent
 * coordination — see RedisMessaging and the /signal-channels endpoints).
 *
 * Signal payloads are machine-consumed data, so the policy differs from chat
 * on purpose: nothing is ever rewritten (agents may hash, sign, or
 * byte-compare payloads) — a message either passes verbatim or is rejected.
 * The dangerous-character check runs on the PARSED JSON, not the raw body:
 * a raw body carrying a bidi override as a JSON backslash-u escape is pure
 * ASCII on the wire but still parses to a string containing the override,
 * so a wire-level check would miss it.
 */
object SignalContentPolicy {

  /**
   * Maximum accepted publish request body length in characters
   * (prop messaging.channel.max.payload.length). Checked against the raw
   * body BEFORE JSON parsing, so an oversized body is refused without
   * paying the parse cost — the cap protects Redis memory and parser CPU.
   */
  def maxPayloadLength: Int =
    APIUtil.getPropsAsIntValue("messaging.channel.max.payload.length", 65536)

  /**
   * True when any string value or field name anywhere in `json` contains a
   * character from the shared dangerous set (control characters and the
   * Unicode bidi override family — see code.util.DangerousCharacters).
   */
  def containsDangerousCharacters(json: JValue): Boolean = json match {
    case JString(value) => DangerousCharacters.containsAny(value)
    case JObject(fields) => fields.exists { case (name, value) =>
      DangerousCharacters.containsAny(name) || containsDangerousCharacters(value)
    }
    case JArray(items) => items.exists(containsDangerousCharacters)
    case _ => false
  }
}
