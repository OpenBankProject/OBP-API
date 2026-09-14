/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

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

import code.util.Helper.MdcLoggable
import org.apache.commons.lang3.StringUtils
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.reflections.util.{ClasspathHelper, ConfigurationBuilder}
import com.openbankproject.commons.util.ReflectUtils

import scala.jdk.CollectionConverters._
import scala.reflect.runtime.universe.TypeTag

/**
  * Utility methods to scan classes using Reflections library.
  * Replaces classutil (org.clapper) which does not support Fat JAR environments.
  * @author shuang
  */
object ClassScanUtils extends MdcLoggable {

  // Scan the "code" package only to avoid scanning all dependencies
  lazy val reflections: Reflections = {
    val config = new ConfigurationBuilder()
      .setUrls(ClasspathHelper.forPackage("code"))
      .setScanners(Scanners.SubTypes.filterResultsBy(_ => true))
    new Reflections(config)
  }

  /**
    * get companion object or singleton object by class name
    * @param name object class name
    * @tparam U expect type
    * @return companion object or singleton object
    */
  def companion[U: TypeTag](name: String): U = {
    val className = if (name.endsWith("$")) name else name + "$"
    Class.forName(className).getDeclaredField("MODULE$").get(null).asInstanceOf[U]
  }

  /**
    * scan classpath to get all companion objects or singleton objects those implements given trait
    * @tparam T the trait type parameter
    * @return all companion objects or singleton objects those implement the given trait
    */
  def getSubTypeObjects[T: TypeTag]: List[T] = {
    val clazz = ReflectUtils.typeTagToClass[T]
    try {
      val subTypes = reflections.getSubTypesOf(clazz).asScala.toList
      logger.info(s"ClassScanUtils (Reflections) found ${subTypes.size} subtypes of ${clazz.getName}")
      // companion objects have a class name ending with "$"
      val objects = subTypes
        .filter(c => c.getName.endsWith("$"))
        .flatMap { c =>
          try { Some(companion[T](c.getName)) }
          catch { case e: Exception =>
            logger.warn(s"Failed to load companion object ${c.getName}: ${e.getMessage}")
            None
          }
        }
      logger.info(s"Found ${objects.size} companion objects implementing ${clazz.getName}")
      objects
    } catch {
      case e: Exception =>
        logger.warn(s"ClassScanUtils (Reflections) failed for ${clazz.getName}: ${e.getMessage}")
        Nil
    }
  }

  /**
    * find all fit classes, filtered by a predicate on the Class object.
    * @param predict check whether to include this class in the result
    * @return all matching class names (without trailing "$")
    */
  def findTypes(predict: Class[_] => Boolean): List[String] = {
    try {
      // getSubTypesOf(Object) returns all known classes in the scanned packages
      reflections.getSubTypesOf(classOf[Object]).asScala.toList
        .filter { c =>
          try { predict(c) }
          catch { case _: Exception => false }
        }
        .map { c =>
          val name = c.getName
          if (name.endsWith("$")) name.substring(0, name.length - 1) else name
        }
    } catch {
      case e: Exception =>
        logger.warn(s"ClassScanUtils.findTypes failed: ${e.getMessage}")
        Nil
    }
  }

  /**
    * get all subtype of net.liftweb.mapper.LongKeyedMapper, so we can register scanned db models dynamically
    * @param packageName scanned root package name
    * @return all matching class names
    */
  def getMappers(packageName: String = ""): Seq[String] = {
    try {
      val mapperInterface = Class.forName("net.liftweb.mapper.LongKeyedMapper")
      val all = reflections.getSubTypesOf(mapperInterface).asScala.toSeq
        .map(_.getName)
      if (StringUtils.isNotBlank(packageName))
        all.filter(_.startsWith(packageName))
      else
        all
    } catch {
      case e: Exception =>
        logger.warn(s"ClassScanUtils.getMappers failed: ${e.getMessage}")
        Nil
    }
  }

}
