package code.util

import net.liftweb.json
import net.liftweb.json.{Diff, JNothing, JNull}
import net.liftweb.json.JsonAST.{JArray, JBool, JDouble, JField, JInt, JObject, JString, JValue}
import org.apache.commons.lang3.StringUtils
import net.liftweb.json.JsonDSL._
import net.liftweb.json.JsonParser.ParseException

object JsonUtils {
  /* match string that end with '[number]', e.g: hobby[3] */
  private val RegexArrayIndex = """(.*?)\[(\d+)\]$""".r
  /* match string that end with '[]', e.g: hobby[] */
  private val RegexArray = """(.*?)\[\]$""".r

  /* match 'boolean: true' style string, to extract boolean value */
  private val RegexBoolean = """(?i)\s*'\s*boolean\s*:\s*(.*)'\s*""".r
  /* match 'double: 123.11' style string, to extract number value */
  private val RegexDouble = """(?i)\s*'\s*(double|number)\s*:\s*(.*)'\s*""".r
  /* match 'int: 123.11' style string, to extract number value */
  private val RegexInt = """(?i)\s*'\s*int\s*:\s*(.*)'\s*""".r
  /* match 'abc' style string, to extract abc */
  private val RegexStr = """\s*'(.*)'\s*""".r

  /* one of charactor: + - * / ~ & \ | */
  private val opStr = """\+|\-|\*|/|~|&|\|"""
  /* string express: 'hello '' !', content can contains two ' */
  private val strExp = """'.+?[^']'(?!')"""
  /* plus string express: + 'hello world !' */
 // private val plusStrExp = s"""\\+\\s*$strExp"""

  /* match string !abc.efg or -abc.efg or abc.efg */
  private val RegexSingleExp = s"""\\s*([!-])?\\s*($strExp|[^$opStr]+?)\\s*""".r
  /* match string part "& abc.efg | abc.fgh" of "!abc.efg & abc.efg | abc.fgh" */
  val RegexOtherExp = s"""\\s*(\\+\\s*$strExp|[$opStr]\\s*[^$opStr]+)""".r
  /* match string -abc.efg * hij.klm or abc.efg ~ hij.klm */
  val RegexMultipleExp = s"""\\s*($strExp|[!-]?.+?)(($RegexOtherExp)+)""".r
  /* extract operation and operand, e.g: "+ abc.def" extract ("+", "abc.def")*/
  val RegexOpExp = s"""\\s*((\\+)\\s*($strExp)|([$opStr])(.+?))""".r
  val RegexSimplePath = s"""([^$opStr!']+)""".r

