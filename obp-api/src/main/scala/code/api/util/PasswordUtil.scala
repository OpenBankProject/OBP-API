package code.api.util

import com.nulabinc.zxcvbn.Zxcvbn
import com.nulabinc.zxcvbn.Strength

object PasswordUtil {

  private val zxcvbn = new Zxcvbn()

  /** Check password strength score: 0 (very weak) to 4 (very strong) */
  def getStrength(password: String): Strength = {
    zxcvbn.measure(password)
  }

  /** Recommend minimum score of 3 (strong) */
  def isAcceptable(password: String, minScore: Int = 3): Boolean = {
    getStrength(password).getScore >= minScore
  }

}

