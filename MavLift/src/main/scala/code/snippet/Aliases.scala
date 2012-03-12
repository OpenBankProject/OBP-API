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
    
    def publicAliases = {
      val alis = for{
	      acc <- currentAccount
	      aliases <- Some(acc.publicAliases.get)
	  } yield aliases
	  
	  alis.getOrElse(List()).sort(orderByRealValueAtoZ)
    }
    
    def privateAliases = {
      val alis = for{
	      acc <- currentAccount
	      aliases <- Some(acc.privateAliases.get)
	  } yield aliases
	  
	  alis.getOrElse(List()).sort(orderByRealValueAtoZ)
    }
    
    def editablePublicAlias(alias: Alias) = {
      var alVal = alias.aliasValue
      
      def setPublicAliasValue(a: Alias, newValue: String) = {
        val oldAl = publicAliases.find(al => al match{
          case Alias(a.realValue, _) => true
          case _ => false
        }) getOrElse Alias(a.realValue,"")
        
        val newAliases = publicAliases ++ List(Alias(a.realValue, newValue)) -- List(oldAl)
        currentAccount match{
          case Full(a) => a.publicAliases(newAliases).save
          case _ => println("error retrieving current account")
        }
      }
      
      SHtml.ajaxEditable(Text(alVal), SHtml.text(alVal, alVal = _), () =>{
        setPublicAliasValue(alias, alVal)
        Noop
      })
    }
    
    def editablePrivateAlias(alias: Alias) = {
      var alVal = alias.aliasValue
      
      def setPrivateAliasValue(a: Alias, newValue: String) = {
        val oldAl = privateAliases.find(al => al match{
          case Alias(a.realValue, _) => true
          case _ => false
        }) getOrElse Alias(a.realValue,"")
        
        val newAliases = privateAliases ++ List(Alias(a.realValue, newValue)) -- List(oldAl)
        currentAccount match{
          case Full(a) => a.privateAliases(newAliases).save
          case _ => println("error retrieving current account")
        }
      }
      
      SHtml.ajaxEditable(Text(alVal), SHtml.text(alVal, alVal = _), () =>{
        setPrivateAliasValue(alias, alVal)
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