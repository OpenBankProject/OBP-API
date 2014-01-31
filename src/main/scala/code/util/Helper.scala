package code.util

object Helper{

  def generatePermalink(name: String): String = {
    name.trim.toLowerCase.replace("-","").replaceAll(" +", " ").replaceAll(" ", "-")
  }
}