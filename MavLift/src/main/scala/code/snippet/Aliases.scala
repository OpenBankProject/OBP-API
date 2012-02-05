package code.snippet

import net.liftweb.util.Helpers._
import scala.xml.NodeSeq
import code.model.Account
import net.liftweb.json.JsonAST.JObject
import net.liftweb.json.JsonAST.JField
import net.liftweb.json.JsonAST.JString
import net.liftweb.http.SHtml
import code.model.Alias
import scala.xml.Text
import net.liftweb.common.{Box, Full, Empty}
import net.liftweb.http.js.JsCmds.Noop
import net.liftweb.http.StatefulSnippet

class Aliases {

  def listAll(xhtml: NodeSeq) : NodeSeq = {
    
    //temporary way to retrieve the account
    val accJObj = JObject(List(JField("holder", JString("Music Pictures Limited"))))
    val currentAccount = Account.find(accJObj)
    
    val publicAliases = {
      val alis = for{
	      acc <- currentAccount
	      aliases <- Some(acc.publicAliases.get)
	  } yield aliases
	  
	  alis.getOrElse(List())
    }
    
    def editablePublicAlias(alias: Alias) = {
      var alVal = alias.aliasValue
      
      def setPublicAliasValue(newValue: String) = {
        val newAliases = publicAliases ++ List(Alias(alias.realValue, newValue)) -- List(Alias(alias.realValue, alias.aliasValue))
        currentAccount match{
          case Full(a) => a.publicAliases(newAliases).save
          case _ => println("error retrieving current account")
        }
      }
      
      //TODO: How to reload the page on edit?
      SHtml.ajaxEditable(Text(alVal), SHtml.text(alVal, alVal = _), () =>{
        setPublicAliasValue(alVal)
        Noop
      })
    }
    
    publicAliases.flatMap(alias => {
        (".real_name *" #> alias.realValue &
    	".private_alias_name *" #> "Boooooooop" &
    	".public_alias_name *" #> editablePublicAlias(alias)).apply(xhtml)
    })
  }
  
}