/**
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd.

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
TESOBE Ltd.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package com.openbankproject.commons.model

import java.lang
import java.util.Date

import scala.collection.immutable.List

/**
*
* This is the base class for all kafka outbound case class
* action and messageFormat are mandatory
* The optionalFields can be any other new fields .
*/
abstract class OutboundMessageBase(
  optionalFields: String*
) {
  def action: String
  def messageFormat: String
}

abstract class InboundMessageBase(
  optionalFields: String*
) {
  def errorCode: String
}

case class InboundStatusMessage(
  source: String,
  status: String,
  errorCode: String,
  text: String
)

case class InboundAdapterInfoInternal(
  errorCode: String,
  backendMessages: List[InboundStatusMessage],
  name: String,
  version: String,
  git_commit: String,
  date: String
) extends InboundMessageBase


case class BankConnector(
  bankId: BankId,
  shortName: String,
  fullName: String,
  logoUrl: String,
  websiteUrl: String,
  bankRoutingScheme: String,
  bankRoutingAddress: String,
  swiftBic: String,
  nationalIdentifier: String
) extends Bank

case class CustomerConnector(
  customerId: String,
  bankId: String,
  number: String,
  legalName: String,
  mobileNumber: String,
  email: String,
  faceImage: CustomerFaceImage,
  dateOfBirth: Date,
  relationshipStatus: String,
  dependents: Integer,
  dobOfDependents: List[Date],
  highestEducationAttained: String,
  employmentStatus: String,
  creditRating: CreditRating,
  creditLimit: CreditLimit,
  kycStatus: lang.Boolean,
  lastOkDate: Date,
  title: String,
  branchId: String,
  nameSuffix: String,
) extends Customer


case class CounterpartyConnector(
  createdByUserId: String,
  name: String,
  thisBankId: String,
  thisAccountId: String,
  thisViewId: String,
  counterpartyId: String,
  otherAccountRoutingScheme: String,
  otherAccountRoutingAddress: String,
  otherBankRoutingScheme: String,
  otherBankRoutingAddress: String,
  otherBranchRoutingScheme: String,
  otherBranchRoutingAddress: String,
  isBeneficiary: Boolean,
  description: String,
  otherAccountSecondaryRoutingScheme: String,
  otherAccountSecondaryRoutingAddress: String,
  bespoke: List[CounterpartyBespoke]
) extends CounterpartyTrait