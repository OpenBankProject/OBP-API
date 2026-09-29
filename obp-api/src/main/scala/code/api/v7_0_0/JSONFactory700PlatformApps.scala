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

import code.api.util.ExampleValue
import code.platformapp.{PlatformAppRequiredScopeTrait, PlatformAppTrait}

/*
 * The JSON of the v7.0.0 Platform Apps endpoints. Package-level case classes, for the reason given in
 * JSONFactory700Operations.
 */

/** Mark a Consumer as a Platform App. */
case class PostPlatformAppJsonV700(consumer_id: String, label: String)

/** One Scope an app declares it needs. */
case class PlatformAppRequiredScopeJsonV700(role_name: String, bank_id: String, needed_for: String, optional: Option[Boolean])

/** An app's declaration of the Scopes it needs, sent as itself. */
case class PutPlatformAppDeclarationJsonV700(version: Option[String], required_scopes: List[PlatformAppRequiredScopeJsonV700])

/** One declared Scope and whether the app's Consumer holds it. */
case class PlatformAppScopeStatusJsonV700(role_name: String, bank_id: String, needed_for: String, optional: Boolean, held: Boolean)

case class PlatformAppJsonV700(
  consumer_id: String,
  consumer_name: String,
  label: String,
  marked_by_user_id: String,
  marked_at: Date,
  declared_at: Option[Date],
  version: Option[String],
  /** ok: every required Scope is held; missing: some are not; not_declared: the app has not said what it needs. */
  state: String,
  required_scopes: List[PlatformAppScopeStatusJsonV700]
)

case class PlatformAppsJsonV700(platform_apps: List[PlatformAppJsonV700])

object JSONFactory700PlatformApps {

  def createPlatformAppJson(
    app: PlatformAppTrait,
    consumerName: String,
    declared: List[PlatformAppRequiredScopeTrait],
    held: List[(String, String)]
  ): PlatformAppJsonV700 = {
    val statuses = declared.map(s => PlatformAppScopeStatusJsonV700(
      role_name = s.roleName,
      bank_id = s.bankId,
      needed_for = s.neededFor,
      optional = s.isOptional,
      held = held.contains((s.roleName, s.bankId))))
    val state =
      if (app.declaredAt.isEmpty) "not_declared"
      else if (statuses.exists(s => !s.held && !s.optional)) "missing"
      else "ok"
    PlatformAppJsonV700(
      consumer_id = app.consumerId,
      consumer_name = consumerName,
      label = app.label,
      marked_by_user_id = app.markedByUserId,
      marked_at = app.markedAt,
      declared_at = app.declaredAt,
      version = app.declaredVersion,
      state = state,
      required_scopes = statuses)
  }

  lazy val postPlatformAppJsonV700Example = PostPlatformAppJsonV700(
    consumer_id = ExampleValue.consumerIdExample.value,
    label = "Portal")

  lazy val putPlatformAppDeclarationJsonV700Example = PutPlatformAppDeclarationJsonV700(
    version = Some("1.1.0"),
    required_scopes = List(
      PlatformAppRequiredScopeJsonV700("CanGetDynamicEntityRecord_obp_portal_page", "SYS",
        "Showing the pages published with App Studio at /pages, to every visitor.", Some(false))))

  lazy val platformAppJsonV700Example = PlatformAppJsonV700(
    consumer_id = ExampleValue.consumerIdExample.value,
    consumer_name = "obp-portal-client",
    label = "Portal",
    marked_by_user_id = ExampleValue.userIdExample.value,
    marked_at = new Date(),
    declared_at = Some(new Date()),
    version = Some("1.1.0"),
    state = "missing",
    required_scopes = List(
      PlatformAppScopeStatusJsonV700("CanGetDynamicEntityRecord_obp_portal_page", "SYS",
        "Showing the pages published with App Studio at /pages, to every visitor.", optional = false, held = false)))

  lazy val platformAppsJsonV700Example = PlatformAppsJsonV700(List(platformAppJsonV700Example))
}
