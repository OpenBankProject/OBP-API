package code.snippet
import code.model.traits.{Bank}
import net.liftweb.http.{PaginatorSnippet, StatefulSnippet}
import java.text.SimpleDateFormat
import net.liftweb.http._
import java.util.Calendar
import code.model.dataAccess.{OBPTransaction,OBPEnvelope,OBPAccount, OtherAccount, PostBankLocalStorage}
import xml.NodeSeq
import com.mongodb.QueryBuilder
import net.liftweb.mongodb.Limit._
import net.liftweb.mongodb.Skip._
import net.liftweb.util.Helpers._
import net.liftweb.util._
import scala.xml.Text
import net.liftweb.http.js.JsCmds.Noop
class SingleBankInformations(bank : Bank)
{
	def name ={ 
		"#name * " #> bank.name
	}
}
