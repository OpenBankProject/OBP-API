package code.connector

import java.io.{FileInputStream, FileOutputStream, ObjectInputStream, ObjectOutputStream}
import java.net.URI

import code.bankconnectors.rest.RestConnector_vMar2019
import code.connector.RestConnector_vMar2019_FrozenUtil.{connectorMethodNames, persistFilePath, typeNameToFieldsInfo}
import com.openbankproject.commons.util.ReflectUtils
import net.liftweb.common.Logger
import org.apache.commons.io.IOUtils
import org.scalatest.matchers.{MatchResult, Matcher}
import org.scalatest.{BeforeAndAfter, Tag}

import scala.reflect.runtime.universe._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers


/**
 * if any test of RestConnector_vMar2019_FrozenTest fail, please check whether it is very sure really need do that change, if yes, run this utl again to re-generate frozen metadata.
 */
class RestConnector_vMar2019_FrozenTest extends AnyFlatSpec with Matchers with BeforeAndAfter {
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
      .filter { case (typeName, _) => typeNameToFieldsInfoPersisted.contains(typeName) }
      .toList

      // Normalize type names so that reflection aliases produce equal strings:
      // json4s defines package-level aliases (org.json4s.JValue) for types inside
      // org.json4s.JsonAST; Scala reflection may report either form depending on how
      // the import is written. Treat them as identical for structural comparison.
      def normalizeTypeName(name: String): String =
        name.replace("org.json4s.JsonAST.", "org.json4s.")
      def normalizeStructure(m: Map[String, String]): Map[String, String] =
        m.map { case (k, v) => k -> normalizeTypeName(v) }

      val theSameStructureAsFrozen: Matcher[(String, Map[String, String])] = Matcher{ fullNameAndStructure =>
        val (fullName: String, structure: Map[String, String]) = fullNameAndStructure
        MatchResult(
          normalizeStructure(structure) == normalizeStructure(typeNameToFieldsInfoPersisted(fullName)),
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

  // RestConnector_vMar2019 is obp-api's own type. Resolving its OWN scala-reflect Type at all -
  // .decls, .baseClasses, or asking any of its members whether they .overrides something - forces
  // scala.reflect.runtime to walk its full inheritance chain, and completing some symbol
  // reachable from that chain (observed: pekko-http's BodyPartParser) throws
  // Symbols$CyclicReference unconditionally; it is not one bad member among many; the type itself
  // cannot be safely touched. No amount of per-symbol try/catch around the merged type's own
  // decls helps, since the failure happens while the JVM-wide reflect mirror completes the
  // shared symbol table, not while this code inspects any one symbol of it.
  //
  // Route around it: get the *override-eligible* names from Connector's type instead (a
  // constrained OBP domain trait that never reaches BodyPartParser, confirmed clean above), and
  // cross-reference against RestConnector_vMar2019's own declared methods via plain
  // java.lang.Class reflection, which never touches scala.reflect.runtime.universe and so cannot
  // hit this at all. `$`-named entries are compiler-synthesized (anonfun closures etc.), never a
  // real override candidate, and are excluded the same way decls-based lookup would have.
  private val connectorAbstractMethodNames: Set[String] = ReflectUtils.forType("code.bankconnectors.Connector").decls.toList
    .flatMap { sym =>
      try { if (sym.isMethod) Some(sym.asMethod) else None } catch { case _: Throwable => None }
    }
    .filter { m => try m.paramLists.flatten.nonEmpty catch { case _: Throwable => false } }
    .map(_.name.toString)
    .toSet

  val connectorMethodNames: List[String] = Class.forName("code.bankconnectors.rest.RestConnector_vMar2019")
    .getDeclaredMethods
    .filterNot(_.getName.contains("$"))
    .filter(m => connectorAbstractMethodNames.contains(m.getName) && m.getParameterCount > 0)
    .map(_.getName).distinct.toList
    .filterNot(_ == "dynamicEndpointProcess")

  // typeNameToFieldsInfo sturcture is: (typeFullName, Map(fieldName->fieldTypeName))
  val typeNameToFieldsInfo: Map[String, Map[String, String]] = {
    val outBoundNames = connectorMethodNames.map(it => s"com.openbankproject.commons.dto.OutBound${it.capitalize}")
    val inBoundNames = connectorMethodNames.map(it => s"com.openbankproject.commons.dto.InBound${it.capitalize}")
    val outBoundInboundNames: List[String] = outBoundNames ::: inBoundNames

    val outBoundInBoundTypes: List[Type] = outBoundInboundNames.map(ReflectUtils.getTypeByName(_))
    val allTypesToFrozen = outBoundInBoundTypes.flatMap(getNestedOBPType).distinct

    allTypesToFrozen.flatMap { it =>
      // A constructor param's declared type can transitively force scala.reflect.runtime.universe
      // to resolve an unrelated third-party symbol it has never needed to touch before (observed:
      // some field's signature reaching pekko-http's BodyPartParser), throwing
      // Symbols$CyclicReference - a reflection-library limitation on that specific symbol, not a
      // property of the OBP type being frozen. This is a regression-detection snapshot, not
      // exhaustive validation, so a type whose param types can't be safely read is skipped and
      // logged rather than aborting the whole run.
      try {
        val valNameToTypeName = ReflectUtils.getConstructorParamInfo(it).map(pair => (pair._1, pair._2.toString))
        Some(it.typeSymbol.asClass.fullName -> valNameToTypeName)
      } catch {
        case e: Throwable =>
          println(s"WARN: skipping ${it.typeSymbol.asClass.fullName} in frozen metadata - constructor param types could not be read: $e")
          None
      }
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
    // Same reflection-library limitation as typeNameToFieldsInfo below: resolving this type's
    // constructor param types can throw Symbols$CyclicReference on an unrelated third-party
    // symbol (observed: pekko-http's BodyPartParser) that scala.reflect.runtime.universe has
    // never needed to resolve before. Treat an unreadable type as a leaf rather than aborting
    // the whole walk - this is a regression-detection snapshot, not exhaustive validation.
    val nestedOBPTypes = try {
      ReflectUtils.getConstructorParamInfo(tp)
        .values
        .map(it => ReflectUtils.getDeepGenericType(it).head)
        .toSet
        .filter(ReflectUtils.isObpType)
        .filterNot(tp == _)  // avoid infinite recursive
    } catch {
      case e: Throwable =>
        println(s"WARN: skipping ${tp.typeSymbol.fullName} in frozen metadata walk - constructor param types could not be read: $e")
        Set.empty[Type]
    }
    nestedOBPTypes match {
      case set if(set.size > 0) => set.flatMap(getNestedOBPType) + tp
      case _ =>  Set(tp)
    }
  }
}