/**
Open Bank Project - API
Copyright (C) 2011, 2013, TESOBE / Music Pictures Ltd

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
TESOBE / Music Pictures Ltd
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


/**
* the message to be received in message queue
* so that the API create an Bank (if necessary),
* the bank account and an owner view.
*/
package com.tesobe.model{
  case class CreateBankAccount(
    accountOwnerId: String,
    accountOwnerProvider: String,
    accountNumber: String,
    bankIdentifier: String,
    bankName: String
  ) extends BankAccountNumber

  trait BankAccountNumber {
    val accountNumber : String
  }

}

package code.model.dataAccess {

import java.util.UUID

import code.model.{User, AccountId, BankId}
import code.users.Users
import com.rabbitmq.client.{ConnectionFactory,Channel}
  import net.liftmodules.amqp.{
    AMQPDispatcher,
    AMQPMessage,
    SerializedConsumer,
    AMQPAddListener
  }

  import net.liftweb.util._
  import net.liftweb.common.{Loggable, Full}
  import net.liftweb.actor.LiftActor
  import com.tesobe.model.{BankAccountNumber, CreateBankAccount, UpdateBankAccount}
  import code.util.Helper
  import net.liftweb.mapper.By
  import net.liftweb.util.Helpers._

import scala.util.Random


/**
  *  an AMQP dispatcher that waits for message coming from a specif queue
  *  and dispatching them to the subscribed actors
  */
  class BankAccountCreationDispatcher[T](factory: ConnectionFactory)
      extends AMQPDispatcher[T](factory) {
    override def configure(channel: Channel) {
      channel.exchangeDeclare("directExchange4", "direct", false)
      channel.queueDeclare("createBankAccount", false, false, false, null)
      channel.queueBind ("createBankAccount", "directExchange4", "createBankAccount")
      channel.basicConsume("createBankAccount", false, new SerializedConsumer(channel, this))
    }
  }

  object BankAccountCreation extends Loggable {
    def createBank(message: CreateBankAccount): HostedBank = {
      // TODO: use a more unique id for the long term
      HostedBank.find(HostedBank.national_identifier.name, message.bankIdentifier) match {
        case Full(b)=> {
          logger.info(s"bank ${b.name} found")
          b
        }
        case _ =>{
          //TODO: if name is empty use bank id as name alias

          //TODO: need to handle the case where generatePermalink returns a permalink that is already used for another bank

          logger.info(s"creating HostedBank")
          HostedBank
          .createRecord
          .name(message.bankName)
          .alias(message.bankName)
          .permalink(Helper.generatePermalink(message.bankName))
          .national_identifier(message.bankIdentifier)
          .save
        }
      }
    }

    private def createAccount(bankAccountNumber: BankAccountNumber, accountId : AccountId, bank : HostedBank, u: User) : Account = {
      //TODO: fill these fields using the HBCI library.
      import net.liftweb.mongodb.BsonDSL._
      Account.find(
        (Account.accountNumber.name -> bankAccountNumber.accountNumber)~
          (Account.bankID.name -> bank.id.is)
      ) match {
        case Full(bankAccount) => {
          logger.info(s"account ${bankAccount.accountNumber} found")
          bankAccount
        }
        case _ => {
          logger.info("creating account record ")
          val accountHolder = s"${u.name}"
          val bankAccount =
            Account
              .createRecord
              .accountBalance(0)
              .holder(accountHolder)
              .accountNumber(bankAccountNumber.accountNumber)
              .kind("current")
              .accountName("")
              .permalink(accountId.value)
              .bankID(bank.id.is)
              .accountLabel("")
              .accountCurrency("EUR")
              .accountIban("")
              .lastUpdate(now)
              .save
          bankAccount
        }
      }
    }

    def createAccount(bankAccountNumber: BankAccountNumber, bank: HostedBank, u: User): Account = {
      createAccount(bankAccountNumber, AccountId(UUID.randomUUID().toString), bank, u)
    }

    def createAccount(accountId: AccountId, bank: HostedBank, u: User): Account = {
      import net.liftweb.mongodb.BsonDSL._
      val uniqueAccountNumber = {
        def exists(number : String) = Account.count((Account.accountNumber.name -> number) ~ (Account.bankID.name -> bank.id.get)) > 0

        def appendUntilOkay(number : String) : String = {
          val newNumber = number + Random.nextInt(10)
          if(!exists(newNumber)) newNumber
          else appendUntilOkay(newNumber)
        }

        //generates a random 8 digit account number
        val firstTry = (Random.nextDouble() * 10E8).toInt.toString
        appendUntilOkay(firstTry)
      }

      createAccount(new BankAccountNumber {
        override val accountNumber: String = uniqueAccountNumber
      }, accountId, bank, u)
    }

    def setAsOwner(bankId : BankId, accountId : AccountId, user: User): Unit = {
      createOwnerView(bankId, accountId, user)
      setAsAccountOwner(bankId, accountId, user)
    }

    private def setAsAccountOwner(bankId : BankId, accountId : AccountId, user : User) : Unit = {
      MappedAccountHolder.create
        .accountBankPermalink(bankId.value)
        .accountPermalink(accountId.value)
        .user(user.apiId.value)
        .save
    }

    private def createOwnerView(bankId : BankId, accountId : AccountId, user: User): Unit = {

      val existingOwnerView = ViewImpl.find(
        By(ViewImpl.permalink_, "owner") ::
        ViewImpl.accountFilter(bankId, accountId): _*)

      existingOwnerView match {
        case Full(v) => {
          logger.info(s"account $accountId at bank $bankId has already an owner view")
          v.users_.toList.find(_.id == user.apiId.value) match {
            case Some(u) => {
              logger.info(s"user ${user.emailAddress} has already an owner view access on account $accountId at bank $bankId")
            }
            case _ =>{
              //TODO: When can this case occur?
              logger.info(s"creating owner view access to user ${user.emailAddress}")
              ViewPrivileges
                .create
                .user(user.apiId.value)
                .view(v)
                .save
            }
          }
        }
        case _ => {
          {
            //TODO: if we add more permissions to ViewImpl we need to remember to set them here...
            logger.info(s"creating owner view on account account $accountId at bank $bankId")
            val view = ViewImpl.createAndSaveOwnerView(bankId, accountId, "")

            logger.info(s"creating owner view access to user ${user.emailAddress}")
            ViewPrivileges
              .create
              .user(user.apiId.value)
              .view(view)
              .save
          }
        }
      }
    }
  }

  object BankAccountCreationListener extends Loggable {

    lazy val factory = new ConnectionFactory {
      import ConnectionFactory._
      setHost(Props.get("connection.host", "localhost"))
      setPort(DEFAULT_AMQP_PORT)
      setUsername(Props.get("connection.user", DEFAULT_USER))
      setPassword(Props.get("connection.password", DEFAULT_PASS))
      setVirtualHost(DEFAULT_VHOST)
    }

    val amqp = new BankAccountCreationDispatcher[CreateBankAccount](factory)

    val createBankAccountListener = new LiftActor {
      protected def messageHandler = {
        case msg@AMQPMessage(message: CreateBankAccount) => {
          logger.info(s"got message to create account/bank: ${message.accountNumber} / ${message.bankIdentifier}")

          val foundUser  = Users.users.vend.getUserByProviderId(message.accountOwnerProvider, message.accountOwnerId)
          foundUser.map{ user => {
              logger.info("user found for owner view")

              val bank: HostedBank = BankAccountCreation.createBank(message)
              val bankAccount = BankAccountCreation.createAccount(message, bank, user)
              BankAccountCreation.setAsOwner(BankId(bank.permalink.get), bankAccount.accountId, user)

              logger.info(s"created account with id ${bankAccount.bankId.value} with number ${bankAccount.number} at bank with identifier ${message.bankIdentifier}")

              logger.info(s"Send message to get updates for the account ${message.accountNumber} at ${message.bankIdentifier}")
              UpdatesRequestSender.sendMsg(UpdateBankAccount(message.accountNumber, message.bankIdentifier))
            }
          }.getOrElse(
            logger.warn(s"user ${message.accountOwnerId} at ${message.accountOwnerProvider} not found. Could not create the account with owner view")
          )
        }
      }
    }
    def startListen = {
      logger.info("started to listen for bank account creation messages")
      amqp ! AMQPAddListener(createBankAccountListener)
    }
  }
}