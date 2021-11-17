package code.setup

import net.liftweb.util.Props
import org.apache.commons.lang3.reflect.FieldUtils
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}


/**
 * Any unit test that extends this trait, have a chance to set new Props value,
 * after each test rollback original Props values
 */
trait PropsProgrammatically {

  private def getLockedProviders = {
    FieldUtils.readDeclaredField(Props, "net$liftweb$util$Props$$lockedProviders", true)
      .asInstanceOf[List[Map[String, String]]]
  }

  def setPropsValues(keyValues: (String, String)*): Unit = {
    val newLockedProviders = keyValues.toMap :: getLockedProviders
    FieldUtils.writeDeclaredField(Props, "net$liftweb$util$Props$$lockedProviders", newLockedProviders,  true)
  }
}
