package code.bankconnectors.rabbitmq



import com.rabbitmq.client.{Connection, ConnectionFactory}
import org.apache.commons.pool2.impl.{GenericObjectPool, GenericObjectPoolConfig}
import org.apache.commons.pool2.BasePooledObjectFactory
import org.apache.commons.pool2.PooledObject
import org.apache.commons.pool2.impl.DefaultPooledObject

// Factory to create RabbitMQ connections
class RabbitMQConnectionFactory extends BasePooledObjectFactory[Connection] {
  private val factory = new ConnectionFactory()
  factory.setHost("localhost")
  factory.setUsername("guest")
  factory.setPassword("guest")

  // Create a new RabbitMQ connection
  override def create(): Connection = factory.newConnection()

  // Wrap the connection in a PooledObject
  override def wrap(conn: Connection): PooledObject[Connection] = new DefaultPooledObject[Connection](conn)

  // Destroy a connection when it's no longer needed
  override def destroyObject(p: PooledObject[Connection]): Unit = {
    val connection = p.getObject
    if (connection.isOpen) {
      connection.close()
    }
  }

  // Validate the connection before using it from the pool
  override def validateObject(p: PooledObject[Connection]): Boolean = {
    val connection = p.getObject
    connection != null && connection.isOpen
  }
}

// Pool to manage RabbitMQ connections
class RabbitMQConnectionPool {
  private val poolConfig = new GenericObjectPoolConfig()
  poolConfig.setMaxTotal(10)           // Maximum number of connections
  poolConfig.setMinIdle(2)             // Minimum number of idle connections
  poolConfig.setMaxIdle(5)             // Maximum number of idle connections
  poolConfig.setMaxWaitMillis(30000)   // Wait time for obtaining a connection

  // Create the pool
  private val pool = new GenericObjectPool[Connection](new RabbitMQConnectionFactory(), poolConfig)

  // Method to borrow a connection from the pool
  def borrowConnection(): Connection = pool.borrowObject()

  // Method to return a connection to the pool
  def returnConnection(conn: Connection): Unit = pool.returnObject(conn)
}

object RabbitMQWithCommonsPool extends App {
  // Initialize the RabbitMQ connection pool
  val rabbitMQPool = new RabbitMQConnectionPool()

  // Function to delete a queue
  def deleteQueue(queueName: String): Unit = {
    // Borrow a connection from the pool
    val connection = rabbitMQPool.borrowConnection()
    val channel = connection.createChannel()

    try {
      // Delete the queue
      channel.queueDelete(queueName)
      println(s"Queue '$queueName' deleted successfully.")
    } catch {
      case e: Exception => println(s"Error deleting queue '$queueName': ${e.getMessage}")
    } finally {
      // Close the channel and return the connection to the pool
      channel.close()
      rabbitMQPool.returnConnection(connection)
    }
  }

  // Example: Deleting the queue 'replyQueue'
  deleteQueue("replyQueue")
}

