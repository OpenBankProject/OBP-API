package com.openbankproject.commons.util

import java.util.Date

import com.openbankproject.commons.util.Functions.deepFlatten
import com.openbankproject.commons.util.Functions.Implicits._
import org.scalatest.Tag
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class FunctionsTest extends AnyFlatSpec with Matchers {
  object FunctionsTag extends Tag("Functions")

  "deepFlatten" should "flatten all deep elements for Array" taggedAs FunctionsTag in {
     val array = Array("hello", Array("world", Array("foo", Array("bar", "good", Set(1,3))), List("job", "well")))

     deepFlatten(array) should contain theSameElementsAs Array("hello", "world", "foo", "bar", 1, 3, "good", "job", "well")
  }
  "deepFlatten" should "flatten all deep elements for collection" taggedAs FunctionsTag in {
     val list = List("hello", Array("world", Seq("foo", Array("bar", "good", Set(1,3))), List("job", "well")))

     deepFlatten(list) should contain theSameElementsAs List("hello", "world", "foo", "bar", 1, 3, "good", "job", "well")
  }

  "addIfAbsent" should "add element to collection if not contains." taggedAs FunctionsTag in {
     var list = List("hello", "world")

    (list ?+ "good") should contain theSameElementsAs  "good":: list

    (list ?+ "world") should contain theSameElementsAs  list

    list ?+= "good"
    list should contain theSameElementsAs  List("hello", "world", "good")
  }

  "removeIfAbsent" should "add element to collection if not contains." taggedAs FunctionsTag in {
     var list = List("hello", "world")

    (list ?- "good") should contain theSameElementsAs list

    (list ?- "world") should contain theSameElementsAs  List("hello")

    list ?-= "hello"
    list should contain theSameElementsAs  List("world")
  }

  case class FPerson(name: String, age: Int)

  "distinctBy" should "distinct elements by given calculate role." taggedAs FunctionsTag in {
    val list = List(FPerson("foo", 12), FPerson("bar", 15), FPerson("foo", 16))

    list.distinctBy(_.name) should contain theSameElementsAs  List(FPerson("foo", 12), FPerson("bar", 15))
  }

  "classify" should "split a collection into the elements that match and those that do not" taggedAs FunctionsTag in {
    // The production caller is validateRequiredFields in code.bankconnectors, which classifies
    // validation results by isLeft and then reads only the left half; it had no test.
    val list = List(FPerson("foo", 12), FPerson("bar", 15), FPerson("baz", 11))

    val (adults, minors) = list.classify(_.age >= 12)

    adults should contain theSameElementsAs List(FPerson("foo", 12), FPerson("bar", 15))
    minors should contain theSameElementsAs List(FPerson("baz", 11))
  }

  it should "keep the element order of the source collection within each half" taggedAs FunctionsTag in {
    val (even, odd) = List(1, 2, 3, 4, 5, 6).classify(_ % 2 == 0)

    even should equal(List(2, 4, 6))
    odd should equal(List(1, 3, 5))
  }

  it should "return two empty collections for an empty source" taggedAs FunctionsTag in {
    val (matched, unmatched) = List.empty[Int].classify(_ > 0)

    matched should be(empty)
    unmatched should be(empty)
  }

  "toMapByKey and toMapByValue" should "index a collection either way round" taggedAs FunctionsTag in {
    val list = List(FPerson("foo", 12), FPerson("bar", 15))

    list.toMapByKey(_.name) should equal(Map("foo" -> FPerson("foo", 12), "bar" -> FPerson("bar", 15)))
    list.toMapByValue(_.age) should equal(Map(FPerson("foo", 12) -> 12, FPerson("bar", 15) -> 15))
  }

  "notExists" should "be the negation of exists" taggedAs FunctionsTag in {
    val list = List(1, 2, 3)

    list.notExists(_ > 5) should equal(true)
    list.notExists(_ > 2) should equal(false)
  }

  "findByType" should "find one or none element" taggedAs FunctionsTag in {
    val list = List(12, "", new Date(), FPerson("foo", 12), FPerson("bar", 15), FPerson("foo", 16))
    val person = list.findByType[FPerson]

    person should be equals FPerson("foo", 12)
  }

  "BinaryOp" should "work with binary operation." taggedAs FunctionsTag in {
    def str(i: Int): String = "Hello"
    val array: Array[Int] = null

    val noNullStr = str(10) ?: "World"
    val noNullArray = array ?: Array()

    noNullStr should be equals "Hello"

    noNullArray should be equals Array()



    var invoked = false
    str(1) ?: {
          invoked = true
          "This will not be called if str non null."
        }
    invoked should be equals false
  }
}
