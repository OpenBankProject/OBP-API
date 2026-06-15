/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH.

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
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

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

  import code.accountholders.AccountHolders
  import code.api.util.{APIUtil, CallContext}
  import code.bankconnectors.LocalMappedConnectorInternal
  import code.users.Users
  import code.util.Helper.MdcLoggable
  import com.openbankproject.commons.ExecutionContext.Implicits.global
  import com.openbankproject.commons.model._
  import com.rabbitmq.client.{AMQP, ConnectionFactory, DefaultConsumer, Envelope}
  import com.tesobe.model.{CreateBankAccount, UpdateBankAccount}
  import net.liftweb.common.{Failure, Full}

  import java.io.{ByteArrayInputStream, ObjectInputFilter, ObjectInputStream}

  object BankAccountCreation extends MdcLoggable {

    def setAccountHolderAndRefreshUserAccountAccess(bankId: BankId, accountId: AccountId, user: User, callContext: Option[CallContext]) = {
      AccountHolders.accountHolders.vend.getOrCreateAccountHolder(user: User, BankIdAccountId(bankId, accountId))
      AuthUser.refreshUser(user, callContext)
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

    def handleMessage(message: CreateBankAccount): Unit = {
      logger.debug(s"got message to create account/bank: ${message.accountNumber} / ${message.bankIdentifier}")

      val accountType = "AMPQ"
      val accountLabel = message.accountNumber
      val currency = "EUR"

      val foundUser = Users.users.vend.getUserByProviderId(message.accountOwnerProvider, message.accountOwnerId)
      val result = for {
        user <- foundUser ?~!
          s"user ${message.accountOwnerId} at ${message.accountOwnerProvider} not found. Could not create the account with owner view"
        (_, bankAccount) <- LocalMappedConnectorInternal.createBankAndAccount(
          message.bankName,
          message.bankIdentifier,
          message.accountNumber,
          accountType, accountLabel,
          currency, user.name,
          "",
          "",
          "", //added field in V220
          None
        )
      } yield {
        logger.debug(s"created account with id ${bankAccount.bankId.value} with number ${bankAccount.number} at bank with identifier ${message.bankIdentifier}")
        BankAccountCreation.setAccountHolderAndRefreshUserAccountAccess(bankAccount.bankId, bankAccount.accountId, user, None).map { _ =>
          logger.debug(s"Successfully set account holder and refreshed user account access for account ${bankAccount.accountId.value}")
        }.recover {
          case ex: Exception =>
            logger.error(s"Failed to set account holder and refresh user account access: ${ex.getMessage}", ex)
        }
        bankAccount
      }

      result match {
        case Full(_) =>
          logger.debug(s"Send message to get updates for the account with account number ${message.accountNumber} at ${message.bankIdentifier}")
          UpdatesRequestSender.sendMsg(UpdateBankAccount(message.accountNumber, message.bankIdentifier))
        case Failure(msg, _, _) => logger.warn(s"account creation failed: $msg")
        case _ => logger.warn(s"account creation failed")
      }
    }

    def startListen: Unit = {
      logger.debug("started to listen for bank account creation messages")
      val connection = factory.newConnection()
      val channel = connection.createChannel()
      channel.exchangeDeclare("directExchange4", "direct", false)
      channel.queueDeclare("createBankAccount", false, false, false, null)
      channel.queueBind("createBankAccount", "directExchange4", "createBankAccount")
      channel.basicConsume("createBankAccount", false, new DefaultConsumer(channel) {
        override def handleDelivery(
          consumerTag: String,
          envelope: Envelope,
          properties: AMQP.BasicProperties,
          body: Array[Byte]
        ): Unit = {
          try {
            val ois = new ObjectInputStream(new ByteArrayInputStream(body))
            // Allowlist: only CreateBankAccount (5 String fields) may be deserialized.
            // Rejects gadget-chain classes (commons-collections, beanutils, etc.) at the
            // filter layer, before readObject() can instantiate them.
            ois.setObjectInputFilter(ObjectInputFilter.Config.createFilter(
              "com.tesobe.model.CreateBankAccount;java.lang.*;!*"))
            val msg = ois.readObject().asInstanceOf[CreateBankAccount]
            handleMessage(msg)
            channel.basicAck(envelope.getDeliveryTag, false)
          } catch {
            case ex: Exception =>
              logger.error(s"Failed to process AMQP message: ${ex.getMessage}", ex)
              // Deliberately do not requeue: a failed create-account message is dropped rather
              // than redelivered in a tight loop. This preserves the prior actor behaviour
              // (failures were logged, never retried). Switch to a dead-letter queue if the
              // adapter ever needs durable retry semantics.
              channel.basicNack(envelope.getDeliveryTag, false, false)
          }
        }
      })
    }
  }
}
