package com.openbankproject.commons.util

import com.openbankproject.commons.util.Color.Color
import com.openbankproject.commons.util.Shape.Shape
import org.scalatest._

import scala.reflect.runtime.universe._

import org.scalatest.Tag

// to show bad design of scala enumeration
object Shape extends Enumeration {
  type Shape = Value
  val Circle = Value
  val Square = Value
  val Other = Value
}

object Color extends Enumeration {
  type Color = Value
  val Red = Value
  val Green = Value
  val Other = Value
}

object OBPEnumTag extends Tag("OBPEnumeration")
/**
 * just for demonstrate what problem of scala enumeration, so here just set to ignore
 */
@Ignore
class ScalaEnumerationTest extends FlatSpec with Matchers {

  it should "legal to create two overloaded methods with parameter Shape and Color" taggedAs(OBPEnumTag) in {
    // if remove the comment of process method, will can't compile
    object OverloadTest{
      def process(shape: Shape) = ???
//      def process(Color: Color) = ???
    }
  }

  it should "have compile warnings when match case is not exhaustive with enumeration" taggedAs(OBPEnumTag) in {
    val shape: Shape = Shape.Other
    shape match {
      case Shape.Other => println("hi other")
    }
  }

  "shape.isInstanceOf[Color] " should "return false" taggedAs(OBPEnumTag) in {
    // the worst: confused type check
    val shape: Shape = Shape.Other

    // shape is not Color, isColor should be false
    val isColor = shape.isInstanceOf[Color]
    isColor shouldBe false
  }
  "shape cast to Color" should "throw ClassCastException" taggedAs(OBPEnumTag) in {
    val shape: Shape = Shape.Other
    // shape cast to Color should throw ClassCastException
    a[ClassCastException] should be thrownBy {
      val color: Color = shape.asInstanceOf[Color]
    }
  }
  "if shape can be cast to Color, casted value" should "match Color value" taggedAs(OBPEnumTag) in {
    val shape: Shape = Shape.Other
    val color: Color = shape.asInstanceOf[Color]
    val wrong: String = color match {
      case Color.Other => "match Color#other"
      case _ => "NOT MATCH"
    }
    color.toString shouldBe "Other"

    wrong shouldBe "match Color#other"
  }
}


// to demonstrate OBPEnumeration
sealed trait OBPShape extends EnumValue

object OBPShape extends OBPEnumeration[OBPShape]{
  object Circle extends OBPShape
  object Square extends OBPShape
  object Other extends OBPShape
}

sealed trait OBPColor extends EnumValue

object OBPColor extends OBPEnumeration[OBPColor]{
  object Red extends OBPColor
  object Green extends OBPColor
  object Other extends OBPColor
}

class OBPEnumerationTest extends FlatSpec with Matchers {
  it should "legal to create two overloaded methods with parameter OBPShape and OBPColor" taggedAs(OBPEnumTag) in {
    // first bad: can't overload for different enumeration
    object OverloadTest{
      def process(shape: OBPShape) = ???
      def process(Color: OBPColor) = ???
    }
  }

  // to see the compile warning, just uncomment the follow lines test
  /*it should "have compile warnings when match case is not exhaustive with enumeration" taggedAs(OBPEnumTag) in {
    val shape: OBPShape = OBPShape.Other
    shape match {
      case OBPShape.Other => "hi other"
    }
  }*/

  "shape.isInstanceOf[OBPColor] " should "return false" taggedAs(OBPEnumTag) in {
    // the worst: confused type check
    val shape: OBPShape = OBPShape.Other

    // shape is not Color, isColor should be false
    val isColor = shape.isInstanceOf[OBPColor]
    isColor shouldBe false
  }

  "shape cast to Color" should "throw ClassCastException" taggedAs(OBPEnumTag) in {
    val shape: OBPShape = OBPShape.Other
    // shape cast to Color should throw ClassCastException
    a [ClassCastException] should be thrownBy {
      val color = shape.asInstanceOf[OBPColor]
    }
  }

  "values" should "contains all values" taggedAs(OBPEnumTag) in {
    OBPShape.values should contain only (OBPShape.Square, OBPShape.Circle, OBPShape.Other)
  }

  "example" should "be one value of OBPShape enumeration" taggedAs(OBPEnumTag) in {
    OBPShape.example shouldBe a [OBPShape]
  }
  "nameToValue" should "be name map to value of OBPShape enumeration" taggedAs(OBPEnumTag) in {
    OBPShape.nameToValue should  contain theSameElementsAs Map("Square" -> OBPShape.Square, "Circle" -> OBPShape.Circle, "Other" -> OBPShape.Other)
  }

  it should "get a Some(v) value when call withNameOption to retrieve exists OBPShape value" taggedAs(OBPEnumTag) in {
    OBPShape.withNameOption("Square") shouldBe Some(OBPShape.Square)
  }

  it should "get a None value when call withNameOption to retrieve not exists OBPShape value" taggedAs(OBPEnumTag) in {
    OBPShape.withNameOption("NOT_EXISTS") shouldBe None
  }

  it should "get a value when call withName to retrieve exists OBPShape value" taggedAs(OBPEnumTag) in {
    OBPShape.withName("Circle") shouldBe OBPShape.Circle
  }

  it should "throw NoSuchElementException when call withName to retrieve not exists OBPShape value" taggedAs(OBPEnumTag) in {
    a [NoSuchElementException] should be thrownBy {
      OBPShape.withName("NOT_EXISTS") shouldBe OBPShape.Circle
    }
  }

