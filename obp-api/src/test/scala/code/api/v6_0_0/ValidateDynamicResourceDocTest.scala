package code.api.v6_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.ApiRole
import code.entitlement.Entitlement
import org.json4s.native.Serialization.write

/**
 * `POST /obp/v6.0.0/management/dynamic-resource-docs/validate` must reject an unsupported
 * `programming_lang` the same way `POST .../dynamic-resource-docs` (create) does, rather than
 * reporting `valid = true` for a language create would actually 400 on -- see
 * Http4s600.validateDynamicResourceDoc's own doc comment: CompiledObjects falls through to the
 * Scala compile path for any programming_lang value it doesn't recognise as Java, so a body that
 * happens to be valid Scala would otherwise "validate" successfully under a bogus/misspelled
 * language.
 */
class ValidateDynamicResourceDocTest extends V600ServerSetup {

  private def validateRequest = (v6_0_0_Request / "management" / "dynamic-resource-docs" / "validate").POST <@ (user1)

  private def docWith(programmingLang: String) =
    SwaggerDefinitionsJSON.jsonDynamicResourceDoc.copy(
      dynamicResourceDocId = None,
      programmingLang = programmingLang
    )

  feature("Validate Dynamic Resource Doc rejects an unsupported programming_lang") {
    scenario("An unsupported programming_lang is rejected, not silently validated as Scala") {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canCreateDynamicResourceDoc.toString)

      val resp = makePostRequest(validateRequest, write(docWith("Python")))

      Then("the request is rejected with 400, not a 200 valid=true/false body")
      resp.code should equal(400)
      resp.body.toString should include("OBP-40049")
    }

    scenario("programming_lang \"Scala\" is accepted (baseline, unaffected by the language check)") {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canCreateDynamicResourceDoc.toString)

      val resp = makePostRequest(validateRequest, write(docWith("Scala")))

      Then("the request reaches the compile step and responds 200")
      resp.code should equal(200)
      (resp.body \ "valid").values should equal(true)
    }

    scenario("programming_lang \"Java\" is accepted (baseline, unaffected by the language check)") {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canCreateDynamicResourceDoc.toString)

      val javaBody =
        """package code.api.util.dynamic;
          |
          |import java.util.LinkedHashMap;
          |import java.util.Map;
          |import java.util.function.Function;
          |import java.util.function.Supplier;
          |
          |public class ValidateEndpointJavaProbe implements Supplier<Function<Object[], Object>> {
          |    private Object apply(Object[] args) {
          |        Map<String, Object> response = new LinkedHashMap<>();
          |        response.put("greeting", "hello");
          |        return response;
          |    }
          |
          |    @Override
          |    public Function<Object[], Object> get() {
          |        return this::apply;
          |    }
          |}
          |""".stripMargin

      val doc = docWith("Java").copy(methodBody = java.net.URLEncoder.encode(javaBody, "UTF-8"))
      val resp = makePostRequest(validateRequest, write(doc))

      Then("the request reaches the compile step and responds 200")
      resp.code should equal(200)
      (resp.body \ "valid").values should equal(true)
    }
  }
}
