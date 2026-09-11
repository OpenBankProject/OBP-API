package code.bankconnectors

import code.setup.ServerSetupWithTestData
import com.openbankproject.commons.model.Bank
import net.liftweb.common.{Box, Full}
import org.scalatest.Tag

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Covers `ConnectorUtils.proxyConnector`, registered as the "proxy" connector.
 *
 * Its own comment says it exists for unit tests, yet nothing referenced it: neither the string
 * "proxy" nor `proxyConnector` appeared anywhere under src/test, and no props file selects it. It
 * is a generated proxy over the `Connector` trait, so a change of proxy library rewrites it
 * wholesale with nothing to catch a mistake. These scenarios pin the four behaviours that a
 * rewrite can silently get wrong.
 *
 * The no-argument scenarios matter because of how the argument array arrives. cglib passed an
 * empty array for a method that declares no parameters; byte-buddy's InvocationHandlerAdapter
 * passes null, following `java.lang.reflect.Proxy`. This interceptor forwards with
 * `method.invoke(LocalMappedConnector, args: _*)`, which survives that - it compiles to Java
 * varargs and Method.invoke reads a null array as no arguments - so what these scenarios pin is
 * that the forwarding keeps working, not that the array is non-null. A handler that instead treats
 * `args` as a collection does not survive it; see ConnectorProxy for the one that did not.
 */
class ProxyConnectorTest extends ServerSetupWithTestData {

  object ProxyConnectorTag extends Tag("ProxyConnector")

  private lazy val proxy: Connector = Connector.getConnectorInstance("proxy")

  private def bankIdsOf(result: Box[(List[Bank], Option[code.api.util.CallContext])]): List[String] =
    result.map(_._1.map(_.bankId.value).sorted).getOrElse(Nil)

  Feature("The proxy connector delegates to LocalMappedConnector") {

    Scenario("it is registered under the name proxy and is a distinct instance", ProxyConnectorTag) {
      proxy shouldBe a[Connector]
      // A proxy, not the delegate handed back under another name.
      proxy should not be theSameInstanceAs(LocalMappedConnector)
    }

    Scenario("a method that takes no arguments reaches the delegate", ProxyConnectorTag) {
      // callableMethods has an empty parameter list, so this is the call that receives null args.
      proxy.callableMethods should equal(LocalMappedConnector.callableMethods)
    }

    Scenario("a $default$ accessor returns the delegate's default value", ProxyConnectorTag) {
      // Synthetic default-argument accessors are also no-argument methods, and the interceptor
      // gives them a branch of their own: their results must be passed through untouched rather
      // than run through the InBound field stripping.
      val accessor = classOf[Connector].getMethod("checkBankAccountExists$default$3")
      accessor.invoke(proxy) should equal(None)
    }

    Scenario("a method whose result has an InBound DTO is delegated and its payload survives", ProxyConnectorTag) {
      // getBanks returns Future[Box[(List[Bank], Option[CallContext])]], so this walks the whole
      // result-unwrapping chain in deleteIgnoreFieldValue: Future, then Full of a tuple. An
      // InBoundGetBanks class exists, so the stripping branch runs rather than the pass-through.
      val viaProxy = Await.result(proxy.getBanks(None), 30.seconds)
      val direct = Await.result(LocalMappedConnector.getBanks(None), 30.seconds)

      viaProxy shouldBe a[Full[_]]
      // The point of the proxy is to drop fields the InBound DTO marks as ignored, so the two
      // results are not required to be equal - but the banks themselves must all still be there.
      bankIdsOf(viaProxy) should equal(bankIdsOf(direct))
      bankIdsOf(viaProxy) should not be empty
    }
  }
}
