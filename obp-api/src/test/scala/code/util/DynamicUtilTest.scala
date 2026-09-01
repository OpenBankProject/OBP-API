/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH.

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

import org.json4s._
import code.api.util.DynamicUtil.Sandbox
import code.api.util._
import code.setup.PropsReset
import com.openbankproject.commons.model.BankId
import com.openbankproject.commons.util.{JsonUtils, ReflectUtils}
import net.liftweb.common.{Box}
import com.openbankproject.commons.util.json
import org.scalatest.{FeatureSpec, FlatSpec, GivenWhenThen, Matchers, Tag}

import java.io.File
import java.security.{AccessControlException}
import scala.collection.immutable.List
import scala.io.Source

class DynamicUtilTest extends FlatSpec with Matchers {
  object DynamicUtilsTag extends Tag("DynamicUtil")

  private val securityManagerUnavailable =
    "SecurityManager enforcement is not available on JDK 17+ (JEP 411); skip on JDK 21"

  /**
   * Skip the sandbox checks when no SecurityManager can enforce them -- but let CI refuse the skip.
   *
   * `assume` cancels, and a cancelled check is indistinguishable from a passing one in every
   * report anybody reads: this suite has shown "canceled 0" while three of its scenarios never
   * ran, because ScalaTest counts a cancellation separately from a failure and the summary line
   * people look at is the failure count. Since JDK 17 removed SecurityManager enforcement
   * (JEP 411, completed by JEP 486), DynamicUtil.Sandbox is a no-op on any modern JDK and these
   * three have been skipping on every run, everywhere, for as long as the build has been on 21+.
   *
   * That is a real gap, not a formality: the sandbox is what stops runtime-compiled endpoint code
   * from reading the filesystem or opening sockets, and nothing else covers it.
   *
   * OBP_TEST_SANDBOX_REQUIRED=true turns the cancellation into a failure, the same lever
   * RedisTestTarget gives the Redis-dependent checks. Set it wherever a JDK that can still
   * enforce is available; leave it unset and the skip stands, but now it is a decision somebody
   * made rather than a silence.
   */
  private def requireSecurityManager(): Unit =
    if (System.getSecurityManager == null) {
      val required = sys.env.get("OBP_TEST_SANDBOX_REQUIRED").exists(_.trim.equalsIgnoreCase("true"))
      if (required)
        fail("OBP_TEST_SANDBOX_REQUIRED=true but no SecurityManager is installed, so the sandbox " +
             "checks cannot run. They are the only cover for what runtime-compiled endpoint code " +
             "is allowed to touch -- run them on a JDK that still enforces, or unset the variable " +
             "to go back to skipping.")
      else cancel(securityManagerUnavailable)
    }

  implicit val formats = code.api.util.CustomJsonFormats.formats


  "DynamicUtil.compileScalaCode method" should "return correct function" taggedAs DynamicUtilsTag in {
      val functionBody: Box[Int => Int] = DynamicUtil.compileScalaCode("def getBank(bankId : Int): Int = bankId+2; getBank _")
      
      val getBankResponse = functionBody.openOrThrowException("")(123)

      getBankResponse should be (125)
  }

