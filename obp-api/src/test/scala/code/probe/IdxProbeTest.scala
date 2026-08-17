package code.probe
import code.api.util.DoobieUtil
import code.setup.ServerSetup
import doobie.implicits._
class IdxProbeTest extends ServerSetup {
  Feature("probe") { Scenario("dump") {
    val cols = DoobieUtil.runQuery(
      sql"""SELECT column_name, data_type, character_maximum_length FROM information_schema.columns
            WHERE table_schema = 'PUBLIC' AND table_name = 'ACCOUNTACCESS' ORDER BY ordinal_position""".query[(String,String,Option[Int])].to[List])
    cols.foreach { case (n,t,l) => println(s"COL|$n|$t|${l.getOrElse(0)}") }
    val idx = DoobieUtil.runQuery(
      sql"""SELECT index_name, index_type_name FROM information_schema.indexes
            WHERE table_schema = 'PUBLIC' AND table_name = 'ACCOUNTACCESS'""".query[(String,String)].to[List])
    idx.foreach { case (n,t) => println(s"IDX|$n|$t") }
    succeed } }
}
