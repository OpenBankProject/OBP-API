package code.util

/**
 * The character class both chat and signal content policies are built on:
 * C0 controls except \t \n \r, DEL + C1 controls, and the Unicode bidi
 * override/isolate/mark characters ("Trojan Source" family). None have a
 * legitimate use in user- or agent-supplied text, and the bidi ones can
 * visually reverse text to disguise what a URL or name says.
 *
 * Chat STRIPS these at ingest (humans typing — be forgiving, content is
 * rendered); signal REJECTS messages containing them (machines publishing —
 * payloads must be stored verbatim or refused, never silently rewritten).
 * See ChatContentPolicy and SignalContentPolicy for the two applications.
 */
object DangerousCharacters {

  val Pattern: String =
    "[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F-\\u009F\\u061C\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]"

  private val compiledPattern = java.util.regex.Pattern.compile(Pattern)

  def strip(content: String): String = content.replaceAll(Pattern, "")

  def containsAny(content: String): Boolean = compiledPattern.matcher(content).find()
}
