package code.api.util.liquibase

import java.util.jar.JarFile
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * snakeyaml must not be dragged backwards by a dependency that happens to sit nearer the root.
 *
 * Adding liquibase-core did exactly that: it declares snakeyaml 2.2 directly, jackson-dataformat-yaml
 * pulls 2.3 one level deeper, and Maven's nearest-wins resolved the whole build down to 2.2 without
 * printing anything. The version came back only because the dependency tree was diffed before and
 * after on purpose. Nothing else would have caught it - the build succeeds either way, and no test
 * asserted a version.
 *
 * So the exclusion in obp-api/pom.xml is held in place by an assertion rather than by a comment, and
 * the assertion is against the jar actually on the test classpath rather than against the pom, since
 * the pom is what was already believed to be right.
 *
 * The floor is a minimum, not an equality: upgrading snakeyaml should not fail this.
 */
class SnakeYamlVersionTest extends AnyFlatSpec with Matchers {

  private val floor = (2, 3)

  /** Read the version off the manifest of the jar the class was actually loaded from. */
  private def loadedSnakeYamlVersion: Option[(Int, Int)] = {
    val location = Option(classOf[org.yaml.snakeyaml.Yaml].getProtectionDomain.getCodeSource)
      .flatMap(cs => Option(cs.getLocation))
    location.flatMap { url =>
      val path = java.nio.file.Paths.get(url.toURI).toString
      if (!path.endsWith(".jar")) None
      else {
        val jar = new JarFile(path)
        try {
          // Bundle-Version rather than Implementation-Version: snakeyaml ships the OSGi header and
          // not the other one, on both 2.2 and 2.3.
          Option(jar.getManifest.getMainAttributes.getValue("Bundle-Version")).flatMap { v =>
            v.split("\\.").toList match {
              case major :: minor :: _ => scala.util.Try((major.toInt, minor.toInt)).toOption
              case _                   => None
            }
          }
        } finally jar.close()
      }
    }
  }

  "snakeyaml" should s"be at least ${floor._1}.${floor._2} on the test classpath" in {
    val version = loadedSnakeYamlVersion
    withClue("could not read the version from the loaded jar's manifest: ") {
      version should not be empty
    }
    withClue(
      s"snakeyaml was resolved to ${version.map { case (a, b) => s"$a.$b" }.getOrElse("?")}, below " +
      s"${floor._1}.${floor._2}. A dependency declaring an older version nearer the root has won " +
      s"Maven's nearest-wins - `mvn dependency:tree | grep snakeyaml` names it. Exclude snakeyaml " +
      s"from that dependency in obp-api/pom.xml, as liquibase-core already is. ") {
      version.foreach(_ should be >= floor)
    }
  }
}
