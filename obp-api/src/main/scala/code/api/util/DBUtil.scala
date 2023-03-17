package code.api.util

import code.api.Constant

object DBUtil {
  def getDbConnectionParameters: (String, String, String) = {
    val dbUrl = APIUtil.getPropsValue("db.url") openOr Constant.h2DatabaseDefaultUrlValue
    val usernameAndPassword = dbUrl.split("\\?").filter(_.contains("user")).mkString
    val username = usernameAndPassword.split("&").filter(_.contains("user")).mkString.split("=")(1)
    val password = usernameAndPassword.split("&").filter(_.contains("password")).mkString.split("=")(1)
    val dbUser = APIUtil.getPropsValue("db.user").getOrElse(username)
    val dbPassword = APIUtil.getPropsValue("db.password").getOrElse(password)
    (dbUrl, dbUser, dbPassword)
  }
}
