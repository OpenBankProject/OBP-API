package com.openbankproject.commons.util
import java.util.regex.Pattern

import scala.collection.{GenSetLike, GenTraversableOnce, SeqLike, TraversableLike, immutable}
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
  def predicateTo[T](b: Boolean): T => Boolean = _ => b

  @inline
  def unary[T]: T => T = t => t

  private val obpTypeNamePattern = Pattern.compile("""(code|com\.openbankproject\.commons)\..+""")

  def isOBPType(tp: Type) = obpTypeNamePattern.matcher(tp.typeSymbol.fullName).matches()
  def isOBPClass(clazz: Class[_]) = obpTypeNamePattern.matcher(clazz.getName).matches()

  /**
   * build a function ()=> T, according call by name function. make sure call by name function return value initial lazily,
   * repeatedly call the built function will not re invoke call by name function, so constructed a lazy value.
   * @param f call by name function
   * @tparam T result type
   * @return a new
   */
  def lazyValue[T](f: => T): () => T = {
      lazy val value = f
      ()=> value
  }

      def deepFlatten(arr: Array[_]): Array[Any] = {
        arr.collect {
          case a:Array[_] => a
          case coll: GenTraversableOnce[_] => coll.toArray[Any]
        }.flatMap(deepFlatten(_)) ++
          arr.filterNot(it => it.isInstanceOf[Array[_]] || it.isInstanceOf[GenTraversableOnce[_]])
      }

      def deepFlatten[A](coll: Traversable[A]): Traversable[Any] = {
        coll.collect {
          case a:Array[_] => a.toTraversable
          case coll: Traversable[_] => coll
        }.flatMap(deepFlatten(_)) ++
          coll.filterNot(it => it.isInstanceOf[Array[_]] || it.isInstanceOf[GenTraversableOnce[_]])
      }

  /**
   * momoize function, to avoid re calculate values
   * @tparam A key
   * @tparam R cached value
   */
  class Memo[A, R] {
      private val cache = new java.util.concurrent.atomic.AtomicReference(Map[A, R]())

      def memoize(x: A)(f: => R): R = {
        def addToCache(): R = {
          val ret = f

          // if after execute f, the x not cached or cached but value changed, update cached value
          val c: Map[A, R] = cache.get
          val cachedValue = c.get(x)
          if(cachedValue.isEmpty || cachedValue.get != ret) {
            cache.set(c + (x -> ret))
          }

          ret
        }
        cache.get.getOrElse(x, addToCache)
      }
    }

      // implicit functions place in this object
      object Implicits {

        implicit class BinaryOp[A](a: => A) {
          def ?:[B >: A](b: B): B = if(b == null) a else b
        }

        implicit class RichCollection[A, Repr](iterable: TraversableLike[A, Repr]){
          def distinctBy[B, That](f: A => B)(implicit canBuildFrom: CanBuildFrom[Repr, A, That]): That = {
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
          def toMap[K, V](keyFn: A => K, valueFn: A => V): Map[K, V] = {
            val b = immutable.Map.newBuilder[K, V]
            for (x <- iterable)
              b += keyFn(x) -> valueFn(x)

            b.result()
          }

          def toMapByKey[K](f: A => K): immutable.Map[K, A] = toMap(f, unary)

          def toMapByValue[V](f: A => V): immutable.Map[A, V] = toMap(unary, f)

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

          /**
           * add one element if coll not exists that element
           * @param ele
           * @param canBuildFrom
           * @tparam That
           * @return new coll contains given ele
           */
          def ?+ [That](ele: A)(implicit canBuildFrom: CanBuildFrom[Repr, A, That]): That = {
            if(existsElement(ele)) {
              iterable.asInstanceOf[That]
            } else {
              val builder = canBuildFrom(iterable.repr)
              builder ++= iterable
              builder += ele
              builder.result()
            }
          }

          /**
           * remove element if coll exists that element, may remove multiple if exists more than one.
           * @param ele
           * @param canBuildFrom
           * @tparam That
           * @return a new coll not contains given ele
           */
          def ?- [That](ele: A)(implicit canBuildFrom: CanBuildFrom[Repr, A, That]): That = {
            if(!existsElement(ele)) {
              iterable.asInstanceOf[That]
            } else {
              val builder = canBuildFrom(iterable.repr)
              for(e <- iterable if e != ele)
                builder += e
              builder.result()
            }
          }

          private def existsElement[That](ele: A) = {
            iterable match {
              case seq: SeqLike[A, Repr] => seq.contains(ele)
              case set: GenSetLike[A, Repr] => set.contains(ele)
              case _ => iterable.exists(ele ==)
            }
          }

          def findByType[B <: A : Manifest]: Option[B] = {
            val clazz = manifest[B].runtimeClass
            iterable.find(clazz.isInstance(_)).asInstanceOf[Option[B]]
          }

          def notExists(p: A => Boolean): Boolean = ! iterable.exists(p)
        }
      }
  }
