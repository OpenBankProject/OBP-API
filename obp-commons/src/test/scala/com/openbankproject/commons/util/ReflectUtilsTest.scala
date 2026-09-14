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

import org.scalatest.{FlatSpec, Matchers}

import scala.reflect.runtime.universe._
import org.scalatest.Tag
import org.scalatest.matchers.Matcher

class ReflectUtilsTest extends FlatSpec with Matchers {
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
}
