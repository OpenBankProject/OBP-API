package code.util

import com.openbankproject.commons.util.ReflectUtils
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PrimaryConstructorDiag2Test extends AnyFlatSpec with Matchers {
  "MethodRoutingParam class" should "reveal its declared fields and constructor alternatives via raw Java/scala reflection" in {
    val clazz = Class.forName("code.methodrouting.MethodRoutingParam")
    println(s"declared fields: ${clazz.getDeclaredFields.map(f => s"${f.getName}:${f.getType.getSimpleName}").mkString(", ")}")
    println(s"declared constructors:")
    clazz.getDeclaredConstructors.foreach { c =>
      println(s"  params: ${c.getParameterTypes.map(_.getSimpleName).mkString(", ")}")
    }

    val tp = ReflectUtils.forType("code.methodrouting.MethodRoutingParam")
    val alternatives = tp.decl(scala.reflect.runtime.universe.termNames.CONSTRUCTOR).alternatives
    println(s"scala-reflect constructor alternatives count: ${alternatives.size}")
    alternatives.foreach { alt =>
      val m = alt.asMethod
      val params = m.paramLists.headOption.getOrElse(Nil).map(p => s"${p.name}: ${p.info}").mkString(", ")
      println(s"  alt: isPrimaryConstructor=${m.isPrimaryConstructor} params=($params)")
    }

    val picked = ReflectUtils.getPrimaryConstructor(tp)
    val pickedParams = picked.paramLists.headOption.getOrElse(Nil).map(p => s"${p.name}: ${p.info}").mkString(", ")
    println(s"getPrimaryConstructor picked: ($pickedParams)")
    pickedParams should equal("key: String, value: String")
  }
}
