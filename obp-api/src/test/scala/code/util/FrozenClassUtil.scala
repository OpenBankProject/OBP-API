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
    val server = TestServer
    val out = new ObjectOutputStream(new FileOutputStream(persistFilePath))
    try {
      out.writeObject(getFrozenApiInfo)
    } finally {
      IOUtils.closeQuietly(out)
      // there is no graceful way to shutdown jetty server, so here use brutal way to shutdown it.
      server.server.stop()
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

    val typeNameToTypeValFields: Map[String, Map[String, String]] = allFreezingTypes
      .map(it => {
        val valNameToTypeName = ReflectUtils.getConstructorParamInfo(it).map(pair => (pair._1, pair._2.toString))
        (it.typeSymbol.asClass.fullName, valNameToTypeName)
      })
      .toMap
    (versionToEndpointNames, typeNameToTypeValFields)
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
      .filterNot(tp ==)  // avoid infinite recursive
    match {
      case set if(set.size > 0) => set.flatMap(getNestedOBPType) + tp
      case _ =>  Set(tp)
    }
  }
}
