/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH.

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

package com.openbankproject.commons.model

import scala.collection.immutable.List

trait CounterpartyTrait {
  def createdByUserId: String
  def name: String
  def description: String
  def thisBankId: String
  def thisAccountId: String
  def thisViewId: String
  def counterpartyId: String
  def otherAccountRoutingScheme: String
  def otherAccountRoutingAddress: String
  def otherAccountSecondaryRoutingScheme: String
  def otherAccountSecondaryRoutingAddress: String
  def otherBankRoutingScheme: String
  def otherBankRoutingAddress: String
  def otherBranchRoutingScheme: String
  def otherBranchRoutingAddress: String
  def isBeneficiary : Boolean
  def currency: String
  def bespoke: List[CounterpartyBespoke]
}

case class CounterpartyInMemory(
                                 createdByUserId: String,
                                 name: String,
                                 description: String,
                                 thisBankId: String,
                                 thisAccountId: String,
                                 thisViewId: String,
                                 counterpartyId: String,
                                 otherAccountRoutingScheme: String,
                                 otherAccountRoutingAddress: String,
                                 otherAccountSecondaryRoutingScheme: String,
                                 otherAccountSecondaryRoutingAddress: String,
                                 otherBankRoutingScheme: String,
                                 otherBankRoutingAddress: String,
                                 otherBranchRoutingScheme: String,
                                 otherBranchRoutingAddress: String,
                                 isBeneficiary : Boolean,
                                 bespoke: List[CounterpartyBespoke]
                               )
