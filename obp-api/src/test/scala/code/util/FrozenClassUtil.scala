package code.util

import java.io._
import java.net.URI

import code.TestServer
import com.openbankproject.commons.util.ApiVersion
import code.api.util.http4s.Http4sResourceDocAggregation
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
  // current project absolute path: the classpath root that answers getResource("/"), which is the
  // FIRST classpath entry. Overridable with -Dfrozen.metadata.path=... when the generator is run as
  // a plain JVM main with a hand-built classpath (see README "Steps to freeze an API").
  val basePath = this.getClass.getResource("/").toString .replaceFirst("target[/\\\\].*$", "")
  val persistFilePath = sys.props.getOrElse("frozen.metadata.path",
    new URI(s"${basePath}/src/test/resources/frozen_type_meta_data").getPath)

  /** Scan the STABLE versions and (re)write the snapshot blob. No server management, no exit: callable from a suite. */
  def writeSnapshot(): String = {
    val out = new ObjectOutputStream(new FileOutputStream(persistFilePath))
    try out.writeObject(getFrozenApiInfo) finally IOUtils.closeQuietly(out)
    persistFilePath
  }

  /**
    * Plain-JVM entry point. Prefer the Maven route (FrozenSnapshotGenerate suite, see README): it
    * runs on the reactor classpath. Everything sits inside try/finally so a failure can never leave
    * the embedded http4s server's threads keeping the JVM alive.
    */
  def main(args: Array[String]): Unit = {
    System.setProperty("run.mode", "test") // make sure this Props.mode is the same as unit test Props.mode
    var exitCode = 0
    try {
      val _ = TestServer // trigger initialization
      println(s"wrote ${writeSnapshot()}")
    } catch {
      case e: Throwable =>
        e.printStackTrace()
        exitCode = 1
    } finally {
      // http4s server is managed by TestServer shutdown hook; force exit here.
      System.exit(exitCode)
    }
  }

  /**
    * get frozen api information by scan classes
    * @return frozen api information, include api names of given api version and frozen class metadata
    */
  def getFrozenApiInfo: (List[(ApiVersion, Set[String])], Map[String, Map[String, String]]) = {
    // Was a classpath scan for VersionedOBPApis implementors, which meant the OBPAPIx_x_x
    // aggregator objects. Those are gone; Http4sResourceDocAggregation.allVersions is the same
    // enumeration made explicit, carrying each version's status and cumulative doc catalog.
    val stableVersions: List[Http4sResourceDocAggregation.VersionedCatalog] =
      Http4sResourceDocAggregation.allVersions.filter(_.versionStatus == "STABLE")

    val versionToEndpointNames: List[(ApiVersion, Set[String])] = stableVersions
      .map(it => {
        val version = it.version
        val currentVersionApis = it.docs().filter(version == _.implementedInApiVersion).toSet
        (version, currentVersionApis.map(_.partialFunctionName))
      })

    val allFreezingTypes: Set[Type] = stableVersions
      .flatMap(_.docs())
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
      .filterNot(tp == _)  // avoid infinite recursive
    match {
      case set if(set.size > 0) => set.flatMap(getNestedOBPType) + tp
      case _ =>  Set(tp)
    }
  }
}
