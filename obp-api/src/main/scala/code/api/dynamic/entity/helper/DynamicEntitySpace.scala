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
package code.api.dynamic.entity.helper

import code.api.Constant.DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID
import code.api.util.APIUtil.{EndpointAuthMode, UserOnly}
import code.api.util.ErrorMessages.UserHasMissingRoles
import code.api.util.{APIUtil, ApiRole, CallContext}
import code.util.Helper
import net.liftweb.common.Box

import scala.concurrent.Future

/**
 * This object holds the rules for naming a Dynamic Entity's space, in one place.
 *
 * Every Dynamic Entity lives in a space: either a bank, or the system space, whose bank id is
 * DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID (SYS). Inside the Dynamic Entity code a space is still an
 * Option, with None for the system space, while URLs, Roles, consents and storage all say SYS. The
 * two conversions below are the only places that translate between those forms, so that the rule is
 * stated once rather than wherever a space is read. See DYNAMIC_ENTITY_SPACE_MODEL_PLAN.md.
 */
object DynamicEntitySpace {

  /**
   * The bank id as the Dynamic Entity code asks about it: None for the system space.
   *
   * Both SYS and the empty string name the system space. The empty string is the older form, which
   * existing callers (consents written before SYS was published, for instance) still send.
   */
  def bankIdOrNoneForSystem(bankId: String): Option[String] =
    Option(bankId).filter(b => b.nonEmpty && b != DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID)

  /** The bank id as URLs, Roles and storage write it: SYS for the system space. */
  def bankIdOrSystem(bankId: Option[String]): String =
    bankId.filter(_.nonEmpty).getOrElse(DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID)

  /**
   * Refuse with 403 unless the caller holds a Definition Role at the system space.
   *
   * The v4.0.0 and v6.0.0 `/management/system-dynamic-entities` endpoints have no bank id in their
   * URL, so the middleware would check their Role at the empty bank id, where no Definition Role is
   * granted any more. Those endpoints declare the Role with disableAutoValidateRoles(), so it still
   * shows in the catalogue, and call this instead. It answers exactly as the middleware would have:
   * the same access rule, the same 403 and the same message. `authMode` is the endpoint's own, as its
   * ResourceDoc declares it.
   */
  def requireRoleAtSystemSpace(role: ApiRole, cc: CallContext, authMode: EndpointAuthMode = UserOnly): Future[Box[Unit]] = {
    val userId = cc.user.map(_.userId).openOr("")
    Helper.booleanToFuture(UserHasMissingRoles + role.toString, 403, Some(cc)) {
      APIUtil.handleAccessControlWithAuthMode(
        DYNAMIC_ENTITY_SYSTEM_LEVEL_BANK_ID, userId, APIUtil.getConsumerPrimaryKey(Some(cc)), List(role), authMode)
    }
  }
}
