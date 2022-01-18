/**
Open Bank Project - API
Copyright (C) 2011-2022, TESOBE GmbH.

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

import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON
import code.api.v4_0_0.V400ServerSetup
import code.api.v4_0_0.dynamic.CompiledObjects
import code.setup.PropsReset
import org.scalatest.Tag



class DynamicEndpointsTest extends V400ServerSetup  with PropsReset {
  object DynamicUtilsTag extends Tag("DynamicEndpoints")

  feature("test DynamicEndpoints.CompiledObjects.validateDependency method") {
    scenario("validateDependency should work well "){
      //This mean, we are only disabled the v4.0.0, all other versions should be enabled
      setPropsValues(
        "dynamic_code_compile_validate_enable" -> "true",
        "dynamic_code_compile_validate_dependencies" -> """[
                                                          |      NewStyle.function.getClass.getTypeName -> "*",
                                                          |      CompiledObjects.getClass.getTypeName -> "sandbox",
                                                          |      HttpCode.getClass.getTypeName -> "200",
                                                          |      DynamicCompileEndpoint.getClass.getTypeName -> "getPathParams, scalaFutureToBoxedJsonResponse",
                                                          |      APIUtil.getClass.getTypeName -> "errorJsonResponse, errorJsonResponse$default$1, errorJsonResponse$default$2, errorJsonResponse$default$3, errorJsonResponse$default$4, scalaFutureToLaFuture, futureToBoxedResponse",
                                                          |      ErrorMessages.getClass.getTypeName -> "*",
                                                          |      ExecutionContext.Implicits.getClass.getTypeName -> "global",
                                                          |      JSONFactory400.getClass.getTypeName -> "createBanksJson",
                                                          |      classOf[Sandbox].getTypeName -> "runInSandbox",
                                                          |      classOf[CallContext].getTypeName -> "*",
                                                          |      classOf[ResourceDoc].getTypeName -> "getPathParams",
                                                          |      "scala.reflect.runtime.package$" -> "universe",
                                                          |      PractiseEndpoint.getClass.getTypeName + "*" -> "*"
                                                          |]""".stripMargin
      )

      val jsonDynamicResourceDoc = SwaggerDefinitionsJSON.jsonDynamicResourceDoc
      CompiledObjects(jsonDynamicResourceDoc.exampleRequestBody, jsonDynamicResourceDoc.successResponseBody, jsonDynamicResourceDoc.methodBody)
        .validateDependency() 
    }
  }
}