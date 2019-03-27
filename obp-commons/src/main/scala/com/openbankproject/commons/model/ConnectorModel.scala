package com.openbankproject.commons.model

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