  // the follow test is for OBPEnumeration utils functions
  "call OBPEnumeration.getValuesByType with an OBPEnumeration type" should "get all values" taggedAs(OBPEnumTag) in {
    val unknownType = typeOf[OBPColor]
    OBPEnumeration.getValuesByType(unknownType) shouldBe(OBPColor.values)
  }
  "call OBPEnumeration.getValuesByClass with an OBPEnumeration type" should "get all values" taggedAs(OBPEnumTag) in {
    val unknownClazz = classOf[OBPShape].asInstanceOf[Class[EnumValue]]
    OBPEnumeration.getValuesByClass(unknownClazz) should be(OBPShape.values)
  }
  "call OBPEnumeration.withNameOption with an OBPEnumeration type OR class and exists name" should "get correct Some(v)" taggedAs(OBPEnumTag) in {
    val unknownType = typeOf[OBPShape]
    val unknownClazz = classOf[OBPShape].asInstanceOf[Class[EnumValue]]

    OBPEnumeration.withNameOption(unknownType, "Square") shouldBe Some(OBPShape.Square)
    OBPEnumeration.withNameOption(unknownClazz, "Circle") shouldBe Some(OBPShape.Circle)
  }
  "call OBPEnumeration.withNameOption with an OBPEnumeration type OR class and not exists name" should "get correct None" taggedAs(OBPEnumTag) in {
    val unknownType = typeOf[OBPShape]
    val unknownClazz = classOf[OBPShape].asInstanceOf[Class[EnumValue]]

    OBPEnumeration.withNameOption(unknownType, "NOT_EXISTS") shouldBe empty
    OBPEnumeration.withNameOption(unknownClazz, "NOT_EXISTS") shouldBe empty
  }
  "call OBPEnumeration.withName with an OBPEnumeration type OR class and exists name" should "get correct value" taggedAs(OBPEnumTag) in {
    val unknownType = typeOf[OBPShape]
    val unknownClazz = classOf[OBPShape].asInstanceOf[Class[EnumValue]]

    OBPEnumeration.withName(unknownType, "Square") shouldBe OBPShape.Square
    OBPEnumeration.withName(unknownClazz, "Circle") shouldBe OBPShape.Circle
  }
  "call OBPEnumeration.withName with an OBPEnumeration type OR class and not exists name" should "throw NoSuchElementException" taggedAs(OBPEnumTag) in {
    a [NoSuchElementException] should be thrownBy {
      val unknownType = typeOf[OBPShape]
      OBPEnumeration.withName(unknownType, "NOT_EXISTS")
    }

    a [NoSuchElementException] should be thrownBy {
      val unknownClazz = classOf[OBPShape].asInstanceOf[Class[EnumValue]]
      OBPEnumeration.withName(unknownClazz, "NOT_EXISTS")
    }
  }


  "call OBPEnumeration.withIndexOption with an OBPEnumeration type OR class and exists taggedAs(OBPEnumTag) index" should "get correct Some(v)" taggedAs(OBPEnumTag) in {
    val unknownType = typeOf[OBPShape]
    val unknownClazz = classOf[OBPShape].asInstanceOf[Class[EnumValue]]

    OBPEnumeration.withIndexOption(unknownType, 0) shouldBe Some(OBPShape.Circle)
    OBPEnumeration.withIndexOption(unknownClazz, 1) shouldBe Some(OBPShape.Square)
  }
  "call OBPEnumeration.withIndexOption with an OBPEnumeration type OR class and not exists taggedAs(OBPEnumTag) index" should "get correct None" taggedAs(OBPEnumTag) in {
    val unknownType = typeOf[OBPShape]
    val unknownClazz = classOf[OBPShape].asInstanceOf[Class[EnumValue]]

    OBPEnumeration.withIndexOption(unknownType, 4) shouldBe empty
    OBPEnumeration.withIndexOption(unknownClazz, -1) shouldBe empty
  }
  "call OBPEnumeration.withIndex with an OBPEnumeration type OR class and exists taggedAs(OBPEnumTag) index" should "get correct value" taggedAs(OBPEnumTag) in {
    val unknownType = typeOf[OBPShape]
    val unknownClazz = classOf[OBPShape].asInstanceOf[Class[EnumValue]]

    OBPEnumeration.withIndex(unknownType, 0) shouldBe OBPShape.Circle
    OBPEnumeration.withIndex(unknownClazz, 1) shouldBe OBPShape.Square
  }
  "call OBPEnumeration.withIndex with an OBPEnumeration type OR class and not exists taggedAs(OBPEnumTag) index" should "throw NoSuchElementException" taggedAs(OBPEnumTag) in {
    a [NoSuchElementException] should be thrownBy {
      val unknownType = typeOf[OBPShape]
      OBPEnumeration.withIndex(unknownType, 8)
    }

    a [NoSuchElementException] should be thrownBy {
      val unknownClazz = classOf[OBPShape].asInstanceOf[Class[EnumValue]]
      OBPEnumeration.withIndex(unknownClazz, -1)
    }
  }

  "call OBPEnumeration.getExampleByType" should "get one of values" taggedAs(OBPEnumTag) in {
    val unknownType = typeOf[OBPShape]
    OBPEnumeration.getExampleByType(unknownType) shouldBe(OBPShape.Circle)
  }

  "call OBPEnumeration.getExampleByClass" should "get one of values" taggedAs(OBPEnumTag) in {
    val unknownClazz = classOf[OBPColor].asInstanceOf[Class[EnumValue]]
    OBPEnumeration.getExampleByClass(unknownClazz) shouldBe(OBPColor.Red)
  }
}