package code.api.util

sealed trait ApiTrigger{
  override def toString() = getClass().getSimpleName
}

object ApiTrigger {

  case class OnBalanceChange() extends ApiTrigger
  lazy val onBalanceChange = OnBalanceChange()

  case class OnCreditTransaction() extends ApiTrigger
  lazy val onCreditTransaction = OnCreditTransaction()

  case class OnDebitTransaction() extends ApiTrigger
  lazy val onDebitTransaction = OnDebitTransaction()

  private val triggers = onBalanceChange :: onCreditTransaction :: onDebitTransaction :: Nil

  lazy val triggersMappedToClasses = triggers.map(_.getClass)

  def valueOf(value: String): ApiTrigger = {
    triggers.filter(_.toString == value) match {
      case x :: Nil => x // We find exactly one Trigger
      case x :: _ => throw new Exception("Duplicated trigger: " + x) // We find more than one Trigger
      case _ => throw new IllegalArgumentException("Incorrect ApiTrigger value: " + value) // There is no Trigger
    }
  }

  def availableTriggers: List[String] = triggers.map(_.toString)

}

