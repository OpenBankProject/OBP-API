package code.util

import java.io.File

import org.apache.commons.lang3.StringUtils
import org.clapper.classutil.{ClassFinder, ClassInfo}

import scala.reflect.runtime.universe.TypeTag

/**
  * this is some util method to scan any class according some rules
  * @author shuang
  */
object ClassScanUtils {

  lazy val finder = ClassFinder(List(getClassPath(this.getClass)))

  /**
    * get companion object or singleton object by class name
    * @param name object class name
    * @tparam U expect type
    * @return companion object or singleton object
    */
  def companion[U:TypeTag](name : String) : U = {
    val className = if(name.endsWith("$")) name else name + "$"
    Class.forName(className).getDeclaredField("MODULE$").get(null).asInstanceOf[U]
  }

  /**
    * scan classpath to get all companion objects or singleton objects those implements given trait
    * @param clazz a trait type for filter object
    * @tparam T the trait type parameter
    * @return all companion objects or singleton object those implements given clazz
    */
  def getSubTypeObjects[T:TypeTag](clazz: Class[T]) = {
    finder.getClasses().filter(_.implements(clazz.getName)).map(_.name).map(companion[T](_)).toList
  }

  /**
    * get given class exists jar File
    * @param clazz to find this class file path
    * @return this class exists jar File
    */
  private[this] def getClassPath(clazz: Class[_]) = {
    val classFile = "/" + clazz.getName.replace('.', '/') + ".class"
    val uri = clazz.getResource(classFile).toURI.toString
    val path = uri.replaceFirst("^(jar:|file:)?(.*)\\!?\\Q" + classFile + "\\E$", "$2")
    new File(path)
  }

  /**
    * get all subtype of net.liftweb.mapper.LongKeyedMapper, so we can register scanned db models dynamic
    * @param packageName scanned root package name
    * @return all scanned ClassInfo
    */
  def getMappers(packageName:String = ""): Seq[ClassInfo] = {
    val mapperInterface = "net.liftweb.mapper.LongKeyedMapper"
    val infos = finder.getClasses().filter(it => it.interfaces.contains(mapperInterface))
    if(StringUtils.isNoneBlank()) {
      infos.filter(classInfo => classInfo.name.startsWith(packageName))
    } else {
      infos
    }
  }

}