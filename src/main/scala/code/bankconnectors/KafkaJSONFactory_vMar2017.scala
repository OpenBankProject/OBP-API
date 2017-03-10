/**
Open Bank Project - API
Copyright (C) 2011-2016, TESOBE Ltd

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
TESOBE Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)
  by
  Simon Redfern : simon AT tesobe DOT com
  Stefan Bethge : stefan AT tesobe DOT com
  Everett Sochowski : everett AT tesobe DOT com
  Ayoub Benali: ayoub AT tesobe DOT com

 */
package code.bankconnectors

import code.metadata.counterparties.CounterpartyTrait

case class GetCounterpartyByIban(
  action: String,
  version: String,
  userId: String,
  username: String,
  otherAccountRoutingAddress: String,
  otherAccountRoutingScheme: String
)

case class GetCounterpartyByCounterpartyId(
  action: String,
  version: String,
  userId: String,
  username: String,
  counterpartyId: String
)

case class KafkaInboundCounterparty(
  name: String,
  created_by_user_id: String,
  this_bank_id: String,
  this_account_id: String,
  this_view_id: String,
  counterparty_id: String,
  other_bank_routing_scheme: String,
  other_account_routing_scheme: String,
  other_bank_routing_address: String,
  other_account_routing_address: String,
  is_beneficiary: Boolean
)

case class KafkaCounterparty(counterparty: KafkaInboundCounterparty) extends CounterpartyTrait {
  def createdByUserId: String = counterparty.created_by_user_id
  def name: String = counterparty.name
  def thisBankId: String = counterparty.this_bank_id
  def thisAccountId: String = counterparty.this_account_id
  def thisViewId: String = counterparty.this_view_id
  def counterpartyId: String = counterparty.counterparty_id
  def otherAccountRoutingScheme: String = counterparty.other_account_routing_scheme
  def otherAccountRoutingAddress: String = counterparty.other_account_routing_address
  def otherBankRoutingScheme: String = counterparty.other_bank_routing_scheme
  def otherBankRoutingAddress: String = counterparty.other_bank_routing_address
  def isBeneficiary: Boolean = counterparty.is_beneficiary
}

object KafkaJSONFactory_vMar2017{
  
}


