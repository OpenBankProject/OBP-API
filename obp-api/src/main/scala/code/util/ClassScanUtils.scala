package code.util

import java.io.File

import com.openbankproject.commons.model.Bank
import org.apache.commons.lang3.StringUtils
import org.clapper.classutil.{ClassFinder, ClassInfo}
import com.openbankproject.commons.util.ReflectUtils

import scala.reflect.runtime.universe.TypeTag

/**
  * this is some util method to scan any class according some rules
  * @author shuang
  */
object ClassScanUtils {

  lazy val finder = ClassFinder(getClassPath(this.getClass, classOf[Bank], classOf[String]))

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
    * @tparam T the trait type parameter
    * @return all companion objects or singleton object those implements given clazz
    */
  def getSubTypeObjects[T:TypeTag]: List[T] = {
    val clazz = ReflectUtils.typeTagToClass[T]
    finder.getClasses().filter(_.implements(clazz.getName)).map(_.name).map(companion[T](_)).toList
  }

  /**
    * find all fit classes, do filter with predict function
    * @param predict check whether include this type in the result
    * @return all fit type names
    */
  def findTypes(predict: ClassInfo => Boolean): List[String] = finder.getClasses()
    .filter(predict)
    .map(it => {
      val name = it.name
      if(name.endsWith("$")) name.substring(0, name.length - 1)
      else name
    }) //some companion type name ends with $, it added by scalac, should remove from class name
    .toList

  /**
    * get given class exists jar Files
    * @param classes to find class paths contains these class files
    * @return this class exists jar File
    */
  private[this] def getClassPath(classes: Class[_]*): Seq[File] =  classes.map { clazz =>
      val classFile = "/" + clazz.getName.replace('.', '/') + ".class"
      val uri = clazz.getResource(classFile).toURI.toString
      val path = uri.replaceFirst("^(jar:|file:){0,2}(.*?)\\!?\\Q" + classFile + "\\E$", "$2")
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