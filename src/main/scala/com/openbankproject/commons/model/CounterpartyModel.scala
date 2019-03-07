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
