package code.dynamicResourceDoc

import code.dynamicMessageDoc.DynamicMessageDoc
import code.setup.ServerSetup
import net.liftweb.common.Full

/**
 * A column a store is willing to WRITE as NULL has to be readable again.
 *
 * Mapper's MappedString wrote a null field as SQL NULL and read it straight back as null. These
 * stores kept the writing half - every free-text column is bound through Option, so a null field
 * still becomes SQL NULL - but read the same column as a bare String. Doobie's Get[String] throws
 * NonNullableColumnRead on a NULL, and it fails the whole query rather than the one row, so a
 * single doc with a null summary makes the entire listing endpoint 500.
 *
 * The insert paths here re-read the row they just wrote, so the write and the read disagree inside
 * one call: no legacy data is needed to hit it. A client that posts `"tags": null` (json4s extracts
 * a JSON null into a String field as null) is enough.
 */
class NullableColumnRoundTripTest extends ServerSetup {

  private def uniqueSuffix = code.api.util.APIUtil.generateUUID().take(8)

  override def beforeAll(): Unit = {
    super.beforeAll()
    DynamicResourceDoc.deleteAll()
    DynamicMessageDoc.deleteAll()
  }

  feature("a resource doc whose optional free-text columns are null") {

    scenario("survives the read-back inside insert") {
      val id = code.api.util.APIUtil.generateUUID()
      val inserted = DynamicResourceDoc.insert(
        dynamicResourceDocId = id,
        bankId = None,
        partialFunctionName = s"nullRoundTrip_$uniqueSuffix",
        requestVerb = "GET",
        requestUrl = s"/null-round-trip/$uniqueSuffix",
        summary = "a summary",
        description = "a description",
        exampleRequestBody = None,
        successResponseBody = None,
        // The three a caller most plausibly leaves out: Mapper stored each as NULL.
        errorResponseBodies = null,
        tags = null,
        roles = null,
        methodBody = "()",
        // provenance is not what this test exercises; it wants the NULL shape
        createdByUserId = None, methodBodyHash = None)

      inserted.tags should be(null)
      inserted.roles should be(null)
      inserted.errorResponseBodies should be(null)

      DynamicResourceDoc.findById(None, id) match {
        case Full(found) =>
          found.dynamicResourceDocId should equal(id)
          found.tags should be(null)
        case other => fail(s"the doc that was just inserted must be readable, got $other")
      }
    }

    scenario("does not take the listing down with it") {
      // Reading many rows is where this bites hardest: one NULL fails the whole query, so every
      // other doc becomes unreachable too.
      val id = code.api.util.APIUtil.generateUUID()
      DynamicResourceDoc.insert(
        dynamicResourceDocId = id,
        bankId = None,
        partialFunctionName = s"nullListing_$uniqueSuffix",
        requestVerb = "POST",
        requestUrl = s"/null-listing/$uniqueSuffix",
        summary = null,
        description = null,
        exampleRequestBody = None,
        successResponseBody = None,
        errorResponseBodies = "[]",
        tags = "[]",
        roles = "[]",
        methodBody = "()",
        // provenance is not what this test exercises; it wants the NULL shape
        createdByUserId = None, methodBodyHash = None)

      DynamicResourceDoc.findAll(None).map(_.dynamicResourceDocId) should contain(id)
    }
  }

  feature("a message doc whose optional free-text columns are null") {

    scenario("survives the read-back inside insert") {
      val id = code.api.util.APIUtil.generateUUID()
      val process = s"nullMessageDoc_$uniqueSuffix"
      DynamicMessageDoc.insert(
        dynamicMessageDocId = id,
        bankId = None,
        process = process,
        messageFormat = "KAFKA",
        description = null,
        outboundTopic = null,
        inboundTopic = null,
        exampleOutboundMessage = "{}",
        exampleInboundMessage = "{}",
        outboundAvroSchema = null,
        inboundAvroSchema = null,
        adapterImplementation = null,
        methodBody = "()",
        programmingLang = "Scala",
        // provenance is not what this test exercises; it wants the NULL shape
        createdByUserId = None, methodBodyHash = None)

      DynamicMessageDoc.findById(None, id) match {
        case Full(found) => found.process should equal(process)
        case other => fail(s"the message doc that was just inserted must be readable, got $other")
      }
    }
  }

  feature("a resource doc whose bodies are longer than a legacy varchar(255)") {

    // develop widens examplerequestbody, successresponsebody and errorresponsebodies with
    // MigrationOfDynamicResourceDocBodyFieldsLength, whose own comment says the value "routinely
    // exceeds varchar(255)". That migration reads Mapper metadata that does not exist here, so it
    // was deleted in the develop merge - and unlike the other two deleted migrations it had no
    // changeset written in its place, leaving the three columns at the baseline's VARCHAR(255).
    //
    // 255 is not a generous limit for a JSON response example: a handful of fields reaches it. The
    // failure is at write time ("Value too long for column" on H2, "value too long for type
    // character varying(255)" on Postgres), and the endpoints wrap the write, so a caller sees a
    // generic error rather than a length complaint.
    scenario("stores and reads back bodies well past 255 characters") {
      // Shaped like a real response example rather than one long run of 'x', so the failure is
      // the column width and not something about the content.
      val longBody =
        "{" + (1 to 40).map(i => s""""field_number_$i": "value_number_$i"""").mkString(", ") + "}"
      withClue("the fixture must exceed the legacy limit or this test proves nothing ") {
        longBody.length should be > 255
      }

      val id = code.api.util.APIUtil.generateUUID()
      val inserted = DynamicResourceDoc.insert(
        dynamicResourceDocId = id,
        bankId = None,
        partialFunctionName = s"longBodies_$uniqueSuffix",
        requestVerb = "POST",
        requestUrl = s"/long-bodies/$uniqueSuffix",
        summary = "a summary",
        description = "a description",
        exampleRequestBody = Some(longBody),
        successResponseBody = Some(longBody),
        errorResponseBodies = longBody,
        tags = "tag",
        roles = "role",
        methodBody = "()",
        createdByUserId = None, methodBodyHash = None)

      inserted.successResponseBody should equal(Some(longBody))

      DynamicResourceDoc.findById(None, id) match {
        case Full(found) =>
          found.exampleRequestBody should equal(Some(longBody))
          found.successResponseBody should equal(Some(longBody))
          found.errorResponseBodies should equal(longBody)
        case other => fail(s"the doc that was just inserted must be readable, got $other")
      }
    }
  }
}
