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
