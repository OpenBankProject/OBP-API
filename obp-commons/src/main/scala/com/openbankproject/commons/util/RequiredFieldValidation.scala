package com.openbankproject.commons.util

import java.util.Objects

import com.openbankproject.commons.util.ApiVersion.allVersion
import net.liftweb.json.JsonAST.{JArray, JField, JNothing, JNull, JObject, JString}

import scala.annotation.StaticAnnotation
import scala.reflect.runtime.universe._
import Functions.Implicits._
import net.liftweb.json.{Formats, JValue}
import net.liftweb.json.JsonDSL._

import scala.collection.GenTraversableOnce
import scala.collection.mutable.ArrayBuffer

/**
 * Mark given type's field or constructor variable is required for some apiVersion
 *
 * Only the follow three style be legal
 * example:
 *  required for all versions: [allVersion]
 *  > @OBPRequired
 *
 *  required for some versions except some versions: [v3_0_0, v4_1_0]
 *  > @OBPRequired(Array(ApiVersion.v3_0_0, ApiVersion.v4_1_0))
 *
 *  required for all versions except some versions: [-v3_0_0, -v4_1_0]
 *  > @OBPRequired(value=Array(ApiVersion.allVersion), exclude=Array(ApiVersion.v3_0_0, ApiVersion.v4_1_0))
 *
 * Note: The include and exclude parameter should not change order, because this is not a real class, it is annotation, scala's
 * annotation not allowed switch parameter's order as these:
 *    > @OBPRequired(exclude = Array(ApiVersion.allVersion), value = Array(ApiVersion.v3_0_0)
 *    > @OBPRequired(exclude = Array(ApiVersion.allVersion))
 *
 * @param value do validate for these apiVersions
 * @param exclude not do validate for these apiVersions
 */
@scala.annotation.meta.field
@scala.annotation.meta.param
class OBPRequired(value: Array[ApiVersion] = Array(ApiVersion.allVersion),
                  exclude: Array[ApiVersion] = Array.empty
                 ) extends StaticAnnotation


sealed class RequiredFields
/**
 * cooperate with RequiredInfo to deal with generate swagger doc and json serialization problem (show wrong structure of json)
 * field `data.bankId`: it is special identifier name that just for ResourceDoc definitions
 */
object FieldNameApiVersions extends RequiredFields with JsonAble {
  val `data.bankId`: List[String] = List(ApiVersion.v2_2_0.toString, ApiVersion.v3_1_0.toString)

  override def toJValue(implicit format: Formats): JObject = "data.bankId" -> JArray(this.`data.bankId`.map(JString(_)))
}

/**
 * cooperate with RequiredInfo to deal with generate swagger doc and json serialization problem (show wrong structure of json)
 * @param requiredArgs
 */
