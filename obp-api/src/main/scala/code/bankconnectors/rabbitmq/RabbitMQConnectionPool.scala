package code.bankconnectors.rabbitmq



import code.api.util.APIUtil
import com.rabbitmq.client.{Connection, ConnectionFactory}
import org.apache.commons.pool2.impl.{GenericObjectPool, GenericObjectPoolConfig}
import org.apache.commons.pool2.BasePooledObjectFactory
import org.apache.commons.pool2.PooledObject
import org.apache.commons.pool2.impl.DefaultPooledObject

// Factory to create RabbitMQ connections
class RabbitMQConnectionFactory extends BasePooledObjectFactory[Connection] {

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
object RabbitMQConnectionPool {
  private val poolConfig = new GenericObjectPoolConfig()
  poolConfig.setMaxTotal(5)           // Maximum number of connections
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

object RabbitMQConnectionPoolTest extends App {
  // Initialize the RabbitMQ connection pool

  // Function to delete a queue
  def deleteQueue(queueName: String): Unit = {
    // Borrow a connection from the pool
    val connection = RabbitMQConnectionPool.borrowConnection()
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
      RabbitMQConnectionPool.returnConnection(connection)
    }
  }

  // Example: Deleting the queue 'replyQueue'
  deleteQueue("replyQueue")
}

