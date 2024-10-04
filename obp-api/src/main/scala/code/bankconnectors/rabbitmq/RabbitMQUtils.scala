package code.bankconnectors.rabbitmq

import code.api.util.APIUtil
import code.api.util.ErrorMessages.AdapterUnknownError
import code.bankconnectors.Connector
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.TopicTrait
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.json.Serialization.write


/**
 * RabbitMQ utils.
 * The reason of extract this util: if not call RabbitMQ connector method, the db connection of RabbitMQ will not be initialized.
 */
object RabbitMQUtils extends MdcLoggable{

  private implicit val formats = code.api.util.CustomJsonFormats.nullTolerateFormats

  // lazy initial RabbitMQ connection
  val host = APIUtil.getPropsValue("rabbitmq_connector.host").openOrThrowException("mandatory property rabbitmq_connector.host is missing!")
  val port = APIUtil.getPropsAsIntValue("rabbitmq_connector.port").openOrThrowException("mandatory property rabbitmq_connector.port is missing!")
  val username = APIUtil.getPropsValue("rabbitmq_connector.username").openOrThrowException("mandatory property rabbitmq_connector.username is missing!")
  val password = APIUtil.getPropsValue("rabbitmq_connector.password").openOrThrowException("mandatory property rabbitmq_connector.password is missing!") 
  
  //TODO ,need to check if we really need the pool for it.
//    Class.forName(driver)
//    val settings = ConnectionPoolSettings(
//      initialSize = initialSize,
//      maxSize = maxSize,
//      connectionTimeoutMillis = timeoutMillis,
//      validationQuery = validationQuery,
//      connectionPoolFactoryName = poolFactoryName
//    )
//    ConnectionPool.singleton(url, user, password, settings)


  def sendRequestUndGetResponseFromRabbitMQ[T: Manifest](procedureName: String, outBound: TopicTrait): Box[T] = {
    val procedureParam: String = write(outBound) // convert OutBound to json string
    val responseJson: String ={
      var rpcClient: RabbitMQRPClient = null
      try {
        logger.debug(s"${RabbitMQConnector_vOct2024.toString} outBoundJson: $procedureName = $procedureParam")
//        rpcClient = new RabbitMQRPClient(host,port, username, password)
        rpcClient = new RabbitMQRPClient("localhost",5672, "guest", "guest")
        rpcClient.call(procedureParam)
      } catch {
        case e: Throwable =>{
          logger.debug(s"${RabbitMQConnector_vOct2024.toString} inBoundJson exception: $procedureName = ${e}")
          throw new RuntimeException(s"$AdapterUnknownError Please Check Adapter Side! Details: ${e.getMessage}")//TODO error handling to API level
        }
      } finally {
        if (rpcClient != null) {
          try {
            rpcClient.close()
          } catch {
            case e: Throwable =>{
              logger.debug(s"${RabbitMQConnector_vOct2024.toString} inBoundJson exception: $procedureName = ${e}")
              throw new RuntimeException(s"$AdapterUnknownError Please Check Adapter Side! Details: ${e.getMessage}")//TODO error handling to API Level
            }
          }
        }
      }
    }
    logger.debug(s"${RabbitMQConnector_vOct2024.toString} inBoundJson: $procedureName = $responseJson" )
    Connector.extractAdapterResponse[T](responseJson, Empty)
  }
}