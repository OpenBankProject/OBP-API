package code.api.util

import java.util.Date

import com.openbankproject.commons.dto.CustomerAndAttribute
import com.openbankproject.commons.model.enums.TransactionRequestStatus
import com.openbankproject.commons.model.enums.StrongCustomerAuthentication
import com.openbankproject.commons.model.{CardAction, CardReplacementReason, InboundAdapterCallContext, OutboundAdapterCallContext, PinResetReason, Status}
import com.openbankproject.commons.util.{EnumValue, ReflectUtils}
import net.liftweb.util.StringHelpers
import org.apache.commons.lang3.StringUtils

import scala.collection.immutable.List
import scala.language.postfixOps
import scala.reflect.runtime.universe._
import scala.reflect.runtime.{universe => ru}

object CodeGenerateUtils {

  /**
   * when create example, if fieldName and|or fieldType match, the example is fixed value
   * @param fieldName fieldName, this value can be null, but should not together with tp are both null
   * @param tp fieldType, this value can be null, but should not together with fieldName are both null
   * @param example example string
   */
  private case class NameTypeExample(fieldName: String, tp: Type, example: String) {
    assert(StringUtils.isNotBlank(fieldName) || tp != null, s"fieldName and tp should not both empty")

    def isFieldMatch(fieldName: String, tp: Type): Boolean =
      if(tp != null && StringUtils.isNotBlank(this.fieldName)) {
        this.tp <:< tp && fieldName == this.fieldName
      } else if(tp != null) {
        this.tp <:< tp
      } else {
        fieldName == this.fieldName
      }

    def getExample(fieldName: String, tp: Type): Option[String] =
      if(isFieldMatch(fieldName, tp)) {
        Some(example)
      } else {
        None
      }
  }
  // fixed example for given field or type
  private val fixedExamples: List[NameTypeExample] = List(
    NameTypeExample(null, typeOf[OutboundAdapterCallContext], "MessageDocsSwaggerDefinitions.outboundAdapterCallContext"),
    NameTypeExample(null, typeOf[InboundAdapterCallContext], "MessageDocsSwaggerDefinitions.inboundAdapterCallContext"),
    NameTypeExample("status", typeOf[Status], "MessageDocsSwaggerDefinitions.inboundStatus"),
    NameTypeExample("statusValue", typeOf[String], s""""${TransactionRequestStatus.COMPLETED}""""),
//    NameTypeExample("hashOfSuppliedAnswer", typeOf[String], s"""HashUtil.Sha256Hash("123")"""),
    NameTypeExample(null, typeOf[List[CustomerAndAttribute]],
      """ List(
        |         CustomerAndAttribute(
        |             MessageDocsSwaggerDefinitions.customerCommons,
        |             List(MessageDocsSwaggerDefinitions.customerAttribute)
        |          )
        |         )
        |""".stripMargin),
  )


  private def getFixedExample(fieldName: String, tp: Type): Option[String] =
    fixedExamples.find(_.isFieldMatch(fieldName, tp)).map(_.example)

  /**
    * create messageDocs example object string, for example exampleOutboundMessage and exampleInboundMessage,
    * this is just return a string for code generation
    * @param tp to generate type of object
    * @param fieldName field name
    * @param parentFieldName current field belongs parent field name
    * @param parentType current field belongs type
    * @return initialize object string
    */
  def createDocExample(tp: ru.Type, fieldName: Option[String] = None, parentFieldName: Option[String] = None, parentType: Option[ru.Type] = None): String = {
    // if given fieldName and tp have fixed example, just return fixed example
    val fixedExample = getFixedExample(fieldName.orNull, tp)
    if(fixedExample.isDefined) {
      return fixedExample.get
    } else if(tp =:= typeOf[CardAction]) {
      return "com.openbankproject.commons.model.CardAction.DEBIT"
    } else if(tp =:= typeOf[CardReplacementReason]) {
      return "com.openbankproject.commons.model.CardReplacementReason.FIRST"
    } else if(tp =:= typeOf[PinResetReason]) {
      return "com.openbankproject.commons.model.PinResetReason.FORGOT"
    } else if(tp =:= typeOf[StrongCustomerAuthentication.Value]) {
      return "com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SMS"
    } else if(tp <:< typeOf[EnumValue]) {
      return s"${tp.typeSymbol.fullName}.example"
    }

    val uncapitalizedTypeName = StringUtils.uncapitalize(tp.typeSymbol.name.toString)
    // try to get example value from ExampleValue
    val example: Option[String] = {
      var result =  for {
        pName <- parentFieldName
        fName <- fieldName
        example <- getExampleValue(s"$pName${fName.capitalize}")
      } yield example

      if(result.isEmpty) {
        result = for {
          pType <- parentType
          pName = StringUtils.uncapitalize(pType.typeSymbol.name.toString).replaceFirst("(Commons|Trait|T)$", "")
          fName <- fieldName
          example <- getExampleValue(s"$pName${fName.capitalize}", s"$pName${StringHelpers.camelify(fName)}")
        } yield example
      }
      // special logic for InternalBasicUser kind naming, example: usenameExample
      if(result.isEmpty && parentType.filter(_.typeSymbol.name.toString.endsWith("User")).isDefined) {
        result = fieldName.flatMap(it => getExampleValue(s"user${it.capitalize}", s"user$it"))
      }
      // some class name start with Core, should ignore "Core"
      if(result.isEmpty && parentType.filter(_.typeSymbol.name.toString.startsWith("Core")).isDefined) {
        val composedName = parentType.map(_.typeSymbol.name.toString)
          .map(_.replaceFirst("^Core|Commons$", ""))
          .map(StringUtils.uncapitalize)
          .flatMap(it => fieldName.map(it + _.capitalize))
        result = composedName.flatMap(getExampleValue(_))
      }
      if(result.isEmpty) {
        result = fieldName.flatMap(getExampleValue(_))
      }
      if(result.isEmpty) { // emailAdress -> email
        result = fieldName.map(_.replaceFirst("Address$", "")).flatMap(getExampleValue(_))
      }

      //some example name is just type name: TransactionId(value: String) ---> transactionIdExample
      if(result.isEmpty && parentType.exists(_.typeSymbol.name.toString.endsWith("Id"))) {
        result = getExampleValue(parentType.map(_.typeSymbol.name.toString).get)
      }
      //some field name start with other, example otherBankId, then find with bankId
      if(result.isEmpty && fieldName.exists(_.startsWith("other"))) {
        val removedOtherFieldName = fieldName.map(_.substring("other".size)).map(StringUtils.uncapitalize).get
        result = getExampleValue(removedOtherFieldName)
      }
      if(result.isEmpty && fieldName.isDefined && tp <:< typeOf[Date]) {
        val Some(field) = fieldName
        result = getExampleValue(s"${field}Date",s"date${field.capitalize}")
      }
      if(result.isEmpty && (tp =:= typeOf[BigDecimal] || tp =:= typeOf[BigInt])) {
        val Some(field) = fieldName
        result = getExampleValue(s"${field}Amount")
      }
      if(result.isEmpty) {
        result = getExampleValue(uncapitalizedTypeName)
      }
      result
    }

    val typeName = tp.typeSymbol.name.toString
    val fullTypeName = tp.typeSymbol.fullName
    val isObpType = fullTypeName.matches("""com\.openbankproject\.commons\..+|code\..+""")
    val isAbstractType = tp.typeSymbol.asClass.isAbstract

    // if type is OBP project defined, get the concrete type, or get None
    val concreteObpType: Option[ru.Type] = (isObpType, isAbstractType) match {
      case (false, _) => None
      case (true, false) => Some(tp)
      case _ =>
        ReflectUtils.findImplementedClass(fullTypeName)
        .map(ReflectUtils.classToType(_))
    }

    // if type is OBP project defined, and constructor have single parameter, return true
    def isConstructorSingleParam = concreteObpType match {
      case None => false
      case Some(t) => ReflectUtils.getPrimaryConstructor(t).paramLists.headOption.exists(_.size == 1)
    }

    //is OBP project defined type, and have single constructor parameter, return single parameter type, or return None
    def getSingleConstructorType = concreteObpType match {
      case Some(t) => ReflectUtils.getPrimaryConstructor(t).paramLists.headOption match {
        case Some(singleType::Nil) => Some(singleType.info)
        case _ => None
      }
      case _ => None
    }

    if (tp =:= ru.typeOf[String]) {
      example
        .getOrElse(""""string"""")
    } else if (tp =:= ru.typeOf[Int] || tp =:= ru.typeOf[java.lang.Integer]) {
      example.map(it => s"$it.toInt").getOrElse("123")
    } else if (tp =:= ru.typeOf[Long] || tp =:= ru.typeOf[java.lang.Long]) {
      example.map(it => s"$it.toLong").getOrElse("123")
    } else if (tp =:= ru.typeOf[Float] || tp =:= ru.typeOf[java.lang.Float]) {
      example.map(it => s"$it.toFloat").getOrElse("123.123")
    } else if (tp =:= ru.typeOf[Double] || tp =:= ru.typeOf[java.lang.Double]) {
      example.map(it => s"$it.toDouble").getOrElse("123.123")
    } else if (tp =:= ru.typeOf[BigDecimal]) {
      val numberValue = example.getOrElse(""""123.321"""")
      s"""BigDecimal($numberValue)"""
    } else if (tp =:= ru.typeOf[Date]) {
      example.orElse(Some("dateExample.value"))
        .map(date => {
            val exampleName = StringUtils.substringBeforeLast(date, ".value")
            s"toDate($exampleName)"
          }
        )
        .get
    } else if (tp =:= ru.typeOf[Boolean] || tp =:= ru.typeOf[java.lang.Boolean]) {
      example.map(it => s"$it.toBoolean").getOrElse("true")
    } else if(concreteObpType.isDefined && isConstructorSingleParam) {
      example match {
        case Some(v) if(getSingleConstructorType.get =:= typeOf[String]) => s"""${concreteObpType.get.typeSymbol.name}($v)"""
        case _ => {
          val value = createDocExample(getSingleConstructorType.get, fieldName, parentFieldName, parentType)
          s"""${concreteObpType.get.typeSymbol.name}($value)"""
        }
      }
    } else if(tp <:< typeOf[Option[_]]) {
      val TypeRef(_, _, args: List[Type]) = tp
      val optionValue = createDocExample(args.head, fieldName, parentFieldName, parentType)
      s"""Some($optionValue)"""
    } else if(tp <:< typeOf[Map[String, List[String]]]) {
      s"""Map("some_name" -> List("name1", "name2"))"""
    } else if(typeName.matches("""Array|List|Seq""")) {
      val TypeRef(_, _, args: List[Type]) = tp
      (example, typeName) match {
        case (Some(v), "Array") if(args.head =:= typeOf[String]) => s"""$v.replace("[","").replace("]","").split(",")"""
        case (Some(v), "List")  if(args.head =:= typeOf[String]) => s"""$v.replace("[","").replace("]","").split(",").toList"""
        case (Some(v), "Seq")   if(args.head =:= typeOf[String]) => s"""$v.replace("[","").replace("]","").split(",").toSeq"""
        case (Some(v), "Array") if(args.head =:= typeOf[Date]) => s"""$v.replace("[","").replace("]","").split(",").map(parseDate).flatMap(_.toSeq)"""
        case (Some(v), "List")  if(args.head =:= typeOf[Date]) => s"""$v.replace("[","").replace("]","").split(",").map(parseDate).flatMap(_.toSeq).toList"""
        case (Some(v), "Seq")   if(args.head =:= typeOf[Date]) => s"""$v.replace("[","").replace("]","").split(",").map(parseDate).flatMap(_.toSeq).toSeq"""
        case (_, collName) if ReflectUtils.isObpType(args.head) =>
          val itemExpression = createDocExample(args.head, fieldName, parentFieldName, parentType)
          s"$collName($itemExpression)"
        case (None, _) => {
          val singleValue = createDocExample(args.head, fieldName.map(_.replaceFirst("s$", "")))// if fieldName endsWith s, remove s
          s"""$typeName($singleValue)"""
        }
      }
    } else if(typeName.matches("""Tuple\d+""")) {
      val TypeRef(_, _, args: List[Type]) = tp
       args.map(createDocExample(_)).mkString("(", ", ", ")")
    } else if (isObpType) {
      val fields = concreteObpType.orNull.decls.find(it => it.isConstructor).toList.flatMap(_.asMethod.paramLists(0)).foldLeft("")((str, symbol) => {
        val valName = symbol.name.toString
        val TypeRef(pre: Type, sym: Symbol, args: List[Type]) = symbol.info
        val value = if (pre <:< ru.typeOf[EnumValue]) {
          s"${pre.typeSymbol.fullName}.example"
        } else {
            createDocExample(symbol.info, Some(valName), fieldName, Some(tp))
        }
        val valueName = symbol.name.toString.replaceFirst("^type$", "`type`")
        s"""$str,
           |${valueName}=${value}""".stripMargin
      }).replaceFirst("""^\s*,\s*""", "")
      val withNew = if(!concreteObpType.get.typeSymbol.asClass.isCaseClass) "new" else ""
      s"$withNew ${concreteObpType.get.typeSymbol.name}($fields)"
    } else {
      throw new IllegalStateException(s"type ${fieldName.map(_+": ").getOrElse("")}$tp is not supported, please add this type to here.")
    }
  }

  private def getExampleValue(name: String, otherNames: String*): Option[String] =
    (name +: otherNames).collectFirst {
    case x if exampleNameToValue.contains(x + "Example") => exampleNameToValue(x + "Example")
    case x if exampleNameToValue.contains(x) => exampleNameToValue(x)
  }

  /**
    * extract ExampleValues, to map, key is removed Example val name, value is ConnectorField#value
    */
  private lazy val exampleNameToValue: Map[String, String] = {
    ExampleValue.exampleNameToValue.keys.map(exampleName => {
      (exampleName, s"$exampleName.value")
    }).toMap

  }
}
