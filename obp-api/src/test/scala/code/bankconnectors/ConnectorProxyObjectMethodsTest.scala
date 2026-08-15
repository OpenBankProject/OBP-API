package code.bankconnectors

import code.setup.ServerSetupWithTestData
import org.scalatest.Tag

/**
 * Reproduces the Object-method hole in ConnectorProxy.
 *
 * ConnectorProxy intercepts with ElementMatchers.any(), which covers the methods inherited from
 * Object as well as the Connector ones. Whether that is safe depends entirely on what each handler
 * does with a name it does not recognise, and InternalConnector's sends it to getFunction, which
 * ends in openOrThrowException. So calling toString on that proxy - which anything that logs or
 * interpolates it does - throws.
 *
 * Nothing in the suite stringifies a connector, which is why this went unnoticed. These scenarios
 * do it deliberately.
 */
class ConnectorProxyObjectMethodsTest extends ServerSetupWithTestData {

  object ProxyObjectMethods extends Tag("ConnectorProxyObjectMethods")

  feature("A generated Connector proxy survives the methods every object has") {

    scenario("toString on the internal connector does not throw", ProxyObjectMethods) {
      // The internal connector is the sharp case: its handler treats an unknown method name as a
      // dynamic connector method to look up and compile.
      noException should be thrownBy InternalConnector.instance.toString
    }

    scenario("hashCode and equals on the internal connector do not throw", ProxyObjectMethods) {
      noException should be thrownBy InternalConnector.instance.hashCode()
      noException should be thrownBy InternalConnector.instance.equals(InternalConnector.instance)
    }

    scenario("a proxy can be used as a map key and printed", ProxyObjectMethods) {
      // Both go through Object methods on the proxy, and both are things ordinary code does.
      val connector = InternalConnector.instance
      noException should be thrownBy Map(connector -> "internal").get(connector)
      noException should be thrownBy s"connector is $connector"
    }

    scenario("the proxy connector answers Object methods too", ProxyObjectMethods) {
      val proxy = ConnectorUtils.proxyConnector
      noException should be thrownBy proxy.toString
      noException should be thrownBy proxy.hashCode()
    }

    scenario("the members Connector inherits from MdcLoggable are answered, not compiled", ProxyObjectMethods) {
      // Connector extends Helper.MdcLoggable, which contributes public abstract interface methods -
      // logger(), clazzName(), the two _setter_ bridges - and a default initiate(). They are
      // declared by MdcLoggable, not by Object, so excluding Object's methods does not cover them:
      // they still reach the handler, and InternalConnector's reads any unrecognised name as a
      // dynamic connector method to look up and compile.
      //
      // They cannot simply be left unintercepted either. Being abstract, something has to implement
      // them or the generated class cannot be instantiated - which is why the fix belongs in the
      // handler rather than in the element matcher.
      val loggerMethod = classOf[Connector].getMethod("logger")

      noException should be thrownBy loggerMethod.invoke(InternalConnector.instance)
      noException should be thrownBy loggerMethod.invoke(ConnectorUtils.proxyConnector)
    }

    scenario("every method Connector inherits from outside its own API is answerable", ProxyObjectMethods) {
      // A shape check rather than a list: anything on the interface that InternalConnector does not
      // recognise as a connector method must still return rather than throw.
      val inherited = classOf[Connector].getMethods.toList
        .filter(m => m.getParameterCount == 0)
        .filter(m => m.getDeclaringClass != classOf[Connector])
        .filter(m => m.getDeclaringClass != classOf[Object])
        .filterNot(_.getName.contains("$default$"))

      inherited should not be empty

      val failures = inherited.flatMap { m =>
        try { m.invoke(InternalConnector.instance); None }
        catch { case e: java.lang.reflect.InvocationTargetException => Some(m.getName -> e.getCause.toString) }
      }

      withClue(s"methods that threw: $failures") { failures shouldBe empty }
    }

    scenario("equality is still reference equality for a proxy", ProxyObjectMethods) {
      // Worth pinning: if Object methods are ever routed to a delegate rather than handled by the
      // proxy itself, two distinct proxies over the same delegate would start comparing equal.
      val internal = InternalConnector.instance
      val proxy = ConnectorUtils.proxyConnector

      internal should equal(internal)
      internal should not equal proxy
    }
  }
}
