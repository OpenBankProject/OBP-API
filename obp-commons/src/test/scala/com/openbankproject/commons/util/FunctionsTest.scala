package com.openbankproject.commons.util

import com.openbankproject.commons.util.Functions.deepFlatten
import com.openbankproject.commons.util.Functions.Implicits._
import org.scalatest.{FlatSpec, Matchers, Tag}

class FunctionsTest extends FlatSpec with Matchers {
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

  "BinaryOp" should "work with binary operation." taggedAs FunctionsTag in {
    val str: String = "Hello"
    val array: Array[Int] = null

    val noNullStr = str ?: "World"
    val noNullArray = array ?: Array()

    noNullStr  should be equals str

    noNullArray should be equals Array()



    var invoked = false
    str ?: {
          invoked = true
          "This will not be called if str non null."
        }
    invoked should be equals false
  }
}
