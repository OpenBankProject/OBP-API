package code.metadata.counterparties

import code.api.util.DoobieUtil
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.CounterpartyBespoke
import doobie._
import doobie.implicits._

import scala.collection.immutable.List

/**
 * A free-form key/value pair attached to a counterparty.
 *
 * `counterpartyKey` is MAPPEDCOUNTERPARTY's numeric primary key, not the public counterparty_id —
 * the callers already pass that key in.
 *
 * The value column is spelled `mvaule` in the database. That typo is load-bearing: renaming it here
 * would stop the code finding existing rows.
 */
case class MappedCounterpartyBespoke(
  counterpartyKey: Long,
  key: String,
  value: String
)

object MappedCounterpartyBespoke {

  private val selectColumns = fr"SELECT mcounterparty, mkey, mvaule FROM mappedcounterpartybespoke"

  private def query(condition: Fragment): List[MappedCounterpartyBespoke] =
    DoobieUtil.runQuery((selectColumns ++ condition).query[(Long, String, String)].to[List])
      .map { case (counterpartyKey, key, value) =>
        MappedCounterpartyBespoke(counterpartyKey, key, value) }

  def insert(counterpartyKey: Long, key: String, value: String): MappedCounterpartyBespoke = {
    DoobieUtil.runUpdate(
      sql"""INSERT INTO mappedcounterpartybespoke (mcounterparty, mkey, mvaule)
            VALUES ($counterpartyKey, $key, $value)"""
        .update.run)
    MappedCounterpartyBespoke(counterpartyKey, key, value)
  }

  def findAllByCounterpartyKey(counterpartyKey: Long): List[MappedCounterpartyBespoke] =
    query(fr"WHERE mcounterparty = $counterpartyKey ORDER BY id ASC")

  def deleteByCounterpartyKey(counterpartyKey: Long): Boolean = {
    DoobieUtil.runUpdate(
      sql"DELETE FROM mappedcounterpartybespoke WHERE mcounterparty = $counterpartyKey".update.run)
    true
  }

  def deleteAll(): Unit = {
    DoobieUtil.runUpdate(sql"DELETE FROM mappedcounterpartybespoke".update.run)
    ()
  }
}

object MapperCounterpartyBespokes extends CounterpartyBespokes with MdcLoggable {

  def createCounterpartyBespokes(mapperCounterpartyPrimaryKey: Long,
                                 bespokes: List[CounterpartyBespoke]): List[MappedCounterpartyBespoke] =
    bespokes.map(b => MappedCounterpartyBespoke.insert(mapperCounterpartyPrimaryKey, b.key, b.value))

  def getCounterpartyBespokesByCounterpartyId(mapperCounterpartyPrimaryKey: Long): List[MappedCounterpartyBespoke] =
    MappedCounterpartyBespoke.findAllByCounterpartyKey(mapperCounterpartyPrimaryKey)
}
