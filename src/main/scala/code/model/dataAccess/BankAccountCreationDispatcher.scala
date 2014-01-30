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

  import com.rabbitmq.client.{ConnectionFactory,Channel}
  import net.liftmodules.amqp.{
    AMQPDispatcher,
    AMQPMessage,
    SerializedConsumer,
    AMQPAddListener
  }

  import net.liftweb.util._
  import net.liftweb.common.{Loggable, Failure, Full, Empty, Box}
  import net.liftweb.actor.LiftActor
  import Helpers.tryo
  import com.tesobe.model.CreateBankAccount
  import code.util.Helper
  import net.liftweb.mapper.By
  import net.liftweb.util.Helpers._
  import com.tesobe.model.UpdateBankAccount




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

  object BankAccountCreationListener extends Loggable {

    def createBank(message: CreateBankAccount): HostedBank = {
      HostedBank.find("national_identifier", message.bankIdentifier).getOrElse({
        //create the bank if necessary
        //TODO: if name is empty use bank id as name alias
        HostedBank
        .createRecord
        .name(message.bankName)
        .alias(message.bankName)
        .permalink(Helper.generatePermalink(message.bankName))
        .national_identifier(message.bankIdentifier)
        .save
      })
    }

    def createAccount(message: CreateBankAccount, bank: HostedBank, u: APIUser): Account = {
      //TODO: fill these fields using the HBCI library.
      import net.liftweb.mongodb.BsonDSL._
      Account.find(
        ("number" -> message.accountNumber)~
        ("bankID" -> bank.id.is)
      ).getOrElse{
        val accountHolder = s"${u.theFirstName} ${u.theLastName}"
        Account
        .createRecord
        .balance(0)
        .holder(accountHolder)
        .number(message.accountNumber)
        .kind("current")
        .name("")
        .permalink(message.accountNumber)
        .bankID(bank.id.is)
        .label("")
        .currency("EUR")
        .iban("")
        .lastUpdate(now)
        .save
      }
    }

    def createOwnerView(account: HostedAccount): ViewImpl = {
      ViewImpl
      .create
      .account(account)
      .name_("Owner")
      .permalink_("owner")
      .canSeeTransactionThisBankAccount_(true)
      .canSeeTransactionOtherBankAccount_(true)
      .canSeeTransactionMetadata_(true)
      .canSeeTransactionDescription_(true)
      .canSeeTransactionAmount_(true)
      .canSeeTransactionType_(true)
      .canSeeTransactionCurrency_(true)
      .canSeeTransactionStartDate_(true)
      .canSeeTransactionFinishDate_(true)
      .canSeeTransactionBalance_(true)
      .canSeeComments_(true)
      .canSeeOwnerComment_(true)
      .canSeeTags_(true)
      .canSeeImages_(true)
      .canSeeBankAccountOwners_(true)
      .canSeeBankAccountType_(true)
      .canSeeBankAccountBalance_(true)
      .canSeeBankAccountCurrency_(true)
      .canSeeBankAccountLabel_(true)
      .canSeeBankAccountNationalIdentifier_(true)
      .canSeeBankAccountSwift_bic_(true)
      .canSeeBankAccountIban_(true)
      .canSeeBankAccountNumber_(true)
      .canSeeBankAccountBankName_(true)
      .canSeeBankAccountBankPermalink_(true)
      .canSeeOtherAccountNationalIdentifier_(true)
      .canSeeOtherAccountSWIFT_BIC_(true)
      .canSeeOtherAccountIBAN_(true)
      .canSeeOtherAccountBankName_(true)
      .canSeeOtherAccountNumber_(true)
      .canSeeOtherAccountMetadata_(true)
      .canSeeOtherAccountKind_(true)
      .canSeeMoreInfo_(true)
      .canSeeUrl_(true)
      .canSeeImageUrl_(true)
      .canSeeOpenCorporatesUrl_(true)
      .canSeeCorporateLocation_(true)
      .canSeePhysicalLocation_(true)
      .canSeePublicAlias_(true)
      .canSeePrivateAlias_(true)
      .canAddMoreInfo_(true)
      .canAddURL_(true)
      .canAddImageURL_(true)
      .canAddOpenCorporatesUrl_(true)
      .canAddCorporateLocation_(true)
      .canAddPhysicalLocation_(true)
      .canAddPublicAlias_(true)
      .canAddPrivateAlias_(true)
      .canDeleteCorporateLocation_(true)
      .canDeletePhysicalLocation_(true)
      .canEditOwnerComment_(true)
      .canAddComment_(true)
      .canDeleteComment_(true)
      .canAddTag_(true)
      .canDeleteTag_(true)
      .canAddImage_(true)
      .canDeleteImage_(true)
      .canAddWhereTag_(true)
      .canSeeWhereTag_(true)
      .canDeleteWhereTag_(true)
      .saveMe
    }

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
          logger.info(s"""got message to create account/bank:
            ${message.accountNumber} / ${message.bankIdentifier}"""
          )

          APIUser.find(
            By(APIUser.provider_, message.accountOwnerProvider),
            By(APIUser.providerId, message.accountOwnerId)
          ).map{ user => {
              logger.info("user found")

              val bank: HostedBank = createBank(message)
              val bankAccount = createAccount(message, bank, user)
              val hostedAccount =
                HostedAccount
                .create
                .accountID(bankAccount.id.toString)
                .saveMe
              val view = createOwnerView(hostedAccount)
              ViewPrivileges
              .create
              .user(user)
              .view(view)
              .save

              logger.info(s"""created account ${message.accountNumber}
                at ${message.bankIdentifier}"""
              )

              logger.info(s"""Send message to get updates for the account ${message.accountNumber} at ${message.bankIdentifier}""")
              UpdatesRequestSender.sendMsg(UpdateBankAccount(message.accountNumber, message.bankIdentifier))
            }
          }.getOrElse({
            logger.warn(s"""user ${message.accountOwnerId}
              at ${message.accountOwnerProvider} not found.
              Could not create the account with owner view"""
            )
          })
        }
      }
    }
  def startListen = {
    amqp ! AMQPAddListener(createBankAccountListener)
  }
  }
}