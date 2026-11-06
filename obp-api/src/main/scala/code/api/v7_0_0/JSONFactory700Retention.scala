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

import java.util.{Base64, Date}

import code.retention._
import com.openbankproject.commons.util.JsonAliases
import org.json4s.JsonAST.JObject
import org.json4s.JsonDSL._
import org.json4s.{JNothing, JValue}

import scala.util.Try

/*
 * The JSON of the v7.0.0 data retention endpoints. Package-level case classes, for the reason given in
 * JSONFactory700Operations.
 */

/** A condition on a resource: its field `field` has the value `value`, compared as text. */
case class RetentionConditionJsonV700(field: String, value: String)

/**
 * Create a retention policy. Only `resource_type`, `retain_days` and `action` are required.
 * `entity_name` is required for, and only allowed with, `dynamic_entity_record`. `archive_days` is
 * required for, and only allowed with, the `archive` action.
 */
case class PostRetentionPolicyJsonV700(
  name: Option[String] = None,
  description: Option[String] = None,
  resource_type: String,
  entity_name: Option[String] = None,
  date_basis: Option[String] = None,
  condition: Option[RetentionConditionJsonV700] = None,
  retain_days: Int,
  archive_days: Option[Int] = None,
  action: String,
  cascade: Option[Boolean] = None,
  requires_approval: Option[Boolean] = None,
  enabled: Option[Boolean] = None
)

/**
 * Change a retention policy. Every field is optional and a field left out keeps its value. The
 * resource type and entity name cannot change. A condition whose field is empty removes the
 * condition. `archive_days` is cleared when the action becomes anything but `archive`.
 */
case class PutRetentionPolicyJsonV700(
  name: Option[String] = None,
  description: Option[String] = None,
  date_basis: Option[String] = None,
  condition: Option[RetentionConditionJsonV700] = None,
  retain_days: Option[Int] = None,
  archive_days: Option[Int] = None,
  action: Option[String] = None,
  cascade: Option[Boolean] = None,
  requires_approval: Option[Boolean] = None,
  enabled: Option[Boolean] = None
)

case class RetentionPolicyJsonV700(
  retention_policy_id: String,
  bank_id: String,
  name: String,
  description: String,
  resource_type: String,
  entity_name: Option[String],
  date_basis: String,
  condition: Option[RetentionConditionJsonV700],
  retain_days: Int,
  archive_days: Option[Int],
  action: String,
  cascade: Boolean,
  requires_approval: Boolean,
  enabled: Boolean,
  created_by_user_id: String,
  created_at: Date,
  updated_at: Date
)

case class RetentionPoliciesJsonV700(retention_policies: List[RetentionPolicyJsonV700])

/** What a run of the policy would dispose of now, and whether this instance would let it. */
case class RetentionPolicyPreviewJsonV700(
  retention_policy_id: String,
  bank_id: String,
  resource_type: String,
  entity_name: Option[String],
  action: String,
  /** A resource whose trigger date is on or before this date is due. */
  cutoff_date: Date,
  matched_count: Int,
  held_count: Int,
  /** matched_count less held_count, though one run disposes of at most max_items_per_run of them. */
  due_count: Int,
  oldest_trigger_date: Option[Date],
  sample_resource_ids: List[String],
  /** Resources this policy archived whose archive period has ended, so the next run deletes them. */
  archive_entries_due_count: Int,
  count_is_capped: Boolean,
  max_items_per_run: Int,
  /** retention.minimum_retain_days: a run never disposes of a resource sooner than this after its trigger date. */
  minimum_retain_days: Int,
  archive_enabled: Boolean,
  delete_enabled: Boolean,
  /** Whether an execution of this policy would be allowed by the props now. */
  can_execute: Boolean,
  requires_approval: Boolean
)

/** Run a policy now. `mode` is execute (the default) or preview. */
case class PostRetentionRunJsonV700(mode: Option[String] = None)