case class RequiredInfo(requiredArgs: Seq[RequiredArgs]) extends RequiredFields with JsonAble {

  override def toJValue(implicit format: Formats): JObject = {
    val jFields = requiredArgs
        .toList
        .map(info => JField(
                      info.fieldPath,
                      JArray(info.apiVersions.map(JString(_)))
                    )
        )
    JObject(jFields)
  }

  /**
   * extract given type instance from JValue, and do validation according the ApiVersion.
   * if all required fields fulfill, return extracted value, or return missing field names.
   * @param jValue
   * @param apiVersion
   * @param formats
   * @param mf
   * @tparam T
   * @return Right if validation passed, Left if validation fail
   */
  def validateAndExtract[T](jValue: JValue, apiVersion: ApiVersion)(implicit formats: Formats, mf: scala.reflect.Manifest[T]): Either[List[String], T] = {
    def isEmpty(jv: JValue) = jv == null || jv == JNothing || jv == JNull

    val noValuePath = scala.collection.mutable.ListBuffer[String]()
    val map = scala.collection.mutable.Map[String, JValue]()
    for {
        arg <- this.requiredArgs
        if arg.isNeedValidate(apiVersion)
        fieldPath = arg.fieldPath
        fieldPathParts = fieldPath.split('.')
        scannedPath <- fieldPathParts.tail.scan(fieldPathParts.head)((pre, cur) => s"$pre.$cur") //e.g: Array("data", "data.user", "data.user.name")
    } {
      val (prePathOption, currentPath) = splitPath(scannedPath)
      val prePathValue = prePathOption match {
        case Some(prePath) => map(prePath)
        case None => jValue
      }

      val scannedPathValue: JValue = prePathValue match {
          case JArray(arr) => {
            val (jArrayList, jValueList) = arr.classify(_.isInstanceOf[JArray])
            val newArr: List[JValue] = jArrayList.flatMap(_.asInstanceOf[JArray].arr) :: jValueList

            val noEmpties = newArr.filterNot(isEmpty)
            JArray(noEmpties) \ currentPath
          }
          case any => any \ currentPath // JObject | JNothing | JNull | JString...  all can't navigate sub path
        }

      map(scannedPath) = scannedPathValue

      // if fieldPath corresponding value is empty, add to result.
      if(scannedPath == fieldPath) {
        if(prePathValue != JNothing && prePathValue != JNull) {
          (prePathValue, scannedPathValue) match {
            case (_: JObject, JNothing | JNull) => noValuePath += fieldPath
            case (JArray(arr), JNothing | JNull) if arr.exists(it => it != JNothing && it != JNull) => noValuePath += fieldPath
            case (JArray(_), JArray(arr)) if arr.filter(isEmpty).nonEmpty  => noValuePath += fieldPath
            case _ =>  () // do nothing
          }
        }
      }

    }

    if(noValuePath.isEmpty) {
      Right(jValue.extract[T])
    } else {
      Left(noValuePath.toList)
    }
  }

 def validate[T](entity: T, apiVersion: ApiVersion): Either[List[String], T] = {

    val noValuePath = scala.collection.mutable.ListBuffer[String]()
    val map = scala.collection.mutable.Map[String, Any]()
    for {
        arg <- this.requiredArgs
        if arg.isNeedValidate(apiVersion)
        fieldPath = arg.fieldPath
        fieldPathParts = fieldPath.split('.')
        scannedPath <- fieldPathParts.tail.scan(fieldPathParts.head)((pre, cur) => s"$pre.$cur") //e.g: Array("data", "data.user", "data.user.name")
    } {
      val (prePathOption, currentPath) = splitPath(scannedPath)
      val prePathValue = prePathOption match {
        case Some(prePath) => map(prePath)
        case None => flatten(entity)
      }

      val scannedPathValue: Any = prePathValue match {
          case null => null
          case arr: Array[_] => arr.filterNot(null ==)
            .map(ele => ReflectUtils.getField(ele.asInstanceOf[AnyRef], currentPath))
          case any: AnyRef => ReflectUtils.getField(any, currentPath)
        }

      map(scannedPath) = flatten(scannedPathValue)

      // if fieldPath corresponding value is empty, add to result.
      if(scannedPath == fieldPath) {
        if(prePathValue != JNull) {
          (prePathValue, scannedPathValue) match {
            case (_: Array[_], arr: Array[_]) if arr.exists(null ==) => noValuePath += fieldPath
            case (_: AnyRef, null) => noValuePath += fieldPath
            case _ =>  () // do nothing
          }
        }
      }

    }

    if(noValuePath.isEmpty) {
      Right(entity)
    } else {
      Left(noValuePath.toList)
    }
  }

  /**
   * when parameter is Array or collection, do deep flatten; when it is other type, return itself
   * @param any
   * @return
   */
  private def flatten(any: Any): Any = any match {
    case a:Array[_] => Functions.deepFlatten(a)
    case ab: ArrayBuffer[_] => Functions.deepFlatten(ab.toArray[Any])
    case coll: GenTraversableOnce[_] => Functions.deepFlatten(coll.toArray[Any])
    case _ => any
  }

  /**
   * "data.user.name" => ("data.user", "name")
   * @param str full path
   * @return previous path and last path
   */
  private def splitPath(str: String): (Option[String], String) = {
    val lastPointIndex = str.lastIndexOf('.')
    if(lastPointIndex != -1)
      (Some(str.substring(0, lastPointIndex)), str.substring(lastPointIndex + 1))
    else (None, str)
  }
}

