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
package code.dynamicentityauditchain

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Date

import code.api.Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID
import code.api.util.APIUtil
import code.users.{UserReference, Users}
import code.util.Helper.MdcLoggable
import net.liftweb.common.{Box, Full}
import net.liftweb.db.DB
import net.liftweb.mapper._
import net.liftweb.util.DefaultConnectionIdentifier
import net.liftweb.util.Helpers.tryo

import scala.collection.mutable

/**
 * A Dynamic Entity audit chain is a tamper-evident record of every change to the records of one Dynamic Entity. Each change adds an entry that holds the record as it was saved, who saved it and when, and the hash of the entry before it. Changing or removing an entry afterwards, or changing a record without adding an entry, breaks the chain at that point, and the check finds it. See the Glossary item "Dynamic Entity Audit Chains".
 *
 * A chain cannot be switched off. There is no endpoint to stop one, and an entity with a chain cannot be deleted or renamed. Someone with direct access to the database can still change anything, so the chain does not prevent tampering: it makes it visible.
 *
 * Each entity's chain is bound to its entity: its first entry names the entity, and that entry's hash covers the name. The chains are tied together by one root chain per OBP instance, which records each chain when it is started, and the last entry of each chain whenever the chains are sealed. So a whole chain deleted, moved, rewound or rewritten shows up in the root chain's check. A consistent rewrite of every chain and of the root chain is only caught by comparing the root chain's last hash with a copy kept somewhere else, which is what that hash is published for.
 */
object DynamicEntityAuditChains extends MdcLoggable {

  /** The name of the hash format, which is the first line of what each entry's hash is made from. */
  val HashFormat = "OBP-DE-AUDIT-1"

  /** The previous hash of the first entry of every chain. */
  val NoPreviousHash: String = "0" * 64

  /** The first entry of a chain, added when the chain is started. */
  val OperationStarted = "STARTED"
  /** A record that already existed when the chain was started, one entry each. */
  val OperationExisting = "EXISTING"
  val OperationCreated = "CREATED"
  val OperationUpdated = "UPDATED"
  val OperationDeleted = "DELETED"
  /** A record deleted by a data retention run rather than by a caller. */
  val OperationDeletedByRetention = "DELETED_BY_RETENTION"

  /** The operations after which a record no longer exists. */
  private val deletions = Set(OperationDeleted, OperationDeletedByRetention)

  /** The space as the chain stores it: a bank id, or SYS for the system space. */
  private def spaceOf(bankId: Option[String]): String = bankId.filter(_.nonEmpty).getOrElse(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID)

  /** The chain of the entity named `entityName` in the space `bankId` (None for the system space), if it has one. */
  def chainOf(bankId: Option[String], entityName: String): Box[DynamicEntityAuditChain] =
    DynamicEntityAuditChain.find(By(DynamicEntityAuditChain.BankId, spaceOf(bankId)), By(DynamicEntityAuditChain.EntityName, entityName))

  /** The chain of the Dynamic Entity with this id, if it has one. */
  def chainOfEntity(dynamicEntityId: String): Box[DynamicEntityAuditChain] =
    DynamicEntityAuditChain.find(By(DynamicEntityAuditChain.DynamicEntityId, dynamicEntityId))

  /** Whether the Dynamic Entity with this id has a chain. Used to refuse deleting or renaming it. */
  def hasChain(dynamicEntityId: String): Boolean =
    Option(dynamicEntityId).exists(id => id.nonEmpty && DynamicEntityAuditChain.count(By(DynamicEntityAuditChain.DynamicEntityId, id)) > 0)

