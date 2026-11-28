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

package code.util

import code.api.util.DynamicUtil
import code.setup.ServerSetup
import net.liftweb.common.Full
import org.scalatest.Tag

/**
 * Dynamic Scala code (connector methods, dynamic endpoints and dynamic resource docs) is
 * compiled at run time against the classes on the classpath, so the compiler of this
 * repository never sees it. A public helper that dynamic code may already call must stay
 * public and keep its signature.
 *
 * The helpers below lost their last caller in this repository when the endpoints of the
 * versions below v5.1.0 were commented out (see docs/OLD_VERSION_ONLY_CODE.md). They are
 * kept on purpose, and this suite fails if one of them is commented out, renamed or changes
 * its parameter types.
 */
class DynamicCodePublicHelpersTest extends ServerSetup {

  object DynamicCodeTag extends Tag("DynamicCodePublicHelpers")

  // Each reference is typed, so a changed signature fails as well as a missing helper. The
  // references sit inside a function that is never called: only compiling is checked here,
  // which does not initialise the version objects.
  private val referencesToPublicHelpers =
    """
      |import code.api.util.{ApiRole, CallContext, NewStyle}
      |import com.openbankproject.commons.model._
      |import net.liftweb.common.Box
      |
      |() => List[Any](
      |  NewStyle.function.ownEntitlement(_: String, _: String, _: ApiRole, _: Option[CallContext], _: String): Box[Unit],
      |  code.api.v1_2_1.JSONFactory.createTransactionsJSON(_: List[code.model.ModeratedTransaction]),
      |  code.api.v1_2_1.JSONFactory.createOtherBankAccountsJSON(_: List[code.model.ModeratedOtherBankAccount]),
      |  code.api.v1_4_0.JSONFactory1_4_0.createAtmsJson(_: List[AtmT]),
      |  code.api.v2_1_0.JSONFactory210.createConsumerJSONs(_: List[code.model.Consumer]),
      |  code.api.v2_1_0.Http4s210.Implementations2_1_0.commonTxReqErrors: List[String],
      |  code.api.v2_2_0.JSONFactory220.createCounterpartiesJSON(_: List[CounterpartyTrait]),
      |  code.api.v2_2_0.JSONFactory220.transformV220ToBranch(_: code.api.v2_2_0.BranchJsonV220),
      |  code.api.v2_2_0.JSONFactory220.transformToAtmFromV220(_: code.api.v2_2_0.AtmJsonV220),
      |  code.api.v3_0_0.JSONFactory300.createAtmsJsonV300(_: List[AtmT]),
      |  code.api.v3_0_0.JSONFactory300.transformToAtmFromV300(_: code.api.v3_0_0.AtmJsonV300),
      |  code.api.v4_0_0.JSONFactory400.createAtmsJsonV400(_: List[AtmT]),
      |  code.api.v4_0_0.JSONFactory400.transformToAtmFromV400(_: code.api.v4_0_0.AtmJsonV400)
      |)
      |""".stripMargin

  feature("Public helpers that dynamic Scala code may call") {
    scenario("dynamic Scala code that refers to each helper compiles", DynamicCodeTag) {
      setPropsValues("allow_user_generated_scala_code" -> "true")

      When("the references are compiled as dynamic Scala code")
      val compiled = DynamicUtil.compileScalaCode[() => List[Any]](referencesToPublicHelpers)

      Then("the code compiles")
      withClue(compiled) {
        compiled shouldBe a[Full[_]]
      }
    }

    scenario("dynamic Scala code can call the list helpers", DynamicCodeTag) {
      setPropsValues("allow_user_generated_scala_code" -> "true")

      When("dynamic Scala code calls two of the helpers with empty lists")
      val compiled = DynamicUtil.compileScalaCode[() => (Int, Int)](
        """
          |() => (
          |  code.api.v2_1_0.JSONFactory210.createConsumerJSONs(Nil).list.size,
          |  code.api.v4_0_0.JSONFactory400.createAtmsJsonV400(Nil).atms.size
          |)
          |""".stripMargin)

      Then("the calls return empty results")
      withClue(compiled) {
        compiled.map(_.apply()) should equal(Full((0, 0)))
      }
    }
  }
}