case class RetentionRunJsonV700(
  retention_run_id: String,
  /** Left out for a run of the scheduler that deleted archived resources across policies. */
  retention_policy_id: Option[String],
  bank_id: String,
  mode: String,
  status: String,
  /** Left out when the scheduler requested the run. */
  requested_by_user_id: Option[String],
  approved_by_user_id: Option[String],
  approval_decision: Option[String],
  approval_comment: Option[String],
  matched_count: Int,
  archived_count: Int,
  anonymised_count: Int,
  deleted_count: Int,
  held_count: Int,
  failed_count: Int,
  requested_at: Date,
  started_at: Option[Date],
  ended_at: Option[Date],
  message: String
)

case class RetentionRunsJsonV700(retention_runs: List[RetentionRunJsonV700])

/** Approve or reject a pending run: `decision` is approve or reject. */
case class PutRetentionRunApprovalJsonV700(decision: String, comment: Option[String] = None)

/** One row of the disposal register. Identifiers only, never content. */
case class RetentionRunItemJsonV700(
  retention_run_item_id: String,
  retention_run_id: String,
  retention_policy_id: Option[String],
  bank_id: String,
  resource_type: String,
  resource_id: String,
  entity_name: Option[String],
  owner_user_id: Option[String],
  action: String,
  parent_resource_type: Option[String],
  parent_resource_id: Option[String],
  retention_archive_id: Option[String],
  disposed_at: Date
)

case class RetentionRunItemsJsonV700(retention_run_items: List[RetentionRunItemJsonV700])

/**
 * Place a legal hold. Give `resource_id` to hold one resource (with `entity_name` too for a dynamic
 * entity record, optionally), or, for `dynamic_entity_record`, `entity_name` alone to hold every
 * record of that dynamic entity.
 */
case class PostRetentionHoldJsonV700(
  resource_type: String,
  resource_id: Option[String] = None,
  entity_name: Option[String] = None,
  reason: String
)

case class RetentionHoldJsonV700(
  retention_hold_id: String,
  bank_id: String,
  resource_type: String,
  resource_id: Option[String],
  entity_name: Option[String],
  reason: String,
  placed_by_user_id: String,
  placed_at: Date,
  released_by_user_id: Option[String],
  released_at: Option[Date],
  is_active: Boolean
)

case class RetentionHoldsJsonV700(retention_holds: List[RetentionHoldJsonV700])

/**
 * One archived resource. `data` (the JSON copy) and `content_base64` (a file's bytes) are given only
 * by Get Retention Archive Entry, the latter only with `include_content=true`.
 */
case class RetentionArchiveEntryJsonV700(
  retention_archive_id: String,
  retention_policy_id: Option[String],
  retention_run_id: String,
  bank_id: String,
  resource_type: String,
  resource_id: String,
  entity_name: Option[String],
  owner_user_id: Option[String],
  parent_resource_type: Option[String],
  parent_resource_id: Option[String],
  archived_at: Date,
  delete_after: Option[Date],
  has_content: Boolean,
  content_size_in_bytes: Option[Long],
  data: Option[JValue] = None,
  content_base64: Option[String] = None
)

case class RetentionArchiveEntriesJsonV700(retention_archive_entries: List[RetentionArchiveEntryJsonV700])

object JSONFactory700Retention {

  def createPolicyJson(p: RetentionPolicy): RetentionPolicyJsonV700 = RetentionPolicyJsonV700(
    retention_policy_id = p.retentionPolicyId,
    bank_id = p.bankId,
    name = p.name,
    description = p.description,
    resource_type = p.resourceType,
    entity_name = p.entityName,
    date_basis = p.dateBasis,
    condition = p.condition.map(c => RetentionConditionJsonV700(c.field, c.value)),
    retain_days = p.retainDays,
    archive_days = p.archiveDays,
    action = p.action,
    cascade = p.cascade,
    requires_approval = p.requiresApproval,
    enabled = p.enabled,
    created_by_user_id = p.createdByUserId,
    created_at = p.createdAt,
    updated_at = p.updatedAt
  )

