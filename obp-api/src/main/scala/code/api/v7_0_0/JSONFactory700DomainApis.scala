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

import code.api.Constant.HostName
import code.domainapi.DomainApiTrait

/*
 * The JSON of the v7.0.0 Domain API endpoints. Package-level case classes, for the reason given in
 * JSONFactory700Operations.
 */

/** Register or change a Domain API. */
case class PostDomainApiJsonV700(base_path: String, version: String, title: String, description: Option[String])

case class DomainApiJsonV700(
  domain_api_id: String,
  bank_id: String,
  base_path: String,
  version: String,
  title: String,
  description: String,
  /** Where the Domain API is published: this instance's host followed by the base path. */
  url: String,
  /** Its OpenAPI document. */
  openapi_url: String,
  created_by_user_id: String,
  created_at: Date,
  updated_at: Date
)

case class DomainApisJsonV700(domain_apis: List[DomainApiJsonV700])

object JSONFactory700DomainApis {

  def createDomainApiJson(domainApi: DomainApiTrait): DomainApiJsonV700 = DomainApiJsonV700(
    domain_api_id = domainApi.domainApiId,
    bank_id = domainApi.bankId,
    base_path = domainApi.basePath,
    version = domainApi.version,
    title = domainApi.title,
    description = domainApi.description,
    url = s"$HostName/${domainApi.basePath}",
    openapi_url = s"$HostName/${domainApi.basePath}/openapi.yaml",
    created_by_user_id = domainApi.createdByUserId,
    created_at = domainApi.createdAt,
    updated_at = domainApi.updatedAt
  )

  val postDomainApiJsonV700Example = PostDomainApiJsonV700(
    base_path = "carbon-registry/v1",
    version = "1.0.0",
    title = "Open Carbon Registry API",
    description = Some("Activities and land parcels of the registry.")
  )

  val domainApiJsonV700Example = DomainApiJsonV700(
    domain_api_id = "5f4a1c8e-2b7d-4e9a-9c3f-1d2e3f4a5b6c",
    bank_id = "SYS",
    base_path = "carbon-registry/v1",
    version = "1.0.0",
    title = "Open Carbon Registry API",
    description = "Activities and land parcels of the registry.",
    url = "https://api.example.org/carbon-registry/v1",
    openapi_url = "https://api.example.org/carbon-registry/v1/openapi.yaml",
    created_by_user_id = "9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
    created_at = new Date(),
    updated_at = new Date()
  )

  val domainApisJsonV700Example = DomainApisJsonV700(List(domainApiJsonV700Example))
}
