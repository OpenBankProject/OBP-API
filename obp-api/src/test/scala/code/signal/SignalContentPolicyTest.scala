package code.signal

import code.util.DangerousCharacters
import com.openbankproject.commons.util.JsonAliases
import org.json4s.JsonAST._
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.should.Matchers

class SignalContentPolicyTest extends AnyFeatureSpec with Matchers {

  // Built from code points so this source file itself stays free of literal
  // control/bidi bytes (which scanners rightly flag as Trojan Source).
  private val bidiOverride = 0x202E.toChar.toString // RIGHT-TO-LEFT OVERRIDE
  private val rightToLeftMark = 0x200F.toChar.toString
  private val nullControl = 0x0000.toChar.toString

  Feature("DangerousCharacters shared character class") {

    Scenario("containsAny detects bidi override and control characters") {
      DangerousCharacters.containsAny(s"invoice${bidiOverride}fdp.exe") should be(true)
      DangerousCharacters.containsAny(s"abc${nullControl}def") should be(true)
      DangerousCharacters.containsAny(rightToLeftMark) should be(true)
    }

    Scenario("containsAny accepts legitimate international text and whitespace") {
      DangerousCharacters.containsAny("Müller, Straße 12, São Paulo, 東京") should be(false)
      DangerousCharacters.containsAny("line one\nline two\ttabbed\r\n") should be(false)
      DangerousCharacters.containsAny("") should be(false)
    }

    Scenario("strip removes exactly the characters containsAny detects") {
      val dirty = s"a${bidiOverride}b${nullControl}c"
      val stripped = DangerousCharacters.strip(dirty)
      stripped should equal("abc")
      DangerousCharacters.containsAny(stripped) should be(false)
    }
  }

  Feature("SignalContentPolicy.containsDangerousCharacters walks parsed JSON") {

    Scenario("clean nested payload passes") {
      val json = JsonAliases.parse("""{"task":"settle","amounts":[1,2.5],"meta":{"note":"ok","done":true,"none":null}}""")
      SignalContentPolicy.containsDangerousCharacters(json) should be(false)
    }

    Scenario("dangerous character in a nested string value is detected") {
      val json = JObject(List("meta" -> JObject(List("note" -> JString(s"click${bidiOverride}here")))))
      SignalContentPolicy.containsDangerousCharacters(json) should be(true)
    }

    Scenario("dangerous character in an array element is detected") {
      val json = JArray(List(JString("fine"), JString(s"bad${nullControl}")))
      SignalContentPolicy.containsDangerousCharacters(json) should be(true)
    }

    Scenario("dangerous character in a field NAME is detected") {
      val json = JObject(List(s"na${bidiOverride}me" -> JString("value")))
      SignalContentPolicy.containsDangerousCharacters(json) should be(true)
    }

    Scenario("a JSON backslash-u escape in the raw body still parses to the dangerous character") {
      // The raw body below is pure ASCII on the wire ("\\u202e" is the
      // six-character escape sequence, not the code point); the check must
      // run post-parse or this slips through.
      val rawBody = "{\"note\":\"click\\u202ehere\"}"
      val json = JsonAliases.parse(rawBody)
      SignalContentPolicy.containsDangerousCharacters(json) should be(true)
    }

    Scenario("non-string primitives never trip the check") {
      SignalContentPolicy.containsDangerousCharacters(JInt(42)) should be(false)
      SignalContentPolicy.containsDangerousCharacters(JBool(true)) should be(false)
      SignalContentPolicy.containsDangerousCharacters(JNull) should be(false)
    }
  }

  Feature("SignalContentPolicy.maxPayloadLength") {
    Scenario("default is positive") {
      SignalContentPolicy.maxPayloadLength should be > 0
    }
  }
}