  def createPreviewJson(p: RetentionPolicy, r: RetentionEngine.PreviewResult): RetentionPolicyPreviewJsonV700 =
    RetentionPolicyPreviewJsonV700(
      retention_policy_id = p.retentionPolicyId,
      bank_id = p.bankId,
      resource_type = p.resourceType,
      entity_name = p.entityName,
      action = p.action,
      cutoff_date = r.cutoff,
      matched_count = r.matched,
      held_count = r.held,
      due_count = r.matched - r.held,
      oldest_trigger_date = r.oldestTriggerDate,
      sample_resource_ids = r.sampleResourceIds,
      archive_entries_due_count = r.archiveEntriesDue,
      count_is_capped = r.scanLimitReached,
      max_items_per_run = RetentionProps.maxItemsPerRun,
      minimum_retain_days = RetentionProps.minimumRetainDays,
      archive_enabled = RetentionProps.archiveEnabled,
      delete_enabled = RetentionProps.deleteEnabled,
      can_execute = RetentionEngine.refusal(p).isEmpty,
      requires_approval = p.requiresApproval
    )

  def createRunJson(r: RetentionRun): RetentionRunJsonV700 = RetentionRunJsonV700(
    retention_run_id = r.retentionRunId,
    retention_policy_id = r.retentionPolicyId,
    bank_id = r.bankId,
    mode = r.mode,
    status = r.status,
    requested_by_user_id = Option(r.requestedByUserId).filter(_.nonEmpty),
    approved_by_user_id = r.approvedByUserId,
    approval_decision = r.approvalDecision,
    approval_comment = r.approvalComment,
    matched_count = r.matchedCount,
    archived_count = r.archivedCount,
    anonymised_count = r.anonymisedCount,
    deleted_count = r.deletedCount,
    held_count = r.heldCount,
    failed_count = r.failedCount,
    requested_at = r.requestedAt,
    started_at = r.startedAt,
    ended_at = r.endedAt,
    message = r.message
  )

  def createRunItemJson(i: RetentionRunItem): RetentionRunItemJsonV700 = RetentionRunItemJsonV700(
    retention_run_item_id = i.retentionRunItemId,
    retention_run_id = i.retentionRunId,
    retention_policy_id = i.retentionPolicyId,
    bank_id = i.bankId,
    resource_type = i.resourceType,
    resource_id = i.resourceId,
    entity_name = i.entityName,
    owner_user_id = i.ownerUserId,
    action = i.action,
    parent_resource_type = i.parentResourceType,
    parent_resource_id = i.parentResourceId,
    retention_archive_id = i.retentionArchiveId,
    disposed_at = i.disposedAt
  )

  def createHoldJson(h: RetentionHold): RetentionHoldJsonV700 = RetentionHoldJsonV700(
    retention_hold_id = h.retentionHoldId,
    bank_id = h.bankId,
    resource_type = h.resourceType,
    resource_id = h.resourceId,
    entity_name = h.entityName,
    reason = h.reason,
    placed_by_user_id = h.placedByUserId,
    placed_at = h.placedAt,
    released_by_user_id = h.releasedByUserId,
    released_at = h.releasedAt,
    is_active = h.isActive
  )

  def createArchiveEntryJson(a: RetentionArchive, withData: Boolean, content: Option[Array[Byte]]): RetentionArchiveEntryJsonV700 =
    RetentionArchiveEntryJsonV700(
      retention_archive_id = a.retentionArchiveId,
      retention_policy_id = a.retentionPolicyId,
      retention_run_id = a.retentionRunId,
      bank_id = a.bankId,
      resource_type = a.resourceType,
      resource_id = a.resourceId,
      entity_name = a.entityName,
      owner_user_id = a.ownerUserId,
      parent_resource_type = a.parentResourceType,
      parent_resource_id = a.parentResourceId,
      archived_at = a.archivedAt,
      delete_after = a.deleteAfter,
      has_content = a.hasContent,
      content_size_in_bytes = if (a.hasContent) Some(a.contentSizeInBytes) else None,
      data = if (withData) Try(JsonAliases.parse(a.jsonCopy)).toOption.filterNot(_ == JNothing) else None,
      content_base64 = content.map(bytes => Base64.getEncoder.encodeToString(bytes))
    )