  "DynamicUtil.compileScalaCode method" should "compile the permissions and dependences" taggedAs DynamicUtilsTag in {

    val permissions:Box[List[java.security.Permission]] = DynamicUtil.compileScalaCode("""
                                                                                 |List[java.security.Permission](
                                                                                 |      new java.net.NetPermission("specifyStreamHandler"),
                                                                                 |      new java.lang.reflect.ReflectPermission("suppressAccessChecks"),
                                                                                 |      new java.lang.RuntimePermission("getenv.*")
                                                                                 |    ) """.stripMargin)

    val permissionList = permissions.openOrThrowException("Can not compile the string to permissions")
    Sandbox.createSandbox(permissionList)
    permissionList.toString contains ("""java.net.NetPermission""") shouldBe (true)

    val permissionString =
      """[new java.net.NetPermission("specifyStreamHandler"),
        |new java.lang.reflect.ReflectPermission("suppressAccessChecks"),
        |new java.lang.RuntimePermission("getenv.*"),
        |new java.lang.RuntimePermission("accessDeclaredMembers"),
        |new java.lang.RuntimePermission("getClassLoader")]""".stripMargin

    val scalaCode = "List[java.security.Permission]"+permissionString.replaceFirst("\\[","(").dropRight(1)+")"
    val permissions2:Box[List[java.security.Permission]] = DynamicUtil.compileScalaCode(scalaCode)

    val permissionList2 = permissions2.openOrThrowException("Can not compile the string to permissions")
    Sandbox.createSandbox(permissionList2)
    permissionList2.toString contains ("""java.net.NetPermission""") shouldBe (true)

    
    val dependenciesBox: Box[Map[String, Set[String]]] = DynamicUtil.compileScalaCode(s"${DynamicUtil.importStatements}"+"""
                                                                             |
                                                                             |Map[String, String](
                                                                             |      // companion objects methods
                                                                             |      NewStyle.function.getClass.getTypeName -> "*",
                                                                             |      CompiledObjects.getClass.getTypeName -> "sandbox",
                                                                             |      HttpCode.getClass.getTypeName -> "200",
                                                                             |      DynamicCompileEndpoint.getClass.getTypeName -> "getPathParams, scalaFutureToBoxedJsonResponse",
                                                                             |      APIUtil.getClass.getTypeName -> "errorJsonResponse, errorJsonResponse$default$1, errorJsonResponse$default$2, errorJsonResponse$default$3, errorJsonResponse$default$4, scalaFutureToLaFuture, futureToBoxedResponse",
                                                                             |      ErrorMessages.getClass.getTypeName -> "*",
                                                                             |      ExecutionContext.Implicits.getClass.getTypeName -> "global",
                                                                             |      JSONFactory400.getClass.getTypeName -> "createBanksJson",
                                                                             |
                                                                             |      // class methods
                                                                             |      classOf[Sandbox].getTypeName -> "runInSandbox",
                                                                             |      classOf[CallContext].getTypeName -> "*",
                                                                             |      classOf[ResourceDoc].getTypeName -> "getPathParams",
                                                                             |      "scala.reflect.runtime.package$" -> "universe",
                                                                             |
                                                                             |      // allow any method of PractiseEndpoint for test
                                                                             |      PractiseEndpoint.getClass.getTypeName + "*" -> "*",
                                                                             |
                                                                             |    ).mapValues(v => StringUtils.split(v, ',').map(_.trim).toSet).toMap""".stripMargin)
    val dependencies = dependenciesBox.openOrThrowException("Can not compile the string to Map")
    dependencies.toString contains ("code.api.util.NewStyle") shouldBe (true)

    val dependenciesString = """[NewStyle.function.getClass.getTypeName -> "*",CompiledObjects.getClass.getTypeName -> "sandbox",HttpCode.getClass.getTypeName -> "200",DynamicCompileEndpoint.getClass.getTypeName -> "getPathParams, scalaFutureToBoxedJsonResponse",APIUtil.getClass.getTypeName -> "errorJsonResponse, errorJsonResponse$default$1, errorJsonResponse$default$2, errorJsonResponse$default$3, errorJsonResponse$default$4, scalaFutureToLaFuture, futureToBoxedResponse",ErrorMessages.getClass.getTypeName -> "*",ExecutionContext.Implicits.getClass.getTypeName -> "global",JSONFactory400.getClass.getTypeName -> "createBanksJson",classOf[Sandbox].getTypeName -> "runInSandbox",classOf[CallContext].getTypeName -> "*",classOf[ResourceDoc].getTypeName -> "getPathParams","scala.reflect.runtime.package$" -> "universe",PractiseEndpoint.getClass.getTypeName + "*" -> "*"]""".stripMargin
    
    // DynamicUtil.Validation.dependenciesScalaCode, not a copy of it. This line used to be a
    // character-for-character duplicate of the production expression, which meant an edit to
    // either one left the test green while the two disagreed -- and this is the only compile that
    // happens reflectively at boot, so nothing at compile time would have noticed either.
    val scalaCode2 = DynamicUtil.Validation.dependenciesScalaCode(dependenciesString)
    val dependenciesBox2: Box[Map[String, Set[String]]] = DynamicUtil.compileScalaCode(scalaCode2)
    val dependencies2 = dependenciesBox2.openOrThrowException("Can not compile the string to Map")
    dependencies2.toString contains ("code.api.util.NewStyle") shouldBe (true)
  }

