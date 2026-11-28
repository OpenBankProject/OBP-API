package code.api.util

import code.setup.{EnvVarOverride, ServerSetup}

/**
 * With no enforceable sandbox, running user-supplied Scala needs a second, explicit consent.
 *
 * `Sandbox.runInSandbox` was the isolation for dynamic endpoints, dynamic connector methods and
 * ABAC rules: it installed a SecurityManager and ran bodies under `AccessController.doPrivileged`
 * with a restricted permission set. JEP 486 removed SecurityManager in JDK 24, so
 * `System.setSecurityManager` throws and `doPrivileged` degrades to a pass-through - file, network
 * and reflection access from dynamic code are unguarded. The object already logs that loudly, and
 * three DynamicUtilTest scenarios are `assume`-skipped for the same reason.
 *
 * What it did not do is change behaviour: `allow_user_generated_scala_code=true` still compiled and
 * ran user code exactly as before, so a deployment that enabled the feature when the sandbox worked
 * silently lost its isolation on a JDK upgrade, with only a log line to say so.
 *
 * This does NOT refuse to boot, and does not touch the default (the feature is off by default):
 * it refuses to COMPILE user-supplied Scala when the sandbox cannot enforce anything unless the
 * operator says so a second time with `allow_user_generated_scala_code_without_sandbox=true`. A
 * deployment that means it keeps working after one deliberate edit; one that upgraded JDK without
 * realising gets an actionable failure instead of silent exposure.
 */
class DynamicCodeSandboxGateTest extends ServerSetup with EnvVarOverride {

  private val trivial = """ () => 1 """

  Feature("compiling user-supplied Scala requires an enforceable sandbox, or explicit consent") {

    // The suite's own environment sets both switches on (see run_tests_parallel.sh and the
    // workflows' Setup-props step), and an env var always wins over setPropsValues - see
    // APIUtil.getPropsValue. withEnvOverride forces the relevant one out of the way so the
    // "false" this scenario is about actually takes effect.
    Scenario("refused when the sandbox cannot enforce and consent was not given") {
      withEnvOverride("OBP_ALLOW_USER_GENERATED_SCALA_CODE_WITHOUT_SANDBOX" -> "false") {
        setPropsValues(
          "allow_user_generated_scala_code" -> "true",
          "allow_user_generated_scala_code_without_sandbox" -> "false")

        val result = DynamicUtil.compileScalaCode[Function0[Int]](trivial)

        withClue("on a JVM with no SecurityManager the sandbox enforces nothing, so compiling " +
          "user-supplied Scala must be refused until the operator opts in explicitly: ") {
          result.isDefined should equal(false)
        }
      }
    }

    Scenario("allowed when the operator has explicitly accepted the unsandboxed risk") {
      setPropsValues(
        "allow_user_generated_scala_code" -> "true",
        "allow_user_generated_scala_code_without_sandbox" -> "true")

      val result = DynamicUtil.compileScalaCode[Function0[Int]](trivial)

      withClue("with the second switch on, the feature must still work - this gate is a consent " +
        "check, not a removal of the capability: ") {
        result.isDefined should equal(true)
      }
    }

    Scenario("the kill switch still wins on its own") {
      withEnvOverride("OBP_ALLOW_USER_GENERATED_SCALA_CODE" -> "false") {
        setPropsValues(
          "allow_user_generated_scala_code" -> "false",
          "allow_user_generated_scala_code_without_sandbox" -> "true")

        val result = DynamicUtil.compileScalaCode[Function0[Int]](trivial)

        withClue("the new switch must not become a way around the original kill switch: ") {
          result.isDefined should equal(false)
        }
      }
    }
  }
}
