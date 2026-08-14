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

      // Asserted by the names the entity declares, not by an exact rendering of the whole list.
      // The old assertion pinned the entire toString, ordering included, and with it the
      // compiler and library internals that reflection also returns - $outer, Nil$.MODULE$,
      // Nil$.serialVersionUID. None of that is what getAllFields is for, and all of it moves
      // between Scala versions: 2.13's Nil adds an EmptyUnzip field and orders members
      // differently, so the string could not survive the upgrade no matter what the method did.
      val fieldNames = listFields.map(_.getName)
      fieldNames should contain("classes")
      fieldNames should contain("string1")
    }

    // The scenario that passed a List to getAllFields is gone. It only ever compiled because
    // 2.12's List extended Product, and what it pinned was the leak that came with it: its
    // expected value listed Nil$.MODULE$ and Nil$.serialVersionUID alongside the entity's own
    // fields, because getAllFields walks a List's product elements, which are head and tl. 2.13
    // drops List <: Product, so the call no longer typechecks and the leak is unreachable.
    // ResourceDocs that describe an array-shaped body now use APIUtil.jArrayBodyOf.
    
    scenario("getJValueAndAllFields -input it the complexNestedClass") {
      val listFields: List[Field] = JSONFactory1_4_0.getAllFields(complexNestedClass)

      // The assertions that named library and JDK internals - Nil$.MODULE$, None$, Some.value,
      // $outer, java.lang.String.hash and its serialVersionUID - are gone. They pinned reflection
      // output that is not this method's contract and that moves between Scala and JDK versions;
      // one of them even asserted that String.hash appears immediately before the entity's own
      // field. What remains checks the fields the entities actually declare.
      
       listFields.toString contains ("private final java.lang.String code.api.v1_4_0.JSONFactory1_4_0_LightTest$ComplexNestedClass$1.complexNestedClassString, ") shouldBe  (true)
       listFields.toString contains ("private final int code.api.v1_4_0.JSONFactory1_4_0_LightTest$ComplexNestedClass$1.complexNestedClassInt, ") shouldBe (true)
       listFields.toString contains ("private final scala.collection.immutable.List code.api.v1_4_0.JSONFactory1_4_0_LightTest$ComplexNestedClass$1.classes2, ") shouldBe (true)
       listFields.toString contains ("private final java.lang.String code.api.v1_4_0.JSONFactory1_4_0_LightTest$ClassTwo$1.string2, ") shouldBe (true)
       listFields.toString contains ("private final scala.Option code.api.v1_4_0.JSONFactory1_4_0_LightTest$ComplexNestedClass$1.complexNestedClassOptionSomeInt") shouldBe (true)
       listFields.toString contains ("private final java.util.Date code.api.v1_4_0.JSONFactory1_4_0_LightTest$ComplexNestedClass$1.complexNestedClassDate, ") shouldBe (true)
       listFields.toString contains ("private final scala.collection.immutable.List code.api.v1_4_0.JSONFactory1_4_0_LightTest$ClassTwo$1.strings2, ") shouldBe (true)
       listFields.toString contains ("private final scala.Option code.api.v1_4_0.JSONFactory1_4_0_LightTest$ComplexNestedClass$1.complexNestedClassOptionNoneIn") shouldBe (true)
//      println(listFields)
    }


  }
  
}
