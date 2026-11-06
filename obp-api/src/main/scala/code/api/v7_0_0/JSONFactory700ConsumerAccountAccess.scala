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
import code.consumeraccountaccess.ConsumerAccountAccessTrait

/*
 * The JSON of the v7.0.0 Consumer account access endpoints. Package-level case classes, for the reason given in
 * JSONFactory700Operations.
 */

/** Give a Consumer access to a view of this account. */
case class PostConsumerAccountAccessJsonV700(consumer_id: String, view_id: String)

case class ConsumerAccountAccessJsonV700(
  consumer_account_access_id: String,
  consumer_id: String,
  bank_id: String,
  account_id: String,
  view_id: String,
  granted_by_user_id: String,
  created_at: Date
)

case class ConsumerAccountAccessesJsonV700(consumer_account_access: List[ConsumerAccountAccessJsonV700])

object JSONFactory700ConsumerAccountAccess {

  def createConsumerAccountAccessJson(a: ConsumerAccountAccessTrait): ConsumerAccountAccessJsonV700 =
    ConsumerAccountAccessJsonV700(
      consumer_account_access_id = a.consumerAccountAccessId,
      consumer_id = a.consumerId,
      bank_id = a.bankId,
      account_id = a.accountId,
      view_id = a.viewId,
      granted_by_user_id = a.grantedByUserId,
      created_at = a.createdAt)

  def createConsumerAccountAccessesJson(list: List[ConsumerAccountAccessTrait]): ConsumerAccountAccessesJsonV700 =
    ConsumerAccountAccessesJsonV700(list.map(createConsumerAccountAccessJson))

  lazy val postConsumerAccountAccessJsonV700Example = PostConsumerAccountAccessJsonV700(
    consumer_id = ExampleValue.consumerIdExample.value,
    view_id = "_payment-initiation")

  lazy val consumerAccountAccessJsonV700Example = ConsumerAccountAccessJsonV700(
    consumer_account_access_id = "2bb6a0c4-0d8b-4c1b-9f5e-4a9d3c1f7e21",
    consumer_id = ExampleValue.consumerIdExample.value,
    bank_id = ExampleValue.bankIdExample.value,
    account_id = ExampleValue.accountIdExample.value,
    view_id = "_payment-initiation",
    granted_by_user_id = ExampleValue.userIdExample.value,
    created_at = new Date())

  lazy val consumerAccountAccessesJsonV700Example = ConsumerAccountAccessesJsonV700(List(consumerAccountAccessJsonV700Example))
}
