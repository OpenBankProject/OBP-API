package code.api.util.dynamiccompiler

import code.api.util.DynamicUtil
import code.setup.{EnvVarOverride, PropsReset}
import net.liftweb.common.Box
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * `allow_user_generated_scala_code` is the master kill-switch for run-time compilation of
 * user-supplied Scala - the RCE surface described in the migration plan's S-4. With it off,
 * nothing must reach the compiler, whichever compiler is behind the DynamicScalaCompiler seam.
 *
 * A separate suite from DynamicCompilerFourChainPocTest on purpose. That suite pushes the
 * switch ON in every test, and Lift resolves a property against its stack of locked providers
 * in a way that does not let a later "false" push override those - the assertion passed when
 * the suite ran alone and failed in the full run. PropsReset wipes owned pushes at suite
 * start, so a suite whose only push is "false" gives the same answer either way.
 */
class DynamicCompilerKillSwitchTest extends AnyFlatSpec with Matchers with PropsReset with EnvVarOverride {

  // run_tests_parallel.sh exports OBP_ALLOW_USER_GENERATED_SCALA_CODE=true for every shard
  // (mirroring CI), and that env var beats setPropsValues in APIUtil.getPropsValue - so the
  // env var has to be overridden too, or this suite passes alone and fails in the full run.
  // Same approach as code.api.v4_0_0.DynamicCodeKillSwitchTest.
  private def withDynamicCodeDisabled[A](f: => A): A =
    withEnvOverride("OBP_ALLOW_USER_GENERATED_SCALA_CODE" -> "false") {
      setPropsValues("allow_user_generated_scala_code" -> "false")
      f
    }

  "the kill switch" should "stop trivial code from compiling when it is off" in {
    withDynamicCodeDisabled {
      DynamicUtil.dynamicCodeExecutionEnabled should be(false)
      val result: Box[Any] = DynamicUtil.compileScalaCode[Any]("""1 + 1""")
      result.isDefined should be(false)
    }
  }

  it should "stop a connector-style method_body from compiling when it is off" in {
    withDynamicCodeDisabled {
      val result: Box[Any] = DynamicUtil.compileScalaCode[Any](
        """def getBankName(bankId: String): String = { "bank-" + bankId }
          |getBankName _""".stripMargin)
      result.isDefined should be(false)
    }
  }
}
