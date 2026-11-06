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

package code.bankconnectors.grpc

import code.bankconnectors.generator.ConnectorBuilderUtil._
import net.liftweb.util.StringHelpers

import scala.language.postfixOps

// To regenerate the dynamic region in GrpcConnector_vFeb2026.scala, run:
//   MAVEN_OPTS="--add-opens java.base/java.lang=ALL-UNNAMED" \
//     mvn exec:java -pl obp-api -Dexec.mainClass="code.bankconnectors.grpc.GrpcConnectorBuilder" -Dexec.classpathScope=compile
//
// Notes:
//   - The --add-opens flag is needed on Java 17+ because ConnectorBuilderUtil uses javassist
//     to rewrite MappedWebUiPropsProvider, which requires reflective access to java.lang.ClassLoader.
//   - If you see "unsafe symbol X (child of package model)", run `mvn install -pl obp-commons`
//     first. The exec:java plugin resolves classpath from the local Maven repo, so newly added
//     commons types won't be found until the commons JAR is reinstalled.
//   - The process may hang after completion due to a non-daemon thread; it is safe to kill it
//     once the file has been updated (check the "created on" timestamp in the dynamic region).
object GrpcConnectorBuilder extends App {

  buildMethods(commonMethodNames.diff(omitMethods),
    "src/main/scala/code/bankconnectors/grpc/GrpcConnector_vFeb2026.scala",
     methodName => s"""sendRequest[InBound]("obp_${StringHelpers.snakify(methodName)}", req, callContext)""")
}
