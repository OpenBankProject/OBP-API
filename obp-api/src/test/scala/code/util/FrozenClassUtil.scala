package code.util

import java.io._
import java.net.URI

import code.TestServer
import com.openbankproject.commons.util.ApiVersion
import code.api.util.VersionedOBPApis
import com.openbankproject.commons.util.ReflectUtils
import net.liftweb.common.Loggable
import org.apache.commons.io.IOUtils

import scala.reflect.runtime.universe._

/**
  * this util is for persist metadata of frozen type, those frozen type is versionStatus = "STABLE" related example classes,
  * after persist the metadata, the FrozenClassTest can check whether there some modify change any frozen type, the test will fail when there are some changes in the frozen type
  */
object FrozenClassUtil extends Loggable{

  val sourceName = s"""${this.getClass.getName.replace("$", "")}.scala"""
  // current project absolute path
  val basePath = this.getClass.getResource("/").toString .replaceFirst("target[/\\\\].*$", "")
  val persistFilePath = new URI(s"${basePath}/src/test/resources/frozen_type_meta_data").getPath

  def main(args: Array[String]): Unit = {
    System.setProperty("run.mode", "test") // make sure this Props.mode is the same as unit test Props.mode
    val _ = TestServer // trigger initialization
    val out = new ObjectOutputStream(new FileOutputStream(persistFilePath))
    try {
      out.writeObject(getFrozenApiInfo)
    } finally {
      IOUtils.closeQuietly(out)
      // http4s server is managed by TestServer shutdown hook; force exit here.
      System.exit(0)
    }
  }

  /**
    * get frozen api information by scan classes
    * @return frozen api information, include api names of given api version and frozen class metadata
    */
  def getFrozenApiInfo: (List[(ApiVersion, Set[String])], Map[String, Map[String, String]]) = {
    val versionedOBPApisList: List[VersionedOBPApis] = ClassScanUtils.getSubTypeObjects[VersionedOBPApis]
      .filter(_.versionStatus == "STABLE")

    val versionToEndpointNames: List[(ApiVersion, Set[String])] = versionedOBPApisList
      .map(it => {
        val version = it.version
        val currentVersionApis = it.allResourceDocs.filter(version == _.implementedInApiVersion).toSet
        (version, currentVersionApis.map(_.partialFunctionName))
      })

    val allFreezingTypes: Set[Type] = versionedOBPApisList
      .flatMap(_.allResourceDocs)
      .flatMap(it => it.exampleRequestBody :: it.successResponseBody :: Nil)
      .filter(ReflectUtils.isObpObject(_))
      .map(ReflectUtils.getType(_))
      .toSet
      .flatMap(getNestedOBPType(_))

    val refinements: Map[(String, String), String] = erasedTypeRefinements(
      versionedOBPApisList
        .flatMap(_.allResourceDocs)
        .flatMap(it => it.exampleRequestBody :: it.successResponseBody :: Nil))

    val typeNameToTypeValFields: Map[String, Map[String, String]] = allFreezingTypes
      .map(it => {
        val className = it.typeSymbol.asClass.fullName
        val valNameToTypeName = ReflectUtils.getConstructorParamInfo(it)
          .map(pair => (pair._1, refinements.getOrElse((className, pair._1), pair._2.toString)))
        (className, valNameToTypeName)
      })
      .toMap
    (versionToEndpointNames, typeNameToTypeValFields)
  }

  /**
    * The declared type of each field whose type the class file erased, recovered from the example
    * value, as (class name, field name) -> type name.
    *
    * `scala-reflect` reads ScalaSig, an attribute only Scala 2 classes carry. On a Scala 3 class it
    * falls back to the class file's Java generic signature, and there a value type cannot be a type
    * argument: `Option[Long]` is emitted as `scala.Option<java.lang.Object>`. Reference types are
    * unaffected - `Option[String]` keeps its argument - so what is lost is exactly Option of a value
    * type, and what is lost with it is this contract's ability to notice one becoming another.
    *
    * The example value is the only runtime source of the erased type. Every field this has to cover
    * has one, and is kept having one: SwaggerFactoryUnitTest fails when an Option of a value type
    * reachable from a resource doc's example bodies has no value, because the published swagger
    * derives its type from the same place. FrozenTypePrecisionTest fails if anything reaches the
    * fixture still erased.
    */
  private def erasedTypeRefinements(roots: List[Any]): Map[(String, String), String] = {
    val out = scala.collection.mutable.Map.empty[(String, String), String]
    val seen = scala.collection.mutable.Set.empty[Class[_]]

    def refinedName(declared: Type, value: Any): Option[String] =
      if (declared.toString != "Option[Object]") None
      else value match {
        case Some(_: java.lang.Boolean) => Some("Option[Boolean]")
        case Some(_: java.lang.Integer) => Some("Option[Int]")
        case Some(_: java.lang.Long) => Some("Option[Long]")
        case Some(_: java.lang.Float) => Some("Option[Float]")
        case Some(_: java.lang.Double) => Some("Option[Double]")
        case _ => None
      }

    def walk(value: Any): Unit = value match {
      case null => ()
      case Some(inner) => walk(inner)
      case None => ()
      case items: Iterable[_] => items.foreach(walk)
      case obj: AnyRef if ReflectUtils.isObpObject(obj) =>
        // Once per class, not once per instance: the fields are a property of the class, and an
        // API's example bodies hold the same one many times over.
        if (seen.add(obj.getClass)) {
          val tp = ReflectUtils.getType(obj)
          val className = tp.typeSymbol.asClass.fullName
          val declaredTypes = ReflectUtils.getConstructorParamInfo(tp)
          val values = ReflectUtils.getConstructorArgs(obj)
          declaredTypes.foreach { case (fieldName, declared) =>
            values.get(fieldName).foreach { fieldValue =>
              refinedName(declared, fieldValue).foreach(name => out += ((className, fieldName) -> name))
              walk(fieldValue)
            }
          }
        }
      case _ => ()
    }

    roots.filter(ReflectUtils.isObpObject(_)).foreach(walk)
    out.toMap
  }

  /**
    * read persisted frozen api info from persist file
    * @return persisted frozen api information, include api names of given api version and frozen class metadata
    */
  def readPersistedFrozenApiInfo: (List[(ApiVersion, Set[String])], Map[String, Map[String, String]]) = {
    assume(new File(persistFilePath).exists(), s"freeze type not persisted yet, please run ${this.sourceName}")
    val input = new ObjectInputStream(new FileInputStream(persistFilePath))
    try {
      input.readObject().asInstanceOf[(List[(ApiVersion, Set[String])], Map[String, Map[String, String]])]
    } catch {
      case e: Throwable =>
        logger.error("read PersistedFrozenApiInfo fail." + e)
        throw e
    } finally {
      IOUtils.closeQuietly(input)
    }
  }

  private def getNestedOBPType(tp: Type): Set[Type] = {
    ReflectUtils.getConstructorParamInfo(tp)
      .values
      .map(it => ReflectUtils.getDeepGenericType(it).head)
      .toSet
      .filter(ReflectUtils.isObpType)
      .filterNot(tp == _)  // avoid infinite recursive
    match {
      case set if(set.size > 0) => set.flatMap(getNestedOBPType) + tp
      case _ =>  Set(tp)
    }
  }
}