  /** The SHA-256 of a string's UTF-8 bytes, as 64 lowercase hexadecimal characters. */
  def sha256Hex(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)).map(byte => f"$byte%02x").mkString

  /**
   * The hash of an entry: the SHA-256 of these lines, joined by a line feed with none at the end, where a missing value is an empty line. Anyone can work it out again from an entry's JSON, without OBP.
   *
   * HashFormat, chain_id, sequence, operation, record_id, owner_user_id, data_hash, user_id, on_behalf_of_user_id, recorded_at_epoch_millis, previous_hash.
   *
   * data_hash is itself the SHA-256 of the record's JSON exactly as stored, so the record's content is covered without having to agree on how JSON is written out.
   */
  def entryHash(chainId: String, sequence: Long, operation: String, recordId: String, ownerUserId: String, dataHash: String,
                userId: String, onBehalfOfUserId: String, recordedAtMillis: Long, previousHash: String): String =
    sha256Hex(List(HashFormat, chainId, sequence.toString, operation, recordId, ownerUserId, dataHash, userId, onBehalfOfUserId,
      recordedAtMillis.toString, previousHash).map(value => Option(value).getOrElse("")).mkString("\n"))

  /** The data_hash of a record's stored JSON: empty when there is none, as for a deletion. */
  def dataHashOf(dataJson: Option[String]): String = dataJson.map(sha256Hex).getOrElse("")

  /**
   * The two user ids recorded for a call made by `callerUserId`: the caller itself, which is an agent's own id when an agent made the call, and the user the call was made for. The same pair the record's own writer columns hold.
   */
  private def writersOf(callerUserId: Option[String], ref: UserReference): (String, String) =
    callerUserId.filter(_.nonEmpty) match {
      case None => ("", "")
      case Some(caller) =>
        Users.users.vend.attributionOf(caller, ref) match {
          case Full(attribution) => (attribution.userId, attribution.onBehalfOfUserId)
          case _                 => (caller, caller)
        }
    }

  /**
   * This adds one entry to a chain, in the caller's transaction.
   *
   * The order of the entries is decided by the database, not by the clock. The UPDATE below adds one to the chain's last sequence number, and in doing so locks the chain's row until the transaction ends, so a second write to the same entity waits here until the first has committed or rolled back, and then reads the sequence number and hash the first left behind. Writes to other entities have other chains and do not wait. The unique index on (chain_id, sequence) is the backstop: two entries can never share a number.
   */
  private def append(chain: DynamicEntityAuditChain, operation: String, recordId: String, ownerUserId: Option[String],
                     dataJson: Option[String], callerUserId: Option[String]): DynamicEntityAuditEntry = {
    val table = DynamicEntityAuditChain.dbTableName
    val lastSequenceColumn = DynamicEntityAuditChain.LastSequence._dbColumnNameLC
    val chainIdColumn = DynamicEntityAuditChain.ChainId._dbColumnNameLC
    val updated = DB.runUpdate(s"UPDATE $table SET $lastSequenceColumn = $lastSequenceColumn + 1 WHERE $chainIdColumn = ?", List(chain.chainId))
    if (updated != 1) throw new IllegalStateException(s"append says: the audit chain ${chain.chainId} of ${chain.entityName} could not be locked")
    // Read again now that the row is locked: these are the values the entry before this one left.
    val locked = DynamicEntityAuditChain.find(By(DynamicEntityAuditChain.ChainId, chain.chainId))
      .openOrThrowException(s"the audit chain ${chain.chainId}")
    val sequence = locked.LastSequence.get
    val previousHash = Option(locked.LastHash.get).filter(_.nonEmpty).getOrElse(NoPreviousHash)
    val (userId, onBehalfOfUserId) = writersOf(callerUserId, UserReference.DynamicEntityAuditEntry_UserId)
    val dataHash = dataHashOf(dataJson)
    val recordedAtMillis = System.currentTimeMillis()
    val owner = ownerUserId.getOrElse("")
    val hash = entryHash(locked.chainId, sequence, operation, recordId, owner, dataHash, userId, onBehalfOfUserId, recordedAtMillis, previousHash)
    val entry = DynamicEntityAuditEntry.create
      .ChainId(locked.chainId)
      .Sequence(sequence)
      .Operation(operation)
      .RecordId(recordId)
      .OwnerUserId(owner)
      .DataJson(dataJson.orNull)
      .DataHash(dataHash)
      .UserId(userId)
      .OnBehalfOfUserId(onBehalfOfUserId)
      .RecordedAtMillis(recordedAtMillis)
      .PreviousHash(previousHash)
      .Hash(hash)
      .saveMe()
    locked.LastHash(hash).saveMe()
    entry
  }

  /** The chain id of the root chain. There is one root chain per OBP instance, and it holds no records. */
  val RootChainId = "root"

  /** The first entry of the root chain names it as the root; the first entry of an entity's chain names its entity. */
  private val RootIdentityJson = """{"chain":"root"}"""

  /** An entity's chain was started. Recorded in the root chain, with the chain's entity, its first entry's hash and its last entry. */
  val OperationChainStarted = "CHAIN_STARTED"
  /** The last entry of an entity's chain at that moment, recorded in the root chain by a seal. */
  val OperationChainHead = "CHAIN_HEAD"

  /**
   * The data of an entity chain's STARTED entry: the space, the entity's id and the entity's name, in that order. Because the entry's hash covers it, the chain is bound to its entity: pointing the chain's row at another entity makes the check fail.
   */
  def identityJson(chain: DynamicEntityAuditChain): String =
    compactJson(List("bank_id" -> chain.bankId, "dynamic_entity_id" -> chain.dynamicEntityId, "entity_name" -> chain.entityName))

  /** A flat JSON object of strings and numbers, in the order given, written out the same way every time. */
  private def compactJson(fields: List[(String, Any)]): String = {
    import org.json4s._
    val values = fields.map {
      case (name, number: Long) => JField(name, JInt(number))
      case (name, number: Int)  => JField(name, JInt(number))
      case (name, text)         => JField(name, JString(String.valueOf(text)))
    }
    org.json4s.native.JsonMethods.compact(org.json4s.native.JsonMethods.render(JObject(values)))
  }

  private def parseJson(text: String): Map[String, String] = {
    import org.json4s._
    scala.util.Try(org.json4s.native.JsonMethods.parse(text)).toOption.collect { case JObject(fields) =>
      fields.collect {
        case JField(name, JString(value)) => name -> value
        case JField(name, JInt(value))    => name -> value.toString
      }.toMap
    }.getOrElse(Map.empty)
  }

  /** The root chain, if it has been created. */
  def rootChain: Box[DynamicEntityAuditChain] = DynamicEntityAuditChain.find(By(DynamicEntityAuditChain.ChainId, RootChainId))

  /** Every entity's chain, oldest first. The root chain is not one of them. */
  def entityChains: List[DynamicEntityAuditChain] =
    DynamicEntityAuditChain.findAll(NotBy(DynamicEntityAuditChain.ChainId, RootChainId), OrderBy(DynamicEntityAuditChain.id, Ascending))

  /** The entry of a chain with this sequence number, if there is one. */
  private def entryAt(chainId: String, sequence: Long): Box[DynamicEntityAuditEntry] =
    DynamicEntityAuditEntry.find(By(DynamicEntityAuditEntry.ChainId, chainId), By(DynamicEntityAuditEntry.Sequence, sequence))

  /** The entry of a chain with the highest sequence number, if there is one. */
  private def lastEntryOf(chainId: String): Box[DynamicEntityAuditEntry] =
    DynamicEntityAuditEntry.findAll(By(DynamicEntityAuditEntry.ChainId, chainId), OrderBy(DynamicEntityAuditEntry.Sequence, Descending), MaxRows(1)).headOption

  /**
   * The root chain, created when the first entity chain is started. When it is created, every entity chain that already exists is registered in it, so no chain is left out.
   */
  private def ensureRoot(callerUserId: Option[String]): DynamicEntityAuditChain =
    rootChain.openOr {
      val (userId, onBehalfOfUserId) = writersOf(callerUserId, UserReference.DynamicEntityAuditChain_StartedByUserId)
      val root = DynamicEntityAuditChain.create
        .ChainId(RootChainId)
        .BankId(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID)
        .DynamicEntityId(RootChainId)
        .EntityName("")
        .LastSequence(-1L)
        .LastHash("")
        .StartedByUserId(userId)
        .StartedByOnBehalfOfUserId(onBehalfOfUserId)
        .StartedAt(new Date())
        .saveMe()
      append(root, OperationStarted, "", None, Some(RootIdentityJson), callerUserId)
      entityChains.foreach(register(root, _, callerUserId))
      root
    }

  /** This records in the root chain that an entity's chain exists: its entity, the hash of its first entry, and its last entry now. */
  private def register(root: DynamicEntityAuditChain, chain: DynamicEntityAuditChain, callerUserId: Option[String]): Unit = {
    val first = entryAt(chain.chainId, 0L).openOrThrowException(s"the first entry of the audit chain ${chain.chainId}")
    val last = lastEntryOf(chain.chainId).openOrThrowException(s"the last entry of the audit chain ${chain.chainId}")
    append(root, OperationChainStarted, chain.chainId, None, Some(compactJson(List(
      "chain_id" -> chain.chainId, "bank_id" -> chain.bankId, "dynamic_entity_id" -> chain.dynamicEntityId, "entity_name" -> chain.entityName,
      "started_hash" -> first.Hash.get, "last_sequence" -> last.Sequence.get, "last_hash" -> last.Hash.get))), callerUserId)
  }

  /**
   * This starts the chain of a Dynamic Entity. The first entry is STARTED, whose data names the entity. Then there is one EXISTING entry for each record the entity already holds, oldest first, so that the chain starts from the records as they are. Last, the chain is registered in the root chain. It all happens in one transaction: the chain starts completely or not at all.
   */
  def start(bankId: Option[String], dynamicEntityId: String, entityName: String, callerUserId: Option[String]): Box[DynamicEntityAuditChain] =
    tryo {
      DB.use(DefaultConnectionIdentifier) { _ =>
        val root = ensureRoot(callerUserId)
        val (userId, onBehalfOfUserId) = writersOf(callerUserId, UserReference.DynamicEntityAuditChain_StartedByUserId)
        val chain = DynamicEntityAuditChain.create
          .ChainId(APIUtil.generateUUID())
          .BankId(spaceOf(bankId))
          .DynamicEntityId(dynamicEntityId)
          .EntityName(entityName)
          .LastSequence(-1L)
          .LastHash("")
          .StartedByUserId(userId)
          .StartedByOnBehalfOfUserId(onBehalfOfUserId)
          .StartedAt(new Date())
          .saveMe()
        append(chain, OperationStarted, "", None, Some(identityJson(chain)), callerUserId)
        code.DynamicData.DynamicData.findAll(
          By(code.DynamicData.DynamicData.BankId, spaceOf(bankId)),
          By(code.DynamicData.DynamicData.DynamicEntityName, entityName),
          OrderBy(code.DynamicData.DynamicData.id, Ascending)
        ).foreach { record =>
          append(chain, OperationExisting, record.DynamicDataId.get, Option(record.UserId.get), Option(record.DataJson.get), callerUserId)
        }
        register(root, chain, callerUserId)
        DynamicEntityAuditChain.find(By(DynamicEntityAuditChain.ChainId, chain.chainId)).openOrThrowException("the new audit chain")
      }
    }

  /**
   * This records in the root chain the last entry of every entity chain that has changed since its last entry was last recorded there. The last entry is read from the entries themselves, not from the chain's row. Afterwards, the root chain's last hash covers every entity chain as it is now, so keeping that one hash outside OBP covers them all. Returns the root chain and how many chains were sealed.
   */
  def seal(callerUserId: Option[String]): Box[(DynamicEntityAuditChain, Int)] =
    tryo {
      DB.use(DefaultConnectionIdentifier) { _ =>
        val root = ensureRoot(callerUserId)
        val recorded = latestHeads(root.chainId)
        val sealedCount = entityChains.count { chain =>
          lastEntryOf(chain.chainId).toOption.exists { last =>
            val changed = !recorded.get(chain.chainId).contains((last.Sequence.get, last.Hash.get))
            if (changed)
              append(root, OperationChainHead, chain.chainId, None, Some(compactJson(List(
                "chain_id" -> chain.chainId, "last_sequence" -> last.Sequence.get, "last_hash" -> last.Hash.get))), callerUserId)
            changed
          }
        }
        (rootChain.openOrThrowException("the root audit chain"), sealedCount)
      }
    }

  /** The last entry of each entity chain as the root chain last recorded it: chain id to (sequence, hash). */
  private def latestHeads(rootChainId: String): Map[String, (Long, String)] = {
    val heads = mutable.HashMap[String, (Long, String)]()
    var page = entries(rootChainId, 0L, PageSize)
    while (page.nonEmpty) {
      page.foreach { entry =>
        if (entry.Operation.get == OperationChainStarted || entry.Operation.get == OperationChainHead) {
          val data = parseJson(Option(entry.DataJson.get).getOrElse(""))
          for (chainId <- data.get("chain_id"); sequence <- data.get("last_sequence").flatMap(text => scala.util.Try(text.toLong).toOption); hash <- data.get("last_hash"))
            heads(chainId) = (sequence, hash)
        }
      }
      page = entries(rootChainId, page.last.Sequence.get + 1, PageSize)
    }
    heads.toMap
  }

  /**
   * A record of the entity was created or updated. Called by the record data provider inside the write, after the record is saved, so the entry is part of the same transaction: if the write rolls back, so does the entry. Does nothing for an entity without a chain.
   */
  def recordSaved(bankId: Option[String], entityName: String, recordId: String, isNew: Boolean, ownerUserId: Option[String],
                  dataJson: String, callerUserId: Option[String]): Unit =
    chainOf(bankId, entityName).foreach { chain =>
      append(chain, if (isNew) OperationCreated else OperationUpdated, recordId, ownerUserId, Some(dataJson), callerUserId)
    }

  /** A record of the entity was deleted, by a caller or, with `byRetention`, by a data retention run. As recordSaved. */
  def recordDeleted(bankId: Option[String], entityName: String, recordId: String, ownerUserId: Option[String],
                    callerUserId: Option[String], byRetention: Boolean = false): Unit =
    chainOf(bankId, entityName).foreach { chain =>
      append(chain, if (byRetention) OperationDeletedByRetention else OperationDeleted, recordId, ownerUserId, None, callerUserId)
    }

  /** Up to `limit` entries of a chain, from the sequence number `fromSequence` on, in order. */
  def entries(chainId: String, fromSequence: Long, limit: Int): List[DynamicEntityAuditEntry] =
    DynamicEntityAuditEntry.findAll(
      By(DynamicEntityAuditEntry.ChainId, chainId),
      By_>=(DynamicEntityAuditEntry.Sequence, fromSequence),
      OrderBy(DynamicEntityAuditEntry.Sequence, Ascending),
      MaxRows(limit))

  /** The entries of one record in a chain, oldest first. In the root chain, the record is an entity chain, named by its chain id. */
  def entriesOfRecord(chainId: String, recordId: String): List[DynamicEntityAuditEntry] =
    DynamicEntityAuditEntry.findAll(
      By(DynamicEntityAuditEntry.ChainId, chainId),
      By(DynamicEntityAuditEntry.RecordId, recordId),
      OrderBy(DynamicEntityAuditEntry.Sequence, Ascending))

  /** One thing wrong with a chain: at an entry (`sequence`), with a record (`recordId`), or both. `chainId` names the entity chain, for a problem the root check found in one. */
  case class Problem(sequence: Option[Long], recordId: Option[String], problem: String, chainId: Option[String] = None)

  /** What a check found. `problems` lists the first MaxProblemsListed; `problemCount` counts them all. For the root chain, `recordsChecked` counts the entity chains checked. */
  case class CheckResult(isIntact: Boolean, entriesChecked: Long, recordsChecked: Long, lastSequence: Long, lastHash: String,
                         problemCount: Int, problems: List[Problem], checkedAt: Date)

  val MaxProblemsListed = 100
  private val PageSize = 1000

  /** Collects problems, keeping the first MaxProblemsListed and counting them all. */
  private class Problems {
    val listed = mutable.ArrayBuffer[Problem]()
    var count = 0
    def found(sequence: Option[Long], recordId: Option[String], problem: String, chainId: Option[String] = None): Unit = {
      count += 1
      if (listed.size < MaxProblemsListed) listed += Problem(sequence, recordId, problem, chainId)
    }
    def sorted: List[Problem] =
      listed.toList.sortBy(problem => (problem.chainId.getOrElse(""), problem.sequence.getOrElse(Long.MaxValue), problem.recordId.getOrElse("")))
  }

  /**
   * This checks the entries of a chain on their own, from the first: each sequence number follows the one before, each previous_hash is the hash of the one before, each data_hash is the hash of its data, and each hash is right. Then the chain's row must name its last entry. `visit` sees each entry. Returns how many entries there were, and the sequence number and hash of the last.
   */
  private def checkEntries(chain: DynamicEntityAuditChain, problems: Problems, visit: DynamicEntityAuditEntry => Unit): (Long, Long, String) = {
    var expectedSequence = 0L
    var previousHash = NoPreviousHash
    var entriesChecked = 0L
    var page = entries(chain.chainId, 0L, PageSize)
    while (page.nonEmpty) {
      page.foreach { entry =>
        val sequence = entry.Sequence.get
        if (sequence != expectedSequence)
          problems.found(Some(sequence), None, s"Expected entry $expectedSequence here, found entry $sequence: an entry is missing or out of place.")
        if (entry.PreviousHash.get != previousHash)
          problems.found(Some(sequence), None, "Its previous_hash is not the hash of the entry before it.")
        if (entry.DataHash.get != dataHashOf(Option(entry.DataJson.get)))
          problems.found(Some(sequence), Option(entry.RecordId.get).filter(_.nonEmpty), "Its data does not match its data_hash: the data was changed.")
        val hash = entryHash(entry.ChainId.get, sequence, entry.Operation.get, entry.RecordId.get, entry.OwnerUserId.get, entry.DataHash.get,
          entry.UserId.get, entry.OnBehalfOfUserId.get, entry.RecordedAtMillis.get, entry.PreviousHash.get)
        if (entry.Hash.get != hash)
          problems.found(Some(sequence), None, "Its hash is not the hash of its contents: the entry was changed.")
        visit(entry)
        expectedSequence = sequence + 1
        previousHash = entry.Hash.get
        entriesChecked += 1
      }
      page = entries(chain.chainId, expectedSequence, PageSize)
    }
    if (entriesChecked == 0)
      problems.found(None, None, "The chain has no entries: its first entry is missing.")
    val lastSequence = expectedSequence - 1
    val lastHash = if (entriesChecked == 0) "" else previousHash
    if (chain.LastSequence.get != lastSequence || Option(chain.LastHash.get).getOrElse("") != lastHash)
      problems.found(None, None, s"The chain says its last entry is ${chain.LastSequence.get}, with hash ${chain.LastHash.get}, but its last entry is $lastSequence, with hash $lastHash: entries were added or removed.")
    (entriesChecked, lastSequence, lastHash)
  }

  /**
   * This checks an entity's chain from its first entry, and the entity's current records against it.
   *
   * First the entries, as checkEntries describes. The first entry must be STARTED and name this chain's entity, and that entity must still exist under that name. Then each current record must match the latest entry for it (the same data and owner, and not a deletion), and each record whose latest entry is not a deletion must still exist.
   *
   * A record changed, added or deleted without going through OBP-API, or a chain pointed at another entity, shows up as a problem. A chain rewritten consistently from the first change onwards does not. The root chain's check, and an outside copy of the root chain's last hash, catch that.
   */
  def check(chain: DynamicEntityAuditChain): CheckResult = {
    val problems = new Problems
    // The latest entry of each record: its operation, data hash, owner and sequence number.
    val latest = mutable.HashMap[String, (String, String, String, Long)]()
    val (entriesChecked, lastSequence, lastHash) = checkEntries(chain, problems, { entry =>
      if (entry.Sequence.get == 0L && (entry.Operation.get != OperationStarted || Option(entry.DataJson.get).getOrElse("") != identityJson(chain)))
        problems.found(Some(0L), None, "The first entry does not name this chain's entity: the chain was moved to another entity, or its first entry was changed.")
      Option(entry.RecordId.get).filter(_.nonEmpty).foreach { recordId =>
        latest(recordId) = (entry.Operation.get, entry.DataHash.get, Option(entry.OwnerUserId.get).getOrElse(""), entry.Sequence.get)
      }
    })

    val bankIdOrNone = Option(chain.bankId).filterNot(_ == DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID)
    val entity = code.dynamicEntity.DynamicEntityProvider.connectorMethodProvider.vend.getById(bankIdOrNone, chain.dynamicEntityId)
    if (!entity.exists(_.entityName == chain.entityName))
      problems.found(None, None, s"The Dynamic Entity ${chain.entityName} (${chain.dynamicEntityId}) of this chain no longer exists, or has another name: it was deleted or renamed without going through OBP-API.")

    // The current records, page by page, against the latest entry of each.
    val seen = mutable.HashSet[String]()
    var recordsChecked = 0L
    var lastRowId = 0L
    var rows = recordsAfter(chain, lastRowId)
    while (rows.nonEmpty) {
      rows.foreach { record =>
        val recordId = record.DynamicDataId.get
        seen += recordId
        recordsChecked += 1
        latest.get(recordId) match {
          case None =>
            problems.found(None, Some(recordId), "This record has no entry in the chain: it was added without going through OBP-API.")
          case Some((operation, _, _, sequence)) if deletions.contains(operation) =>
            problems.found(Some(sequence), Some(recordId), "This record exists, but the chain says it was deleted: it was put back without going through OBP-API.")
          case Some((_, dataHash, owner, sequence)) =>
            if (dataHash != sha256Hex(record.DataJson.get))
              problems.found(Some(sequence), Some(recordId), "This record does not match its latest entry: it was changed without going through OBP-API.")
            else if (owner != Option(record.UserId.get).getOrElse(""))
              problems.found(Some(sequence), Some(recordId), "This record's owner does not match its latest entry: it was changed without going through OBP-API.")
        }
        lastRowId = record.id.get
      }
      rows = recordsAfter(chain, lastRowId)
    }
    latest.foreach { case (recordId, (operation, _, _, sequence)) =>
      if (!deletions.contains(operation) && !seen.contains(recordId))
        problems.found(Some(sequence), Some(recordId), "The chain says this record exists, but it does not: it was deleted without going through OBP-API.")
    }

    CheckResult(problems.count == 0, entriesChecked, recordsChecked, lastSequence, lastHash, problems.count, problems.sorted, new Date())
  }

  /**
   * This checks the root chain: its own entries, as checkEntries describes, and then every entity chain against what the root chain recorded about it.
   *
   * Each chain registered in the root chain must still exist, still name the same entity, and still have the same first entry. Its entry at the last sequence number recorded for it must still have the hash recorded. Each entity chain that exists must be registered. This finds a chain deleted, moved, started again, rewound or rewritten, and a chain added, without going through OBP-API.
   *
   * With `includeChains`, each entity chain is also checked in full, as check describes, and its problems are listed with its chain id.
   */
  def checkRoot(root: DynamicEntityAuditChain, includeChains: Boolean): CheckResult = {
    val problems = new Problems
    // What the root chain recorded about each entity chain: its identity and first hash, and its last recorded entry.
    val registered = mutable.LinkedHashMap[String, (Map[String, String], Long)]()
    val heads = mutable.HashMap[String, (Long, String, Long)]()
    val (entriesChecked, lastSequence, lastHash) = checkEntries(root, problems, { entry =>
      val sequence = entry.Sequence.get
      if (sequence == 0L && (entry.Operation.get != OperationStarted || Option(entry.DataJson.get).getOrElse("") != RootIdentityJson))
        problems.found(Some(0L), None, "The first entry does not name this chain as the root chain: it was changed.")
      val data = parseJson(Option(entry.DataJson.get).getOrElse(""))
      val headOf = for (headSequence <- data.get("last_sequence").flatMap(text => scala.util.Try(text.toLong).toOption); headHash <- data.get("last_hash")) yield (headSequence, headHash)
      entry.Operation.get match {
        case OperationChainStarted =>
          data.get("chain_id").foreach { chainId =>
            if (registered.contains(chainId))
              problems.found(Some(sequence), Some(chainId), "This chain is registered a second time: it was started again without going through OBP-API.")
            registered(chainId) = (data, sequence)
            headOf.foreach { case (headSequence, headHash) => heads(chainId) = (headSequence, headHash, sequence) }
          }
        case OperationChainHead =>
          data.get("chain_id").foreach { chainId =>
            if (!registered.contains(chainId))
              problems.found(Some(sequence), Some(chainId), "This entry records the last entry of a chain that was never registered.")
            headOf.foreach { case (headSequence, headHash) => heads(chainId) = (headSequence, headHash, sequence) }
          }
        case _ =>
      }
    })

    val chains = entityChains
    val byId = chains.map(chain => chain.chainId -> chain).toMap
    registered.foreach { case (chainId, (data, sequence)) =>
      byId.get(chainId) match {
        case None =>
          problems.found(Some(sequence), Some(chainId), s"The chain of ${data.getOrElse("entity_name", "")} is registered here, but it no longer exists: it was deleted without going through OBP-API.")
        case Some(chain) =>
          val sameEntity = data.get("bank_id").contains(chain.bankId) && data.get("dynamic_entity_id").contains(chain.dynamicEntityId) && data.get("entity_name").contains(chain.entityName)
          if (!sameEntity) problems.found(Some(sequence), Some(chainId), s"The chain of ${data.getOrElse("entity_name", "")} now names another entity: it was moved without going through OBP-API.")
          if (!entryAt(chainId, 0L).toOption.exists(first => data.get("started_hash").contains(first.Hash.get)))
            problems.found(Some(sequence), Some(chainId), s"The first entry of the chain of ${chain.entityName} is not the one registered here: the chain was rewritten from the start.")
          heads.get(chainId).foreach { case (headSequence, headHash, rootSequence) =>
            if (!entryAt(chainId, headSequence).toOption.exists(_.Hash.get == headHash))
              problems.found(Some(rootSequence), Some(chainId), s"Entry $headSequence of the chain of ${chain.entityName} is no longer the one recorded here: the chain was rewound or rewritten.")
          }
      }
    }
    chains.filterNot(chain => registered.contains(chain.chainId)).foreach { chain =>
      problems.found(None, Some(chain.chainId), s"The chain of ${chain.entityName} is not registered in the root chain: it was added without going through OBP-API.")
    }
    if (includeChains)
      chains.foreach { chain =>
        val result = check(chain)
        result.problems.foreach(problem => problems.found(problem.sequence, problem.recordId, s"In the chain of ${chain.entityName}: ${problem.problem}", Some(chain.chainId)))
        // Problems beyond those listed are counted too.
        problems.count += result.problemCount - result.problems.size
      }

    CheckResult(problems.count == 0, entriesChecked, chains.size.toLong, lastSequence, lastHash, problems.count, problems.sorted, new Date())
  }

  private def recordsAfter(chain: DynamicEntityAuditChain, rowId: Long): List[code.DynamicData.DynamicData] =
    code.DynamicData.DynamicData.findAll(
      By(code.DynamicData.DynamicData.BankId, chain.BankId.get),
      By(code.DynamicData.DynamicData.DynamicEntityName, chain.EntityName.get),
      By_>(code.DynamicData.DynamicData.id, rowId),
      OrderBy(code.DynamicData.DynamicData.id, Ascending),
      MaxRows(PageSize))
}

