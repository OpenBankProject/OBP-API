/**
Open Bank Project - API
Copyright (C) 2011-2016, TESOBE Ltd

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
* the message to be sent in message queue
* so that the transactions of the bank account
* get refreshed in the database
*/
package com.tesobe.model{
  case class UpdateBankAccount(
    val accountNumber : String,
    val bankNationalIdentifier : String
  )
}

package code.model.dataAccess {

  import com.rabbitmq.client.{ConnectionFactory,Channel}
  import net.liftmodules.amqp.{AMQPSender,AMQPMessage}
  import net.liftweb.util._
  import net.liftweb.common.Loggable
  import com.tesobe.model.UpdateBankAccount



  object UpdatesRequestSender extends Loggable {
    private val factory = new ConnectionFactory {
      import ConnectionFactory._
      setHost(Props.get("connection.host", "localhost"))
      setPort(DEFAULT_AMQP_PORT)
      setUsername(Props.get("connection.user", DEFAULT_USER))
      setPassword(Props.get("connection.password", DEFAULT_PASS))
      setVirtualHost(DEFAULT_VHOST)
    }

    private val amqp = new UpdateRequestsAMQPSender(factory, "directExchange3", "transactions")


    def sendMsg(message: UpdateBankAccount) = {
      logger.info(s"""Send message to get updates for the account ${message.accountNumber} at ${message.bankNationalIdentifier}""")
      amqp ! AMQPMessage(message)
    }
  }

  class UpdateRequestsAMQPSender(cf: ConnectionFactory, exchange: String, routingKey: String)
   extends AMQPSender[UpdateBankAccount](cf, exchange, routingKey) {
    override def configure(channel: Channel) = {
      val conn = cf.newConnection()
      val channel = conn.createChannel()
      channel
    }
  }
}