  /**
   * according schema and source json to build new json
   * @param source source json
   * @param schema new built json schema, name and value can have express
   * @return built json
   */
  def buildJson(source: JValue, schema: JValue): JValue = {
    transformField(schema){
      case (jField, path) if path.contains("$default") =>
        jField

      case (JField(name, v), _) if name.endsWith("$default") =>
        val newName = StringUtils.substringBeforeLast(name,"$default")
        JField(newName, v)

      case (JField(name, JString(s)), _) if name.endsWith("[]") => {
        val jValue = calculateValue(source, s)
        val jArray = jValue match {
          case array: JArray => array
          case _ @JNothing | _ @JNull => JArray(Nil)
          case v => JArray(List(v))
        }
        val newName = StringUtils.substringBeforeLast(name, "[]")
        JField(newName, jArray)
      }

      case (JField(name, jArray: JArray), _) if name.endsWith("[]") =>
        val newName = StringUtils.substringBeforeLast(name, "[]")
        JField(newName, jArray)

      case (JField(name, jObj @JObject(jFields)), _) if name.endsWith("[]") =>
        val newName = StringUtils.substringBeforeLast(name, "[]")
        if(jFields.isEmpty) {
          JField(newName, JArray(Nil))
        } else if(allValueIsSameSizeArray(jFields)) {
          /*convert the follow structure
           * {
           *  "foo[]": {
           *    "name": ["Ken", "Billy"],
           *    "address": [{
           *        "name": "Berlin",
           *        "PostCode" : 116100
           *      }, {
           *        "name": "DaLian",
           *        "PostCode": 11660
           *      }]
           *  }
           * }
           * to real list:
           *{
           *  "foo": [{
           *     "name": "Ken",
           *     "address": {
           *        "name": "Berlin",
           *        "PostCode" : 116100
           *      }
           *    }, {
           *    "name": "Billy",
           *    "address": {
           *        "name": "DaLian",
           *        "PostCode": 11660
           *    }
           * }]
           */

          val arraySize = jFields.head.value.asInstanceOf[JArray].arr.size
          val allFields = for {
            i <- (0 until arraySize).toList
            newJFields = jFields.collect {
              case JField(fieldName, JArray(arr)) => JField(fieldName, arr(i))
            }
          } yield {
            JObject(newJFields)
          }
          JField(newName, JArray(allFields))
        } else {
          JField(newName, JArray(jObj :: Nil))
        }

      // to avoid regex do check multiple times, make top case to match regex.
      case (JField(name, jValue), _) if RegexArrayIndex.findFirstIn(name).isDefined => {
        val RegexArrayIndex(newName, indexStr) = name
        val index = indexStr.toInt
        jValue match {
          case JString(s) =>
            val value = getIndexValue(calculateValue(source, s), index)
            JField(newName, value)

          case jArray: JArray => JField(newName, jArray(index))

          case jObj @JObject(jFields) =>
            if(jFields.isEmpty) {
              JField(newName, JNothing)
            } else if(allValueIsSameSizeArray(jFields)) {
              /*convert the follow structure
               * {
               *  "foo[1]": {
               *    "name": ["Ken", "Billy"],
               *    "address": [{
               *        "name": "Berlin",
               *        "PostCode" : 116100
               *      }, {
               *        "name": "DaLian",
               *        "PostCode": 11660
               *      }]
               *  }
               * }
               * to real list:
               *{
               *  "foo": [{
               *     "name": "Ken",
               *     "address": {
               *        "name": "Berlin",
               *        "PostCode" : 116100
               *      }
               *    }]
               */
              val newFields =  jFields.collect {
                case JField(fieldName, arr :JArray) => JField(fieldName, getIndexValue(arr, index))
              }
              if(newFields.forall(_.value == JNothing)) JField(newName, JNothing) else JField(newName, JObject(newFields))
            } else {
              JField(newName, jObj)
            }
        }

      }

      case (JField(name, JString(s)), _) =>
        JField(name, calculateValue(source, s))

    }
  }

  def buildJson(source: String, schema: JValue): JValue = buildJson(json.parse(source), schema)

  def buildJson(source: String, schema: String): JValue = buildJson(source, json.parse(schema))

  /**
   * Get given index value from path value, get direct index value if it JArray, escape IndexOutOfBoundsException
   * @param jValue
   * @param index
   * @return JNothing or array element of given index
   */
  private def getIndexValue(jValue: JValue, index: Int): JValue = jValue match {
    case JArray(arr) => arr.lift(index)
    case _ => JNothing
  }

  /**
   * check whither all the JField value is same size JArray
   * @param jFields
   * @return
   */
  def allValueIsSameSizeArray(jFields: List[JField]): Boolean = {
    val firstFieldSize = jFields.headOption.collect {
      case JField(_, JArray(arr)) => arr.size
    }

    firstFieldSize.exists( arraySize => jFields.tail.forall {
      case JField(_, JArray(arr)) => arr.size == arraySize
      case _ => false
    })
  }

  /**
   * according callback function to transform all nested fields
   * @param jValue
   * @param f
   * @return
   */
  def mapField(jValue: JValue)(f: (JField, String) => JField): JValue = {
    def buildPath(parentPath: String, currentFieldName: String): String =
      if(parentPath == "") currentFieldName else s"$parentPath.$currentFieldName"

    def rec(v: JValue, path: String): JValue = v match {
      case JObject(l) => JObject(l.map { field =>
        f(field.copy(value = rec(field.value, buildPath(path, field.name))), path)
      }
      )
      case JArray(l) => JArray(l.map(rec(_, path)))
      case x => x
    }
    rec(jValue, "")
  }

  /**
   * according callback function to transform all fulfill fields
   * @param jValue
   * @param f
   * @return
   */
  def transformField(jValue: JValue)(f: PartialFunction[(JField, String), JField]): JValue = mapField(jValue) { (field, path) =>
    if (f.isDefinedAt(field, path)) f(field, path) else field
  }

