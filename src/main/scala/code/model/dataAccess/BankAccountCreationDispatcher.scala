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

import code.model.{AccountId, BankId}
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

    def createAccount(bankAccountNumber: BankAccountNumber, bank: HostedBank, u: APIUser): Account = {
      //TODO: fill these fields using the HBCI library.
      import net.liftweb.mongodb.BsonDSL._
      Account.find(
        (Account.number.name -> bankAccountNumber.accountNumber)~
        (Account.bankID.name -> bank.id.is)
      ) match {
        case Full(bankAccount) => {
          logger.info(s"account ${bankAccount.number} found")
          bankAccount
        }
        case _ => {
          logger.info("creating account record ")
          val accountHolder = s"${u.name}"
          val bankAccount =
            Account
            .createRecord
            .balance(0)
            .holder(accountHolder)
            .number(bankAccountNumber.accountNumber)
            .kind("current")
            .name("")
            .permalink(UUID.randomUUID().toString)
            .bankID(bank.id.is)
            .label("")
            .currency("EUR")
            .iban("")
            .lastUpdate(now)
            .save
          bankAccount
        }
      }
    }

    def setAsOwner(bankId : BankId, accountId : AccountId, user: APIUser): Unit = {
      createOwnerView(bankId, accountId, user)
      setAsAccountOwner(bankId, accountId, user)
    }

    private def setAsAccountOwner(bankId : BankId, accountId : AccountId, user : APIUser) : Unit = {
      MappedAccountHolder.create
        .accountBankPermalink(bankId.value)
        .accountPermalink(accountId.value)
        .user(user)
        .save
    }

    private def createOwnerView(bankId : BankId, accountId : AccountId, user: APIUser): Unit = {

      val existingOwnerView = ViewImpl.find(
        By(ViewImpl.permalink_, "owner") ::
        ViewImpl.accountFilter(bankId, accountId): _*)

      existingOwnerView match {
        case Full(v) => {
          logger.info(s"account $accountId at bank $bankId has already an owner view")
          v.users_.toList.find(_.id == user.id) match {
            case Some(u) => {
              logger.info(s"user ${user.email.get} has already an owner view access on account $accountId at bank $bankId")
            }
            case _ =>{
              //TODO: When can this case occur?
              logger.info(s"creating owner view access to user ${user.email.get}")
              ViewPrivileges
                .create
                .user(user)
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

            logger.info(s"creating owner view access to user ${user.email.get}")
            ViewPrivileges
              .create
              .user(user)
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

          APIUser.find(
            By(APIUser.provider_, message.accountOwnerProvider),
            By(APIUser.providerId, message.accountOwnerId)
          ).map{ user => {
              logger.info("user found for owner view")

              val bank: HostedBank = BankAccountCreation.createBank(message)
              val bankAccount = BankAccountCreation.createAccount(message, bank, user)
              BankAccountCreation.setAsOwner(BankId(bank.permalink.get), AccountId(message.accountNumber), user)

              logger.info(s"created account ${message.accountNumber} at ${message.bankIdentifier}")

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