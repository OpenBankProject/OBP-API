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

import code.api.Constant.ApiPathZero
import code.api.dynamic.entity.helper.DynamicEntitySpace
import code.api.util.APIUtil.{EmptyBody, Http4sRoute, ResourceDoc}
import code.api.util.ApiRole._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.http4s.Http4sRequestAttributes.{EndpointHelpers, RequestOps}
import code.api.util.{CallContext, CustomJsonFormats, Glossary}
import code.dynamicEntity.{DynamicEntityProvider, DynamicEntityT}
import code.dynamicentityauditchain.{DynamicEntityAuditChain, DynamicEntityAuditChains}
import code.util.Helper
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.util.ApiVersion
import org.http4s._
import org.http4s.dsl.io._
import org.json4s.Formats

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

/**
 * This object holds the v7.0.0 endpoints of Dynamic Entity audit chains: tamper-evident records of every change to the records of one Dynamic Entity (see [[code.dynamicentityauditchain.DynamicEntityAuditChains]] and the Glossary item "Dynamic Entity Audit Chains").
 *
 * As for the v7.0.0 Dynamic Entity definitions, BANK_ID is a bank's id or SYS for the system space. The ResourceDocs declare allowSystemSpace(), so the middleware lets SYS through and checks each Role at the BANK_ID in the URL.
 *
 * There is deliberately no endpoint to stop or delete a chain.
 *
 * Declared in its own object to keep Http4s700's initialiser under the JVM's 64KB method limit.
 */
object Http4s700DynamicEntityAuditChains {

  implicit val formats: Formats = CustomJsonFormats.formats

  private val implementedInApiVersion = ApiVersion.v7_0_0
  private val prefixPath = Root / ApiPathZero.toString / implementedInApiVersion.toString

  val resourceDocs = ArrayBuffer[ResourceDoc]()

  private val DefaultLimit = 100
  private val MaxLimit = 1000

  /** The Dynamic Entity DYNAMIC_ENTITY_ID of the space BANK_ID, or a 404. */
  private def entityFound(bankIdInUrl: String, dynamicEntityId: String, cc: CallContext): Future[DynamicEntityT] =
    Future(DynamicEntityProvider.connectorMethodProvider.vend.getById(DynamicEntitySpace.bankIdOrNoneForSystem(bankIdInUrl), dynamicEntityId))
      .map(box => code.api.util.APIUtil.unboxFullOrFail(box, Some(cc),
        s"$DynamicEntityNotFoundByDynamicEntityId Current DYNAMIC_ENTITY_ID is $dynamicEntityId.", 404))

  /** The chain of that Dynamic Entity, or a 404 if it has none. */
  private def chainFound(dynamicEntityId: String, cc: CallContext): Future[DynamicEntityAuditChain] =
    Future(DynamicEntityAuditChains.chainOfEntity(dynamicEntityId))
      .map(box => code.api.util.APIUtil.unboxFullOrFail(box, Some(cc), DynamicEntityAuditChainNotStarted, 404))

  private val auditChainsDescription =
    s"""An audit chain is a tamper-evident record of every change to the records of one Dynamic Entity. Once it is started, each record that is created, updated or deleted adds an entry to the chain. The entry holds the record as it was saved, who saved it, when, and the hash of the entry before it. Changing or removing an entry afterwards, or changing a record without adding an entry, breaks the chain at that point, and the check finds it.
       |
       |An audit chain cannot be stopped. There is no endpoint to stop or delete one, and a Dynamic Entity with an audit chain cannot be deleted or renamed. Someone with direct access to the database can still change anything, so the chain does not prevent tampering: it makes it visible. Each chain is bound to its entity, and registered in the root audit chain, which ties every chain together. To catch even a consistent rewrite, seal the root chain regularly and keep its `last_hash` outside OBP.
       |
       |Entries are added in the same database transaction as the change, so a change that rolls back adds nothing. Writes to one entity are put in order by the database, one at a time; writes to different entities do not wait for each other. Entities without a chain are not affected.
       |
       |BANK_ID is the space: a bank's id, or `SYS` for the system space. The Role is checked at that BANK_ID.
       |
       |For more information, including how to check an entry's hash without OBP, see ${Glossary.getGlossaryItemLink("Dynamic Entity Audit Chains")}""".stripMargin

