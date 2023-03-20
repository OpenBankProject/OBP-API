package code.api.util

import code.api.Constant

object DBUtil {
  def dbUrl: String = APIUtil.getPropsValue("db.url") openOr Constant.h2DatabaseDefaultUrlValue
  
  def getDbConnectionParameters: (String, String, String) = {
    dbUrl.contains("jdbc:h2") match {
      case true => getH2DbConnectionParameters
      case false => getOtherDbConnectionParameters
    }
  }
  
  private def getOtherDbConnectionParameters: (String, String, String) = {
    val usernameAndPassword = dbUrl.split("\\?").filter(_.contains("user")).mkString
    val username = usernameAndPassword.split("&").filter(_.contains("user")).mkString.split("=")(1)
    val password = usernameAndPassword.split("&").filter(_.contains("password")).mkString.split("=")(1)
    val dbUser = APIUtil.getPropsValue("db.user").getOrElse(username)
    val dbPassword = APIUtil.getPropsValue("db.password").getOrElse(password)
    (dbUrl, dbUser, dbPassword)
  }
  // H2 database has specific bd url string which is different compared to other databases
  private def getH2DbConnectionParameters: (String, String, String) = {
    val username = dbUrl.split(";").filter(_.contains("user")).toList.headOption.map(_.split("=")(1))
    val password = dbUrl.split(";").filter(_.contains("password")).toList.headOption.map(_.split("=")(1))
    val dbUser = APIUtil.getPropsValue("db.user").orElse(username)
    val dbPassword = APIUtil.getPropsValue("db.password").orElse(password)
    (dbUrl, dbUser.getOrElse(""), dbPassword.getOrElse(""))
  }
}
