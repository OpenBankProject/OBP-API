package code.obp.grpc.metricsstream

import code.obp.grpc.metricsstream.api.{MetricEvent, MetricsStreamProto}
import code.setup.ServerSetup
import org.json4s.native.JsonMethods.parse

/**
 * This suite checks the fields that the live metrics stream carries beyond the original 18:
 * forwarded_for, auth_type, certificate_trust and certificate_trust_detail. MetricEvent and its
 * descriptor are written by hand (no protoc plugin runs in the build), so a field number or name
 * that disagrees between them, or a field left out of the wire format, would not be caught by the
 * compiler. The suite also checks that the JSON payload WriteMetricUtil publishes is read into
 * those fields.
 */
class MetricEventFieldsTest extends ServerSetup {

  private val newFields = List(
    19 -> "forwarded_for",
    20 -> "auth_type",
    21 -> "certificate_trust",
    22 -> "certificate_trust_detail",
    23 -> "domain_api_url"
  )

  private val event = MetricEvent(
    url = "/obp/v6.0.0/banks",
    sourceIp = "203.0.113.9",
    consentReferenceId = "consent-1",
    forwardedFor = "203.0.113.9, 10.0.0.2, 10.0.0.3",
    authType = "OAuth2",
    certificateTrust = "forwarded",
    certificateTrustDetail = "CN=proxy,O=Example",
    domainApiUrl = "/carbon-registry/v1/activity?limit=10"
  )

  feature("MetricEvent fields 19 to 23") {

    scenario("the descriptor names them with the numbers the proto file gives them") {
      val descriptor = MetricsStreamProto.javaDescriptor.findMessageTypeByName("MetricEvent")
      newFields.foreach { case (number, name) =>
        descriptor.findFieldByNumber(number).getName shouldBe name
      }
    }

    scenario("they survive a round trip through the wire format") {
      MetricEvent.parseFrom(event.toByteArray) shouldBe event
    }

    scenario("getFieldByNumber returns each of them") {
      event.getFieldByNumber(19) shouldBe "203.0.113.9, 10.0.0.2, 10.0.0.3"
      event.getFieldByNumber(20) shouldBe "OAuth2"
      event.getFieldByNumber(21) shouldBe "forwarded"
      event.getFieldByNumber(22) shouldBe "CN=proxy,O=Example"
      event.getFieldByNumber(23) shouldBe "/carbon-registry/v1/activity?limit=10"
    }

    scenario("the published JSON payload is read into them") {
      val payload = parse(
        """{"url":"/obp/v6.0.0/banks","source_ip":"203.0.113.9","forwarded_for":"203.0.113.9, 10.0.0.2",
          |"auth_type":"Consent","certificate_trust":"direct","certificate_trust_detail":"",
          |"domain_api_url":"/carbon-registry/v1/activity"}""".stripMargin)
      val fromPayload = MetricsStreamServiceImpl.jsonToMetricEvent(payload)
      fromPayload.sourceIp shouldBe "203.0.113.9"
      fromPayload.forwardedFor shouldBe "203.0.113.9, 10.0.0.2"
      fromPayload.authType shouldBe "Consent"
      fromPayload.certificateTrust shouldBe "direct"
      fromPayload.certificateTrustDetail shouldBe ""
      fromPayload.domainApiUrl shouldBe "/carbon-registry/v1/activity"
    }
  }
}