  // ---- examples for the ResourceDocs. Neutral values only.

  private val exampleDate = new Date(1767225600000L) // 2026-01-01T00:00:00Z
  private val examplePolicyId = "6a1f0e2c-3b8d-4c5e-9f7a-1d2e3f4a5b6c"
  private val exampleRunId = "0b9c8d7e-6f5a-4b3c-2d1e-0f9e8d7c6b5a"
  private val exampleUserId = "9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1"
  private val exampleOtherUserId = "4f3e2d1c-0b9a-4876-9543-210fedcba987"

  val postRetentionPolicyJsonV700Example = PostRetentionPolicyJsonV700(
    name = Some("Close applications after two years"),
    description = Some("Applications are kept for two years after they were last changed, then archived for five more."),
    resource_type = Retention.ResourceDynamicEntityRecord,
    entity_name = Some("application"),
    date_basis = Some(Retention.BasisUpdated),
    condition = Some(RetentionConditionJsonV700("status", "CLOSED")),
    retain_days = 2555,
    archive_days = Some(1825),
    action = Retention.ActionArchive,
    cascade = Some(true),
    requires_approval = Some(true),
    enabled = Some(true)
  )

  val putRetentionPolicyJsonV700Example = PutRetentionPolicyJsonV700(
    retain_days = Some(3650),
    archive_days = Some(1825),
    cascade = Some(false),
    requires_approval = Some(true),
    enabled = Some(true)
  )

  val retentionPolicyJsonV700Example = RetentionPolicyJsonV700(
    retention_policy_id = examplePolicyId,
    bank_id = "SYS",
    name = "Close applications after two years",
    description = "Applications are kept for two years after they were last changed, then archived for five more.",
    resource_type = Retention.ResourceDynamicEntityRecord,
    entity_name = Some("application"),
    date_basis = Retention.BasisUpdated,
    condition = Some(RetentionConditionJsonV700("status", "CLOSED")),
    retain_days = 2555,
    archive_days = Some(1825),
    action = Retention.ActionArchive,
    cascade = true,
    requires_approval = true,
    enabled = true,
    created_by_user_id = exampleUserId,
    created_at = exampleDate,
    updated_at = exampleDate
  )

  val retentionPoliciesJsonV700Example = RetentionPoliciesJsonV700(List(retentionPolicyJsonV700Example))

  val retentionPolicyPreviewJsonV700Example = RetentionPolicyPreviewJsonV700(
    retention_policy_id = examplePolicyId,
    bank_id = "SYS",
    resource_type = Retention.ResourceDynamicEntityRecord,
    entity_name = Some("application"),
    action = Retention.ActionArchive,
    cutoff_date = exampleDate,
    matched_count = 42,
    held_count = 2,
    due_count = 40,
    oldest_trigger_date = Some(new Date(1704067200000L)),
    sample_resource_ids = List("3d2c1b0a-9f8e-4d7c-6b5a-4f3e2d1c0b9a"),
    archive_entries_due_count = 0,
    count_is_capped = false,
    max_items_per_run = RetentionProps.MaxItemsPerRunDefault,
    minimum_retain_days = RetentionProps.MinimumRetainDaysDefault,
    archive_enabled = true,
    delete_enabled = false,
    can_execute = true,
    requires_approval = true
  )

  val postRetentionRunJsonV700Example = PostRetentionRunJsonV700(mode = Some(Retention.ModeExecute))

  val retentionRunJsonV700Example = RetentionRunJsonV700(
    retention_run_id = exampleRunId,
    retention_policy_id = Some(examplePolicyId),
    bank_id = "SYS",
    mode = Retention.ModeExecute,
    status = Retention.StatusDone,
    requested_by_user_id = Some(exampleUserId),
    approved_by_user_id = Some(exampleOtherUserId),
    approval_decision = Some(Retention.DecisionApproved),
    approval_comment = Some("Checked against the preview."),
    matched_count = 42,
    archived_count = 43,
    anonymised_count = 0,
    deleted_count = 0,
    held_count = 2,
    failed_count = 0,
    requested_at = exampleDate,
    started_at = Some(exampleDate),
    ended_at = Some(exampleDate),
    message = "Done."
  )

