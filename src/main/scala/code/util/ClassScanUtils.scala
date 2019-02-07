package code.util

import java.io.File

import code.api.berlin.group.v1_3.OBP_BERLIN_GROUP_1_3
import code.model.dataAccess.ViewImpl
import net.liftweb.mapper.LongKeyedMapper
import org.apache.commons.lang3.StringUtils
import org.clapper.classutil.ClassInfo
import org.clapper.classutil.ClassFinder

import scala.reflect.runtime.universe.TypeTag

object ClassScanUtils {

  lazy val finder = ClassFinder(List(getClassPath(this.getClass)))

  def companion[U:TypeTag](name : String) : U = {
    val className = if(name.endsWith("$")) name else name + "$"
    Class.forName(className).getDeclaredField("MODULE$").get(null).asInstanceOf[U]
  }
  def getImplementClass[T:TypeTag](clazz: Class[T]) = {
    //finder.getClasses().filter(_.implements(clazz.getName)).map(_.name).map(companion[T](_)).toList
    List(OBP_BERLIN_GROUP_1_3)
  }
  private[this] def getClassPath(clazz: Class[_]) = {
    val classFile = "/" + clazz.getName.replace('.', '/') + ".class"
    val uri = clazz.getResource(classFile).toURI.toString
    val path = uri.replaceFirst("^(jar:|file:)?(.*)\\!?\\Q" + classFile + "\\E$", "$2")
    new File(path)
  }

  def getMappers(packageName:String = ""): Seq[ClassInfo] = {
    val mapperInterface = "net.liftweb.mapper.LongKeyedMapper"
    val infoes = finder.getClasses().filter(it => it.interfaces.contains(mapperInterface))
    if(StringUtils.isNoneBlank()) {
      infoes.filter(classInfo => classInfo.name.startsWith(packageName))
    } else {
      infoes
    }
  }

}