/**
 * The chain of one Dynamic Entity: which entity, who started it and when, and the sequence number and hash of its last entry. The last sequence number is what each new entry locks and moves on; the last hash is what the next entry points back to, and what can be kept outside OBP to compare against later.
 */
class DynamicEntityAuditChain extends LongKeyedMapper[DynamicEntityAuditChain] with IdPK {
  def getSingleton = DynamicEntityAuditChain

  object ChainId extends MappedString(this, 36) {
    override def dbColumnName = "chain_id"
  }
  // SYS for the system space, never NULL: see DynamicEntitySpace.
  object BankId extends MappedString(this, 255) {
    override def dbColumnName = "bank_id"
    override def defaultValue = DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID
  }
  object DynamicEntityId extends MappedString(this, 255) {
    override def dbColumnName = "dynamic_entity_id"
  }
  // Kept beside the id because a record write knows the entity by name, and looks the chain up by it.
  object EntityName extends MappedString(this, 255) {
    override def dbColumnName = "entity_name"
  }
  object LastSequence extends MappedLong(this) {
    override def dbColumnName = "last_sequence"
  }
  object LastHash extends MappedString(this, 64) {
    override def dbColumnName = "last_hash"
  }
  object StartedByUserId extends MappedString(this, 255) {
    override def dbColumnName = "started_by_user_id"
  }
  object StartedByOnBehalfOfUserId extends MappedString(this, 255) {
    override def dbColumnName = "started_by_on_behalf_of_user_id"
  }
  object StartedAt extends MappedDateTime(this) {
    override def dbColumnName = "started_at"
  }

