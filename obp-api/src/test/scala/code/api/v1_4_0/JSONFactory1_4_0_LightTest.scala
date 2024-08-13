package code.api.v1_4_0

import code.api.util.CustomJsonFormats
import code.util.Helper.MdcLoggable
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, FeatureSpec, GivenWhenThen, Matchers}

import java.lang.reflect.Field
import java.util.Date

class JSONFactory1_4_0_LightTest extends FeatureSpec 
  with BeforeAndAfterEach 
  with GivenWhenThen
  with BeforeAndAfterAll
  with Matchers 
  with MdcLoggable 
  with CustomJsonFormats {
  
  feature("Test JSONFactory1_4_0.getJValueAndAllFields method") {
    case class ClassOne(
      string1: String = "1"
    )

    case class ClassTwo(
      string2: String = "2",
      strings2: List[String] = List("List-2")
    )

    val oneObject = ClassOne()

    case class NestedClass(
      classes: List[ClassOne] = List(oneObject)
    )

    val twoObject = ClassTwo()

    case class NestedListClass(
      classes1: List[ClassOne] = List(oneObject)
    )

    val nestedClass = NestedClass()
    
    val nestedListClass = NestedListClass()

    case class ComplexNestedClass(
      complexNestedClassString: String = "ComplexNestedClass1",
      complexNestedClassInt: Int = 1, 
      complexNestedClassDate: Date = new Date(), 
      complexNestedClassOptionSomeInt: Option[Int] = Some(1), 
      complexNestedClassOptionNoneInt: Option[Int] = None, 
      classes1: List[ClassOne] = List(oneObject),
      classes2: List[ClassTwo] = List(twoObject),
    )
    
    val complexNestedClass = ComplexNestedClass()
    

    
    scenario("getJValueAndAllFields -input is the oneObject, basic no nested, no List inside") {
      val listFields: List[Field] = JSONFactory1_4_0.getAllFields(oneObject)
      
      val expectedListFieldsString = "List(private final java.lang.String code.api.v1_4_0.JSONFactory1_4_0_LightTest$ClassOne$1.string1, " +
        "private final code.api.v1_4_0.JSONFactory1_4_0_LightTest code.api.v1_4_0.JSONFactory1_4_0_LightTest$ClassOne$1.$outer)"

      listFields.toString shouldBe (expectedListFieldsString)
//      println(listFields)
    }
    
    scenario("getJValueAndAllFields -input it the nestedClass") {
      val listFields: List[Field] = JSONFactory1_4_0.getAllFields(nestedClass)
      val expectedListFieldsString = "List(" +
        "public static final long scala.collection.immutable.Nil$.serialVersionUID, public static scala.collection.immutable.Nil$ scala.collection.immutable.Nil$.MODULE$, " +
        "private final code.api.v1_4_0.JSONFactory1_4_0_LightTest code.api.v1_4_0.JSONFactory1_4_0_LightTest$ClassOne$1.$outer, " +
        "private final code.api.v1_4_0.JSONFactory1_4_0_LightTest code.api.v1_4_0.JSONFactory1_4_0_LightTest$NestedClass$1.$outer, " +
        "private final java.lang.String code.api.v1_4_0.JSONFactory1_4_0_LightTest$ClassOne$1.string1, " +
        "private final scala.collection.immutable.List code.api.v1_4_0.JSONFactory1_4_0_LightTest$NestedClass$1.classes)"
      listFields.toString shouldBe (expectedListFieldsString) 
//      println(listFields)
    }

    scenario("getJValueAndAllFields - input is the List[nestedClass]") {
      val listFields: List[Field] = JSONFactory1_4_0.getAllFields(List(oneObject))
//    it should return all the fields in the List
      val expectedListFieldsString = "List(private final java.lang.String code.api.v1_4_0.JSONFactory1_4_0_LightTest$ClassOne$1.string1, " +
        "private final code.api.v1_4_0.JSONFactory1_4_0_LightTest code.api.v1_4_0.JSONFactory1_4_0_LightTest$ClassOne$1.$outer, " +
        "public static scala.collection.immutable.Nil$ scala.collection.immutable.Nil$.MODULE$, " +
        "public static final long scala.collection.immutable.Nil$.serialVersionUID)"
      listFields.toString shouldBe (expectedListFieldsString)
//      println(listFields)
    }
    
    scenario("getJValueAndAllFields -input it the complexNestedClass") {
      val listFields: List[Field] = JSONFactory1_4_0.getAllFields(complexNestedClass)
      
       listFields.toString contains ("private final java.lang.String code.api.v1_4_0.JSONFactory1_4_0_LightTest$ComplexNestedClass$1.complexNestedClassString, ") shouldBe  (true)
       listFields.toString contains ("private final int code.api.v1_4_0.JSONFactory1_4_0_LightTest$ComplexNestedClass$1.complexNestedClassInt, ") shouldBe (true)
       listFields.toString contains ("private final code.api.v1_4_0.JSONFactory1_4_0_LightTest code.api.v1_4_0.JSONFactory1_4_0_LightTest$ComplexNestedClass$1.$outer,") shouldBe (true)
       listFields.toString contains ("public static final long scala.collection.immutable.Nil$.serialVersionUID, ") shouldBe (true)
       listFields.toString contains ("private final scala.collection.immutable.List code.api.v1_4_0.JSONFactory1_4_0_LightTest$ComplexNestedClass$1.classes2, ") shouldBe (true)
       listFields.toString contains ("private final java.lang.String code.api.v1_4_0.JSONFactory1_4_0_LightTest$ClassTwo$1.string2, ") shouldBe (true)
       listFields.toString contains ("public static scala.collection.immutable.Nil$ scala.collection.immutable.Nil$.MODULE$, public static final long scala.None$.serialVersionUID, ") shouldBe (true)
       listFields.toString contains ("private final code.api.v1_4_0.JSONFactory1_4_0_LightTest code.api.v1_4_0.JSONFactory1_4_0_LightTest$ClassOne$1.$outer, ") shouldBe (true)
       listFields.toString contains ("private final scala.Option code.api.v1_4_0.JSONFactory1_4_0_LightTest$ComplexNestedClass$1.complexNestedClassOptionSomeInt") shouldBe (true)
       listFields.toString contains ("public static scala.None$ scala.None$.MODULE$, private final java.lang.Object scala.Some.value, private final scala.collection.immutable.List code.api.v1_4_0.JSONFactory1_4_0_LightTest$ComplexNestedClass$1.classes1, ") shouldBe (true)
       listFields.toString contains ("private final java.util.Date code.api.v1_4_0.JSONFactory1_4_0_LightTest$ComplexNestedClass$1.complexNestedClassDate, ") shouldBe (true)
       listFields.toString contains ("private final scala.collection.immutable.List code.api.v1_4_0.JSONFactory1_4_0_LightTest$ClassTwo$1.strings2, ") shouldBe (true)
       listFields.toString contains ("private int java.lang.String.hash, private final java.lang.String code.api.v1_4_0.JSONFactory1_4_0_LightTest$ClassOne$1.string1, ") shouldBe (true)
       listFields.toString contains ("private static final long java.lang.String.serialVersionUID,") shouldBe (true)
       listFields.toString contains ("private final code.api.v1_4_0.JSONFactory1_4_0_LightTest code.api.v1_4_0.JSONFactory1_4_0_LightTest$ClassTwo$1.$outer, ") shouldBe (true)
       listFields.toString contains ("private final scala.Option code.api.v1_4_0.JSONFactory1_4_0_LightTest$ComplexNestedClass$1.complexNestedClassOptionNoneIn") shouldBe (true)
//      println(listFields)
    }


  }
  
}
