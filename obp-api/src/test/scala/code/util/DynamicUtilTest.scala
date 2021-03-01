/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH.

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

package code.util

import code.api.util._
import code.setup.PropsReset
import com.openbankproject.commons.util.{JsonUtils, ReflectUtils}
import net.liftweb.common.Box
import net.liftweb.json
import org.scalatest.{FeatureSpec, FlatSpec, GivenWhenThen, Matchers, Tag}

class DynamicUtilTest extends FlatSpec with Matchers {
  object DynamicUtilsTag extends Tag("DynamicUtil")

  implicit val formats = code.api.util.CustomJsonFormats.formats


  "DynamicUtil.compileScalaCode method" should "return correct function" taggedAs DynamicUtilsTag in {
      val functionBody: Box[Int => Int] = DynamicUtil.compileScalaCode("def getBank(bankId : Int): Int = bankId+2; getBank _")
      
      val getBankResponse = functionBody.openOrThrowException("")(123)

      getBankResponse should be (125)
  }


  val zson = {
    """
      |{
      |   "name": "Sam",
      |   "age": [12],
      |   "isMarried": true,
      |   "weight": 12.11,
      |   "class": "2",
      |   "def": 12,
      |   "email": ["abc@def.com", "hijk@abc.com"],
      |   "address": [{
      |     "name": "jieji",
      |     "code": 123123,
      |     "street":{"road": "gongbin", "number": 123},
      |     "_optional_fields_": ["code"]
      |   }],
      |   "street": {"name": "hongqi", "width": 12.11},
      |   "_optional_fields_": ["age", "weight", "address"]
      |}
      |""".stripMargin
  }
  val zson2 = """{"road": "gongbin", "number": 123}"""
  val zson3 = """[{"road": "gongbin", "number": 123}]"""

  def buildFunction(jsonStr: String): String => Any = {
    val caseClasses = JsonUtils.toCaseClasses(json.parse(jsonStr))

    val code =
      s"""
         | $caseClasses
         |
         | // throws exception: net.liftweb.json.MappingException:
         | //No usable value for name
         | //Did not find value which can be converted into java.lang.String
         |
         |implicit val formats = code.api.util.CustomJsonFormats.formats
         |(str: String) => {
         |  net.liftweb.json.parse(str).extract[RootJsonClass]
         |}
         |""".stripMargin

    val fun: Box[String => Any] = DynamicUtil.compileScalaCode(code)
    fun.orNull
  }

  "Parse json to dynamic case object" should "success" taggedAs DynamicUtilsTag in {

    val func = buildFunction(zson)
    val func2 = buildFunction(zson2)
    val func3 = buildFunction(zson3)
    val value1 = func.apply(zson)
    val value2 = func2.apply(zson2)
    val value3 = func2.apply(zson3)
    

    ReflectUtils.getNestedField(value1.asInstanceOf[AnyRef], "street", "name") should be ("hongqi")
    ReflectUtils.getField(value1.asInstanceOf[AnyRef], "weight") shouldEqual Some(12.11)
    ReflectUtils.getField(value2.asInstanceOf[AnyRef], "number") shouldEqual (123)
    ReflectUtils.getField(value3.asInstanceOf[AnyRef], "number") shouldEqual (123)
  }

  "DynamicUtil.toCaseObject method" should "return correct object" taggedAs DynamicUtilsTag in {
    
    val jValueZson2  = json.parse(zson2)
    val zson2Object: Product = DynamicUtil.toCaseObject(jValueZson2)
    zson2Object.isInstanceOf[Product] should be (true)
  }
}