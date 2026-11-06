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

package com.openbankproject.commons

import com.alibaba.ttl.TtlRunnable

import java.security.{AccessController, PrivilegedAction}
import scala.concurrent.{ExecutionContext => ScalaExecutionContext}

object ExecutionContext {
  val enableSandbox = System.getProperty("dynamic_code_sandbox_enable", "false").toBoolean

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
      override def execute(runnable: Runnable): Unit = {
        val privilegedRunnable = if(enableSandbox) PrivilegedRunnable(runnable) else runnable
        executionContext.execute(TtlRunnable.get(privilegedRunnable, true, true))
      }
      override def reportFailure(cause: Throwable): Unit = executionContext.reportFailure(cause)
    }
  }

  def PrivilegedRunnable(runnable: Runnable): Runnable = {
    val acc = AccessController.getContext
    val privilegedAction: PrivilegedAction[Unit] = () => runnable.run()
    () => AccessController.doPrivileged(privilegedAction, acc)
  }
}