case class RequiredArgs(fieldPath:String, include: Array[ApiVersion],
                        exclude: Array[ApiVersion] = Array.empty) extends JsonAble {
  {
    val includeAll = include.contains(allVersion)
    val excludeAll = exclude.contains(allVersion)
    val excludeSome = exclude.filterNot(allVersion ==).nonEmpty

    def assertNot(assertion: Boolean, message: => Any) = assert(!assertion, message)

    assertNot(includeAll && include.size > 1, s"OBPRequired's include have $allVersion, it should not have other ApiVersion values.")
    assertNot(excludeAll, s"OBPRequired's should not exclude $allVersion.")

    assertNot(!includeAll && excludeSome, s"OBPRequired's when exclude have values, include must be $allVersion.")
    assertNot(include.isEmpty && exclude.isEmpty, s"OBPRequired's should not both include and exclude are empty.")
  }

  def isNeedValidate(version: ApiVersion): Boolean = {
    if(include.contains(allVersion)) {
      !exclude.contains(version)
    } else {
      include.contains(version)
    }
  }

  // only the follow pattern are valid: [allVersion], [v3_0_0, v4_1_0], [-v3_0_0, -v4_1_0]
  override def toString: String = (include, exclude) match {
    case (_, Array()) => fieldPath + ": "+ include.mkString("[", ", ", "]")
    case (_, ex) if ex.nonEmpty => fieldPath + ": "+ exclude.mkString("[-", ", -", "]")
  }

  override def equals(obj: Any): Boolean = obj match {
    case RequiredArgs(path, inc, exc) => Objects.equals(fieldPath, path) && include.sameElements(inc) && exclude.sameElements(exc)
    case _ => false
  }
  override def toJValue(implicit format: Formats): JArray = toJson

  private val toJson: JArray = (include, exclude) match {
    case (_, Array()) =>
      val includeList = include.toList.map(_.toString).map(JString(_))
      JArray(includeList)
    case _ =>
      val excludeList = include.toList.map("-" + _.toString).map(JString(_))
      JArray(excludeList)
  }
  val apiVersions: List[String] = (include, exclude) match {
    case (_, Array()) => include.toList.map(_.toString)
    case _ => exclude.toList.map("-" + _.toString)
  }
}

object RequiredFieldValidation {
  val OBP_REQUIRED_NAME = classOf[OBPRequired].getSimpleName()

  //OBPRequired's first default param and second default param name, they are end with 1 and 2 accordingly
  val defaultParam = """\$lessinit\$greater\$default\$([1,2])""".r

  private def getVersions(tree: Tree): Array[ApiVersion] = tree match {
    // match default parameter value
    case Select(Select(_: Tree, TermName(OBP_REQUIRED_NAME)), TermName(defaultParam(num))) => {
      if(num == "1") Array(allVersion) else Array.empty
    }
    // match Array.empty
    case Apply(TypeApply(Select(Select(Ident(TermName("scala")),TermName("Array")), TermName("empty")) , _: List[Tree]), _) => {
      Array.empty
    }
    // match Array() and Array(...)
    case Apply(Apply(TypeApply(Select(Select(Ident(TermName("scala")),TermName("Array")), TermName("apply")), TypeTree()::Nil), versionList:List[Tree]), _) => {
      versionList.toArray.map({
        case Select(Select(packageName: Select, TermName(typeName)), TermName(versionName)) =>
          ReflectUtils.getField(s"$packageName.$typeName", versionName).asInstanceOf[ApiVersion]

        case Select(outer: Ident, TermName(versionName)) =>
          val outerName = outer.tpe.toString.replace(".type", "")
          ReflectUtils.getField(outerName, versionName).asInstanceOf[ApiVersion]

        case ident: Ident =>
          ReflectUtils.getObject(ident.tpe.toString).asInstanceOf[ApiVersion]

        case _ => throw new IllegalArgumentException(s"$OBP_REQUIRED_NAME's parameter not correct: $versionList")
      })
    }
    // match include and exclude order exchanged
    case Ident(TermName("x$1")) | Ident(TermName("x$2")) => throw new IllegalArgumentException(s"$OBP_REQUIRED_NAME's parameter order should not be changed, the first parameter must be [include]'")
    case _ => throw new IllegalArgumentException(s"$OBP_REQUIRED_NAME's parameter not correct.")
  }



