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

import code.organisation.OrganisationMemberTrait

case class PostOrganisationMemberJsonV700(
  user_id: String,
  member_role: Option[String],
  title: Option[String],
  status: Option[String],
  is_primary: Option[Boolean]
)

case class PutOrganisationMemberJsonV700(
  member_role: Option[String],
  title: Option[String],
  status: Option[String],
  is_primary: Option[Boolean]
)

case class OrganisationMemberJsonV700(
  organisation_member_id: String,
  organisation_id: String,
  bank_id: String,
  user_id: String,
  username: String,
  member_role: String,
  title: Option[String],
  status: String,
  is_primary: Boolean,
  created_by_user_id: String,
  created_by_on_behalf_of_user_id: String,
  updated_by_user_id: String,
  updated_by_on_behalf_of_user_id: String,
  created_at: java.util.Date,
  updated_at: java.util.Date
)

case class OrganisationMembersJsonV700(members: List[OrganisationMemberJsonV700])

case class MyOrganisationJsonV700(organisation: JSONFactory700.OrganisationJsonV700, membership: OrganisationMemberJsonV700)

case class MyOrganisationsJsonV700(organisations: List[MyOrganisationJsonV700])

object JSONFactory700OrganisationMembers {

  def createOrganisationMemberJson(m: OrganisationMemberTrait, bankId: String, username: String): OrganisationMemberJsonV700 =
    OrganisationMemberJsonV700(
      organisation_member_id = m.organisationMemberId,
      organisation_id = m.organisationId,
      bank_id = bankId,
      user_id = m.userId,
      username = username,
      member_role = m.memberRole,
      title = m.title,
      status = m.status,
      is_primary = m.isPrimary,
      created_by_user_id = m.createdByUserId,
      created_by_on_behalf_of_user_id = m.createdByOnBehalfOfUserId,
      updated_by_user_id = m.updatedByUserId,
      updated_by_on_behalf_of_user_id = m.updatedByOnBehalfOfUserId,
      created_at = m.createdAt,
      updated_at = m.updatedAt
    )

  lazy val postOrganisationMemberJsonV700Example = PostOrganisationMemberJsonV700(
    user_id = "9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
    member_role = Some("member"),
    title = Some("CTO"),
    status = Some("active"),
    is_primary = Some(true)
  )

  lazy val putOrganisationMemberJsonV700Example = PutOrganisationMemberJsonV700(
    member_role = Some("admin"),
    title = Some("CTO"),
    status = Some("active"),
    is_primary = Some(true)
  )

  lazy val organisationMemberJsonV700Example = OrganisationMemberJsonV700(
    organisation_member_id = "6fb17583-1e49-4435-bb74-a14fe0996723",
    organisation_id = "tesobe",
    bank_id = "SYS",
    user_id = "9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1",
    username = "felixsmith",
    member_role = "member",
    title = Some("CTO"),
    status = "active",
    is_primary = true,
    created_by_user_id = "8a0b3c1d-6d02-40e3-a129-0b2bf89de9b1",
    created_by_on_behalf_of_user_id = "8a0b3c1d-6d02-40e3-a129-0b2bf89de9b1",
    updated_by_user_id = "8a0b3c1d-6d02-40e3-a129-0b2bf89de9b1",
    updated_by_on_behalf_of_user_id = "8a0b3c1d-6d02-40e3-a129-0b2bf89de9b1",
    created_at = new java.util.Date(),
    updated_at = new java.util.Date()
  )

  lazy val organisationMembersJsonV700Example = OrganisationMembersJsonV700(List(organisationMemberJsonV700Example))

  lazy val myOrganisationsJsonV700Example = MyOrganisationsJsonV700(List(
    MyOrganisationJsonV700(JSONFactory700.organisationJsonV700Example, organisationMemberJsonV700Example)))
}
