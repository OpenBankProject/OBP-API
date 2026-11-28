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
package code.api.v7_0_0

import java.util.Date

import code.dynamicentityauditchain.{DynamicEntityAuditChain, DynamicEntityAuditChains, DynamicEntityAuditEntry}
import com.openbankproject.commons.util.JsonAliases
import org.json4s.JValue

import scala.util.Try

/*
 * The JSON of the v7.0.0 Dynamic Entity audit chain endpoints. Package-level case classes, for the reason given in JSONFactory700Operations.
 */

/** A chain: which entity, who started it and when, and its last entry's sequence number and hash. */
case class DynamicEntityAuditChainJsonV700(
  chain_id: String,
  bank_id: String,
  dynamic_entity_id: String,
  entity_name: String,
  /** The name of the format each entry's hash is made in. */
  hash_format: String,
  started_at: Date,
  started_by_user_id: String,
  started_by_on_behalf_of_user_id: String,
  last_sequence: Long,
  /** The hash of the last entry: the value to keep outside OBP and compare later. */
  last_hash: String
)

/** One entry. Every string field is given exactly as it went into the hash, empty where there is no value. */
case class DynamicEntityAuditEntryJsonV700(
  sequence: Long,
  operation: String,
  record_id: String,
  owner_user_id: String,
  /** The record as it was saved; left out for STARTED and for deletions. */
  data: Option[JValue],
  /** The SHA-256 of the record's JSON exactly as stored, which is `data_json`. */
  data_hash: String,
  /** The record's JSON exactly as stored, the text data_hash is the hash of; left out where data is. */
  data_json: Option[String],
  user_id: String,
  on_behalf_of_user_id: String,
  recorded_at: Date,
  recorded_at_epoch_millis: Long,
  previous_hash: String,
  hash: String
)

case class DynamicEntityAuditEntriesJsonV700(entries: List[DynamicEntityAuditEntryJsonV700])

/** One problem. `chain_id` is given only by the root chain's check, for a problem it found in an entity's chain. */
case class DynamicEntityAuditChainProblemJsonV700(sequence: Option[Long], record_id: Option[String], problem: String, chain_id: Option[String] = None)

case class DynamicEntityAuditChainCheckJsonV700(
  is_intact: Boolean,
  entries_checked: Long,
  records_checked: Long,
  last_sequence: Long,
  last_hash: String,
  problem_count: Int,
  problems: List[DynamicEntityAuditChainProblemJsonV700],
  checked_at: Date
)

/** What the root chain's check found. `chains_checked` counts the entity chains. */
case class DynamicEntityAuditRootChainCheckJsonV700(
  is_intact: Boolean,
  entries_checked: Long,
  chains_checked: Long,
  last_sequence: Long,
  last_hash: String,
  problem_count: Int,
  problems: List[DynamicEntityAuditChainProblemJsonV700],
  checked_at: Date
)

/** The result of a seal: how many entity chains had their last entry recorded, and the root chain afterwards. */
case class DynamicEntityAuditRootChainSealJsonV700(chains_sealed: Int, root_chain: DynamicEntityAuditChainJsonV700)

object JSONFactory700DynamicEntityAuditChains {

  def createChainJson(chain: DynamicEntityAuditChain): DynamicEntityAuditChainJsonV700 =
    DynamicEntityAuditChainJsonV700(
      chain_id = chain.chainId,
      bank_id = chain.bankId,
      dynamic_entity_id = chain.dynamicEntityId,
      entity_name = chain.entityName,
      hash_format = DynamicEntityAuditChains.HashFormat,
      started_at = chain.startedAt,
      started_by_user_id = chain.startedByUserId,
      started_by_on_behalf_of_user_id = chain.startedByOnBehalfOfUserId,
      last_sequence = chain.lastSequence,
      last_hash = chain.lastHash
    )

  def createEntryJson(entry: DynamicEntityAuditEntry): DynamicEntityAuditEntryJsonV700 = {
    val dataJson = entry.dataJson
    DynamicEntityAuditEntryJsonV700(
      sequence = entry.sequence,
      operation = entry.operation,
      record_id = Option(entry.recordId).getOrElse(""),
      owner_user_id = Option(entry.ownerUserId).getOrElse(""),
      data = dataJson.flatMap(text => Try(JsonAliases.parse(text)).toOption),
      data_hash = Option(entry.dataHash).getOrElse(""),
      data_json = dataJson,
      user_id = Option(entry.userId).getOrElse(""),
      on_behalf_of_user_id = Option(entry.onBehalfOfUserId).getOrElse(""),
      recorded_at = new Date(entry.recordedAtMillis),
      recorded_at_epoch_millis = entry.recordedAtMillis,
      previous_hash = entry.previousHash,
      hash = entry.hash
    )
  }

  def createCheckJson(result: DynamicEntityAuditChains.CheckResult): DynamicEntityAuditChainCheckJsonV700 =
    DynamicEntityAuditChainCheckJsonV700(
      is_intact = result.isIntact,
      entries_checked = result.entriesChecked,
      records_checked = result.recordsChecked,
      last_sequence = result.lastSequence,
      last_hash = result.lastHash,
      problem_count = result.problemCount,
      problems = result.problems.map(problemJson),
      checked_at = result.checkedAt
    )

  private def problemJson(problem: DynamicEntityAuditChains.Problem): DynamicEntityAuditChainProblemJsonV700 =
    DynamicEntityAuditChainProblemJsonV700(problem.sequence, problem.recordId, problem.problem, problem.chainId)

