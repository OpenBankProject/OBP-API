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

  // Mirrors sample.props.template's default dynamic_code_compile_validate_dependencies exactly,
  // minus the trailing newlines/line-continuations -- deliberately does NOT list APIUtil.getPropsValue.
  private def defaultDependenciesWhitelist: String =
    """[NewStyle.function.getClass.getTypeName -> "*", CompiledObjects.getClass.getTypeName -> "sandbox", HttpCode.getClass.getTypeName -> "200", DynamicCompileEndpoint.getClass.getTypeName -> "getPathParams, scalaFutureToBoxedJsonResponse", APIUtil.getClass.getTypeName -> "errorJsonResponse, errorJsonResponse$default$1, errorJsonResponse$default$2, errorJsonResponse$default$3, errorJsonResponse$default$4, scalaFutureToLaFuture, futureToBoxedResponse", ErrorMessages.getClass.getTypeName -> "*", ExecutionContext.Implicits.getClass.getTypeName -> "global", JSONFactory400.getClass.getTypeName -> "createBanksJson", classOf[Sandbox].getTypeName -> "runInSandbox", classOf[CallContext].getTypeName -> "*", classOf[ResourceDoc].getTypeName -> "getPathParams", "scala.reflect.runtime.package$" -> "universe", PractiseEndpoint.getClass.getTypeName + "*" -> "*"]"""

  // Deliberately does NOT set show_used_connector_methods: that prop exists to opt in to an
  // unrelated, expensive introspection/reporting feature and was never meant to gate security
  // validation, which happens to reuse the same underlying bytecode scan. An operator who reads
  // only dynamic_code_compile_validate_enable's own prop documentation and sets just these two
  // props (as this method does) must still get real enforcement -- proving that is the point of
  // every scenario below.
  //
  // Block body (not `= setPropsValues(...)`) so .github/scripts/check_test_isolation.py's brace
  // scanner sees an opening `{` right after `def enableStrictValidation` and treats this as a
  // safe "helper called from scenarios" scope rather than a class-body-level setPropsValues call.
  private def enableStrictValidation(): Unit = {
    setPropsValues(
      "dynamic_code_compile_validate_enable" -> "true",
      "dynamic_code_compile_validate_dependencies" -> defaultDependenciesWhitelist
    )
  }

  // Every Java method_body implements Supplier<Function<Object[], Object>> per convention (see
  // DynamicUtil.createJavaHttp4sEndpoint's doc comment). javac always erases that generic
  // Supplier.get() to a synthetic bridge method `Object get()` whose body just invokevirtual-calls
  // the real, properly-typed get() -- an ordinary same-class call regardless of what the body does.
  private def benignMethodBody: String =
    """package code.api.util.dynamic;
      |
      |import java.util.LinkedHashMap;
      |import java.util.Map;
      |import java.util.function.Function;
      |import java.util.function.Supplier;
      |
      |public class DynamicJavaSecurityBenignProbe implements Supplier<Function<Object[], Object>> {
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

  private def createRequest = (v4_0_0_Request / "management" / "dynamic-resource-docs").POST <@ (user1)

  feature("Security validation of Java method_body against dynamic_code_compile_validate_dependencies") {

    // Regression guard: the Supplier.get() generics-erasure bridge method's same-class call to the
    // real get() must not itself be treated as a call to a forbidden method. Without this, every
    // Java doc -- malicious or not -- was rejected under strict validation, because the compiled
    // class lives under the OBP-owned code.* package but its randomly-generated name can never
    // appear in a static whitelist.
    scenario("Registering a benign Java doc succeeds even with strict validation enabled") {
      enableStrictValidation()
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canCreateDynamicResourceDoc.toString)

      val doc = SwaggerDefinitionsJSON.jsonDynamicResourceDoc.copy(
        dynamicResourceDocId = None,
        bankId = None,
        roles = "",
        partialFunctionName = "benignProbeTest",
        requestUrl = "/benign_probe_test/MY_USER_ID",
        methodBody = java.net.URLEncoder.encode(benignMethodBody, "UTF-8"),
        programmingLang = "Java"
      )
      val resp = makePostRequest(createRequest, write(doc))

      Then("the compile succeeds -- the Supplier.get() bridge method's self-call is not a forbidden dependency")
      resp.code should equal(201)
    }
    scenario("Registering a Java doc that calls a non-whitelisted OBP method is rejected with 400") {
      enableStrictValidation()
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canCreateDynamicResourceDoc.toString)

      val doc = SwaggerDefinitionsJSON.jsonDynamicResourceDoc.copy(
        dynamicResourceDocId = None,
        bankId = None,
        roles = "",
        partialFunctionName = "securityProbeTest",
        requestUrl = "/security_probe_test/MY_USER_ID",
        methodBody = java.net.URLEncoder.encode(maliciousMethodBody, "UTF-8"),
        programmingLang = "Java"
      )
      val resp = makePostRequest(createRequest, write(doc))

      Then("the compile is rejected with 400 DynamicResourceDocMethodDependency, not accepted")
      resp.code should equal(400)
      resp.body.extract[ErrorMessage].message should include(DynamicResourceDocMethodDependency)
    }

    // Regression guard for the bug createJavaHttp4sEndpoint had before it split compilation from
    // validation: memoJavaCompiledScript (formerly memoJavaHttp4sEndpoint) memoized the WHOLE
    // Box[Http4sEndpointIO], keyed only by the exact method_body string. A doc compiled once while
    // validation was off got a cached Full(...) that a later, identical create call -- made AFTER
    // validation was turned on and the whitelist tightened -- would silently reuse, never
    // re-running Validation.validateDependency at all. This scenario reproduces exactly that
    // sequence: compile the same malicious source once with validation off (succeeds, populates
    // the compile cache), then enable strict validation and resubmit the identical source.
    scenario("A Java source compiled once while validation was off is still validated on a later create call") {
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canCreateDynamicResourceDoc.toString)

      val firstDoc = SwaggerDefinitionsJSON.jsonDynamicResourceDoc.copy(
        dynamicResourceDocId = None,
        bankId = None,
        roles = "",
        partialFunctionName = "cacheBypassProbeTest1",
        requestUrl = "/cache_bypass_probe_test_1/MY_USER_ID",
        methodBody = java.net.URLEncoder.encode(maliciousMethodBody, "UTF-8"),
        programmingLang = "Java"
      )
      When("validation is off (the suite default) and we compile the malicious source for the first time")
      val firstResp = makePostRequest(createRequest, write(firstDoc))
      firstResp.code should equal(201)

      When("validation is then turned on with the same source resubmitted under a different doc")
      enableStrictValidation()
      val secondDoc = firstDoc.copy(
        partialFunctionName = "cacheBypassProbeTest2",
        requestUrl = "/cache_bypass_probe_test_2/MY_USER_ID"
      )
      val secondResp = makePostRequest(createRequest, write(secondDoc))

      Then("the second create is still rejected -- the compile-result cache must not bypass fresh validation")
      secondResp.code should equal(400)
      secondResp.body.extract[ErrorMessage].message should include(DynamicResourceDocMethodDependency)
    }
  }
}