  "Sandbox.createSandbox method" should "should throw exception" taggedAs DynamicUtilsTag in {
    requireSecurityManager()
    val permissionList = List(
//      new java.net.SocketPermission("ir.dcs.gla.ac.uk:80","connect,resolve"),
    )

    intercept[AccessControlException] {
      Sandbox.createSandbox(permissionList).runInSandbox {
        scala.io.Source.fromURL("https://apisandbox.openbankproject.com/")
      }
    }
  }

  "Sandbox.createSandbox method" should "should work well" taggedAs DynamicUtilsTag in {
    val permissionList = List(
//      new java.net.SocketPermission("apisandbox.openbankproject.com:443","connect,resolve"),
      new java.util.PropertyPermission("user.dir","read"),
    )

    Sandbox.createSandbox(permissionList).runInSandbox {
//      scala.io.Source.fromURL("https://apisandbox.openbankproject.com/")
      new File(".").getCanonicalPath
    }
  }
  
  "Sandbox.sandbox method test bankId" should "should throw exception" taggedAs DynamicUtilsTag in {
    requireSecurityManager()
    intercept[AccessControlException] {
      Sandbox.sandbox(bankId= "abc").runInSandbox {
        BankId("123" )
      }
    }
  }
  
  "Sandbox.sandbox method test bankId" should "should work well" taggedAs DynamicUtilsTag in {
    Sandbox.sandbox(bankId= "abc").runInSandbox {
      BankId("abc" )
    }
  }

  "Sandbox.sandbox method test default permission" should "should throw exception" taggedAs DynamicUtilsTag in {
    requireSecurityManager()
    intercept[AccessControlException] {
      Sandbox.sandbox(bankId= "abc").runInSandbox {
        scala.io.Source.fromURL("https://apisandbox.openbankproject.com/")
      }
    }
  }

  val zson = {
    """
      |{
      |   "name": "Sam",
      |   "age": [12],
      |   "isMarried": true,
      |   "weight": 12.11,
      |   "class": "2",
      |   "def": 12,
      |   "email": ["abc@def.com", "hijk@abc.com"],
      |   "address": [{
      |     "name": "jieji",
      |     "code": 123123,
      |     "street":{"road": "gongbin", "number": 123},
      |     "_optional_fields_": ["code"]
      |   }],
      |   "street": {"name": "hongqi", "width": 12.11},
      |   "_optional_fields_": ["age", "weight", "address"]
      |}
      |""".stripMargin
  }
  val zson2 = """{"road": "gongbin", "number": 123}"""
  val zson3 = """[{"road": "gongbin", "number": 123}]"""

  def buildFunction(jsonStr: String): String => Any = {
    val caseClasses = JsonUtils.toCaseClasses(json.parse(jsonStr))

    val code =
      s"""
         | $caseClasses
         |
         | // throws exception: org.json4s.MappingException:
         | //No usable value for name
         | //Did not find value which can be converted into java.lang.String
         |
         |implicit val formats = code.api.util.CustomJsonFormats.formats
         |(str: String) => {
         |  com.openbankproject.commons.util.JsonAliases.parse(str).extract[RootJsonClass]
         |}
         |""".stripMargin

    val fun: Box[String => Any] = DynamicUtil.compileScalaCode(code)
    fun.orNull
  }

  "Parse json to dynamic case object" should "success" taggedAs DynamicUtilsTag in {

    val func = buildFunction(zson)
    val func2 = buildFunction(zson2)
    val func3 = buildFunction(zson3)
    val value1 = func.apply(zson)
    val value2 = func2.apply(zson2)
    val value3 = func3.apply(zson3).asInstanceOf[List[_]].head.asInstanceOf[AnyRef]

    ReflectUtils.getNestedField(value1.asInstanceOf[AnyRef], "street", "name") should be ("hongqi")
    ReflectUtils.getField(value1.asInstanceOf[AnyRef], "weight") shouldEqual Some(12.11)
    ReflectUtils.getField(value2.asInstanceOf[AnyRef], "number") shouldEqual (123)
    ReflectUtils.getField(value3, "number") shouldEqual (123)
  }

  "DynamicUtil.toCaseObject method" should "return correct object" taggedAs DynamicUtilsTag in {
    
    val jValueZson2  = json.parse(zson2)
    val zson2Object: Product = DynamicUtil.toCaseObject(jValueZson2)
    zson2Object.isInstanceOf[Product] should be (true)
  }
}