  // Route: POST /obp/v7.0.0/management/banks/BANK_ID/dynamic-entities/DYNAMIC_ENTITY_ID/audit-chain (201)
  lazy val startDynamicEntityAuditChain: Http4sRoute = Http4sRoute {
    case req @ POST -> `prefixPath` / "management" / "banks" / bankIdInUrl / "dynamic-entities" / dynamicEntityId / "audit-chain" =>
      EndpointHelpers.executeFutureCreated(req) {
        implicit val cc: CallContext = req.callContext
        for {
          entity <- entityFound(bankIdInUrl, dynamicEntityId, cc)
          _ <- Helper.booleanToFuture(DynamicEntityAuditChainAlreadyStarted, 409, Some(cc)) {
            !DynamicEntityAuditChains.hasChain(dynamicEntityId)
          }
          chain <- Future(DynamicEntityAuditChains.start(entity.bankId, dynamicEntityId, entity.entityName, cc.user.map(_.userId).toOption.filter(_.nonEmpty)))
            .map(box => code.api.util.APIUtil.unboxFullOrFail(box, Some(cc), UnknownError, 400))
        } yield JSONFactory700DynamicEntityAuditChains.createChainJson(chain)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(startDynamicEntityAuditChain),
    "POST",
    "/management/banks/BANK_ID/dynamic-entities/DYNAMIC_ENTITY_ID/audit-chain",
    "Start Dynamic Entity Audit Chain",
    s"""Start the audit chain of a Dynamic Entity. From now on, every change to its records adds an entry to the chain. This cannot be undone.
       |
       |The first entry is `STARTED`, and its data names the entity, which binds the chain to it. Then each record the entity already holds gets an `EXISTING` entry, oldest first, so the chain starts from the records as they are now. Last, the chain is registered in the root audit chain, which is created if this is the first chain.
       |
       |No request body is needed. The response is the chain.
       |
       |$auditChainsDescription""".stripMargin,
    EmptyBody,
    JSONFactory700DynamicEntityAuditChains.dynamicEntityAuditChainJsonV700Example,
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, DynamicEntityNotFoundByDynamicEntityId,
      DynamicEntityAuditChainAlreadyStarted, UnknownError),
    apiTagManageDynamicEntity :: apiTagApi :: Nil,
    Some(canCreateDynamicEntityAuditChain :: Nil),
    http4sPartialFunction = Some(startDynamicEntityAuditChain)
  ).allowSystemSpace()

