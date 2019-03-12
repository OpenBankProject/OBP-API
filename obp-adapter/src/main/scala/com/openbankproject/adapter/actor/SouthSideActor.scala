package com.openbankproject.adapter.actor

import java.util.Date

import akka.actor.Actor
import com.openbankproject.adapter.service.BankAccountService
import com.openbankproject.commons.dto.{InboundGetBank, InboundGetBanks, OutboundGetBank, OutboundGetBanks}
import javax.annotation.Resource
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

@Component("SouthSideActor")
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
class SouthSideActor  extends Actor  {

  @Resource
  val accountService: BankAccountService = null

  val mockAdapaterInfo =
    s"""
       |{
       |  "name":"String",
       |  "version":"String",
       |  "git_commit":"String",
       |  "date":"${new Date()}"
       |}
    """.stripMargin

  def receive = {
    case OutboundGetBanks(callContext) => sender ! InboundGetBanks(accountService.getBanks(), callContext)
    case OutboundGetBank(bankId, callContext) => sender ! InboundGetBank(this.accountService.getBankById(bankId), callContext)
    case message => sender ! mockAdapaterInfo
  }

}
