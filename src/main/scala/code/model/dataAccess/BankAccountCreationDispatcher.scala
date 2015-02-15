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
  )
}

package code.model.dataAccess {

import code.bankconnectors.Connector
import code.model._
import code.users.Users
import code.views.Views
import com.rabbitmq.client.{ConnectionFactory,Channel}
  import net.liftmodules.amqp.{
    AMQPDispatcher,
    AMQPMessage,
    SerializedConsumer,
    AMQPAddListener
  }

import net.liftweb.util._
import net.liftweb.common.{Failure, Loggable, Full}
import net.liftweb.actor.LiftActor
import com.tesobe.model.{CreateBankAccount, UpdateBankAccount}


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

    def setAsOwner(bankId : BankId, accountId : AccountId, user: User): Unit = {
      createOwnerView(bankId, accountId, user)
      Connector.connector.vend.setAccountHolder(BankAccountUID(bankId, accountId), user)
    }


    private def createOwnerView(bankId : BankId, accountId : AccountId, user: User): Unit = {

      val ownerViewUID = ViewUID(ViewId("owner"), bankId, accountId)
      val existingOwnerView = Views.views.vend.view(ownerViewUID)

      existingOwnerView match {
        case Full(v) => {
          logger.info(s"account $accountId at bank $bankId has already an owner view")
          v.users.toList.find(_.apiId == user.apiId) match {
            case Some(u) => {
              logger.info(s"user ${user.emailAddress} has already an owner view access on account $accountId at bank $bankId")
            }
            case _ =>{
              //TODO: When can this case occur?
              logger.info(s"creating owner view access to user ${user.emailAddress}")
              Views.views.vend.addPermission(ownerViewUID, user)
            }
          }
        }
        case _ => {
          {
            //TODO: if we add more permissions to ViewImpl we need to remember to set them here...
            logger.info(s"creating owner view on account account $accountId at bank $bankId")
            val view = ViewImpl.createAndSaveOwnerView(bankId, accountId, "")

            logger.info(s"creating owner view access to user ${user.emailAddress}")
            Views.views.vend.addPermission(ownerViewUID, user)
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
          val result = for {
            user <- foundUser ?~!
              s"user ${message.accountOwnerId} at ${message.accountOwnerProvider} not found. Could not create the account with owner view"
          } yield {
            val (_, bankAccount) = Connector.connector.vend.createBankAndAccount(message.bankName, message.bankIdentifier, message.accountNumber, user.name)
            logger.info(s"created account with id ${bankAccount.bankId.value} with number ${bankAccount.number} at bank with identifier ${message.bankIdentifier}")
            BankAccountCreation.setAsOwner(bankAccount.bankId, bankAccount.accountId, user)
          }

          result match {
            case Full(_) =>
              logger.info(s"Send message to get updates for the account ${message.accountNumber} at ${message.bankIdentifier}")
              UpdatesRequestSender.sendMsg(UpdateBankAccount(message.accountNumber, message.bankIdentifier))
            case Failure(msg, _, _) => logger.warn(s"account creation failed: $msg")
            case _ => logger.warn(s"account creation failed")
          }

        }
      }
    }
    def startListen = {
      logger.info("started to listen for bank account creation messages")
      amqp ! AMQPAddListener(createBankAccountListener)
    }
  }
}