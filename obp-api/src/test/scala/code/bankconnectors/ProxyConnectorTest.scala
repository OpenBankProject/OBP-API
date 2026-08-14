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
 * wholesale with nothing to catch a mistake. These scenarios pin the three behaviours that a
 * rewrite can silently get wrong.
 *
 * The no-argument scenario is the sharpest of the three. The interceptor forwards with
 * `method.invoke(LocalMappedConnector, args: _*)`, which throws if the proxy library hands it a
 * null argument array rather than an empty one for a method that takes no parameters. cglib passes
 * an empty array; `java.lang.reflect.Proxy` passes null, and adapters built on that convention
 * inherit it.
 */
class ProxyConnectorTest extends ServerSetupWithTestData {

  object ProxyConnectorTag extends Tag("ProxyConnector")

  private lazy val proxy: Connector = Connector.getConnectorInstance("proxy")

  private def bankIdsOf(result: Box[(List[Bank], Option[code.api.util.CallContext])]): List[String] =
    result.map(_._1.map(_.bankId.value).sorted).getOrElse(Nil)

  feature("The proxy connector delegates to LocalMappedConnector") {

    scenario("it is registered under the name proxy and is a distinct instance", ProxyConnectorTag) {
      proxy shouldBe a[Connector]
      // A proxy, not the delegate handed back under another name.
      proxy should not be theSameInstanceAs(LocalMappedConnector)
    }

    scenario("a method that takes no arguments reaches the delegate", ProxyConnectorTag) {
      // callableMethods has an empty parameter list: this is the null-argument-array trap.
      proxy.callableMethods should equal(LocalMappedConnector.callableMethods)
    }

    scenario("a $default$ accessor returns the delegate's default value", ProxyConnectorTag) {
      // Synthetic default-argument accessors are also no-argument methods, and the interceptor
      // gives them a branch of their own: their results must be passed through untouched rather
      // than run through the InBound field stripping.
      val accessor = classOf[Connector].getMethod("checkBankAccountExists$default$3")
      accessor.invoke(proxy) should equal(None)
    }

    scenario("a method whose result has an InBound DTO is delegated and its payload survives", ProxyConnectorTag) {
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
