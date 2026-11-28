package code.api.util

import org.scalatest.GivenWhenThen
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.should.Matchers

/**
 * Mail is sent inside requests (the consent challenge, for one), so an SMTP server that accepts the connection and
 * then goes quiet must not hold the request, and its database connection, forever. Jakarta Mail waits without limit
 * unless the session says otherwise.
 */
class CommonsEmailWrapperTest extends AnyFeatureSpec with Matchers with GivenWhenThen {

  private val config = CommonsEmailWrapper.EmailConfig(smtpHost = "localhost", smtpPort = 1025, username = "", password = "")

  feature("SMTP sessions have timeouts") {
    scenario("connect, read and write are bounded by default") {
      val props = CommonsEmailWrapper.sessionProperties(config)
      for (key <- List("mail.smtp.connectiontimeout", "mail.smtp.timeout", "mail.smtp.writetimeout")) {
        val value = Option(props.getProperty(key))
        withClue(key) { value.map(_.toInt).getOrElse(0) should be > 0 }
      }
    }

    scenario("mail.smtp.timeout_millis sets the timeout, and a value Jakarta Mail would read as 'wait forever' does not") {
      CommonsEmailWrapper.timeoutMillisFrom(Some("2500")) shouldBe 2500
      for (unusable <- List(None, Some("0"), Some("-1"), Some("abc"), Some("")))
        withClue(unusable) { CommonsEmailWrapper.timeoutMillisFrom(unusable) shouldBe CommonsEmailWrapper.defaultTimeoutMillis }
    }

    scenario("the timeouts follow the configuration") {
      val props = CommonsEmailWrapper.sessionProperties(config.copy(timeoutMillis = 2500))
      props.getProperty("mail.smtp.connectiontimeout") shouldBe "2500"
      props.getProperty("mail.smtp.timeout") shouldBe "2500"
      props.getProperty("mail.smtp.writetimeout") shouldBe "2500"
    }
  }
}
