import com.rabbitmq.client.{Channel, Connection, ConnectionFactory}
import org.apache.commons.pool2.impl.{GenericObjectPool, GenericObjectPoolConfig}
import org.apache.commons.pool2.{BasePooledObjectFactory, PooledObject}
import org.apache.commons.pool2.impl.DefaultPooledObject

// Factory for creating and managing RabbitMQ Channel objects
class RabbitChannelFactory(connection: Connection) extends BasePooledObjectFactory[Channel] {
  // Create a new Channel
  override def create(): Channel = connection.createChannel()

  // Wrap the Channel in a pooled object
  override def wrap(channel: Channel): PooledObject[Channel] = new DefaultPooledObject(channel)

  // Destroy the Channel when no longer in use
  override def destroyObject(pooledObject: PooledObject[Channel]): Unit = {
    val channel = pooledObject.getObject
    if (channel.isOpen) channel.close()
  }

  // Validate if the Channel is still usable
  override def validateObject(pooledObject: PooledObject[Channel]): Boolean = pooledObject.getObject.isOpen
}

// Wrapper class for the Channel pool using Apache Commons Pool
class RabbitMQChannelPool(connectionFactory: ConnectionFactory, poolSize: Int) {
  private val connection: Connection = connectionFactory.newConnection()

  // Pool configuration settings
  private val poolConfig = new GenericObjectPoolConfig()
  poolConfig.setMaxTotal(poolSize)
  poolConfig.setMinIdle(2)

  // Create the pool with the Channel factory
  private val pool = new GenericObjectPool[Channel](new RabbitChannelFactory(connection), poolConfig)

  // Borrow a Channel from the pool
  def borrowChannel(): Channel = pool.borrowObject()

  // Return the Channel to the pool
  def returnChannel(channel: Channel): Unit = pool.returnObject(channel)

  // Close the pool and the RabbitMQ connection
  def close(): Unit = {
    pool.close()
    if (connection.isOpen) connection.close()
  }
}

// Example usage of the RabbitMQ Channel pool
object RabbitMQChannelPoolApp extends App {
  val factory = new ConnectionFactory()
  factory.setHost("localhost")

  // Create a Channel pool with a max size of 10
  val channelPool = new RabbitMQChannelPool(factory, 10)

  try {
    // Borrow a Channel from the pool
    val channel = channelPool.borrowChannel()

    // Use the Channel to declare a queue and publish a message
    channel.queueDeclare("testQueue", false, false, false, null)
    val message = "Hello, RabbitMQ with Apache Commons Pool!"
    channel.basicPublish("", "testQueue", null, message.getBytes())
    println(s" [x] Sent '$message'")

    // Return the Channel to the pool after use
    channelPool.returnChannel(channel)
  } finally {
    // Clean up by closing the pool and the connection
    channelPool.close()
  }
}


