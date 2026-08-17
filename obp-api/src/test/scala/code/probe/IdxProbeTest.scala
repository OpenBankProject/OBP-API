package code.probe
import code.api.util.DoobieUtil
import code.setup.ServerSetup
import doobie.implicits._
class IdxProbeTest extends ServerSetup {
  Feature("probe") { Scenario("dump") {
    val lines = DoobieUtil.runQuery(sql"""SCRIPT NODATA TABLE MAPPEDTRANSACTIONREQUEST""".query[String].to[List])
    lines.foreach(l => println("DDL|" + l.replace("\n", " ")))
    succeed } }
}
