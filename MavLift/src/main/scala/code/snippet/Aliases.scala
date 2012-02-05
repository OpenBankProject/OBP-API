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
    
    def orderByRealValueAtoZ = (a1: Alias, a2: Alias) => {
     a1.realValue < a2.realValue
    } 
    
    val publicAliases = {
      val alis = for{
	      acc <- currentAccount
	      aliases <- Some(acc.publicAliases.get)
	  } yield aliases
	  
	  alis.getOrElse(List()).sort(orderByRealValueAtoZ)
    }
    
    val privateAliases = {
      val alis = for{
	      acc <- currentAccount
	      aliases <- Some(acc.privateAliases.get)
	  } yield aliases
	  
	  alis.getOrElse(List()).sort(orderByRealValueAtoZ)
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
      
      SHtml.ajaxEditable(Text(alVal), SHtml.text(alVal, alVal = _), () =>{
        setPublicAliasValue(alVal)
        Noop
      })
    }
    
    def editablePrivateAlias(alias: Alias) = {
      var alVal = alias.aliasValue
      
      def setPrivateAliasValue(newValue: String) = {
        val newAliases = privateAliases ++ List(Alias(alias.realValue, newValue)) -- List(Alias(alias.realValue, alias.aliasValue))
        currentAccount match{
          case Full(a) => a.privateAliases(newAliases).save
          case _ => println("error retrieving current account")
        }
      }
      
      SHtml.ajaxEditable(Text(alVal), SHtml.text(alVal, alVal = _), () =>{
        setPrivateAliasValue(alVal)
        Noop
      })
    }
    
    publicAliases.flatMap(alias => {
        (".real_name *" #> alias.realValue &
    	".private_alias_name *" #> editablePrivateAlias(privateAliases.find(a => {
    	  if(a.realValue.equals(alias.realValue)) true
    	  else false
    	}).getOrElse(Alias(alias.realValue, ""))) &
    	".public_alias_name *" #> editablePublicAlias(alias)).apply(xhtml)
    })
  }
  
}