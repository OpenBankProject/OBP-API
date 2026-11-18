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

import code.api.util.APIUtil
import code.model.Consumer
import code.organisation.{ConsumerOwnership, OrganisationMemberTrait, OrganisationTrait}

/** An Organisation you create for yourself is always at SYS, so there is no bank_id. */
case class PostOrganisationJsonV700(
  name: String,
  description: Option[String],
  website: Option[String]
)

/** Staff create an Organisation and name its first admin, who need not be the caller. */
case class PostManagementOrganisationJsonV700(
  bank_id: Option[String],
  name: String,
  description: Option[String],
  website: Option[String],
  admin_user_id: String
)

case class PutOrganisationJsonV700(
  name: Option[String],
  description: Option[String],
  website: Option[String]
)

case class PutOrganisationStatusJsonV700(status: String)

case class OrganisationJsonV700(
  organisation_id: String,
  bank_id: String,
  name: String,
  description: String,
  website: Option[String],
  status: String,
  created_by_user_id: String,
  created_at: java.util.Date,
  updated_at: java.util.Date
)

case class OrganisationsJsonV700(organisations: List[OrganisationJsonV700])

/** One of the caller's Organisations, with the caller's role in it. */
case class MyOrganisationJsonV700(
  organisation_id: String,
  bank_id: String,
  name: String,
  description: String,
  website: Option[String],
  status: String,
  member_role: String,
  created_by_user_id: String,
  created_at: java.util.Date,
  updated_at: java.util.Date
)

case class MyOrganisationsJsonV700(organisations: List[MyOrganisationJsonV700])

case class PostOrganisationMemberJsonV700(user_id: String, member_role: String)

case class PutOrganisationMemberJsonV700(member_role: String)

case class OrganisationMemberJsonV700(
  organisation_id: String,
  user_id: String,
  username: String,
  provider: String,
  member_role: String,
  created_by_user_id: String,
  created_at: java.util.Date
)

case class OrganisationMembersJsonV700(members: List[OrganisationMemberJsonV700])

case class PutConsumerOrganisationJsonV700(organisation_id: Option[String])

/** A Consumer as its owner sees it. Never its consumer key or secret. */
case class MyConsumerJsonV700(
  consumer_id: String,
  app_name: String,
  app_type: String,
  description: String,
  developer_email: String,
  company: String,
  redirect_url: String,
  logo_url: Option[String],
  enabled: Boolean,
  created_by_user_id: String,
  organisation_id: Option[String],
  created: java.util.Date
)

case class MyConsumersJsonV700(consumers: List[MyConsumerJsonV700])

/** How much a Consumer has used, and the limits it is under now. */
case class MyConsumerUsageJsonV700(
  consumer_id: String,
  active_rate_limits: code.api.v6_0_0.ActiveRateLimitsJsonV600,
  call_counters: code.api.v6_0_0.RedisCallCountersJsonV600
)

case class MyConsumerScopeJsonV700(role_name: String, bank_id: String)

case class MyConsumerScopesJsonV700(consumer_id: String, scopes: List[MyConsumerScopeJsonV700])

object JSONFactory700Organisations {

  def createOrganisationJson(organisation: OrganisationTrait): OrganisationJsonV700 =
    OrganisationJsonV700(organisation.organisationId, organisation.bankId, organisation.name, organisation.description, organisation.website,
      organisation.status, organisation.createdByUserId, organisation.createdAt, organisation.updatedAt)

  def createOrganisationsJson(organisations: List[OrganisationTrait]): OrganisationsJsonV700 =
    OrganisationsJsonV700(organisations.map(createOrganisationJson))

  def createMyOrganisationJson(organisation: OrganisationTrait, membership: OrganisationMemberTrait): MyOrganisationJsonV700 =
    MyOrganisationJsonV700(organisation.organisationId, organisation.bankId, organisation.name, organisation.description, organisation.website,
      organisation.status, membership.memberRole, organisation.createdByUserId, organisation.createdAt, organisation.updatedAt)

  def createMemberJson(member: OrganisationMemberTrait, user: Option[com.openbankproject.commons.model.User]): OrganisationMemberJsonV700 =
    OrganisationMemberJsonV700(member.organisationId, member.userId, user.map(_.name).getOrElse(""),
      user.map(_.provider).getOrElse(""), member.memberRole, member.createdByUserId, member.createdAt)

  def createMyConsumerJson(consumer: Consumer): MyConsumerJsonV700 =
    MyConsumerJsonV700(
      consumer_id = consumer.consumerId.get,
      app_name = consumer.name.get,
      app_type = consumer.appType.get,
      description = Option(consumer.description.get).getOrElse(""),
      developer_email = Option(consumer.developerEmail.get).getOrElse(""),
      company = Option(consumer.company.get).getOrElse(""),
      redirect_url = Option(consumer.redirectURL.get).getOrElse(""),
      logo_url = Option(consumer.logoUrl.get).filter(_.nonEmpty),
      enabled = consumer.isActive.get,
      created_by_user_id = Option(consumer.createdByUserId.get).getOrElse(""),
      organisation_id = ConsumerOwnership.organisationIdOf(consumer),
      created = consumer.createdAt.get
    )

  private val exampleDate = APIUtil.DateWithDayExampleObject

  lazy val organisationJsonV700Example = OrganisationJsonV700("8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0", "SYS", "Example Fintech Ltd",
    "Builds payment apps.", Some("https://fintech.example.com"), "active", "9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1", exampleDate, exampleDate)

  lazy val myOrganisationJsonV700Example = MyOrganisationJsonV700("8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0", "SYS", "Example Fintech Ltd",
    "Builds payment apps.", Some("https://fintech.example.com"), "active", "admin", "9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1", exampleDate, exampleDate)

  lazy val organisationMemberJsonV700Example = OrganisationMemberJsonV700("8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0",
    "9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1", "susan.uk.29@example.com", "https://oidc.example.com", "member",
    "9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1", exampleDate)

  lazy val myConsumerJsonV700Example = MyConsumerJsonV700("7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh", "Example Payments App", "Confidential",
    "Pays bills.", "dev@fintech.example.com", "Example Fintech Ltd", "https://app.fintech.example.com/callback", None, true,
    "9ca9a7e4-6d02-40e3-a129-0b2bf89de9b1", Some("8ca8a7e4-6d02-48e3-a029-0b2bf89de9f0"), exampleDate)

  lazy val myConsumerUsageJsonV700Example = MyConsumerUsageJsonV700("7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
    code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.activeRateLimitsJsonV600,
    code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.redisCallCountersJsonV600)

  lazy val myConsumerScopesJsonV700Example = MyConsumerScopesJsonV700("7uy8a7e4-6d02-40e3-a129-0b2bf89de8uh",
    List(MyConsumerScopeJsonV700("CanGetCustomersAtOneBank", "gh.29.uk")))
}