  // Route: GET /obp/v7.0.0/management/banks/BANK_ID/dynamic-entities/DYNAMIC_ENTITY_ID/audit-chain
  lazy val getDynamicEntityAuditChain: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "management" / "banks" / bankIdInUrl / "dynamic-entities" / dynamicEntityId / "audit-chain" =>
      EndpointHelpers.withUser(req) { (_, cc) =>
        for {
          _ <- entityFound(bankIdInUrl, dynamicEntityId, cc)
          chain <- chainFound(dynamicEntityId, cc)
        } yield JSONFactory700DynamicEntityAuditChains.createChainJson(chain)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getDynamicEntityAuditChain),
    "GET",
    "/management/banks/BANK_ID/dynamic-entities/DYNAMIC_ENTITY_ID/audit-chain",
    "Get Dynamic Entity Audit Chain",
    s"""The audit chain of a Dynamic Entity: who started it and when, and the sequence number and hash of its last entry.
       |
       |`last_hash` can be kept outside OBP and compared later: if it no longer matches the entry with the same `last_sequence`, the chain has been rewritten. Sealing the root audit chain records it for you, and the root chain's `last_hash` covers every chain at once.
       |
       |$auditChainsDescription""".stripMargin,
    EmptyBody,
    JSONFactory700DynamicEntityAuditChains.dynamicEntityAuditChainJsonV700Example,
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, DynamicEntityNotFoundByDynamicEntityId,
      DynamicEntityAuditChainNotStarted, UnknownError),
    apiTagManageDynamicEntity :: apiTagApi :: Nil,
    Some(canGetDynamicEntityAuditChain :: Nil),
    http4sPartialFunction = Some(getDynamicEntityAuditChain)
  ).allowSystemSpace()

  // Route: GET /obp/v7.0.0/management/banks/BANK_ID/dynamic-entities/DYNAMIC_ENTITY_ID/audit-chain/entries
  lazy val getDynamicEntityAuditChainEntries: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "management" / "banks" / bankIdInUrl / "dynamic-entities" / dynamicEntityId / "audit-chain" / "entries" =>
      EndpointHelpers.withUser(req) { (_, cc) =>
        val query = req.uri.query.params
        val fromSequence = query.get("from_sequence").map(value => scala.util.Try(value.trim.toLong).toOption)
        val limit = query.get("limit").map(value => scala.util.Try(value.trim.toInt).toOption)
        val recordId = query.get("record_id").map(_.trim).filter(_.nonEmpty)
        for {
          _ <- Helper.booleanToFuture(s"$InvalidUrlParameters from_sequence must be a whole number, 0 or more.", 400, Some(cc)) {
            fromSequence.forall(_.exists(_ >= 0))
          }
          _ <- Helper.booleanToFuture(s"$InvalidUrlParameters limit must be a whole number from 1 to $MaxLimit.", 400, Some(cc)) {
            limit.forall(_.exists(value => value >= 1 && value <= MaxLimit))
          }
          _ <- entityFound(bankIdInUrl, dynamicEntityId, cc)
          chain <- chainFound(dynamicEntityId, cc)
          entries <- Future(recordId match {
            case Some(theRecordId) => DynamicEntityAuditChains.entriesOfRecord(chain.chainId, theRecordId)
            case None => DynamicEntityAuditChains.entries(chain.chainId, fromSequence.flatten.getOrElse(0L), limit.flatten.getOrElse(DefaultLimit))
          })
        } yield DynamicEntityAuditEntriesJsonV700(entries.map(JSONFactory700DynamicEntityAuditChains.createEntryJson))
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getDynamicEntityAuditChainEntries),
    "GET",
    "/management/banks/BANK_ID/dynamic-entities/DYNAMIC_ENTITY_ID/audit-chain/entries",
    "Get Dynamic Entity Audit Chain Entries",
    s"""The entries of an audit chain, in order.
       |
       |Query parameters: `from_sequence` (default 0) and `limit` (default $DefaultLimit, at most $MaxLimit) page through the whole chain. To read the next page, ask again with `from_sequence` one more than the last `sequence` you got. `record_id` gives instead every entry of one record, oldest first, which is that record's history.
       |
       |`operation` is one of these:
       |
       |* `STARTED`: the first entry, whose data names the entity
       |* `EXISTING`: a record the entity already held when the chain was started
       |* `CREATED`, `UPDATED` or `DELETED`: a change made through OBP-API
       |* `DELETED_BY_RETENTION`: a record deleted by a data retention run
       |
       |`data` is the record as it was saved, and `data_json` is the same record as the exact text stored, which `data_hash` is the hash of. Both are left out for `STARTED` and for deletions. `user_id` is the user who made the call, which is an agent's own id when an agent made it. `on_behalf_of_user_id` is the user the call was made for. They are the same when nobody was delegating, and empty for `DELETED_BY_RETENTION`.
       |
       |$auditChainsDescription""".stripMargin,
    EmptyBody,
    JSONFactory700DynamicEntityAuditChains.dynamicEntityAuditEntriesJsonV700Example,
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidUrlParameters,
      DynamicEntityNotFoundByDynamicEntityId, DynamicEntityAuditChainNotStarted, UnknownError),
    apiTagManageDynamicEntity :: apiTagApi :: Nil,
    Some(canGetDynamicEntityAuditChain :: Nil),
    http4sPartialFunction = Some(getDynamicEntityAuditChainEntries)
  ).allowSystemSpace()

  // Route: GET /obp/v7.0.0/management/banks/BANK_ID/dynamic-entities/DYNAMIC_ENTITY_ID/audit-chain/check
  lazy val checkDynamicEntityAuditChain: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "management" / "banks" / bankIdInUrl / "dynamic-entities" / dynamicEntityId / "audit-chain" / "check" =>
      EndpointHelpers.withUser(req) { (_, cc) =>
        for {
          _ <- entityFound(bankIdInUrl, dynamicEntityId, cc)
          chain <- chainFound(dynamicEntityId, cc)
          result <- Future(DynamicEntityAuditChains.check(chain))
        } yield JSONFactory700DynamicEntityAuditChains.createCheckJson(result)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(checkDynamicEntityAuditChain),
    "GET",
    "/management/banks/BANK_ID/dynamic-entities/DYNAMIC_ENTITY_ID/audit-chain/check",
    "Check Dynamic Entity Audit Chain",
    s"""Check an audit chain from its first entry, and the entity's current records against it.
       |
       |OBP checks that each entry follows the one before it, points back to its hash, and has the hash of its own contents. It then checks that the chain's last sequence number and hash are those of its last entry. The first entry must name this entity, and the entity must still exist under that name. Finally, each current record must match its latest entry, and each record whose latest entry is not a deletion must still exist.
       |
       |`is_intact` is true when nothing was found. Otherwise `problems` lists up to ${DynamicEntityAuditChains.MaxProblemsListed} problems, each with the entry's `sequence`, the `record_id`, or both, and `problem_count` counts them all. A record changed, added or deleted without going through OBP-API shows up here.
       |
       |`last_sequence` and `last_hash` are those of the last entry checked. A chain rewritten consistently from the first change onwards passes this check. Check the root audit chain to catch that, since it recorded what this chain looked like.
       |
       |The check reads the whole chain and every record of the entity, so it takes longer the more entries there are.
       |
       |$auditChainsDescription""".stripMargin,
    EmptyBody,
    JSONFactory700DynamicEntityAuditChains.dynamicEntityAuditChainCheckJsonV700Example,
    List($BankNotFound, $AuthenticatedUserIsRequired, UserHasMissingRoles, DynamicEntityNotFoundByDynamicEntityId,
      DynamicEntityAuditChainNotStarted, UnknownError),
    apiTagManageDynamicEntity :: apiTagApi :: Nil,
    Some(canGetDynamicEntityAuditChain :: Nil),
    http4sPartialFunction = Some(checkDynamicEntityAuditChain)
  ).allowSystemSpace()

  /** The root chain, or a 404 if no chain has been started yet. */
  private def rootFound(cc: CallContext): Future[DynamicEntityAuditChain] =
    Future(DynamicEntityAuditChains.rootChain)
      .map(box => code.api.util.APIUtil.unboxFullOrFail(box, Some(cc), DynamicEntityAuditRootChainNotStarted, 404))

  private val rootChainDescription =
    s"""The root audit chain ties together the audit chains of every Dynamic Entity on this OBP instance. There is one, created when the first entity's chain is started.
       |
       |It gets a `CHAIN_STARTED` entry whenever an entity's chain is started. That entry records the chain's entity, the hash of its first entry and its last entry at that moment. Sealing adds a `CHAIN_HEAD` entry for each chain that has changed since it was last recorded. The root chain's check then finds a whole chain deleted, moved to another entity, started again, rewound or rewritten, or added, without going through OBP-API.
       |
       |After a seal, the root chain's `last_hash` covers every entity's chain as it was at that moment. Keeping that one hash outside OBP, for example with a regulator or in a public ledger, covers them all. Seal regularly, and keep each `last_hash` the seal returns.
       |
       |The Roles are held at no bank, because the root chain covers every space.
       |
       |For more information see ${Glossary.getGlossaryItemLink("Dynamic Entity Audit Chains")}""".stripMargin

  // Route: GET /obp/v7.0.0/management/dynamic-entity-audit-chains/root
  lazy val getDynamicEntityAuditRootChain: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "management" / "dynamic-entity-audit-chains" / "root" =>
      EndpointHelpers.withUser(req) { (_, cc) =>
        rootFound(cc).map(JSONFactory700DynamicEntityAuditChains.createChainJson)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getDynamicEntityAuditRootChain),
    "GET",
    "/management/dynamic-entity-audit-chains/root",
    "Get Dynamic Entity Audit Root Chain",
    s"""The root audit chain: when it was created, and the sequence number and hash of its last entry. Its `chain_id` and `dynamic_entity_id` are `root`, and its `entity_name` is empty.
       |
       |$rootChainDescription""".stripMargin,
    EmptyBody,
    JSONFactory700DynamicEntityAuditChains.dynamicEntityAuditRootChainJsonV700Example,
    List($AuthenticatedUserIsRequired, UserHasMissingRoles, DynamicEntityAuditRootChainNotStarted, UnknownError),
    apiTagManageDynamicEntity :: apiTagApi :: Nil,
    Some(canGetDynamicEntityAuditRootChain :: Nil),
    http4sPartialFunction = Some(getDynamicEntityAuditRootChain)
  )

  // Route: GET /obp/v7.0.0/management/dynamic-entity-audit-chains/root/entries
  lazy val getDynamicEntityAuditRootChainEntries: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "management" / "dynamic-entity-audit-chains" / "root" / "entries" =>
      EndpointHelpers.withUser(req) { (_, cc) =>
        val query = req.uri.query.params
        val fromSequence = query.get("from_sequence").map(value => scala.util.Try(value.trim.toLong).toOption)
        val limit = query.get("limit").map(value => scala.util.Try(value.trim.toInt).toOption)
        val chainId = query.get("chain_id").map(_.trim).filter(_.nonEmpty)
        for {
          _ <- Helper.booleanToFuture(s"$InvalidUrlParameters from_sequence must be a whole number, 0 or more.", 400, Some(cc)) {
            fromSequence.forall(_.exists(_ >= 0))
          }
          _ <- Helper.booleanToFuture(s"$InvalidUrlParameters limit must be a whole number from 1 to $MaxLimit.", 400, Some(cc)) {
            limit.forall(_.exists(value => value >= 1 && value <= MaxLimit))
          }
          root <- rootFound(cc)
          entries <- Future(chainId match {
            case Some(theChainId) => DynamicEntityAuditChains.entriesOfRecord(root.chainId, theChainId)
            case None => DynamicEntityAuditChains.entries(root.chainId, fromSequence.flatten.getOrElse(0L), limit.flatten.getOrElse(DefaultLimit))
          })
        } yield DynamicEntityAuditEntriesJsonV700(entries.map(JSONFactory700DynamicEntityAuditChains.createEntryJson))
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(getDynamicEntityAuditRootChainEntries),
    "GET",
    "/management/dynamic-entity-audit-chains/root/entries",
    "Get Dynamic Entity Audit Root Chain Entries",
    s"""The entries of the root audit chain, in order. Their hashes are made in the same way as an entity chain's.
       |
       |Query parameters: `from_sequence` (default 0) and `limit` (default $DefaultLimit, at most $MaxLimit) page through the chain. `chain_id` gives instead every entry about one entity's chain, oldest first.
       |
       |`operation` is `STARTED` for the first entry, `CHAIN_STARTED` when an entity's chain was started, or `CHAIN_HEAD` when a seal recorded a chain's last entry. `record_id` is the entity chain's `chain_id`. `data` holds what was recorded about it: its `bank_id`, `dynamic_entity_id`, `entity_name` and `started_hash` for `CHAIN_STARTED`, and its `last_sequence` and `last_hash` for both.
       |
       |$rootChainDescription""".stripMargin,
    EmptyBody,
    JSONFactory700DynamicEntityAuditChains.dynamicEntityAuditRootChainEntriesJsonV700Example,
    List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidUrlParameters, DynamicEntityAuditRootChainNotStarted, UnknownError),
    apiTagManageDynamicEntity :: apiTagApi :: Nil,
    Some(canGetDynamicEntityAuditRootChain :: Nil),
    http4sPartialFunction = Some(getDynamicEntityAuditRootChainEntries)
  )

  // Route: POST /obp/v7.0.0/management/dynamic-entity-audit-chains/root/seal (201)
  lazy val sealDynamicEntityAuditRootChain: Http4sRoute = Http4sRoute {
    case req @ POST -> `prefixPath` / "management" / "dynamic-entity-audit-chains" / "root" / "seal" =>
      EndpointHelpers.executeFutureCreated(req) {
        implicit val cc: CallContext = req.callContext
        Future(DynamicEntityAuditChains.seal(cc.user.map(_.userId).toOption.filter(_.nonEmpty)))
          .map(box => code.api.util.APIUtil.unboxFullOrFail(box, Some(cc), UnknownError, 400))
          .map { case (root, sealedCount) =>
            DynamicEntityAuditRootChainSealJsonV700(sealedCount, JSONFactory700DynamicEntityAuditChains.createChainJson(root))
          }
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(sealDynamicEntityAuditRootChain),
    "POST",
    "/management/dynamic-entity-audit-chains/root/seal",
    "Seal Dynamic Entity Audit Root Chain",
    s"""Record in the root audit chain the last entry of every entity's chain that has changed since it was last recorded. The last entry is read from the entries themselves, not from what the chain says about itself.
       |
       |The response says how many chains were sealed, and gives the root chain afterwards. Its `last_hash` now covers every entity's chain as it is. Keep it outside OBP to compare later. A seal when nothing has changed adds nothing.
       |
       |No request body is needed. If no chain has been started yet, this creates the root chain.
       |
       |$rootChainDescription""".stripMargin,
    EmptyBody,
    JSONFactory700DynamicEntityAuditChains.dynamicEntityAuditRootChainSealJsonV700Example,
    List($AuthenticatedUserIsRequired, UserHasMissingRoles, UnknownError),
    apiTagManageDynamicEntity :: apiTagApi :: Nil,
    Some(canSealDynamicEntityAuditRootChain :: Nil),
    http4sPartialFunction = Some(sealDynamicEntityAuditRootChain)
  )

  // Route: GET /obp/v7.0.0/management/dynamic-entity-audit-chains/root/check
  lazy val checkDynamicEntityAuditRootChain: Http4sRoute = Http4sRoute {
    case req @ GET -> `prefixPath` / "management" / "dynamic-entity-audit-chains" / "root" / "check" =>
      EndpointHelpers.withUser(req) { (_, cc) =>
        val includeChains = req.uri.query.params.get("include_chains").map(_.trim.toLowerCase)
        for {
          _ <- Helper.booleanToFuture(s"$InvalidUrlParameters include_chains must be true or false.", 400, Some(cc)) {
            includeChains.forall(value => value == "true" || value == "false")
          }
          root <- rootFound(cc)
          result <- Future(DynamicEntityAuditChains.checkRoot(root, includeChains.contains("true")))
        } yield JSONFactory700DynamicEntityAuditChains.createRootCheckJson(result)
      }
  }

  resourceDocs += ResourceDoc(
    implementedInApiVersion,
    nameOf(checkDynamicEntityAuditRootChain),
    "GET",
    "/management/dynamic-entity-audit-chains/root/check",
    "Check Dynamic Entity Audit Root Chain",
    s"""Check the root audit chain, and every entity's chain against what it recorded.
       |
       |OBP checks the root chain's own entries the same way as an entity chain's. Then each chain registered in it must still exist, name the same entity and have the same first entry. Its entry at the last recorded sequence number must still have the recorded hash. Each chain that exists must be registered.
       |
       |With `include_chains=true`, each entity's chain is also checked in full, records included, as by Check Dynamic Entity Audit Chain. Its problems are listed with its `chain_id`. This is the one call that checks everything, and it takes longer the more entries and records there are.
       |
       |`is_intact` is true when nothing was found. `chains_checked` counts the entity chains. Compare `last_hash` with the copies you kept: a match means nothing recorded up to then has changed.
       |
       |$rootChainDescription""".stripMargin,
    EmptyBody,
    JSONFactory700DynamicEntityAuditChains.dynamicEntityAuditRootChainCheckJsonV700Example,
    List($AuthenticatedUserIsRequired, UserHasMissingRoles, InvalidUrlParameters, DynamicEntityAuditRootChainNotStarted, UnknownError),
    apiTagManageDynamicEntity :: apiTagApi :: Nil,
    Some(canGetDynamicEntityAuditRootChain :: Nil),
    http4sPartialFunction = Some(checkDynamicEntityAuditRootChain)
  )
}
