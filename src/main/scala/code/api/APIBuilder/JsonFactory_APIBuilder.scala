package code.api.APIBuilder
import code.api.util.APIUtil
case class Books(author: String = """Chinua Achebe""", pages: Int = 209, points: Double = 1.3)
case class RootInterface(books: List[Books] = List(Books()))
object JsonFactory_APIBuilder {
  val rootInterface = RootInterface()
  val allFields = for (v <- this.getClass.getDeclaredFields; if APIUtil.notExstingBaseClass(v.getName())) yield {
    v.setAccessible(true)
    v.get(this)
  }
}