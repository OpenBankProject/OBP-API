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