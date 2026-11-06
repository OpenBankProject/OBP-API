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

import code.tenant.TenantTrait

case class PostTenantJsonV700(
  tenant_id: String,
  name: String,
  website: Option[String],
  logo_url: Option[String],
  status: Option[String],
  visibility: Option[String]
)

case class PutTenantJsonV700(
  name: Option[String],
  website: Option[String],
  logo_url: Option[String],
  status: Option[String],
  visibility: Option[String]
)

case class TenantJsonV700(
  tenant_id: String,
  name: String,
  website: Option[String],
  logo_url: Option[String],
  status: String,
  visibility: String,
  created_by_user_id: String,
  created_at: java.util.Date,
  updated_at: java.util.Date
)

case class TenantsJsonV700(tenants: List[TenantJsonV700])

object JSONFactory700Tenants {
  lazy val tenantJsonV700Example: TenantJsonV700 = TenantJsonV700(
    tenant_id = "tesobe",
    name = "TESOBE GmbH",
    website = Some("https://www.tesobe.com"),
    logo_url = None,
    status = "active",
    visibility = "public",
    created_by_user_id = "9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
    created_at = new java.util.Date(1795000000000L),
    updated_at = new java.util.Date(1795000000000L)
  )

  def createTenantJsonV700(t: TenantTrait): TenantJsonV700 =
    TenantJsonV700(
      tenant_id = t.tenantId,
      name = t.name,
      website = t.website,
      logo_url = t.logoUrl,
      status = t.status,
      visibility = t.visibility,
      created_by_user_id = t.createdByUserId,
      created_at = t.createdAt,
      updated_at = t.updatedAt
    )

  def createTenantsJsonV700(tenants: List[TenantTrait]): TenantsJsonV700 =
    TenantsJsonV700(tenants.map(createTenantJsonV700))
}
