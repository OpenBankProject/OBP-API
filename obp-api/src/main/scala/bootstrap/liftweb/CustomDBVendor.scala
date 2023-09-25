package bootstrap.liftweb

import code.api.util.APIUtil
import java.sql.{Connection, DriverManager}

import net.liftweb.common.{Box, Failure, Full, Logger}
import net.liftweb.db.ConnectionManager
import net.liftweb.util.ConnectionIdentifier
import net.liftweb.util.Helpers.tryo

/**
 * The standard DB vendor.
 *
 * @param driverName the name of the database driver
 * @param dbUrl the URL for the JDBC data connection
 * @param dbUser the optional username
 * @param dbPassword the optional db password
 */
class CustomDBVendor(driverName: String,
                     dbUrl: String,
                     dbUser: Box[String],
                     dbPassword: Box[String]) extends CustomProtoDBVendor {

  private val logger = Logger(classOf[CustomDBVendor])

  def createOne: Box[Connection] =  {
    tryo{t:Throwable => logger.error("Cannot load database driver: %s".format(driverName), t)}{Class.forName(driverName);()}

    (dbUser, dbPassword) match {
      case (Full(user), Full(pwd)) =>
        tryo{t:Throwable => logger.error("Unable to get database connection. url=%s, user=%s".format(dbUrl, user),t)}(DriverManager.getConnection(dbUrl, user, pwd))
      case _ =>
        tryo{t:Throwable => logger.error("Unable to get database connection. url=%s".format(dbUrl),t)}(DriverManager.getConnection(dbUrl))
    }
  }
}

trait CustomProtoDBVendor extends ConnectionManager {
  private val logger = Logger(classOf[CustomProtoDBVendor])
  private var freePool: List[Connection] = Nil // no process use the connections, they are available for use
  private var usedPool: List[Connection] = Nil // connections are already used, not available for use
//  private var totalConnectionsCount = 0
//  private var tempMaxSize = maxPoolSize

  /**
   * Override and set to false if the maximum freePool size can temporarily be expanded to avoid freePool starvation
   */
  protected def allowTemporaryPoolExpansion = false

  /**
   *  Override this method if you want something other than 10 connections in the freePool and usedPool
   *  freePool.size + usedPool.size <=10
   */
  val dbMaxPoolSize = APIUtil.getPropsAsIntValue("db.maxPoolSize",30)
  protected def maxPoolSize = dbMaxPoolSize

  /**
   * The absolute maximum that this freePool can extend to
   * The default is 40.  Override this method to change.
   */
  protected def doNotExpandBeyond = 40

  /**
   * The logic for whether we can expand the freePool beyond the current size.  By
   * default, the logic tests allowTemporaryPoolExpansion &amp;&amp; totalConnectionsCount &lt;= doNotExpandBeyond
   */
//  protected def canExpand_? : Boolean = allowTemporaryPoolExpansion && totalConnectionsCount <= doNotExpandBeyond

  /**
   *   How is a connection created?
   */
  def createOne: Box[Connection]

  /**
   * Test the connection.  By default, setAutoCommit(false),
   * but you can do a real query on your RDBMS to see if the connection is alive
   */
  protected def testConnection(conn: Connection) {
    conn.setAutoCommit(false)
  }

  // Tail Recursive function in order to avoid Stack Overflow
  // PLEASE NOTE: Changing this function you can break the above named feature
  def newConnection(name: ConnectionIdentifier): Box[Connection] = {
    val (connection: Box[Connection], needRecursiveAgain: Boolean) = commonPart(name)
    needRecursiveAgain match {
      case true => newConnection(name)
      case false => connection
    }
  }


  def commonPart(name: ConnectionIdentifier): (Box[Connection], Boolean) =
    synchronized {
      freePool match {
        case Nil if (freePool.size + usedPool.size) < maxPoolSize =>{
          val ret = createOne // get oneConnection from JDBC, not in the freePool yet, we add ot the Pool when we release it .
          try {
            ret.head.setAutoCommit(false) // we test the connection status, if it is success, we return it back.
            usedPool = ret.head :: usedPool
            logger.trace(s"Created connection is good, detail is $ret ")
          } catch {
            case e: Exception =>
              logger.trace(s"Created connection is bad, detail is $e")
          }
         
          //Note: we may return the invalid connection
          (ret, false)
        }

        case Nil => //freePool is empty and we are at maxPoolSize limit 
          wait(50L)
          logger.error(s"The (freePool.size + usedPool.size) is at the limit ($maxPoolSize) and there are no free connections.")
          (
            Failure(s"The (freePool.size + usedPool.size) is at the limit ($maxPoolSize) and there are no free connections."),
            false
          )

        case freeHead :: freeTail =>//if freePool is not empty, we just get connection from freePool, no need to create new connection from JDBC.
          logger.trace("Found connection in freePool, name=%s freePool size =%s".format(name, freePool.size))
          
          freePool = freeTail // remove the head from freePool
          //TODO check if we need add head or tail
          usedPool = freeHead :: usedPool // we added connection to usedPool
          
          try {
            this.testConnection(freeHead) // we test the connection status, if it is success, we return it back.
            (Full(freeHead),false)
          } catch {
            case e: Exception => try {
              logger.error(s"testConnection failed, try to close it and call newConnection(name), detail is $e")
              tryo(freeHead.close) // call JDBC to close this connection
              (
                Failure(s"testConnection failed, try to close it and call newConnection(name), detail is $e"),
                true
              )
            } catch {
              case e: Exception =>{
                logger.error(s"could not close connection and call newConnection(name), detail is $e")
                (
                  Failure(s"could not close connection and call newConnection(name), detail is $e"),
                  true
                )
              }
            }
          }
      }
    }

  def releaseConnection(conn: Connection): Unit = synchronized {
    usedPool = usedPool.filterNot(_ ==conn)
    logger.trace(s"Released connection. removed connection from usedPool size is ${usedPool.size}")
    //TODO check if we need add head or tail
    freePool = conn :: freePool
    logger.trace(s"Released connection. added connection to freePool size is ${freePool.size}")
    notifyAll
  }

  def closeAllConnections_!(): Unit = _closeAllConnections_!(0)


  private def _closeAllConnections_!(cnt: Int): Unit = synchronized {
    logger.trace(s"Closing all connections, try the $cnt time")
    if (cnt > 10) ()//we only try this 10 times,
    else {
      freePool.foreach {c => tryo(c.close);}
      usedPool.foreach {c => tryo(c.close);}
      freePool = Nil
      usedPool = Nil

      if (usedPool.length > 0 || freePool.length > 0) wait(250)

      _closeAllConnections_!(cnt + 1)
    }
  }


  //This is only for debugging 
  def logAllConnectionsStatus = {
    logger.trace(s"Hello from logAllConnectionsStatus: usedPool.size is ${usedPool.length}, freePool.size is ${freePool.length}")
    for {
      usedConnection <- usedPool
    } yield {
      logger.trace(s"usedConnection (${usedConnection.toString}): isClosed-${usedConnection.isClosed}, getWarnings-${usedConnection.getWarnings}")
    }
    for {
      freeConnection <- freePool
    } yield {
      logger.trace(s"freeConnection (${freeConnection.toString}): isClosed-${freeConnection.isClosed}, getWarnings-${freeConnection.getWarnings}")
    }
  }
}
