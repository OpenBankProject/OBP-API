package com.openbankproject.adapter.service

import com.openbankproject.adapter.entity.BankAccount
import com.openbankproject.commons.dto.InboundBank
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.{GetMapping, PathVariable}

import scala.tools.nsc.interpreter.JList

@FeignClient(name="account", url="${adapter.remote.base.url}")
trait BankAccountService {

  @GetMapping(Array("banks/{BANK_ID}"))
  def getBankById(@PathVariable("BANK_ID") bankId: String): InboundBank


  @GetMapping(Array("/banks"))
  def getBanks(): List[InboundBank]


  @GetMapping(Array("/banks/{BANK_ID}/accounts"))
  def getAccounts(@PathVariable("BANK_ID") bankId :String): AccountResult
}

case class AccountResult(var accounts: JList[BankAccount])