package code.setup

import java.util.{Map => JMap}

/**
 * Test-only override for OS-level environment variables.
 *
 * Why this exists: `APIUtil.getPropsValue` always checks `sys.env` before the
 * Props file / `setPropsValues` overrides (env vars are meant to win — e.g.
 * `run_tests_parallel.sh` injects `OBP_MAIL_TEST_MODE=true` for every shard so
 * that local runs don't open a real SMTP socket, mirroring CI's `mail.test.mode`
 * props-file default). A scenario that needs to flip one of those props off
 * (e.g. to test the real-SMTP-failure path) cannot do it with `setPropsValues`
 * alone when the corresponding `OBP_*` env var is set in the JVM's process
 * environment — the env var always wins. This trait mutates the process
 * environment for the scope of one block so such a scenario can force the
 * env var out of the way, then restores it.
 *
 * Uses the same reflection trick as `PropsReset`/`PropsProgrammatically`
 * (declared-field access); relies on `--add-opens java.base/java.lang=ALL-UNNAMED`,
 * already granted to the test JVM by `obp-api/pom.xml`'s `scalatest-maven-plugin`
 * argLine. On modern JDKs (Unix `ProcessEnvironment`), the backing map is keyed
 * by internal `Variable`/`Value` wrapper types, not plain `String` — this goes
 * through their `valueOf(String)` factories rather than assuming a `Map[String,String]`.
 */
trait EnvVarOverride {

  def withEnvOverride[T](keyValues: (String, String)*)(block: => T): T = {
    val backend = EnvVarOverride.backend()
    val originals = keyValues.map { case (k, _) => k -> Option(System.getenv(k)) }
    try {
      keyValues.foreach { case (k, v) => backend.put(k, v) }
      block
    } finally {
      originals.foreach {
        case (k, Some(v)) => backend.put(k, v)
        case (k, None) => backend.remove(k)
      }
    }
  }
}

object EnvVarOverride {

  private trait Backend {
    def put(key: String, value: String): Unit
    def remove(key: String): Unit
  }

  private def declaredField(cls: Class[_], name: String) = {
    val f = cls.getDeclaredField(name)
    f.setAccessible(true)
    f
  }

  private def backend(): Backend = {
    try unixBackend()
    catch { case _: NoSuchFieldException => windowsBackend() }
  }

  // Unix `ProcessEnvironment.theEnvironment` is `HashMap<Variable, Value>` (byte-array-backed
  // wrapper types), not `Map<String, String>` — must go through their `valueOf(String)` factories.
  private def unixBackend(): Backend = {
    val processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment")
    val theEnvironment = declaredField(processEnvironmentClass, "theEnvironment")
      .get(null).asInstanceOf[JMap[AnyRef, AnyRef]]
    val variableClass = Class.forName("java.lang.ProcessEnvironment$Variable")
    val valueClass = Class.forName("java.lang.ProcessEnvironment$Value")
    val variableValueOf = variableClass.getMethod("valueOf", classOf[String])
    variableValueOf.setAccessible(true)
    val valueValueOf = valueClass.getMethod("valueOf", classOf[String])
    valueValueOf.setAccessible(true)
    new Backend {
      def put(key: String, value: String): Unit =
        theEnvironment.put(variableValueOf.invoke(null, key), valueValueOf.invoke(null, value))
      def remove(key: String): Unit =
        theEnvironment.remove(variableValueOf.invoke(null, key))
    }
  }

  // Windows fallback: System.getenv() is a Collections.unmodifiableMap wrapping a
  // plain Map[String, String] in a field named "m".
  private def windowsBackend(): Backend = {
    val env = System.getenv()
    val unmodifiableMapClass = Class.forName("java.util.Collections$UnmodifiableMap")
    val underlying = declaredField(unmodifiableMapClass, "m").get(env).asInstanceOf[JMap[String, String]]
    new Backend {
      def put(key: String, value: String): Unit = underlying.put(key, value)
      def remove(key: String): Unit = underlying.remove(key)
    }
  }
}
