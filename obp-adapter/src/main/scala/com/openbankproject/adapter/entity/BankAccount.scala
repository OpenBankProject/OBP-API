package com.openbankproject.adapter.entity

import java.util.Date

import com.fasterxml.jackson.annotation.JsonProperty

import scala.beans.BeanProperty

class BankAccount {

  @JsonProperty("bank_id")
  @BeanProperty
  var bank :String = _

  @BeanProperty
  @JsonProperty("id")
  var theAccountId :String = _

  @BeanProperty
  var accountIban :String = _

  @BeanProperty
  var accountCurrency :String = _

  @BeanProperty
  var accountSwiftBic :String = _

  @JsonProperty("number")
  @BeanProperty
  var accountNumber :String = _

  @deprecated
  @BeanProperty
  var holder :String = _

  //this is the smallest unit of currency! e.g. cents, yen, pence, øre, etc.
  @BeanProperty
  var accountBalance :Long = _

  @BeanProperty
  var accountName :String = _

  @BeanProperty
  var kind :String = _

  @JsonProperty("label")
  @BeanProperty
  var accountLabel :String = _

  //the last time this account was updated via hbci
  @BeanProperty
  var accountLastUpdate: Date = _

  @BeanProperty
  var accountRoutingScheme :String = _

  @BeanProperty
  var accountRoutingAddress :String = _

  @BeanProperty
  var branchId :String = _

  @BeanProperty
  var accountRuleScheme1 :String = _

  @BeanProperty
  var accountRuleValue1 :Long = _

  @BeanProperty
  var accountRuleScheme2 :String = _

  @BeanProperty
  var accountRuleValue2 :Long = _

  @JsonProperty("identifier")
  @BeanProperty
  var id :Long = _
}
