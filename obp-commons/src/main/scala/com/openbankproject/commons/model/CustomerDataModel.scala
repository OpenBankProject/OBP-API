package com.openbankproject.commons.model

import java.lang
import java.util.Date

import scala.collection.immutable.List


trait Customer {
  def customerId : String // The UUID for the customer. To be used in URLs
  def bankId : String
  def number : String // The Customer number i.e. the bank identifier for the customer.
  def legalName : String
  def mobileNumber : String
  def email : String
  def faceImage : CustomerFaceImageTrait
  def dateOfBirth: Date
  def relationshipStatus: String
  def dependents: Integer
  def dobOfDependents: List[Date]
  def highestEducationAttained: String
  def employmentStatus: String
  def creditRating : CreditRatingTrait
  def creditLimit: AmountOfMoneyTrait
  def kycStatus: lang.Boolean
  def lastOkDate: Date
  def title: String
  def branchId: String
  def nameSuffix: String
}

trait CustomerFaceImageTrait {
  def url : String
  def date : Date
}

trait AmountOfMoneyTrait {
  def currency: String
  def amount: String
}

trait CreditRatingTrait {
  def rating: String
  def source: String
}

case class CustomerFaceImage(date : Date, url : String) extends CustomerFaceImageTrait
case class CreditRating(rating: String, source: String) extends CreditRatingTrait
case class CreditLimit(currency: String, amount: String) extends AmountOfMoneyTrait