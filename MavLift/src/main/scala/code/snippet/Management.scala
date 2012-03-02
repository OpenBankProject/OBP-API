package code.snippet
import net.liftweb.json.JsonAST._
import code.model.Account
import net.liftweb.util.Helpers._
import code.model.OtherAccount
import scala.xml.NodeSeq
import scala.xml.Text
import net.liftweb.http.SHtml
import net.liftweb.http.js.JsCmds.Noop

class Management {

  def listAll(xhtml: NodeSeq) : NodeSeq  = {
    
    //temporary way to retrieve the account
    val accJObj = JObject(List(JField("holder", JString("Music Pictures Limited"))))
    val currentAccount = Account.find(accJObj)
    
    val otherAccounts = currentAccount.map(a => a.otherAccounts.get) getOrElse List[OtherAccount]()
    
    def editableMoreInfo(otherAcc : OtherAccount) = {
      var currentValue = otherAcc.moreInfo
      
      def saveValue() = {
        val newOtherAcc = otherAcc.copy(moreInfo = currentValue)
        val newOtherAccs = otherAccounts ++ List(newOtherAcc) -- List(otherAcc)
        if(currentAccount.isDefined) currentAccount.get.otherAccounts(newOtherAccs).save
      }
      
      SHtml.ajaxEditable(Text(currentValue), SHtml.text(currentValue, currentValue = _), () =>{
        saveValue()
        Noop
      })
    }
    
    def editableUrl(otherAcc : OtherAccount) = {
      var currentValue = otherAcc.url
      
      def saveValue() = {
        val newOtherAcc = otherAcc.copy(url = currentValue)
        val newOtherAccs = otherAccounts ++ List(newOtherAcc) -- List(otherAcc)
        if(currentAccount.isDefined) currentAccount.get.otherAccounts(newOtherAccs).save
      }
      
      SHtml.ajaxEditable(Text(currentValue), SHtml.text(currentValue, currentValue = _), () =>{
        saveValue()
        Noop
      })
    }
    
    def editableImageUrl(otherAcc : OtherAccount) = {
      var currentValue = otherAcc.imageUrl
      
      def saveValue() = {
        val newOtherAcc = otherAcc.copy(imageUrl = currentValue)
        val newOtherAccs = otherAccounts ++ List(newOtherAcc) -- List(otherAcc)
        if(currentAccount.isDefined) currentAccount.get.otherAccounts(newOtherAccs).save
      }
      
      SHtml.ajaxEditable(Text(currentValue), SHtml.text(currentValue, currentValue = _), () =>{
        saveValue()
        Noop
      })
    }
    
    def editablePrivateAlias(otherAcc : OtherAccount) = {
      var currentValue = otherAcc.privateAlias
      
      def saveValue() = {
        val newOtherAcc = otherAcc.copy(privateAlias = currentValue)
        val newOtherAccs = otherAccounts ++ List(newOtherAcc) -- List(otherAcc)
        if(currentAccount.isDefined) currentAccount.get.otherAccounts(newOtherAccs).save
      }
      
      SHtml.ajaxEditable(Text(currentValue), SHtml.text(currentValue, currentValue = _), () =>{
        saveValue()
        Noop
      })
    }
    
    def editablePublicAlias(otherAcc : OtherAccount) = {
      var currentValue = otherAcc.publicAlias
      
      def saveValue() = {
        val newOtherAcc = otherAcc.copy(publicAlias = currentValue)
        val newOtherAccs = otherAccounts ++ List(newOtherAcc) -- List(otherAcc)
        if(currentAccount.isDefined) currentAccount.get.otherAccounts(newOtherAccs).save
      }
      
      SHtml.ajaxEditable(Text(currentValue), SHtml.text(currentValue, currentValue = _), () =>{
        saveValue()
        Noop
      })
    }
    
    otherAccounts.flatMap(other => {
      (".image *" #> <img class="logo" width="50" height="50" src={other.imageUrl} /> &
       ".real_name *" #> Text(other.holder) &
       ".public_alias_name *" #> editablePublicAlias(other) &
       ".private_alias_name *" #> editablePrivateAlias(other) &
       ".more_info *" #> editableMoreInfo(other) &
       ".website_url *" #> <a href={other.url}>{other.url}</a> ).apply(xhtml)
    })
    
  }
  
}