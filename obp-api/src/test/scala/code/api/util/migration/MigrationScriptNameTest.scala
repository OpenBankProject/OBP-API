package code.api.util.migration

import com.github.dwickern.macros.NameOf.nameOf
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * Supplying the empty argument list must not change the name a migration script runs under.
 *
 * Migration.scala derives each script's name with `nameOf(script)` and stores it in
 * migration_script_log; `runOnce` skips the script when that name is already there. Scala 3 makes
 * auto-application an error, so every one of those calls had to become `nameOf(script())`, and if
 * the macro read the applied form differently every migration would come back under a new name
 * and run again on a database that has already had it.
 *
 * It does not, and this pins that: the applied form produces the bare method name, with no
 * parentheses in it.
 */
class MigrationScriptNameTest extends AnyFlatSpec with Matchers {

  private def aMigrationScript(): Boolean = true

  "nameOf" should "read the applied form as the bare method name" in {
    nameOf(aMigrationScript()) should equal("aMigrationScript")
  }

  it should "not put the argument list into the name" in {
    nameOf(aMigrationScript()) should not include "("
  }
}
