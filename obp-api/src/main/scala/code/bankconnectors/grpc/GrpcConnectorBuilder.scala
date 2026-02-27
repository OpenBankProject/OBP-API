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
