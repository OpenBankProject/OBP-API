package code.setup

import net.liftweb.util.Props
import org.apache.commons.lang3.reflect.FieldUtils
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}


/**
 * Any unit test that extends this trait can call `setPropsValues` to override
 * Lift Props for the duration of a scenario; `afterEach` restores the snapshot
 * captured at trait initialisation so changes never leak between tests.
 *
 * Guard rail: `setPropsValues` must be called at test-execution time, i.e. from
 * inside `scenario { ... }`, `beforeEach()`, `beforeAll()`, `afterEach()`, or
 * `afterAll()`. Calls placed at `feature(...) { ... }` body level or directly in
 * the class/trait body execute during ScalaTest discovery (before any test runs)
 * and corrupt global Props for every other test class instantiated after them.
 * See TEST_ISOLATION_ADVISE.md at the repo root.
 *
 * To enforce this, `setPropsValues` throws `IllegalStateException` when called
 * outside a scenario/hook context. The check is driven by a thread-local flag
 * that `beforeEach`/`beforeAll` flip on and `afterEach`/`afterAll` flip back.
 */
trait PropsReset extends BeforeAndAfterAll with BeforeAndAfterEach {
  this: Suite =>

  private val inTestScope = new ThreadLocal[Boolean] {
    override def initialValue(): Boolean = false
  }

  override def beforeAll(): Unit = {
    inTestScope.set(true)
    super.beforeAll()
  }
  override def beforeEach(): Unit = {
    inTestScope.set(true)
    super.beforeEach()
  }
  override def afterEach(): Unit = {
    super.afterEach()
    resetLockedProviders()
    inTestScope.set(false)
  }
  override def afterAll(): Unit = {
    super.afterAll()
    resetLockedProviders()
    inTestScope.set(false)
  }

  private val lockedProviders: List[Map[String, String]] = getLockedProviders

  private def getLockedProviders = {
    FieldUtils.readDeclaredField(Props, "net$liftweb$util$Props$$lockedProviders", true)
      .asInstanceOf[List[Map[String, String]]]
  }

  private def resetLockedProviders(): Unit = {
    FieldUtils.writeDeclaredField(Props, "net$liftweb$util$Props$$lockedProviders", lockedProviders,  true)
  }

  def setPropsValues(keyValues: (String, String)*): Unit = {
    if (!inTestScope.get()) {
      throw new IllegalStateException(
        s"setPropsValues called outside a scenario/before*/after* hook in ${this.getClass.getName}. " +
        "This call would run at class-instantiation time (during ScalaTest discovery) and pollute " +
        "global Props for every other test class instantiated after it. " +
        "Move the call inside `scenario { ... }` or `override def beforeEach()`. " +
        "See TEST_ISOLATION_ADVISE.md."
      )
    }
    val newLockedProviders = keyValues.toMap :: getLockedProviders
    FieldUtils.writeDeclaredField(Props, "net$liftweb$util$Props$$lockedProviders", newLockedProviders,  true)
  }
}
