package deletion

import net.liftweb.db.DB
import net.liftweb.util.DefaultConnectionIdentifier

object DeletionUtil {
  def databaseAtomicTask[R](blockOfCode: => R): R = {
    DB.use(DefaultConnectionIdentifier){_ =>
      blockOfCode
    }
  }
}
