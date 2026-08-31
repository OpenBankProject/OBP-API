package code.api.v4_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.ApiRole
import code.api.util.ErrorMessages.DynamicResourceDocMethodDependency
import code.entitlement.Entitlement
import com.openbankproject.commons.model.ErrorMessage
import org.json4s.native.Serialization.write

/**
 * With dynamic_code_compile_validate_enable=true, a Java method_body that calls an OBP method NOT
 * on the dependency whitelist must be rejected -- proving createJavaHttp4sEndpoint validates the
 * real compiled Java class (getCompiledInstance), not just its Scala wrapper.
 */
class DynamicResourceDocJavaSecurityValidationTest extends V400ServerSetup {

  private def maliciousMethodBody: String =
    """package code.api.util.dynamic;
      |
      |import java.util.LinkedHashMap;
      |import java.util.Map;
      |import java.util.function.Function;
      |import java.util.function.Supplier;
      |
      |public class DynamicJavaSecurityProbe implements Supplier<Function<Object[], Object>> {
      |    private Object apply(Object[] args) {
      |        // APIUtil.getPropsValue is NOT on dynamic_code_compile_validate_dependencies'
      |        // whitelist (only errorJsonResponse*/scalaFutureToLaFuture/futureToBoxedResponse are).
      |        String secret = code.api.util.APIUtil$.MODULE$.getPropsValue("hostname", "none");
      |        Map<String, Object> response = new LinkedHashMap<>();
      |        response.put("leaked", secret);
      |        return response;
      |    }
      |
      |    @Override
      |    public Function<Object[], Object> get() {
      |        return this::apply;
      |    }
      |}
      |""".stripMargin

  feature("Security validation of Java method_body against dynamic_code_compile_validate_dependencies") {
    scenario("Registering a Java doc that calls a non-whitelisted OBP method is rejected with 400") {
      setPropsValues(
        "show_used_connector_methods" -> "true",
        "dynamic_code_compile_validate_enable" -> "true",
        "dynamic_code_compile_validate_dependencies" ->
          """[NewStyle.function.getClass.getTypeName -> "*", CompiledObjects.getClass.getTypeName -> "sandbox", HttpCode.getClass.getTypeName -> "200", DynamicCompileEndpoint.getClass.getTypeName -> "getPathParams, scalaFutureToBoxedJsonResponse", APIUtil.getClass.getTypeName -> "errorJsonResponse, errorJsonResponse$default$1, errorJsonResponse$default$2, errorJsonResponse$default$3, errorJsonResponse$default$4, scalaFutureToLaFuture, futureToBoxedResponse", ErrorMessages.getClass.getTypeName -> "*", ExecutionContext.Implicits.getClass.getTypeName -> "global", JSONFactory400.getClass.getTypeName -> "createBanksJson", classOf[Sandbox].getTypeName -> "runInSandbox", classOf[CallContext].getTypeName -> "*", classOf[ResourceDoc].getTypeName -> "getPathParams", "scala.reflect.runtime.package$" -> "universe", PractiseEndpoint.getClass.getTypeName + "*" -> "*"]"""
      )

      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canCreateDynamicResourceDoc.toString)

      val createReq = (v4_0_0_Request / "management" / "dynamic-resource-docs").POST <@ (user1)
      val doc = SwaggerDefinitionsJSON.jsonDynamicResourceDoc.copy(
        dynamicResourceDocId = None,
        bankId = None,
        roles = "",
        partialFunctionName = "securityProbeTest",
        requestUrl = "/security_probe_test/MY_USER_ID",
        methodBody = java.net.URLEncoder.encode(maliciousMethodBody, "UTF-8"),
        programmingLang = "Java"
      )
      val resp = makePostRequest(createReq, write(doc))

      Then("the compile is rejected with 400 DynamicResourceDocMethodDependency, not accepted")
      resp.code should equal(400)
      resp.body.extract[ErrorMessage].message should include(DynamicResourceDocMethodDependency)
    }
  }
}
