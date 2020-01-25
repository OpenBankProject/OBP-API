package com.openbankproject.commons.util

import com.openbankproject.commons.util.Functions.deepFlatten
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
}
