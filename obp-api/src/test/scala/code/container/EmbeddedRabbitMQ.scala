/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

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

package code.container

import code.api.v5_0_0.V500ServerSetup
import code.setup.DefaultUsers
import com.rabbitmq.client.{Channel, Connection, ConnectionFactory}
import org.scalatest.Ignore
import org.testcontainers.containers.RabbitMQContainer

@Ignore
class EmbeddedRabbitMQ extends V500ServerSetup with DefaultUsers {

  val rabbitMQContainer = new RabbitMQContainer("rabbitmq:3.7.25-management-alpine")
  // It registers a shutdown hook, which is a block of code (or function) that runs when the application terminates,
  // - either normally(e.g., when the main method completes)
  // - or due to an external signal(e.g., Ctrl + C or termination by the operating system).
  sys.addShutdownHook {
    rabbitMQContainer.stop()
  }
  override def beforeAll(): Unit = {
    super.beforeAll()
    // Start RabbitMQ container
    rabbitMQContainer.start()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    rabbitMQContainer.stop()
  }

  feature(s"test EmbeddedRabbitMQ") {
    scenario("Publish and Consume Message") {

      val rabbitHost = rabbitMQContainer.getHost
      val rabbitPort = rabbitMQContainer.getAmqpPort

      // Set up RabbitMQ connection
      val factory = new ConnectionFactory()
      factory.setHost(rabbitHost)
      factory.setPort(rabbitPort)

      val connection: Connection = factory.newConnection()
      val channel: Channel = connection.createChannel()

      // Declare a queue
      val queueName = "test-queue"
      channel.queueDeclare(queueName, false, false, false, null)

      // Publish a message
      val message = "Hello, RabbitMQ!"
      channel.basicPublish("", queueName, null, message.getBytes)
      println(s"Published message: $message")

      // Consume the message
      val delivery = channel.basicGet(queueName, true)
      val consumedMessage = new String(delivery.getBody)

      println(s"Consumed message: $consumedMessage")
      consumedMessage shouldBe message

      // Clean up
      channel.close()
      connection.close()

    }
  }

}