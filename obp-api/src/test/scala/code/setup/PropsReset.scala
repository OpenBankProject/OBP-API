package code.setup

import net.liftweb.util.Props
import org.apache.commons.lang3.reflect.FieldUtils
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}


/**
 * Any unit test that extends this trait, have a chance to set new Props value,
 * after each test rollback original Props values
 */
trait PropsReset extends BeforeAndAfterAll with BeforeAndAfterEach {
  this: Suite =>

  override def afterEach(): Unit = {
    super.afterEach()
    resetLockedProviders()
  }
  override def afterAll(): Unit = {
    super.afterEach()
    resetLockedProviders()
  }

  private val lockedProviders: List[Map[String, String]] = getLockedProviders

  private def getLockedProviders = {
    FieldUtils.readDeclaredField(Props, "net$liftweb$util$Props$$lockedProviders", true)
      .asInstanceOf[List[Map[String, String]]]
  }

  private def resetLockedProviders(): Unit = {
    FieldUtils.writeDeclaredField(Props, "net$liftweb$util$Props$$lockedProviders", lockedProviders,  true)
  }

  def setPropsValues(keyValues: (String, String)*): Unit = {
    val newLockedProviders = keyValues.toMap :: getLockedProviders
    FieldUtils.writeDeclaredField(Props, "net$liftweb$util$Props$$lockedProviders", newLockedProviders,  true)
  }
}
