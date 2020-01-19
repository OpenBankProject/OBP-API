package com.openbankproject.commons

import com.alibaba.ttl.TtlRunnable

import scala.concurrent.{ ExecutionContext => ScalaExecutionContext}

object ExecutionContext {
  object Implicits {
    /**
     * The implicit global `ExecutionContext`. Import `global` when you want to provide the global
     * `ExecutionContext` implicitly.
     *
     * The default `ExecutionContext` implementation is backed by a work-stealing thread pool. By default,
     * the thread pool uses a target number of worker threads equal to the number of
     * [[https://docs.oracle.com/javase/8/docs/api/java/lang/Runtime.html#availableProcessors-- available processors]].
     */
    implicit lazy val global: ScalaExecutionContext = wrapExecutionContext(scala.concurrent.ExecutionContext.Implicits.global)
  }

  /**
   * wrap any ExecutionContext to support TransmittableThreadLocal
   * @param executionContext original executionContext
   * @return new wrapped executionContext that support TransmittableThreadLocal
   */
  def wrapExecutionContext(executionContext: ScalaExecutionContext): ScalaExecutionContext = {
    new ScalaExecutionContext{
      override def execute(runnable: Runnable): Unit = executionContext.execute(TtlRunnable.get(runnable, true, true))
      override def reportFailure(cause: Throwable): Unit = executionContext.reportFailure(cause)
    }
  }
}