  /**
   * get all field name to OBPRequired annotation info
   * @param tp to process type
   * @return map of field name to RequiredArgs
   */
  def getAnnotations(tp: Type): Iterable[RequiredArgs] = {

    def isField(symbol: TermSymbol): Boolean =
    symbol.isVal || symbol.isVal || symbol.isLazy || (symbol.isMethod && symbol.asMethod.paramLists.isEmpty)

    // constructor's parameters and fields
    val members: Iterable[Symbol] =
        tp.decls.filter(_.isConstructor).flatMap(_.asMethod.paramLists.head) ++
        tp.members
        .collect({
          case t: TermSymbol if isField(t) => t
        })

    val directAnnotated = members.map(member => getAnnotation(member.name.decodedName.toString.trim, member, false))
      .collect({case Some(requiredArgs) => requiredArgs})
      .distinctBy(_.fieldPath)

    val directAnnotatedNames = directAnnotated.map(_.fieldPath).toSet

    val inDirectAnnotated = members.collect({
        case member if !directAnnotatedNames.contains(member.name.decodedName.toString.trim) =>
          getAnnotation(member.name.decodedName.toString.trim, member, true)
      })
      .collect({case Some(requiredArgs) => requiredArgs})
      .distinctBy(_.fieldPath)
    directAnnotated ++ inDirectAnnotated
  }

  private def getAnnotation(fieldName: String, symbol: Symbol, findOverrides: Boolean): Option[RequiredArgs] = {
    val annotation: Option[Annotation] =
      if(findOverrides) {
        symbol.overrides.
          flatMap(_.annotations)
          .find(_.tree.tpe <:< typeOf[OBPRequired])
      } else {
        symbol.annotations
          .find(_.tree.tpe <:< typeOf[OBPRequired])
      }

    annotation.map { it: Annotation =>
      it.tree.children.tail match {
        case (include: Tree)::(exclude: Tree)::Nil => RequiredArgs(fieldName, getVersions(include), getVersions(exclude))
      }
    }
  }

  def getAllNestedRequiredInfo(tp: Type,
                               predicate: Type => Boolean = Functions.isOBPType,
                               fieldName: String = null
  ): Seq[RequiredArgs] = {

    if(!predicate(tp)) {
      return Nil
    }

    val fieldToRequiredInfo: Iterable[RequiredArgs] = getAnnotations(tp)

    // current type's fields full path to RequiredInfo
    val currentPathToRequiredInfo = fieldName match {
      case null => fieldToRequiredInfo
      case _ => fieldToRequiredInfo.map(it=> it.copy(fieldPath = s"$fieldName.${it.fieldPath}"))
    }

    // find all sub fields RequiredInfo
    val subPathToRequiredInfo: Iterable[RequiredArgs] = tp.members.collect {
      case m: TermSymbol if m.isLazy=> {
        (m.name.decodedName.toString.trim, ReflectUtils.getNestFirstTypeArg(m.asMethod.returnType))
      }
      case m: TermSymbol if m.isVal || m.isVar => {
        (m.name.decodedName.toString.trim, ReflectUtils.getNestFirstTypeArg(m.info))
      }
    } .collect({case tuple @(_, fieldType) if predicate(fieldType) => tuple})
      .distinctBy(_._1)
      .flatMap(pair => {
        val (memberName, membersType) = pair
        val subFieldName = if(fieldName == null) memberName else s"$fieldName.$memberName"
        getAllNestedRequiredInfo(membersType, predicate, subFieldName)
      })

    (currentPathToRequiredInfo ++ subPathToRequiredInfo).toSeq.sortBy(_.fieldPath)
  }

  def getRequiredInfo(tp: Type, predicate: Type => Boolean = Functions.isOBPType): RequiredInfo = {
    val requiredArgs = RequiredFieldValidation.getAllNestedRequiredInfo(tp, predicate)
    RequiredInfo(requiredArgs)
  }
}

