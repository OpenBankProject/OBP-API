package code.api.v4_0_0

import org.json4s._
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.ApiRole
import code.api.util.ErrorMessages.DynamicCodeLangNotSupport
import code.dynamicResourceDoc.JsonDynamicResourceDoc
import code.entitlement.Entitlement
import com.openbankproject.commons.model.ErrorMessage
import com.openbankproject.commons.util.json
import org.json4s.native.JsonMethods.{compact, parse => parseJson, render}
import org.json4s.native.Serialization.write

/**
 * Java-language coverage for the DynamicResourceDoc runtime-compilation mechanism.
 * DynamicResourceDocTest.scala covers the (unchanged) Scala-language path end-to-end; these
 * scenarios exercise the new `programming_lang = "Java"` dispatch added to
 * DynamicEndpoints.CompiledObjects / DynamicUtil.createJavaHttp4sEndpoint, plus the
 * backward-compat and unsupported-language guards added alongside it.
 */
class DynamicResourceDocJavaTest extends V400ServerSetup {

  private def createDynamicResourceDocsRequest = (v4_0_0_Request / "management" / "dynamic-resource-docs").POST <@ (user1)

  // Java-side convention: the pasted class implements Supplier<Function<Object[], Object>>.
  // args(0) = raw request body (String, or null), args(1) = path params (java.util.Map<String,String>),
  // args(2) = the CallContext. See DynamicUtil.createJavaHttp4sEndpoint's doc comment.
  private def javaRoleTestMethodBody: String =
    """package code.api.util.dynamic;
      |
      |import java.util.LinkedHashMap;
      |import java.util.Map;
      |import java.util.function.Function;
      |import java.util.function.Supplier;
      |
      |public class DynamicJavaResourceDocRoleTest implements Supplier<Function<Object[], Object>> {
      |    private Object apply(Object[] args) {
      |        String rawBody = (String) args[0];
      |        @SuppressWarnings("unchecked")
      |        Map<String, String> pathParams = (Map<String, String>) args[1];
      |        String myUserId = pathParams.get("MY_USER_ID");
      |
      |        Map<String, Object> response = new LinkedHashMap<>();
      |        response.put("user_id_from_path", myUserId + "_from_path");
      |        response.put("received_body", rawBody);
      |        return response;
      |    }
      |
      |    @Override
      |    public Function<Object[], Object> get() {
      |        return this::apply;
      |    }
      |}
      |""".stripMargin

  feature("Native execution of a runtime-compiled dynamic resource doc with a Java method_body") {

    scenario("Create a role-gated Java-language dynamic resource doc and verify 401 / 403 / 200") {
      val dynamicRole = "CanCallJavaPieceCRoleTest"
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canCreateDynamicResourceDoc.toString)

      When("We create a Java-language dynamic resource doc gated by that role")
      val createReq = createDynamicResourceDocsRequest
      val doc = SwaggerDefinitionsJSON.jsonDynamicResourceDoc.copy(
        dynamicResourceDocId = None,
        bankId = None,
        roles = dynamicRole,
        partialFunctionName = "javaPieceCRoleTest",
        requestUrl = "/my_java_role_user/MY_USER_ID",
        methodBody = java.net.URLEncoder.encode(javaRoleTestMethodBody, "UTF-8"),
        programmingLang = "Java"
      )
      val createResp = makePostRequest(createReq, write(doc))
      Then("We should get a 201")
      createResp.code should equal(201)
      createResp.body.extract[JsonDynamicResourceDoc].programmingLang should equal("Java")

      val callUrl = dynamicEndpoint_Request / "dynamic-resource-doc" / "my_java_role_user" / "user-1"
      val body = """{"name":"Jhon","age":12,"hobby":["coding"]}"""

      assertRoleGated401Then403Then200(callUrl, body, dynamicRole) { resp200 =>
        val rendered = json.compactRender(resp200.body)
        rendered should include("user-1_from_path")
        rendered should include("Jhon")
      }
    }

    scenario("Reject an unsupported programming_lang before attempting compilation") {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canCreateDynamicResourceDoc.toString)

      When("We create a dynamic resource doc with an unsupported programming_lang")
      val createReq = createDynamicResourceDocsRequest
      val doc = SwaggerDefinitionsJSON.jsonDynamicResourceDoc.copy(
        dynamicResourceDocId = None,
        bankId = None,
        partialFunctionName = "unsupportedLangTest",
        requestUrl = "/unsupported_lang_test/MY_USER_ID",
        programmingLang = "Python"
      )
      val resp = makePostRequest(createReq, write(doc))

      Then("We should get a 400 DynamicCodeLangNotSupport, not a compile-failure error")
      resp.code should equal(400)
      resp.body.extract[ErrorMessage].message should include(DynamicCodeLangNotSupport)
    }

    scenario("Backward compatibility: a request body with programming_lang entirely omitted still creates a Scala-language doc") {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canCreateDynamicResourceDoc.toString)

      When("We create a dynamic resource doc from a JSON payload that predates the programming_lang field")
      val posted = SwaggerDefinitionsJSON.jsonDynamicResourceDoc.copy(
        dynamicResourceDocId = None,
        bankId = None,
        roles = "",
        partialFunctionName = "preExistingClientTest",
        requestUrl = "/pre_existing_client_test/MY_USER_ID"
      )
      // Simulate an old client payload: strip programming_lang out entirely rather than relying on
      // Serialization.write, which always emits every case-class field (default or not).
      val fullJson = parseJson(write(posted))
      val withoutLang = fullJson.removeField { case (name, _) => name == "programming_lang" }
      val requestBodyStr = compact(render(withoutLang))
      requestBodyStr should not include "programming_lang"

      val createReq = createDynamicResourceDocsRequest
      val createResp = makePostRequest(createReq, requestBodyStr)

      Then("We should get a 201 and the stored/served doc defaults to the Scala language")
      createResp.code should equal(201)
      createResp.body.extract[JsonDynamicResourceDoc].programmingLang should equal("Scala")

      Then("calling the endpoint still compiles and serves via the (unchanged) Scala template path")
      val callReq = (dynamicEndpoint_Request / "dynamic-resource-doc" / "pre_existing_client_test" / "user-1").POST <@ (user1)
      val callResp = makePostRequest(callReq, """{"name":"Jhon","age":12,"hobby":["coding"]}""")
      callResp.code should equal(200)
      val rendered = json.compactRender(callResp.body)
      rendered should include("user-1_from_path")
      rendered should include("Jhon")
    }
  }
}
