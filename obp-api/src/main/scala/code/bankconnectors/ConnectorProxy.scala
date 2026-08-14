package code.bankconnectors

import java.lang.reflect.InvocationHandler

import net.bytebuddy.ByteBuddy
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy
import net.bytebuddy.implementation.InvocationHandlerAdapter
import net.bytebuddy.matcher.ElementMatchers

/**
 * Generates the runtime `Connector` proxies. Three of them exist - StarConnector in the package
 * object, InternalConnector, and ConnectorUtils.proxyConnector - and they differ only in what their
 * handler does, so the generation itself lives here rather than three times over.
 *
 * This replaces cglib's Enhancer. cglib 3.3.0 bundles ASM 7.1, which reads class files only up to
 * major version 57; Scala 2.13 compiled with -release 25 emits major 69, so every one of these
 * proxies would fail to generate the moment the compiler is switched. That is why the swap happens
 * before the version flip rather than as part of it.
 *
 * `Connector` is a trait, so the generated class implements it rather than extending it - both
 * Enhancer.setSuperclass and ByteBuddy.subclass accept an interface and do the right thing.
 *
 * Object's own methods are left alone. Enhancer routed them to the callback as well, and for two of
 * the three handlers that was harmless because they forward by `method.invoke(delegate, ...)`. It
 * was not harmless for InternalConnector, whose handler reads any unrecognised name as a dynamic
 * connector method to look up and compile: toString, hashCode and equals on that proxy all threw
 * IllegalStateException, so logging the connector, interpolating it into a string, or using it as a
 * map key blew up. Excluding them gives the generated class the ordinary Object implementations,
 * which is also what makes proxy identity behave - reference equality, and a toString that does not
 * depend on a delegate.
 *
 * One difference between proxy libraries is worth stating, because all three handlers forward with
 * `method.invoke(target, args: _*)` and that expression throws on a null array:
 * `java.lang.reflect.Proxy` passes null for a method that declares no parameters, while cglib
 * passes an empty array. InvocationHandlerAdapter builds the array from the method's parameter
 * list, so a no-argument method gets a zero-length array and the forwarding stays valid.
 * ProxyConnectorTest pins that on the proxy connector, whose synthetic `$default$` accessors are
 * all no-argument methods.
 */
private[bankconnectors] object ConnectorProxy {

  def create(handler: InvocationHandler): Connector =
    new ByteBuddy()
      .subclass(classOf[Connector])
      .method(ElementMatchers.any().and(ElementMatchers.not(ElementMatchers.isDeclaredBy(classOf[Object]))))
      .intercept(InvocationHandlerAdapter.of(handler))
      .make()
      // WRAPPER puts the generated class in a child class loader of the one that defines Connector.
      // The alternative, injecting into that loader itself, needs the kind of JDK-internal access
      // that keeps being closed off in newer releases; the proxies need nothing from it.
      .load(classOf[Connector].getClassLoader, ClassLoadingStrategy.Default.WRAPPER)
      .getLoaded
      .getDeclaredConstructor()
      .newInstance()
      .asInstanceOf[Connector]
}
