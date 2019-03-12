package com.openbankproject.adapter;

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import com.openbankproject.adapter.actor.ResultActor
import com.openbankproject.adapter.service.BankAccountService
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner
import javax.annotation.Resource;

@RunWith(classOf[SpringRunner])
@SpringBootTest
class AkkaAdapterApplicationTest{
  @Resource
  val actorSystem: ActorSystem = null

  @Resource
  val accountService: BankAccountService = null

  @Test
  def contextLoads ={
    val client:ActorRef = actorSystem.actorOf (Props.create (classOf[ResultActor]), "client")
    val actorSelection = actorSystem.actorSelection ("akka.tcp://SouthSideAkkaConnector_127-0-0-1@127.0.0.1:2662/user/akka-connector-actor")
    actorSelection.tell ("increase", client)
    actorSelection.tell ("increase", client)
    actorSelection.tell ("get", client)

    System.out.println ()
  }

  @Test
  def getBanksTest = {
    val banks = this.accountService.getBanks()
    val bank = this.accountService.getBankById("hello-bank-id")
    System.out.println(banks)
    System.out.println(bank)

  }
}
