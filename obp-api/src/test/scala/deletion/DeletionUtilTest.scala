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
package deletion

import org.scalatest.{FeatureSpec, Matchers}

import java.lang.reflect.{InvocationHandler, Method, Proxy => JProxy}
import java.sql.{Connection, SQLException, SQLFeatureNotSupportedException, Savepoint}
import scala.collection.mutable.ListBuffer

/** How a cascade uses the savepoint on its connection: DeletionUtil.withSavepoint against a recording connection. */
class DeletionUtilTest extends FeatureSpec with Matchers {

  private val savepoint: Savepoint = new Savepoint {
    def getSavepointId: Int = 1
    def getSavepointName: String = "cascade"
  }

  /** A connection that records the savepoint calls it gets; `releaseFails` and `rollbackFails` make those calls throw. */
  private class RecordingConnection(releaseFails: Option[SQLException] = None, rollbackFails: Option[SQLException] = None) {
    val calls = ListBuffer.empty[String]
    val connection: Connection = JProxy.newProxyInstance(
      classOf[Connection].getClassLoader,
      Array(classOf[Connection]),
      new InvocationHandler {
        def invoke(proxy: Any, method: Method, args: Array[AnyRef]): AnyRef = method.getName match {
          case "setSavepoint" => calls += "setSavepoint"; savepoint
          case "rollback" if args != null && args.length == 1 =>
            calls += "rollback(savepoint)"; rollbackFails.foreach(e => throw e); null
          case "releaseSavepoint" =>
            calls += "releaseSavepoint"; releaseFails.foreach(e => throw e); null
          case other => calls += other; null
        }
      }
    ).asInstanceOf[Connection]
  }

  feature("A cascade's savepoint") {

    scenario("is released when the cascade succeeds") {
      val recording = new RecordingConnection()

      DeletionUtil.withSavepoint(recording.connection)("deleted") should equal("deleted")

      recording.calls.toList should equal(List("setSavepoint", "releaseSavepoint"))
    }

    scenario("is rolled back to when the cascade throws, and the cascade's exception goes on") {
      val recording = new RecordingConnection()
      val failure = new RuntimeException("cascade failed")

      the[RuntimeException] thrownBy DeletionUtil.withSavepoint(recording.connection)(throw failure) should be theSameInstanceAs failure

      recording.calls.toList should equal(List("setSavepoint", "rollback(savepoint)"))
    }

    scenario("does not fail a cascade that succeeded on a database that cannot release a savepoint") {
      // Oracle's and SQL Server's drivers throw on releaseSavepoint; the savepoint ends with the transaction anyway.
      val recording = new RecordingConnection(releaseFails = Some(new SQLFeatureNotSupportedException("releaseSavepoint")))

      DeletionUtil.withSavepoint(recording.connection)("deleted") should equal("deleted")
    }

    scenario("keeps the cascade's exception when rolling back to it fails too") {
      val rollbackFailure = new SQLException("connection lost")
      val recording = new RecordingConnection(rollbackFails = Some(rollbackFailure))
      val failure = new RuntimeException("cascade failed")

      val thrown = the[RuntimeException] thrownBy DeletionUtil.withSavepoint(recording.connection)(throw failure)

      thrown should be theSameInstanceAs failure
      thrown.getSuppressed.toList should equal(List(rollbackFailure))
    }
  }
}
