package code.DynamicData

import code.api.util.APIUtil
import code.setup.ServerSetup
import net.liftweb.common.Full
import org.json4s.JObject
import org.json4s.JsonDSL._
import net.liftweb.util.Helpers

/**
 * Creating a dynamic-entity record must INSERT, never overwrite a row that already holds the id.
 *
 * The record id can be supplied by the caller in the request body, and `dynamicdataid` is unique
 * across the whole table - not per entity, per bank or per user. So an id-keyed upsert on the
 * create path lets anyone who names an existing id rewrite that row, and because the write also
 * sets `userid`, `bankid`, `ispersonalentity` and `dynamicentityname`, it re-owns it: the record
 * moves to the caller, in the caller's bank, under whatever entity name they asked for.
 *
 * Mapper's create path was `DynamicData.create.DynamicDataId(id)...saveMe()`, an INSERT, so a
 * duplicate id hit DYNAMICDATA_DYNAMICDATAID and came back as a Failure with the existing row
 * untouched. These scenarios pin that: create is insert-only, update still works, and the update
 * path is the one allowed to rewrite - after `get` has scoped the lookup by user and bank.
 */
class DynamicDataCreateIsInsertOnlyTest extends ServerSetup {

  // getIdName turns "<entity>_Id" into snake_case, so an all-lowercase entity name keeps the
  // id field predictable: insertonlyprobe_id.
  private val entityName = "insertonlyprobe"
  private val idField = "insertonlyprobe_id"

  private def body(id: String, marker: String): JObject =
    (idField -> id) ~ ("marker" -> marker)

  feature("creating a record whose id already exists") {

    scenario("does not overwrite the existing row, and does not re-own it") {
      val victimId = APIUtil.generateUUID()
      val victimBank = Some("insertonly-victim-bank")
      val victimUser = Some("insertonly-victim-user")

      MappedDynamicDataProvider.save(
        victimBank, entityName, body(victimId, "victim data"), victimUser,
        isPersonalEntity = true) match {
        case Full(_) => // the victim's record now exists
        case other => fail(s"the first create must succeed, got $other")
      }

      // Someone else creates, naming the victim's id. Under an upsert this rewrote the row.
      val attackerResult = MappedDynamicDataProvider.save(
        Some("insertonly-attacker-bank"), entityName, body(victimId, "attacker data"),
        Some("insertonly-attacker-user"), isPersonalEntity = true)

      attackerResult.isDefined should equal(false)

      // The victim's row is intact: same data, same owner, same bank.
      DynamicData.findPersonal(victimBank, entityName, victimId, victimUser) match {
        case Full(row) =>
          row.dataJson should include("victim data")
          row.dataJson should not include "attacker data"
          row.userId should equal(victimUser)
          row.bankId should equal(victimBank)
        case other => fail(s"the victim's record must still be there, got $other")
      }

      DynamicData.delete(victimId)
    }
  }

  feature("the update path") {

    scenario("still rewrites the record it was given, for its owner") {
      val id = APIUtil.generateUUID()
      val bank = Some("insertonly-update-bank")
      val user = Some("insertonly-update-user-" + Helpers.randomString(6).toLowerCase)

      MappedDynamicDataProvider.save(bank, entityName, body(id, "before"), user,
        isPersonalEntity = true).isDefined should equal(true)

      MappedDynamicDataProvider.update(bank, entityName, body(id, "after"), id, user,
        isPersonalEntity = true).isDefined should equal(true)

      DynamicData.findPersonal(bank, entityName, id, user) match {
        case Full(row) =>
          row.dataJson should include("after")
          row.userId should equal(user)
        case other => fail(s"the updated record must be readable, got $other")
      }

      DynamicData.delete(id)
    }
  }
}
