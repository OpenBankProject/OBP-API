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

package com.openbankproject.commons.util


import scala.reflect.runtime.universe._
import org.scalatest.Tag
import org.scalatest.matchers.Matcher
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import com.openbankproject.commons.model.{BankCommons, BankId}

// Top-level, not a member of ReflectUtilsTest: an inner (path-dependent) class' reflected Type
// resolves to a refinement runtimeClass can't turn back into a java.lang.Class, which is exactly
// the failure getFieldValues's own fallback below is now defended against - keeping this class
// top-level here means the "should exclude a def" test below is actually exercising the field
// vs. method distinction, not that unrelated refinement-type failure mode.
class FieldsAndHelpers {
  val realField: String = "field-value"
  lazy val lazyField: String = "lazy-value"
  def helperMethod: String = "not-a-field"
}

class ReflectUtilsTest extends AnyFlatSpec with Matchers {
  object ReflectUtilsTag extends Tag("ReflectUtils")

  case class Aperson(id: String, age: Int)
  case class Agroup(manager: Aperson, id: Int, members: List[Aperson])

  "when modify Apersion#id to append suffix" should "all the not null id be end with suffix" taggedAs(ReflectUtilsTag) in {
    val members = List(Aperson(null, 10), Aperson("p1-id", 20), Aperson("p2-id", 3))
    val group = Agroup(Aperson("m-id", 11), 3, members)
    val someGroup = Some(group)

    val idSuffix = "---END"

    ReflectUtils.resetNestedFields(someGroup){
      case (fieldName, fieldType, fieldValue: String, ownerType) if(fieldName == "id" && ownerType =:= typeOf[Aperson]) =>
        fieldValue + idSuffix
    }

    group.manager.id should endWith (idSuffix)
    group.id shouldBe(3)
    group.members.head.id shouldBe null

    val endWithSuffix: Matcher[Aperson] = endWith(idSuffix).compose(_.id)
    every(members.tail) should endWithSuffix
  }

  /**
   * BankCommons has a 9-field primary constructor and a 7-field auxiliary constructor
   * (bankId..bankRoutingAddress) whose parameter names are all real declared fields too, so both
   * pass getPrimaryConstructor's "params are a subset of declared fields" filter. Regression for
   * picking the wrong one when the JVM's `alternatives` happens to return the auxiliary
   * constructor first.
   */
  "getPrimaryConstructor" should "resolve the 9-field primary constructor for BankCommons, not the 7-field auxiliary one" taggedAs(ReflectUtilsTag) in {
    val ctor = ReflectUtils.getPrimaryConstructor(typeOf[BankCommons])
    ctor.paramLists.headOption.getOrElse(Nil).size shouldBe 9
  }

  /**
   * The zero-arg-method recovery clause added for Scala 3-compiled val/lazy-val members can't
   * tell a val-accessor from an ordinary def by shape alone - both are a zero-arg method declared
   * directly on the class. Regression for misreporting a genuine helper method as a field.
   */
  "getFieldValues" should "include real vals/lazy vals but exclude an ordinary zero-arg def" taggedAs(ReflectUtilsTag) in {
    val values = ReflectUtils.getFieldValues(new FieldsAndHelpers)()
    values.get("realField") shouldBe Some("field-value")
    values.get("lazyField") shouldBe Some("lazy-value")
    values.get("helperMethod") shouldBe None
  }

  "toOther" should "build a BankCommons using the 9-field primary constructor, not the 7-field auxiliary one" taggedAs(ReflectUtilsTag) in {
    val bank = BankCommons(
      bankId = BankId("bank-id"),
      shortName = "short",
      fullName = "full",
      logoUrl = "logo",
      websiteUrl = "website",
      bankRoutingScheme = "scheme",
      bankRoutingAddress = "address",
      swiftBic = "SWIFTBIC",
      nationalIdentifier = "NATID"
    )
    val converted = ReflectUtils.toOther[BankCommons](bank, typeOf[BankCommons])
    converted.swiftBic shouldBe "SWIFTBIC"
    converted.nationalIdentifier shouldBe "NATID"
  }
}