  def createRootCheckJson(result: DynamicEntityAuditChains.CheckResult): DynamicEntityAuditRootChainCheckJsonV700 =
    DynamicEntityAuditRootChainCheckJsonV700(
      is_intact = result.isIntact,
      entries_checked = result.entriesChecked,
      chains_checked = result.recordsChecked,
      last_sequence = result.lastSequence,
      last_hash = result.lastHash,
      problem_count = result.problemCount,
      problems = result.problems.map(problemJson),
      checked_at = result.checkedAt
    )

  private val exampleDate = new Date(1795427564123L)

  val dynamicEntityAuditChainJsonV700Example = DynamicEntityAuditChainJsonV700(
    chain_id = "5f0c6a8e-3c1d-4a4f-9a51-2f1f6f0c9b11",
    bank_id = "gh.29.uk",
    dynamic_entity_id = "8b2a5d6e-0d3f-4e0a-b1c2-7a9e3f4d5c6b",
    entity_name = "assessment",
    hash_format = DynamicEntityAuditChains.HashFormat,
    started_at = exampleDate,
    started_by_user_id = "9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
    started_by_on_behalf_of_user_id = "9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
    last_sequence = 2,
    last_hash = "4c1f0ce6a2b7d9e8f3a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7"
  )

  val dynamicEntityAuditEntryJsonV700Example = DynamicEntityAuditEntryJsonV700(
    sequence = 2,
    operation = DynamicEntityAuditChains.OperationUpdated,
    record_id = "a1b2c3d4-0000-4000-8000-000000000001",
    owner_user_id = "",
    data = Some(JsonAliases.parse("""{"assessment_id":"a1b2c3d4-0000-4000-8000-000000000001","outcome":"pass"}""")),
    data_hash = "0d5e8b1c7f2a3e4d5c6b7a8f9e0d1c2b3a4f5e6d7c8b9a0f1e2d3c4b5a6f7e8d",
    data_json = Some("""{"assessment_id":"a1b2c3d4-0000-4000-8000-000000000001","outcome":"pass"}"""),
    user_id = "9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
    on_behalf_of_user_id = "9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
    recorded_at = exampleDate,
    recorded_at_epoch_millis = exampleDate.getTime,
    previous_hash = "7e6d5c4b3a2f1e0d9c8b7a6f5e4d3c2b1a0f9e8d7c6b5a4f3e2d1c0b9a8f7e6d",
    hash = "4c1f0ce6a2b7d9e8f3a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7"
  )

  val dynamicEntityAuditEntriesJsonV700Example = DynamicEntityAuditEntriesJsonV700(List(dynamicEntityAuditEntryJsonV700Example))

  val dynamicEntityAuditChainCheckJsonV700Example = DynamicEntityAuditChainCheckJsonV700(
    is_intact = false,
    entries_checked = 3,
    records_checked = 1,
    last_sequence = 2,
    last_hash = "4c1f0ce6a2b7d9e8f3a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7",
    problem_count = 1,
    problems = List(DynamicEntityAuditChainProblemJsonV700(Some(2), Some("a1b2c3d4-0000-4000-8000-000000000001"),
      "This record does not match its latest entry: it was changed without going through OBP-API.")),
    checked_at = exampleDate
  )

  val dynamicEntityAuditRootChainJsonV700Example = dynamicEntityAuditChainJsonV700Example.copy(
    chain_id = DynamicEntityAuditChains.RootChainId,
    bank_id = "SYS",
    dynamic_entity_id = DynamicEntityAuditChains.RootChainId,
    entity_name = "",
    last_sequence = 5
  )

  val dynamicEntityAuditRootChainEntriesJsonV700Example = DynamicEntityAuditEntriesJsonV700(List(dynamicEntityAuditEntryJsonV700Example.copy(
    sequence = 5,
    operation = DynamicEntityAuditChains.OperationChainHead,
    record_id = "5f0c6a8e-3c1d-4a4f-9a51-2f1f6f0c9b11",
    data = Some(JsonAliases.parse("""{"chain_id":"5f0c6a8e-3c1d-4a4f-9a51-2f1f6f0c9b11","last_sequence":2,"last_hash":"4c1f0ce6a2b7d9e8f3a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7"}""")),
    data_json = Some("""{"chain_id":"5f0c6a8e-3c1d-4a4f-9a51-2f1f6f0c9b11","last_sequence":2,"last_hash":"4c1f0ce6a2b7d9e8f3a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7"}""")
  )))

  val dynamicEntityAuditRootChainSealJsonV700Example = DynamicEntityAuditRootChainSealJsonV700(2, dynamicEntityAuditRootChainJsonV700Example)

  val dynamicEntityAuditRootChainCheckJsonV700Example = DynamicEntityAuditRootChainCheckJsonV700(
    is_intact = false,
    entries_checked = 6,
    chains_checked = 2,
    last_sequence = 5,
    last_hash = "9a8b7c6d5e4f3a2b1c0d9e8f7a6b5c4d3e2f1a0b9c8d7e6f5a4b3c2d1e0f9a8b",
    problem_count = 1,
    problems = List(DynamicEntityAuditChainProblemJsonV700(Some(4), Some("5f0c6a8e-3c1d-4a4f-9a51-2f1f6f0c9b11"),
      "Entry 2 of the chain of assessment is no longer the one recorded here: the chain was rewound or rewritten.")),
    checked_at = exampleDate
  )
}
