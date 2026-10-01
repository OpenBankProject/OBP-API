package code.api.util

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.{Method, Request, Uri}
import org.json4s.native.JsonMethods.parse
import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}

/**
 * Focused unit test for DynamicUtil.createJavaHttp4sEndpoint, isolated from the full
 * register -> role-check -> HTTP-dispatch round trip (see DynamicResourceDocJavaTest for that).
 * Exercises the adapter directly: compiled Java Supplier<Function<Object[], Object>> ->
 * Http4sEndpointIO.apply(Request[IO]) -> CallContext => IO[Response[IO]].
 */
class DynamicUtilJavaHttp4sEndpointTest extends FeatureSpec with Matchers with GivenWhenThen {

  private val echoMethodBody =
    """package code.api.util.dynamic;
      |
      |import code.api.util.CallContext;
      |import java.util.LinkedHashMap;
      |import java.util.Map;
      |import java.util.function.Function;
      |import java.util.function.Supplier;
      |
      |public class DynamicJavaHttp4sEndpointUnitTest implements Supplier<Function<Object[], Object>> {
      |    private Object apply(Object[] args) {
      |        String rawBody = (String) args[0];
      |        @SuppressWarnings("unchecked")
      |        Map<String, String> pathParams = (Map<String, String>) args[1];
      |        CallContext cc = (CallContext) args[2];
      |
      |        Map<String, Object> response = new LinkedHashMap<>();
      |        response.put("echoed_body", rawBody);
      |        response.put("path_param_count", pathParams.size());
      |        response.put("correlation_id", cc.correlationId());
      |        return response;
      |    }
      |
      |    @Override
      |    public Function<Object[], Object> get() {
      |        return this::apply;
      |    }
      |}
      |""".stripMargin

  feature("DynamicUtil.createJavaHttp4sEndpoint compiles a Java method_body into a native Http4sEndpointIO") {

    scenario("the compiled endpoint reads args(0)/args(1)/args(2) and serves 200 JSON") {
      Given("a Java method_body compiled via createJavaHttp4sEndpoint")
      val endpoint = DynamicUtil.createJavaHttp4sEndpoint(echoMethodBody).openOrThrowException("compilation failed")

      When("the compiled endpoint handles a request carrying a body, no path params, and a CallContext")
      val req = Request[IO](method = Method.POST, uri = Uri.unsafeFromString("/test"))
      val cc = CallContext(httpBody = Some("""{"hello":"world"}"""), correlationId = "test-correlation-id")
      val resp = endpoint.apply(req)(cc).unsafeRunSync()

      Then("the response is 200 and echoes the body, the (empty) path params, and the CallContext's correlationId")
      resp.status.code should equal(200)
      val bodyString = resp.body.through(fs2.text.utf8.decode).compile.string.unsafeRunSync()
      val json = parse(bodyString)
      (json \ "echoed_body").values should equal("""{"hello":"world"}""")
      (json \ "path_param_count").values should equal(BigInt(0))
      (json \ "correlation_id").values should equal("test-correlation-id")
    }

    scenario("a Java compile error is reported as a Box Failure, not a thrown exception") {
      Given("a method_body that is not valid Java")
      val badMethodBody = "this is not valid java at all"

      When("we try to compile it")
      val result = DynamicUtil.createJavaHttp4sEndpoint(badMethodBody)

      Then("compilation fails gracefully")
      result.isDefined should equal(false)
    }

    scenario("a Java method_body that throws at runtime is recovered as a 500, not an uncaught exception") {
      Given("a Java method_body whose apply() throws")
      val throwingMethodBody =
        """package code.api.util.dynamic;
          |
          |import java.util.function.Function;
          |import java.util.function.Supplier;
          |
          |public class DynamicJavaHttp4sEndpointThrowingTest implements Supplier<Function<Object[], Object>> {
          |    private Object apply(Object[] args) {
          |        throw new RuntimeException("boom");
          |    }
          |
          |    @Override
          |    public Function<Object[], Object> get() {
          |        return this::apply;
          |    }
          |}
          |""".stripMargin
      val endpoint = DynamicUtil.createJavaHttp4sEndpoint(throwingMethodBody).openOrThrowException("compilation failed")

      When("the compiled endpoint is invoked")
      val req = Request[IO](method = Method.POST, uri = Uri.unsafeFromString("/test"))
      val resp = endpoint.apply(req)(CallContext()).unsafeRunSync()

      Then("the response is 500 rather than the IO failing")
      resp.status.code should equal(500)
      val bodyString = resp.body.through(fs2.text.utf8.decode).compile.string.unsafeRunSync()
      bodyString should include("boom")
    }
  }
}
