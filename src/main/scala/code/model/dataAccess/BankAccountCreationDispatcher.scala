/**
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd

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
TESOBE Ltd
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
package com.tesobe.model {
@SerialVersionUID(3988687883966746423L) case class CreateBankAccount (
    accountOwnerId: String,
    accountOwnerProvider: String,
    accountNumber: String,
    bankIdentifier: String,
    bankName: String
  )
}

package code.model.dataAccess {

import code.accountholder.AccountHolders
import code.api.util.APIUtil
import code.bankconnectors.Connector
import code.model._
import code.users.Users
import code.util.Helper.MdcLoggable
import code.views.Views
import com.rabbitmq.client.{Channel, ConnectionFactory}
import com.tesobe.model.{CreateBankAccount, UpdateBankAccount}
import net.liftmodules.amqp.{AMQPAddListener, AMQPDispatcher, AMQPMessage, SerializedConsumer}
import net.liftweb.actor.LiftActor
import net.liftweb.common.{Failure, Full}


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

  object BankAccountCreation extends MdcLoggable {
  
    /**
      * 1 Create `Owner` view if the account do not have `Owner` view.
      * 2 Add Permission to `Owner` view
      * 3 Set the User as the account Holder.
      * 
      * @param bankId 
      * @param accountId
      * @param user the user can be Login user or other users(Have the CanCreateAccount role)
      *             
      * @return This is a procedure, no return value. Just use the side effect.
      */
    def setAsOwner(bankId : BankId, accountId : AccountId, user: User): Unit = {
      createOwnerView(bankId, accountId, user)
      val accountHolder = AccountHolders.accountHolders.vend.getOrCreateAccountHolder(user: User, BankIdAccountId(bankId, accountId))
    }
  
    /**
      * 1 Create or Update `Owner` view for Account.
      * 2 Add Permission to the User
      * @param bankId
      * @param accountId
      * @param user
      *       
      * @return This is a procedure, no return value. Just use the side effect.
      */
    private def createOwnerView(bankId : BankId, accountId : AccountId, user: User): Unit = {

      val ownerViewUID = ViewIdBankIdAccountId(ViewId("owner"), bankId, accountId)
      val existingOwnerView = Views.views.vend.view(ownerViewUID.viewId, BankIdAccountId(ownerViewUID.bankId, ownerViewUID.accountId))

      existingOwnerView match {
        case Full(v) => {
          logger.debug(s"account $accountId at bank $bankId has already an owner view")
          v.users.toList.find(_.userPrimaryKey == user.userPrimaryKey) match {
            case Some(u) => {
              logger.debug(s"user ${user.emailAddress} has already an owner view access on account $accountId at bank $bankId")
            }
            case _ =>{
              //TODO: When can this case occur?
              logger.debug(s"creating owner view access to user ${user.emailAddress}")
              Views.views.vend.addPermission(ownerViewUID, user)
            }
          }
        }
        case _ => {
          {
            //TODO: if we add more permissions to ViewImpl we need to remember to set them here...
            logger.debug(s"creating owner view on account account $accountId at bank $bankId")
            val view = Views.views.vend.getOrCreateOwnerView(bankId, accountId, "Owner View")

            logger.debug(s"creating owner view access to user ${user.emailAddress}")
            Views.views.vend.addPermission(ownerViewUID, user)
          }
        }
      }
    }
  }

  object BankAccountCreationListener extends MdcLoggable {

    lazy val factory = new ConnectionFactory {
      import ConnectionFactory._
      setHost(APIUtil.getPropsValue("connection.host", "localhost"))
      setPort(DEFAULT_AMQP_PORT)
      setUsername(APIUtil.getPropsValue("connection.user", DEFAULT_USER))
      setPassword(APIUtil.getPropsValue("connection.password", DEFAULT_PASS))
      setVirtualHost(DEFAULT_VHOST)
    }

    val amqp = new BankAccountCreationDispatcher[CreateBankAccount](factory)

    val createBankAccountListener = new LiftActor {
      protected def messageHandler = {
        case msg@AMQPMessage(message: CreateBankAccount) => {
          logger.debug(s"got message to create account/bank: ${message.accountNumber} / ${message.bankIdentifier}")

          //TODO: Revise those dummy values
          val accountType = "AMPQ"
          val accountLabel = message.accountNumber
          val currency = "EUR"

          val foundUser  = Users.users.vend.getUserByProviderId(message.accountOwnerProvider, message.accountOwnerId)
          val result = for {
            user <- foundUser ?~!
              s"user ${message.accountOwnerId} at ${message.accountOwnerProvider} not found. Could not create the account with owner view"
            (_, bankAccount) <- Connector.connector.vend.createBankAndAccount(
              message.bankName,
              message.bankIdentifier,
              message.accountNumber,
              accountType, accountLabel,
              currency, user.name,
              "","","" //added field in V220
            )
          } yield {
            logger.debug(s"created account with id ${bankAccount.bankId.value} with number ${bankAccount.number} at bank with identifier ${message.bankIdentifier}")
            BankAccountCreation.setAsOwner(bankAccount.bankId, bankAccount.accountId, user)
          }

          result match {
            case Full(_) =>
              logger.debug(s"Send message to get updates for the account with account number ${message.accountNumber} at ${message.bankIdentifier}")
              UpdatesRequestSender.sendMsg(UpdateBankAccount(message.accountNumber, message.bankIdentifier))
            case Failure(msg, _, _) => logger.warn(s"account creation failed: $msg")
            case _ => logger.warn(s"account creation failed")
          }

        }
      }
    }
    def startListen = {
      logger.debug("started to listen for bank account creation messages")
      amqp ! AMQPAddListener(createBankAccountListener)
    }
  }
}