  /**
   * enhance JValue, to support operations: !,+,-*,/,&,|
   * @param jValue
   */
  implicit class EnhancedJValue(jValue: JValue) {
    def unary_- : JValue = jValue match{
      case JDouble(num) => JDouble(-num)
      case JInt(num) => JInt(-num)
      case JArray(arr) => JArray(arr.map(-_))
      case n @(JNothing | JNull) => n
      case _ => throw new IllegalArgumentException(s"$jValue is not number or Array[number] type, not support - operation")
    }
    def unary_! : JValue = jValue match{
      case JBool(value) => JBool(!value)
      case JArray(arr) => JArray(arr.map(!_))
      case n @(JNothing | JNull) => n
      case _ => throw new IllegalArgumentException(s"$jValue is not boolean or Array[boolean] type, not support ! operation")
    }

    def &(v: JValue): JValue = (jValue, v) match {
      case ((JNothing | JNull), JBool(_)) => JBool(false)
      case (JBool(_), (JNothing | JNull)) => JBool(false)
      case (JBool(v1), JBool(v2)) => JBool(v1 & v2)
      case (JArray(v1), JArray(v2)) =>
        val indexes = (0 until Math.max(v1.size, v2.size)).toList
        val newArray = indexes map {index => getIndexValue(jValue, index) & getIndexValue(v, index) }
        JArray(newArray)
      case (JArray(v1), _: JBool) =>
        val newArray = v1 map {_ & v }
        JArray(newArray)
      case (_: JBool, JArray(v1)) =>
        val newArray = v1 map {jValue & _}
        JArray(newArray)
      case _ => throw new IllegalArgumentException(s"operand of operation & must be two boolean or Array[boolean] value, but current values: $jValue , $v")
    }

    def |(v: JValue): JValue = (jValue, v) match {
      case ((JNothing | JNull), b :JBool) => b
      case (b: JBool, (JNothing | JNull)) => b
      case (JBool(v1), JBool(v2)) => JBool(v1 || v2)
      case (JArray(v1), JArray(v2)) =>
        val indexes = (0 until Math.max(v1.size, v2.size)).toList
        val newArray = indexes map {index => getIndexValue(jValue, index) | getIndexValue(v, index) }
        JArray(newArray)
      case (JArray(v1), _: JBool) =>
        val newArray = v1 map {_ | v }
        JArray(newArray)
      case (_: JBool, JArray(v1)) =>
        val newArray = v1 map {jValue | _}
        JArray(newArray)
      case _ => throw new IllegalArgumentException(s"operand of operation | must be two boolean or Array[boolean] value, but current values: $jValue , $v")
    }

    def ~(v: JValue): JValue = (jValue, v) match {
      case ((JNothing | JNull), _: JObject) => v
      case (_: JObject, (JNothing | JNull)) => jValue
      case (v1: JObject, v2: JObject) => v1 ~ v2
      case (JArray(v1), JArray(v2)) =>
        val indexes = (0 until Math.max(v1.size, v2.size)).toList
        val newArray = indexes map {index => getIndexValue(jValue, index) ~ getIndexValue(v, index) }
        JArray(newArray)
      case (JArray(v1), _: JObject) =>
        val newArray = v1 map { _ ~ v }
        JArray(newArray)
      case (_: JObject, JArray(v1)) =>
        val newArray = v1 map {jValue ~ _}
        JArray(newArray)
      case _ => throw new IllegalArgumentException(s"operand of operation ~ must be two object or Array[object] value, but current values: $jValue , $v")
    }

    def +(v: JValue): JValue = (jValue, v) match {
      case ((JNothing | JNull), _) => v
      case (_, (JNothing | JNull)) => jValue
      case (JDouble(v1), JDouble(v2)) => JDouble((BigDecimal(v1) + BigDecimal(v2)).toDouble)
      case (JInt(v1), JInt(v2)) => JInt(v1 + v2)
      case (JDouble(v1), JInt(v2)) => JDouble((BigDecimal(v1) + BigDecimal(v2)).toDouble)
      case (JInt(v1), JDouble(v2)) => JDouble((BigDecimal(v1) + BigDecimal(v2)).toDouble)
      case (JString(v1), JString(v2)) => JString(v1 + v2)
      case (JArray(v1), JArray(v2)) =>
        val indexes = (0 until Math.max(v1.size, v2.size)).toList
        val newArray = indexes map {index => getIndexValue(jValue, index) + getIndexValue(v, index) }
        JArray(newArray)
      case (JArray(v1), (_:JInt | _: JDouble | _: JString)) =>
        val newArray = v1 map {_ + v }
        JArray(newArray)
      case ((_:JInt | _: JDouble | _: JString), JArray(v1)) =>
        val newArray = v1 map {jValue + _}
        JArray(newArray)
      case _ => throw new IllegalArgumentException(s"operand of operation + must be String, number, Array[String] or Array[number], but current values: $jValue , $v")
    }

    def -(v: JValue): JValue = (jValue, v) match {
      case (JDouble(v1), JDouble(v2)) => JDouble((BigDecimal(v1) - BigDecimal(v2)).toDouble)
      case (JInt(v1), JInt(v2)) => JInt(v1 - v2)
      case (JDouble(v1), JInt(v2)) => JDouble((BigDecimal(v1) - BigDecimal(v2)).toDouble)
      case (JInt(v1), JDouble(v2)) => JDouble((BigDecimal(v1) - BigDecimal(v2)).toDouble)
      case (JArray(v1), JArray(v2)) =>
        val indexes = (0 until Math.max(v1.size, v2.size)).toList
        val newArray = indexes map {index => getIndexValue(jValue, index) - getIndexValue(v, index) }
        JArray(newArray)
      case (JArray(v1), (_:JInt| _: JDouble)) =>
        val newArray = v1 map {_ - v }
        JArray(newArray)
      case ((_:JInt| _: JDouble), JArray(v1)) =>
        val newArray = v1 map {jValue - _}
        JArray(newArray)
      case _ => throw new IllegalArgumentException(s"operand of operation - must be number or Array[number], but current values: $jValue , $v")
    }

    def *(v: JValue): JValue = (jValue, v) match {
      case (JDouble(v1), JDouble(v2)) => JDouble((BigDecimal(v1) * BigDecimal(v2)).toDouble)
      case (JInt(v1), JInt(v2)) => JInt(v1 * v2)
      case (JDouble(v1), JInt(v2)) => JDouble((BigDecimal(v1) * BigDecimal(v2)).toDouble)
      case (JInt(v1), JDouble(v2)) => JDouble((BigDecimal(v1) * BigDecimal(v2)).toDouble)
      case (JArray(v1), JArray(v2)) =>
        val indexes = (0 until Math.max(v1.size, v2.size)).toList
        val newArray = indexes map {index => getIndexValue(jValue, index) * getIndexValue(v, index) }
        JArray(newArray)
      case (JArray(v1), (_:JInt| _: JDouble)) =>
        val newArray = v1 map {_ * v }
        JArray(newArray)
      case ((_:JInt| _: JDouble), JArray(v1)) =>
        val newArray = v1 map {jValue * _}
        JArray(newArray)
      case _ => throw new IllegalArgumentException(s"operand of operation * must be number or Array[number], but current values: $jValue , $v")
    }

    def /(v: JValue): JValue = (jValue, v) match {
      case (JDouble(v1), JDouble(v2)) => JDouble((BigDecimal(v1) / BigDecimal(v2)).toDouble)
      case (JInt(v1), JInt(v2)) => JInt(v1 / v2)
      case (JDouble(v1), JInt(v2)) => JDouble((BigDecimal(v1) / BigDecimal(v2)).toDouble)
      case (JInt(v1), JDouble(v2)) => JDouble((BigDecimal(v1) / BigDecimal(v2)).toDouble)
      case (JArray(v1), JArray(v2)) =>
        val indexes = (0 until Math.max(v1.size, v2.size)).toList
        val newArray = indexes map {index => getIndexValue(jValue, index) / getIndexValue(v, index) }
        JArray(newArray)
      case (JArray(v1), (_:JInt| _: JDouble)) =>
        val newArray = v1 map {_ / v }
        JArray(newArray)
      case ((_:JInt| _: JDouble), JArray(v1)) =>
        val newArray = v1 map {jValue / _}
        JArray(newArray)
      case _ => throw new IllegalArgumentException(s"operand of operation / must be number or Array[number], but current values: $jValue , $v")
    }
  }

