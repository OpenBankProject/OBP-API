package com.openbankproject.commons.util
import java.util.regex.Pattern

import scala.collection.{IterableLike, immutable}
import scala.collection.generic.CanBuildFrom
import scala.reflect.runtime.universe.Type
/**
 * function utils
 */
object Functions {

  /**
   * A placeholder PartialFunction, do nothing because the isDefinedAt method always return false
   * @tparam T function parameter type
   * @tparam D function return type
   * @return function
   */
  def doNothing[T, D]: PartialFunction[T,D] = {
    case _ if false => ???
  }

  def doNothingFn[T](t: T): Unit = ()
  def doNothingFn[T, D](t: T, d: D): Unit = ()

  def truePredicate[T]: T => Boolean = _ => true
  def falsePredicate[T]: T => Boolean = _ => false

  private val obpTypeNamePattern = Pattern.compile("""(code|com\.openbankproject\.commons)\..+""")

  def isOBPType(tp: Type) = obpTypeNamePattern.matcher(tp.typeSymbol.fullName).matches()
  def isOBPClass(clazz: Class[_]) = obpTypeNamePattern.matcher(clazz.getName).matches()

  implicit class RichCollection[A, Repr](iterable: IterableLike[A, Repr]){
    def distinctBy[B, That](f: A => B)(implicit canBuildFrom: CanBuildFrom[Repr, A, That]) = {
      val builder = canBuildFrom(iterable.repr)
      val set = scala.collection.mutable.Set[B]()
      iterable.foreach(it => {
        val calculatedElement = f(it)
        if(set.add(calculatedElement)) {
          builder += it
        }
      })
      builder.result
    }
    def toMapByKey[K](f: A => K): immutable.Map[K, A] = {
      val b = immutable.Map.newBuilder[K, A]
      for (x <- iterable)
        b += f(x) -> x

      b.result()
    }
    def toMapByValue[V](f: A => V): immutable.Map[A, V] = {
      val b = immutable.Map.newBuilder[A, V]
      for (x <- iterable)
        b += x -> f(x)

      b.result()
    }

    def toMap[K, V](keyFn: A => K, valueFn: A => V): immutable.Map[K, V] = {
      val b = immutable.Map.newBuilder[K, V]
      for (x <- iterable)
        b += keyFn(x) -> valueFn(x)

      b.result()
    }

    /**
     * split collection to tuple of two collections, left is predicate check is true, right is predicate check is false
     * @param predicate check element function
     * @param canBuildFrom
     * @tparam That to collection's type
     * @return tuple
     */
    def classify[That](predicate: A => Boolean)(implicit canBuildFrom: CanBuildFrom[Repr, A, That]): (That, That) = {
      val builderLeft = canBuildFrom(iterable.repr)
      val builderRight = canBuildFrom(iterable.repr)
      for (x <- iterable) {
        if(predicate(x)) builderLeft += x else builderRight += x
      }
      (builderLeft.result(), builderRight.result())
    }
  }
}
