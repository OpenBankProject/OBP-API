package com.openbankproject.adapter.endpoint

import com.openbankproject.adapter.service.BankService
import io.swagger.annotations.Api
import javax.annotation.Resource
import org.springframework.web.bind.annotation._

@RestController
@RequestMapping(Array("v1/banks"))
@Api(tags = Array("banks operation."))
class BankEndpoint {
  @Resource
  val bankService: BankService = null

  @GetMapping(Array("/account"))
  def getAll = this.bankService.getAccounts("hello-bank-id")

  @GetMapping()
  def getAllBanks = this.bankService.getBanks()

  @GetMapping(Array("/{BANK_ID}"))
  def getBankById(@PathVariable("BANK_ID") bankId :String) = this.bankService.getBankById(bankId)

}