  /**
   * according path express, calculate JValue
   * @param jValue source json
   * @param pathExp path expression, e.g: "result.price * 'int: 2' + result.count"
   * @return calculated JValue
   */
  private def calculateValue(jValue: JValue, pathExp: String): JValue = {
    pathExp match {
      case RegexSimplePath(p) => getValueByPath(jValue, p)
      case RegexMultipleExp(firstExp, otherExp, _, _) => {
        val firstValue = getValueByPath(jValue, firstExp)
        RegexOtherExp.findAllIn(otherExp).map {
          case RegexOpExp(_, op1, exp1, op2, exp2) => pre: JValue =>
            val op = Option(op1).getOrElse(op2)
            val exp = Option(exp1).getOrElse(exp2)
            val cur = getValueByPath(jValue, exp)
            op match {
            case "+" => pre + cur
            case "-" => pre - cur
            case "*" => pre * cur
            case "/" => pre / cur
            case "~" => pre ~ cur
            case "&" => pre & cur
            case "|" => pre | cur
          }
        }.foldLeft(firstValue)((v, fun)=> fun(v))
      }
      case RegexSingleExp(_, _) =>
        getValueByPath(jValue, pathExp)
    }
  }

  /**
   * get json nested value according path, path can have prefix ! or -
   * @param jValue source json, if path end with [], result type is JArray,
   *               if end with [number], result type is index element of Array
   * @param pathExpress path, can be prefix by - or !, e.g: "-result.count" "!value.isDeleted"
   * @return given nested field value
   */
  private def getValueByPath(jValue: JValue, pathExpress: String): JValue = {
    pathExpress match {
      case str if str.trim == "$root" || str.trim.isEmpty => jValue // if path is "$root" or "", return whole original json
      case RegexBoolean(b) => JBool(b.toBoolean)
      case RegexDouble(_, n) =>
        JDouble(n.toDouble)
      case RegexInt(n) => JInt(n.toInt)
      case RegexStr(s) => JString(s.replace("''", "'"))// escape '' to '
      case RegexSingleExp(op, s) =>
        val path = StringUtils.substringBeforeLast(s, "[").split('.').toList
        def getField(v: JValue, fieldPath: List[String]): JValue = (v, fieldPath) match {
          case (_, Nil)  => v

          case (JArray(arr), fieldName::tail) =>
            val values = arr.map(_ \ fieldName)
            val newArray = JArray(values)
            getField(newArray, tail)

          case (_, fieldName:: tail) =>
            getField(v \ fieldName, tail)
        }
        val value: JValue = (s, getField(jValue, path)) match {
          //convert Array result to no JArray type, e.g: "someValue": "data.foo.bar[0]"
          case (RegexArrayIndex(_, i), v @JArray(arr)) =>
            assume(arr.forall(it => it == JNothing || it == JNull || it.isInstanceOf[JArray]), s"the path has index: '$pathExpress', that means the result should be two-dimension Array, but the value is one-dimension Array: ${json.prettyRender(v)}")
            val index = i.toInt
            val jObj = arr.map(getIndexValue(_, index))
            JArray(jObj)
          // convert result to JArray type, e.g: "someValue": "data.foo.bar[]"
          case (RegexArray(_), v) =>
            assume(v.isInstanceOf[JArray], s"the path marked as Array: '$pathExpress', that means the result should be Array, but the value is ${json.prettyRender(v)}")
            val newArray = v.asInstanceOf[JArray].arr.map(it => JArray(it :: Nil))
            JArray(newArray)
          case (_, v) => v
        }

        op match {
          case "!" => !value
          case "-" => -value
          case _ => value
        }
    }

  }

  def isFieldEquals(jValue: JValue, path: String, expectValue: String): Boolean = {
    getValueByPath(jValue, path) match {
      case v @(_: JObject | _: JArray) =>
        try{
          val expectJson = json.parse(expectValue)
          val Diff(changed, deleted, added) = v.diff(expectJson)
          changed == JNothing && deleted == JNothing && added == JNothing
        } catch {
          case _: ParseException => false
        }
      case JNothing | JNull => expectValue == ""
      case v => v.values.toString == expectValue
    }
  }
}