  val retentionRunsJsonV700Example = RetentionRunsJsonV700(List(retentionRunJsonV700Example))

  val putRetentionRunApprovalJsonV700Example = PutRetentionRunApprovalJsonV700(Retention.DecisionApprove, Some("Checked against the preview."))

  val retentionRunItemsJsonV700Example = RetentionRunItemsJsonV700(List(RetentionRunItemJsonV700(
    retention_run_item_id = "7e6d5c4b-3a29-4180-9f8e-7d6c5b4a3928",
    retention_run_id = exampleRunId,
    retention_policy_id = Some(examplePolicyId),
    bank_id = "SYS",
    resource_type = Retention.ResourceFile,
    resource_id = "1a2b3c4d-5e6f-4a8b-9c0d-1e2f3a4b5c6d",
    entity_name = None,
    owner_user_id = Some(exampleUserId),
    action = Retention.ActionArchive,
    parent_resource_type = Some(Retention.ResourceDynamicEntityRecord),
    parent_resource_id = Some("3d2c1b0a-9f8e-4d7c-6b5a-4f3e2d1c0b9a"),
    retention_archive_id = Some("5c4b3a29-1807-4f6e-8d5c-4b3a29180706"),
    disposed_at = exampleDate
  )))

  val postRetentionHoldJsonV700Example = PostRetentionHoldJsonV700(
    resource_type = Retention.ResourceDynamicEntityRecord,
    resource_id = Some("3d2c1b0a-9f8e-4d7c-6b5a-4f3e2d1c0b9a"),
    entity_name = Some("application"),
    reason = "Requested by the records officer while a complaint is open."
  )

  val retentionHoldJsonV700Example = RetentionHoldJsonV700(
    retention_hold_id = "2b1a0f9e-8d7c-4b6a-a594-837261504f3e",
    bank_id = "SYS",
    resource_type = Retention.ResourceDynamicEntityRecord,
    resource_id = Some("3d2c1b0a-9f8e-4d7c-6b5a-4f3e2d1c0b9a"),
    entity_name = Some("application"),
    reason = "Requested by the records officer while a complaint is open.",
    placed_by_user_id = exampleUserId,
    placed_at = exampleDate,
    released_by_user_id = None,
    released_at = None,
    is_active = true
  )

  val retentionHoldsJsonV700Example = RetentionHoldsJsonV700(List(retentionHoldJsonV700Example))

  val retentionArchiveEntryJsonV700Example = RetentionArchiveEntryJsonV700(
    retention_archive_id = "5c4b3a29-1807-4f6e-8d5c-4b3a29180706",
    retention_policy_id = Some(examplePolicyId),
    retention_run_id = exampleRunId,
    bank_id = "SYS",
    resource_type = Retention.ResourceDynamicEntityRecord,
    resource_id = "3d2c1b0a-9f8e-4d7c-6b5a-4f3e2d1c0b9a",
    entity_name = Some("application"),
    owner_user_id = Some(exampleUserId),
    parent_resource_type = None,
    parent_resource_id = None,
    archived_at = exampleDate,
    delete_after = Some(new Date(1924992000000L)),
    has_content = false,
    content_size_in_bytes = Some(2048L),
    data = Some(("dynamic_data_id" -> "3d2c1b0a-9f8e-4d7c-6b5a-4f3e2d1c0b9a") ~ ("entity_name" -> "application") ~
      ("data" -> (("status" -> "CLOSED") ~ ("title" -> "Example application")))),
    content_base64 = None
  )

  val retentionArchiveEntriesJsonV700Example = RetentionArchiveEntriesJsonV700(
    List(retentionArchiveEntryJsonV700Example.copy(data = None)))
}
