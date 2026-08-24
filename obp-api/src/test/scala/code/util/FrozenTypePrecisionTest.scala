package code.util

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/**
 * The frozen type contract must record what a field is, not what erasure left of it.
 *
 * FrozenClassTest exists to fail when a STABLE API's shape changes. It reads each field's type
 * through `scala-reflect`, which reads ScalaSig - an attribute only Scala 2 classes carry. On a
 * Scala 3 class it falls back to the class file's Java generic signature, where a value type cannot
 * be a type argument: `Option[Long]` is emitted as `scala.Option<java.lang.Object>`. So the flip
 * quietly recorded seventeen fields as `Option[Object]` - and a contract that says `Option[Object]`
 * cannot fail when `Option[Long]` becomes `Option[Int]`, which is exactly the change it is there to
 * catch.
 *
 * The example value is the only runtime source of the erased type, and every one of these fields has
 * one: SwaggerFactoryUnitTest's dangling-$ref check fails if an Option of a value type reachable
 * from a resource doc's example bodies has no value, for the same reason. FrozenClassUtil refines
 * from it; this fails if any field slips back to the erased form, whether because a new field
 * arrives without an example or because the refinement is removed.
 */
class FrozenTypePrecisionTest extends AnyFlatSpec with Matchers {

  "the frozen type fixture" should "record no field as an erased Option[Object]" in {
    val textPath = Paths.get(FrozenMetaDataText.textPathOf(FrozenClassUtil.persistFilePath))
    assume(Files.exists(textPath), s"fixture not rendered yet: $textPath")

    val erased = new String(Files.readAllBytes(textPath), StandardCharsets.UTF_8)
      .linesIterator
      .filter(_.startsWith("field\t"))
      .filter(l => l.endsWith("\tOption[Object]") || l.endsWith("\tObject"))
      .toList

    withClue(
      s"${erased.size} field(s) are recorded at their erased type, so a change to what they " +
      "actually hold cannot fail FrozenClassTest. Each is an Option of a value type whose example " +
      "value FrozenClassUtil refines from - an offender here means that example is missing, or the " +
      s"refinement is gone:\n${erased.mkString("\n")}\n") {
      erased shouldBe empty
    }
  }
}
