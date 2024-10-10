package code.bankconnectors.rabbitmq
import code.api.util.APIUtil
import com.rabbitmq.client.{Channel, Connection, ConnectionFactory}
import com.rabbitmq.client.{Connection, ConnectionFactory}
import org.apache.commons.pool2.impl.{GenericObjectPool, GenericObjectPoolConfig}
import org.apache.commons.pool2.{BasePooledObjectFactory, PooledObject}
import org.apache.commons.pool2.impl.DefaultPooledObject

// Factory for creating RabbitMQ Connection objects
class RabbitConnectionFactory(connectionFactory: ConnectionFactory) extends BasePooledObjectFactory[Connection] {
  // Create a new Connection
  override def create(): Connection = connectionFactory.newConnection()

  // Wrap the Connection in a pooled object
  override def wrap(connection: Connection): PooledObject[Connection] = new DefaultPooledObject(connection)

  // Destroy the connection when no longer in use
  override def destroyObject(pooledObject: PooledObject[Connection]): Unit = {
    val connection = pooledObject.getObject
    if (connection.isOpen) connection.close()
  }

  // Validate if the connection is still usable
  override def validateObject(pooledObject: PooledObject[Connection]): Boolean = pooledObject.getObject.isOpen
}

// Factory for creating RabbitMQ Channel objects
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

object RabbitMQConnectionPool2{

  // lazy initial RabbitMQ connection
  val host = APIUtil.getPropsValue("rabbitmq_connector.host").openOrThrowException("mandatory property rabbitmq_connector.host is missing!")
  val port = APIUtil.getPropsAsIntValue("rabbitmq_connector.port").openOrThrowException("mandatory property rabbitmq_connector.port is missing!")
  val username = APIUtil.getPropsValue("rabbitmq_connector.username").openOrThrowException("mandatory property rabbitmq_connector.username is missing!")
  val password = APIUtil.getPropsValue("rabbitmq_connector.password").openOrThrowException("mandatory property rabbitmq_connector.password is missing!")

  private val factory = new ConnectionFactory()
  factory.setHost(host)
  factory.setPort(port)
  factory.setUsername(username)
  factory.setPassword(password)
  
  val maxConnections: Int = 2
  val maxChannelsPerConnection: Int = 2
  
  // Pool configuration for connections
  private val connectionPoolConfig = new GenericObjectPoolConfig()
  connectionPoolConfig.setMaxTotal(maxConnections)
  private val connectionPool = new GenericObjectPool[Connection](new RabbitConnectionFactory(factory), connectionPoolConfig)

  // Pool configuration for channels (per connection)
  private val channelPoolConfig = new GenericObjectPoolConfig()
  channelPoolConfig.setMaxTotal(maxChannelsPerConnection)

  // Map to hold the channel pools for each connection
  private val channelPools = collection.mutable.Map[String, GenericObjectPool[Channel]]()
  

  // Get or create a channel pool for a connection
  private def getOrCreateChannelPool(connection: Connection): GenericObjectPool[Channel] = {
    channelPools.getOrElseUpdate(connection.toString, new GenericObjectPool[Channel](new RabbitChannelFactory(connection), channelPoolConfig))
  }

  // Borrow a connection from the pool
  def borrowConnection(): Connection = connectionPool.borrowObject()

  // Return a connection to the pool
  def returnConnection(connection: Connection): Unit = connectionPool.returnObject(connection)

  // Borrow a channel from the pool
  def borrowChannel(connection: Connection): Channel = {
    val channelPool = getOrCreateChannelPool(connection)
    channelPool.borrowObject()
  }

  // Return a channel to the pool with validation
  def returnChannel(channel: Channel): Unit = {
    val connection = channel.getConnection
    val channelPool = getOrCreateChannelPool(connection)

    // Check if the channel is still valid before returning
    if (channel.isOpen) {
      channelPool.returnObject(channel)
    } else {
      // Handle closed channel (e.g., log a warning)
      println("Warning: Attempted to return a closed channel.")
    }
  }

  // Close all resources
  def close(): Unit = {
    connectionPool.close()
  }
}


object RabbitMQConnectionPoolApp extends App {
  try {
    // Borrow a channel from the pool
    val connection = RabbitMQConnectionPool2.borrowConnection()
    val channel = RabbitMQConnectionPool2.borrowChannel(connection)

    // Use the channel (e.g., declare a queue and publish a message)
    channel.queueDeclare("testQueue", false, false, false, null)
    val message = "Hello, RabbitMQ with Connection and Channel Pool!"
    channel.basicPublish("", "testQueue", null, message.getBytes())
    println(s" [x] Sent '$message'")

    // Return the channel to the pool

    RabbitMQConnectionPool2.returnChannel(channel)
    RabbitMQConnectionPool2.returnConnection(connection)
  } finally {
    // Clean up by closing the pool and all resources
//    connectionChannelPool.close()
  }
}
