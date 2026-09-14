/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.api.util

import net.liftweb.common.{Full, Failure}
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Verifies that the GraalVM Polyglot JS engine (org.graalvm.polyglot:polyglot 24.x)
 * loads and executes correctly at runtime. Requires JDK 17+ — GraalVM 24.x JARs are
 * compiled at class-file version 61.0 and throw UnsupportedClassVersionError on JDK 11.
 * If this test fails with that error, the runtime JDK must be upgraded to 17+.
 */
class DynamicUtilJsEngineTest extends FlatSpec with Matchers {

  private val engineMustLoad = "GraalVM engine must load successfully"
  private val promiseMustResolve = "JS promise must resolve"

  "DynamicUtil.createJsFunction" should "load the GraalVM JS engine without error" in {
    val result = DynamicUtil.createJsFunction("return 42;")
    result shouldBe a [Full[_]]
  }

  it should "execute JS returning a literal and yield JSON-stringified result" in {
    val fn = DynamicUtil.createJsFunction("return 42;")
      .openOrThrowException(engineMustLoad)
    val boxResult = Await.result(fn(Array.empty[AnyRef], None), 10.seconds)
    boxResult shouldBe a [Full[_]]
    val (json, _) = boxResult.openOrThrowException(promiseMustResolve)
    json shouldBe "42"
  }

  it should "execute JS returning an object and yield valid JSON" in {
    val fn = DynamicUtil.createJsFunction("""return {"status": "ok", "value": 99};""")
      .openOrThrowException(engineMustLoad)
    val boxResult = Await.result(fn(Array.empty[AnyRef], None), 10.seconds)
    boxResult shouldBe a [Full[_]]
    val (json, _) = boxResult.openOrThrowException(promiseMustResolve)
    json should include ("\"status\"")
    json should include ("\"ok\"")
  }

  it should "return Failure on JS syntax error without throwing" in {
    val result = DynamicUtil.createJsFunction("{{ this is not valid JavaScript {{{{")
    result shouldBe a [Failure]
  }

  it should "pass args into JS and compute with them" in {
    val fn = DynamicUtil.createJsFunction("return args[0] * 2;")
      .openOrThrowException(engineMustLoad)
    val boxResult = Await.result(fn(Array[AnyRef](Integer.valueOf(21)), None), 10.seconds)
    boxResult shouldBe a [Full[_]]
    val (json, _) = boxResult.openOrThrowException(promiseMustResolve)
    json shouldBe "42"
  }
}
