package com.openbankproject.commons.util
import java.util.regex.Pattern

import scala.collection.{Factory, immutable}
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

      // Iterable in place of Traversable and GenTraversableOnce: 2.13 removes both. Every value
      // these two actually meet - arrays and ordinary collections - is an Iterable, so the runtime
      // type tests keep selecting the same things.
      def deepFlatten(arr: Array[_]): Array[Any] = {
        arr.collect {
          case a:Array[_] => a
          case coll: Iterable[_] => coll.toArray[Any]
        }.flatMap(deepFlatten(_)) ++
          arr.filterNot(it => it.isInstanceOf[Array[_]] || it.isInstanceOf[Iterable[_]])
      }

      def deepFlatten[A](coll: Iterable[A]): Iterable[Any] = {
        coll.collect {
          case a:Array[_] => a.toIndexedSeq
          case coll: Iterable[_] => coll
        }.flatMap(deepFlatten(_)) ++
          coll.filterNot(it => it.isInstanceOf[Array[_]] || it.isInstanceOf[Iterable[_]])
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

        /**
         * 2.13 removes TraversableLike, SeqLike, GenSetLike and CanBuildFrom outright, so this had
         * to be rebuilt rather than renamed. It is now written against the collection type itself
         * plus scala.collection.Factory, which 2.13 has natively and scala-collection-compat
         * back-ports to 2.12, so one source compiles on both.
         *
         * The result type also narrows: CanBuildFrom[Repr, A, That] allowed the result to be a
         * different kind of collection from the source, and none of the call sites ever used that -
         * distinctBy returns the List it was given, classify splits a Seq into two Seqs, ?+ returns
         * the List it was given. Fixing the result at C[A] keeps every existing call compiling and
         * removes a degree of freedom that only made the rewrite harder.
         */
        implicit class RichCollection[A, C[X] <: Iterable[X]](iterable: C[A]){
          def distinctBy[B](f: A => B)(implicit factory: Factory[A, C[A]]): C[A] = {
            val builder = factory.newBuilder
            val set = scala.collection.mutable.Set[B]()
            iterable.foreach(it => {
              val calculatedElement = f(it)
              if(set.add(calculatedElement)) {
                builder += it
              }
            })
            builder.result()
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
           * @return tuple
           */
          def classify(predicate: A => Boolean)(implicit factory: Factory[A, C[A]]): (C[A], C[A]) = {
            val builderLeft = factory.newBuilder
            val builderRight = factory.newBuilder
            for (x <- iterable) {
              if(predicate(x)) builderLeft += x else builderRight += x
            }
            (builderLeft.result(), builderRight.result())
          }

          /**
           * add one element if coll not exists that element
           * @param ele
           * @param canBuildFrom
           * @return new coll contains given ele
           */
          def ?+ (ele: A)(implicit factory: Factory[A, C[A]]): C[A] = {
            if(existsElement(ele)) {
              iterable
            } else {
              val builder = factory.newBuilder
              builder ++= iterable
              builder += ele
              builder.result()
            }
          }

          /**
           * remove element if coll exists that element, may remove multiple if exists more than one.
           * @param ele
           * @param canBuildFrom
           * @return a new coll not contains given ele
           */
          def ?- (ele: A)(implicit factory: Factory[A, C[A]]): C[A] = {
            if(!existsElement(ele)) {
              iterable
            } else {
              val builder = factory.newBuilder
              for(e <- iterable if e != ele)
                builder += e
              builder.result()
            }
          }

          // Seq and Set stand in for SeqLike and GenSetLike. Both are matched at their
          // scala.collection root so a mutable receiver is still recognised - on 2.13 the
          // unqualified names mean the immutable ones only.
          private def existsElement(ele: A): Boolean = {
            iterable match {
              case seq: scala.collection.Seq[A @unchecked] => seq.contains(ele)
              case set: scala.collection.Set[A @unchecked] => set.contains(ele)
              case _ => iterable.exists(ele == _)
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
