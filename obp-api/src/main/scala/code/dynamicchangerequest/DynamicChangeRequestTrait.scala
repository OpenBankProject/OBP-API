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

package code.dynamicchangerequest

import java.util.Date
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector

object DynamicChangeRequestTrait extends SimpleInjector {
  val dynamicChangeRequest = new Inject(() => buildOne) {}

  def buildOne: DynamicChangeRequestProvider = MappedDynamicChangeRequestProvider
}

/**
 * One row per maker/checker request against a runtime-supplied artefact (dynamic resource doc,
 * connector method, dynamic message doc, ABAC rule ...). The row is never deleted: the table is
 * the audit log. There is deliberately no bank column: dynamic code runs in the shared JVM and
 * approval is system level; the intercepted request path records the scope the maker used.
 */
trait DynamicChangeRequestTrait {
  def dynamicChangeRequestId: String
  def targetType: String
  def targetId: String
  def operation: String
  /** the original call the maker made, e.g. POST /obp/v4.0.0/management/banks/BANK_ID/dynamic-resource-docs */
  def requestVerb: String
  def requestPath: String
  /** the exact JSON body the maker sent, stored verbatim */
  def proposedPayload: String
  /** SHA-256 of the canonicalised proposed payload; what the checker approves */
  def payloadHash: String
  /** body hash of the live target at submission time; empty for CREATE */
  def currentPayloadHash: String
  def status: String
  def requestorUserId: String
  def businessJustification: String
  def checkerUserId: String
  def checkerComment: String
  def created: Date
  def updated: Date
  def actionedAt: Option[Date]
  def expiresAt: Option[Date]
}

trait DynamicChangeRequestProvider {
  def create(
    targetType: String,
    targetId: String,
    operation: String,
    requestVerb: String,
    requestPath: String,
    proposedPayload: String,
    payloadHash: String,
    currentPayloadHash: String,
    requestorUserId: String,
    businessJustification: String,
    expiresAt: Option[Date]
  ): Box[DynamicChangeRequestTrait]

  def getById(dynamicChangeRequestId: String): Box[DynamicChangeRequestTrait]

  def getAll(
    status: Option[String],
    targetType: Option[String],
    targetId: Option[String],
    requestorUserId: Option[String]
  ): List[DynamicChangeRequestTrait]

  def getByRequestorUserId(requestorUserId: String): List[DynamicChangeRequestTrait]

  /** Guarded INITIATED -> status transition; Failure when the row was already actioned. */
  def updateStatus(
    dynamicChangeRequestId: String,
    status: String,
    checkerUserId: String,
    checkerComment: String
  ): Box[DynamicChangeRequestTrait]
}