  def chainId: String = ChainId.get
  def bankId: String = BankId.get
  def dynamicEntityId: String = DynamicEntityId.get
  def entityName: String = EntityName.get
  def lastSequence: Long = LastSequence.get
  def lastHash: String = Option(LastHash.get).getOrElse("")
  def startedByUserId: String = Option(StartedByUserId.get).getOrElse("")
  def startedByOnBehalfOfUserId: String = Option(StartedByOnBehalfOfUserId.get).getOrElse("")
  def startedAt: Date = StartedAt.get
}

object DynamicEntityAuditChain extends DynamicEntityAuditChain with LongKeyedMetaMapper[DynamicEntityAuditChain] {
  override def dbTableName = "dynamic_entity_audit_chain"
  override def dbIndexes =
    UniqueIndex(ChainId) ::
      UniqueIndex(DynamicEntityId) ::
      UniqueIndex(BankId, EntityName) ::
      super.dbIndexes
}

/**
 * One entry of a chain: one change to one record, or the chain's first entry. Entries are only ever added, never changed or deleted.
 */
class DynamicEntityAuditEntry extends LongKeyedMapper[DynamicEntityAuditEntry] with IdPK {
  def getSingleton = DynamicEntityAuditEntry

  object ChainId extends MappedString(this, 36) {
    override def dbColumnName = "chain_id"
  }
  object Sequence extends MappedLong(this) {
    override def dbColumnName = "sequence"
  }
  object Operation extends MappedString(this, 32) {
    override def dbColumnName = "operation"
  }
  // Empty for the STARTED entry.
  object RecordId extends MappedString(this, 255) {
    override def dbColumnName = "record_id"
  }
  // The user a personal record belongs to; empty for other records.
  object OwnerUserId extends MappedString(this, 255) {
    override def dbColumnName = "owner_user_id"
  }
  // The record's JSON exactly as it was stored; NULL for STARTED and for deletions.
  object DataJson extends MappedText(this) {
    override def dbColumnName = "data_json"
  }
  object DataHash extends MappedString(this, 64) {
    override def dbColumnName = "data_hash"
  }
  object UserId extends MappedString(this, 255) {
    override def dbColumnName = "user_id"
  }
  object OnBehalfOfUserId extends MappedString(this, 255) {
    override def dbColumnName = "on_behalf_of_user_id"
  }
  // Milliseconds since 1970 in UTC, kept as a number so that the hash can be worked out again exactly.
  object RecordedAtMillis extends MappedLong(this) {
    override def dbColumnName = "recorded_at_millis"
  }
  object PreviousHash extends MappedString(this, 64) {
    override def dbColumnName = "previous_hash"
  }
  object Hash extends MappedString(this, 64) {
    override def dbColumnName = "hash"
  }
}

object DynamicEntityAuditEntry extends DynamicEntityAuditEntry with LongKeyedMetaMapper[DynamicEntityAuditEntry] {
  override def dbTableName = "dynamic_entity_audit_entry"
  override def dbIndexes =
    UniqueIndex(ChainId, Sequence) ::
      Index(ChainId, RecordId) ::
      super.dbIndexes
}
