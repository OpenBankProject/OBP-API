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
package code.api.v7_0_0

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole
import code.api.util.ErrorMessages.DynamicCodeLangNotSupport
import code.api.v7_0_0.Http4s700.Implementations7_0_0
import code.api.v7_0_0.JSONFactory700.DynamicResourceDocCompileJsonV700
import code.entitlement.Entitlement
import code.setup.ServerSetupWithTestData
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.util.ApiVersion
import org.json4s.JsonAST.{JArray, JBool, JInt}
import org.json4s.native.Serialization.write
import org.scalatest.Tag

import java.net.URLEncoder

/**
 * This suite covers the v7.0.0 dry-run compile of a Dynamic Resource Doc method body
 * (POST /management/dynamic-resource-docs/compile): the role gate, and the compiler diagnostics for
 * each value of programming_lang, with line numbers relative to the body the author sent.
 */
class CompileDynamicResourceDocTest extends ServerSetupWithTestData {

  object VersionOfApi extends Tag(ApiVersion.v7_0_0.toString)
  object ApiEndpoint1 extends Tag(nameOf(Implementations7_0_0.compileDynamicResourceDoc))

  def compileRequest = (baseRequest / "obp" / "v7.0.0" / "management" / "dynamic-resource-docs" / "compile").POST

  // Braced body on purpose: .github/scripts/check_test_isolation.py only recognises `def name {` as a helper.
  private def dynamicCodeOn(): Unit = {
    setPropsValues("allow_user_generated_scala_code" -> "true")
  }

  private def grantCreateRole(): Unit =
    Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, ApiRole.canCreateDynamicResourceDoc.toString)

  private def body(methodBody: String, programmingLang: Option[String]) = write(DynamicResourceDocCompileJsonV700(
    request_verb = "POST",
    request_url = "/compile_test/MY_USER_ID",
    method_body = URLEncoder.encode(methodBody, "UTF-8"),
    // The example bodies generate the case classes the Scala example body refers to; Java ignores them.
    example_request_body = SwaggerDefinitionsJSON.jsonDynamicResourceDoc.exampleRequestBody,
    success_response_body = SwaggerDefinitionsJSON.jsonDynamicResourceDoc.successResponseBody,
    programming_lang = programmingLang
  ))

  private val validScalaBody = SwaggerDefinitionsJSON.jsonDynamicResourceDoc.decodedMethodBody

  // Line 9 of this body (the `return` line) refers to a variable that does not exist.
  private val brokenJavaBody =
    """package code.api.util.dynamic;
      |
      |import java.util.function.Function;
      |import java.util.function.Supplier;
      |
      |public class CompileTestBrokenJava implements Supplier<Function<Object[], Object>> {
      |    @Override
      |    public Function<Object[], Object> get() {
      |        return args -> undefinedVariable;
      |    }
      |}
      |""".stripMargin

  private val validJavaBody = brokenJavaBody
    .replace("CompileTestBrokenJava", "CompileTestValidJava")
    .replace("undefinedVariable", "\"ok\"")

  feature("Compile Dynamic Resource Doc (dry run)") {
    scenario("401 without a user, 403 without the create role", ApiEndpoint1, VersionOfApi) {
      dynamicCodeOn()
      makePostRequest(compileRequest, body(validScalaBody, None)).code should equal(401)
      makePostRequest(compileRequest <@ (user1), body(validScalaBody, None)).code should equal(403)
    }

    scenario("a Scala body compiles when programming_lang is omitted or Scala", ApiEndpoint1, VersionOfApi) {
      dynamicCodeOn(); grantCreateRole()
      for (lang <- List(None, Some("Scala"), Some("scala"))) {
        val response = makePostRequest(compileRequest <@ (user1), body(validScalaBody, lang))
        withClue(s"programming_lang $lang, response ${response.body}: ") {
          response.code should equal(200)
          (response.body \ "compiles") should equal(JBool(true))
        }
      }

      Given("a Scala body with a syntax error on its second line")
      // A syntax error, not a type error: the Scala toolbox reports type errors without a position.
      val broken = makePostRequest(compileRequest <@ (user1), body("val fine = 1\nval broken = )\nFuture.successful((fine, HttpCode.`200`(callContext.callContext)))", None))
      Then("the error is reported on line 2 of the body as sent")
      withClue(s"response ${broken.body}: ") {
        (broken.body \ "compiles") should equal(JBool(false))
        val errors = (broken.body \ "errors").asInstanceOf[JArray].arr
        errors should not be empty
        (errors.head \ "line") should equal(JInt(2))
      }
    }

    scenario("a Java body is compiled by the Java compiler", ApiEndpoint1, VersionOfApi) {
      dynamicCodeOn(); grantCreateRole()
      Given("a valid Java body")
      val valid = makePostRequest(compileRequest <@ (user1), body(validJavaBody, Some("Java")))
      Then("it compiles")
      withClue(s"response ${valid.body}: ") {
        valid.code should equal(200)
        (valid.body \ "compiles") should equal(JBool(true))
      }

      Given("a Java body with an error on line 9")
      val broken = makePostRequest(compileRequest <@ (user1), body(brokenJavaBody, Some("Java")))
      Then("the error is reported on line 9 of the body as sent, not on a line of the server's own")
      withClue(s"response ${broken.body}: ") {
        broken.code should equal(200)
        (broken.body \ "compiles") should equal(JBool(false))
        val errors = (broken.body \ "errors").asInstanceOf[JArray].arr
        errors should not be empty
        (errors.head \ "line") should equal(JInt(9))
        (errors.head \ "message").values.toString should include("undefinedVariable")
      }

      Given("the same valid Java body sent as Scala")
      val asScala = makePostRequest(compileRequest <@ (user1), body(validJavaBody, Some("Scala")))
      Then("it does not compile: the language decides the compiler")
      (asScala.body \ "compiles") should equal(JBool(false))
    }

    scenario("an unsupported programming_lang is rejected before anything is compiled", ApiEndpoint1, VersionOfApi) {
      dynamicCodeOn(); grantCreateRole()
      val response = makePostRequest(compileRequest <@ (user1), body(validScalaBody, Some("Cobol")))
      response.code should equal(400)
      (response.body \ "message").values.toString should include(DynamicCodeLangNotSupport.takeWhile(_ != ':'))
    }
  }
}
