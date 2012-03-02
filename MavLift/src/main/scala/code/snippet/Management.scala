package code.snippet
import net.liftweb.json.JsonAST._
import code.model.Account
import net.liftweb.util.Helpers._
import code.model.OtherAccount
import scala.xml.NodeSeq
import scala.xml.Text
import net.liftweb.http.SHtml
import net.liftweb.http.js.JsCmds.Noop
import net.liftweb.common.Full
import net.liftweb.common.Empty
import net.liftweb.widgets.tablesorter.{TableSorter, DisableSorting, Sorting, Sorter}

class Management {

  val headers = (0, Sorter("text")) :: (5, DisableSorting()) :: Nil
  val sortList = (0, Sorting.DSC) :: Nil
  
  val options = TableSorter.options(headers, sortList)
  
  def tableSorter(xhtml: NodeSeq) : NodeSeq = {
    TableSorter("#other_acc_management", options)
  }
  
  def listAll(xhtml: NodeSeq) : NodeSeq  = {
    
    //temporary way to retrieve the account
    val accJObj = JObject(List(JField("holder", JString("Music Pictures Limited"))))
    val currentAccount = Account.find(accJObj) getOrElse Account.createRecord
    
    def getMostUpToDateOtherAccount(holder: String) = {
    	currentAccount.otherAccounts.get.find(o => o.holder.equals(holder))
    }
    
    def editable(initialValue: String, holder: String,  alterOtherAccount: (OtherAccount, String) => OtherAccount) = {
      var currentValue = initialValue
      
      def saveValue() = {
        val otherAcc = getMostUpToDateOtherAccount(holder)
        if(otherAcc.isDefined){
          val newOtherAcc = alterOtherAccount(otherAcc.get, currentValue)
          val newOtherAccs = currentAccount.otherAccounts.get ++ List(newOtherAcc) -- List(otherAcc.get)
          currentAccount.otherAccounts(newOtherAccs).save
        }
        
      }
      
      SHtml.ajaxEditable(Text(currentValue), SHtml.text(currentValue, currentValue = _), () =>{
        saveValue()
        Noop
      })
    }
    
    def editablePublicAlias(initialValue : String, holder: String) = {
      def alterPublicAlias = (oAccount: OtherAccount, newValue: String) => oAccount.copy(publicAlias = newValue)
      editable(initialValue, holder, alterPublicAlias)
    }
    
    def editablePrivateAlias(initialValue : String, holder: String) = {
      def alterPrivateAlias = (oAccount: OtherAccount, newValue: String) => oAccount.copy(privateAlias = newValue)
      editable(initialValue, holder, alterPrivateAlias)
    }
    
    def editableImageUrl(initialValue : String, holder: String) = {
      def alterImageUrl = (oAccount: OtherAccount, newValue: String) => oAccount.copy(imageUrl = newValue)
      editable(initialValue, holder, alterImageUrl)
    }
    
    def editableUrl(initialValue : String, holder: String) = {
      def alterUrl = (oAccount: OtherAccount, newValue: String) => oAccount.copy(url = newValue)
      editable(initialValue, holder, alterUrl)
    }
    
    def editableMoreInfo(initialValue : String, holder: String) = {
      def moreInfo = (oAccount: OtherAccount, newValue: String) => oAccount.copy(moreInfo = newValue)
      editable(initialValue, holder, moreInfo)
    }
    
    currentAccount.otherAccounts.get.flatMap(other => {
      (".image *" #> editableImageUrl(other.imageUrl, other.holder) &
       ".real_name *" #> Text(other.holder) &
       ".public_alias_name *" #> editablePublicAlias(other.publicAlias, other.holder) &
       ".private_alias_name *" #> editablePrivateAlias(other.privateAlias, other.holder) &
       ".more_info *" #> editableMoreInfo(other.moreInfo, other.holder) &
       ".website_url *" #> editableUrl(other.url, other.holder) ).apply(xhtml)
    })
    
  }
  
}