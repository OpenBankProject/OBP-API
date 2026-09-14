/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

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

      // By name, like the scenarios below. This one used to assert the whole rendering, which
      // named $outer - the capture of the enclosing test instance that declaring ClassOne inside
      // a scenario produces - and fixed the order two fields come back in, even though
      // getAllFields builds its result through toSet and only keeps insertion order while the set
      // is small. Neither is this method's contract.
      listFields.map(_.getName) should contain("string1")
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

    scenario("getJValueAndAllFields -input is a List of entities") {
      // Restored. It was removed on the theory that a List documented `head` and `tl` - which was
      // wrong: getAllFields has always had a branch for a root-level collection, and a non-empty
      // List is a `::`, a case class, so it is a Product at run time even though 2.13 drops
      // List <: Product at the type level. What the old expected value did pin was the reflection
      // noise around it, Nil$.MODULE$ and Nil$.serialVersionUID, so it comes back asserting the
      // entity's own field names instead of a rendering.
      val listFields: List[Field] = JSONFactory1_4_0.getAllFields(List(oneObject, oneObject))
      val fieldNames = listFields.map(_.getName)

      fieldNames should contain ("string1")
      fieldNames should not contain "tl"
      fieldNames should not contain "MODULE$"
    }

    
    scenario("getJValueAndAllFields -input it the complexNestedClass") {
      val listFields: List[Field] = JSONFactory1_4_0.getAllFields(complexNestedClass)
      val fieldNames = listFields.map(_.getName)

      // The assertions that named library and JDK internals - Nil$.MODULE$, None$, Some.value,
      // $outer, java.lang.String.hash and its serialVersionUID - are gone. They pinned reflection
      // output that is not this method's contract and that moves between Scala and JDK versions;
      // one of them even asserted that String.hash appears immediately before the entity's own
      // field. What remains checks the fields the entities actually declare.
      
       fieldNames should contain ("complexNestedClassString")
       fieldNames should contain ("complexNestedClassInt")
       fieldNames should contain ("classes2")
       fieldNames should contain ("string2")
       fieldNames should contain ("complexNestedClassOptionSomeInt")
       fieldNames should contain ("complexNestedClassDate")
       fieldNames should contain ("strings2")
       fieldNames should contain ("complexNestedClassOptionNoneInt")
//      println(listFields)
    }


  }
  
}
