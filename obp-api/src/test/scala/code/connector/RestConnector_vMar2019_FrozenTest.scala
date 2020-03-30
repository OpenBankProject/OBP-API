package code.connector

import java.io.{FileInputStream, FileOutputStream, ObjectInputStream, ObjectOutputStream}
import java.net.URI

import code.bankconnectors.rest.RestConnector_vMar2019
import code.connector.RestConnector_vMar2019_FrozenUtil.{connectorMethodNames, persistFilePath, typeNameToFieldsInfo}
import com.openbankproject.commons.util.ReflectUtils
import net.liftweb.common.Logger
import org.apache.commons.io.IOUtils
import org.scalatest.matchers.{MatchResult, Matcher}
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers, Tag}

import scala.reflect.runtime.universe._


/**
 * if any test of RestConnector_vMar2019_FrozenTest fail, please check whether it is very sure really need do that change, if yes, run this utl again to re-generate frozen metadata.
 */
class RestConnector_vMar2019_FrozenTest extends FlatSpec with Matchers with BeforeAndAfter {
  private var connectorMethodNamesPersisted: List[String] = _
  private var typeNameToFieldsInfoPersisted: Map[String, Map[String, String]] = _
  private val logger = Logger(classOf[RestConnector_vMar2019_FrozenTest])

  before {
    var in: ObjectInputStream = null
    try {
      in = new ObjectInputStream(new FileInputStream(persistFilePath))
      in.readUTF()
      connectorMethodNamesPersisted = in.readObject().asInstanceOf[List[String]]
      typeNameToFieldsInfoPersisted = in.readObject().asInstanceOf[Map[String, Map[String, String]]]
    } catch {
      case e: Throwable =>
        logger.error("read frozen file fail.", e)
    }finally {
      IOUtils.closeQuietly(in)
    }
  }
  object RestConnector_vMar2019Tag extends Tag("RestConnector_vMar2019_FrozenTest")

  "RestConnector_vMar2019 connector methods" should "not be increased" taggedAs RestConnector_vMar2019Tag in {
    val increasedMethodNames = connectorMethodNames.diff(connectorMethodNamesPersisted)
    increasedMethodNames shouldBe empty
  }

  it should "not be decreased" taggedAs RestConnector_vMar2019Tag in {
    val decreasedMethodNames = connectorMethodNamesPersisted.diff(connectorMethodNames)
    decreasedMethodNames shouldBe empty
  }

  "RestConnector_vMar2019 method related types count" should "not be increased" taggedAs RestConnector_vMar2019Tag in {
    val increasedTypeNames = typeNameToFieldsInfo.keySet.diff(typeNameToFieldsInfoPersisted.keySet)
    increasedTypeNames shouldBe empty
  }

  it should "not be decreased" taggedAs RestConnector_vMar2019Tag in {
    val decreasedTypeNames = typeNameToFieldsInfoPersisted.keySet.diff(typeNameToFieldsInfo.keySet)
    decreasedTypeNames shouldBe empty
  }

  "RestConnector_vMar2019 method frozen types structure" should "not be changed" taggedAs RestConnector_vMar2019Tag in {
    // current related types those also exist in persisted metadata.
    val typesToDoCompare: List[(String, Map[String, String])] = typeNameToFieldsInfo
      .filterKeys(typeNameToFieldsInfoPersisted.contains(_))
      .toList

      val theSameStructureAsFrozen: Matcher[(String, Map[String, String])] = Matcher{ fullNameAndStructure =>
        val (fullName: String, structure: Map[String, String]) = fullNameAndStructure
        MatchResult(
          structure == typeNameToFieldsInfoPersisted(fullName),
          s"$fullName structure is changed, frozen structure is ${typeNameToFieldsInfoPersisted(fullName)}, current structure is $structure",
          s"$fullName structure is not changed"
        )
      }
      every(typesToDoCompare) should theSameStructureAsFrozen
  }
}

/**
 * run this util will persist frozen type structure of RestConnector_vMar2019 to file: src/test/scala/code/connector/RestConnector_vMar2019_frozen_meta_data
 * if any test of RestConnector_vMar2019_FrozenTest fail, please check whether it is very sure really need do that change, if yes, run this utl again to re-generate frozen metadata.
 */
object RestConnector_vMar2019_FrozenUtil {
  // current project absolute path
  val basePath = this.getClass.getResource("/").toString .replaceFirst("target[/\\\\].*$", "")
  val persistFilePath = new URI(s"${basePath}/src/test/scala/code/connector/RestConnector_vMar2019_frozen_meta_data").getPath

  val connectorMethodNames: List[String] = typeOf[RestConnector_vMar2019].decls
    .filter(_.isMethod)
    .map(_.asMethod)
    .filter(_.overrides.nonEmpty)
    .filter(_.paramLists.flatten.nonEmpty)
    .map(_.name.toString)
    .toList.filterNot(_ == "dynamicEndpointProcess")

  // typeNameToFieldsInfo sturcture is: (typeFullName, Map(fieldName->fieldTypeName))
  val typeNameToFieldsInfo: Map[String, Map[String, String]] = {
    val outBoundNames = connectorMethodNames.map(it => s"com.openbankproject.commons.dto.OutBound${it.capitalize}")
    val inBoundNames = connectorMethodNames.map(it => s"com.openbankproject.commons.dto.InBound${it.capitalize}")
    val outBoundInboundNames: List[String] = outBoundNames ::: inBoundNames

    val outBoundInBoundTypes: List[Type] = outBoundInboundNames.map(ReflectUtils.getTypeByName(_))
    val allTypesToFrozen = outBoundInBoundTypes.flatMap(getNestedOBPType).distinct

    allTypesToFrozen.map { it =>
      val valNameToTypeName = ReflectUtils.getConstructorParamInfo(it).map(pair => (pair._1, pair._2.toString))
      (it.typeSymbol.asClass.fullName, valNameToTypeName)
    }.toMap
  }

  def main(args: Array[String]): Unit = {

    val out = new ObjectOutputStream(new FileOutputStream(persistFilePath))
    try {
      out.writeUTF(s"this is frozen type meta data persist file, generated by ${RestConnector_vMar2019_FrozenUtil.getClass.getSimpleName}")
      out.writeObject(connectorMethodNames)
      out.writeObject(typeNameToFieldsInfo)
    } finally {
      IOUtils.closeQuietly(out)
    }
  }

  private def getNestedOBPType(tp: Type): Set[Type] = {
    ReflectUtils.getConstructorParamInfo(tp)
      .values
      .map(it => ReflectUtils.getDeepGenericType(it).head)
      .toSet
      .filter(ReflectUtils.isObpType)
      .filterNot(tp ==)  // avoid infinite recursive
    match {
      case set if(set.size > 0) => set.flatMap(getNestedOBPType) + tp
      case _ =>  Set(tp)
    }
  }
}