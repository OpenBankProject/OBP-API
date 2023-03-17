package code.api.util

import code.api.Constant

object DBUtil {
  def getDbConnectionParameters: (String, String, String) = {
    val dbUrl = APIUtil.getPropsValue("db.url") openOr Constant.h2DatabaseDefaultUrlValue
    val username = dbUrl.split(";").filter(_.contains("user")).toList.headOption.map(_.split("=")(1))
    val password = dbUrl.split(";").filter(_.contains("password")).toList.headOption.map(_.split("=")(1))
    val dbUser = APIUtil.getPropsValue("db.user").orElse(username)
    val dbPassword = APIUtil.getPropsValue("db.password").orElse(password)
    (dbUrl, dbUser.getOrElse(""), dbPassword.getOrElse(""))
  }
}
