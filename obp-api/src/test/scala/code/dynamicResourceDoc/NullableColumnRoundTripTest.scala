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
        methodBody = "()")

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
        methodBody = "()")

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
        programmingLang = "Scala")

      DynamicMessageDoc.findById(None, id) match {
        case Full(found) => found.process should equal(process)
        case other => fail(s"the message doc that was just inserted must be readable, got $other")
      }
    }
  }
}
