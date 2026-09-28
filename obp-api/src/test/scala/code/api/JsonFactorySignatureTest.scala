package code.api

import java.io.File

import org.scalatest.{FlatSpec, Matchers}

import scala.io.Source

/**
 * This suite fails when json4s cannot read the field names of a case class declared inside a
 * JSONFactory object.
 *
 * json4s finds a case class's field names in the compiled Scala signature (for a nested class, the
 * enclosing object's). When it cannot, every request or response using that class fails with
 * "Can't find ScalaSig", a 400 or 500 on any endpoint whose body or response uses it. This suite
 * asks json4s to describe every nested case class, which is the lookup that would fail.
 */
class JsonFactorySignatureTest extends FlatSpec with Matchers {

  private val sourceRoot: File =
    List(new File("src/main/scala"), new File("obp-api/src/main/scala")).find(_.isDirectory)
      .getOrElse(throw new IllegalStateException("JsonFactorySignatureTest cannot find obp-api/src/main/scala"))

  private def walk(dir: File): List[File] =
    Option(dir.listFiles).toList.flatten.flatMap(f => if (f.isDirectory) walk(f) else List(f))

  /** Fully qualified names of every `object JSONFactory...` in the source. */
  private lazy val factoryObjects: List[String] =
    walk(sourceRoot).filter(f => f.getName.startsWith("JSONFactory") && f.getName.endsWith(".scala")).flatMap { file =>
      val source = Source.fromFile(file, "UTF-8")
      val text = try source.mkString finally source.close()
      val pkg = """(?m)^package\s+([\w.]+)""".r.findFirstMatchIn(text).map(_.group(1))
      """(?m)^object\s+(JSONFactory\w*)""".r.findAllMatchIn(text).map(_.group(1)).toList.flatMap(name => pkg.map(p => s"$p.$name"))
    }

  "The JSONFactory objects" should "be found" in {
    factoryObjects should contain("code.api.v7_0_0.JSONFactory700")
    factoryObjects.size should be > 5
  }

  they should "have every nested case class readable by json4s" in {
    val unreadable = factoryObjects.flatMap { name =>
      Class.forName(name + "$").getDeclaredClasses.toList
        .filter(c => classOf[Product].isAssignableFrom(c) && !c.getName.endsWith("$"))
        .flatMap { caseClass =>
          try { org.json4s.reflect.Reflector.describe(org.json4s.reflect.Reflector.scalaTypeOf(caseClass)); None }
          catch { case e: Throwable => Some(s"${caseClass.getName}: ${e.getMessage}") }
        }
    }
    withClue("json4s cannot read these case classes. Declare new case classes at package level, not inside the " +
      "JSONFactory object (see JSONFactory700Operations.scala).\n" + unreadable.take(20).mkString("\n") + "\n") {
      unreadable shouldBe empty
    }
  }
}
