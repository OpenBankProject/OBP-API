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
import net.liftweb.http.js.JsCmd

class Management {

  val headers = (0, Sorter("text")) :: (5, DisableSorting()) :: (6, DisableSorting()) :: Nil
  val sortList = (0, Sorting.DSC) :: Nil
  
  val options = TableSorter.options(headers, sortList)
  
  def tableSorter(xhtml: NodeSeq) : NodeSeq = {
    TableSorter("#other_acc_management", options)
  }
  
  def showAll(xhtml: NodeSeq) : NodeSeq = {
    //temporary way to retrieve the account
    val accJObj = JObject(List(JField("holder", JString("Music Pictures Limited"))))
    val currentAccount = Account.find(accJObj) getOrElse Account.createRecord
    
    //TODO: This should go. There is too much presentation stuff living here in the code
    object CustomSHtml extends SHtml{
      def ajaxEditable (displayContents : => NodeSeq, editForm : => NodeSeq, onSubmit : () => JsCmd, defaultValue: String) : NodeSeq = {
        import net.liftweb.http.js
        import net.liftweb.http.S
	    import js.{jquery,JsCmd,JsCmds,JE}
	    import jquery.JqJsCmds
	    import JsCmds.{Noop,SetHtml}
	    import JE.Str
	    import JqJsCmds.{Hide,Show}
	    import net.liftweb.util.Helpers
	
	    val divName = Helpers.nextFuncName
	    val dispName = divName + "_display"
	    val editName = divName + "_edit"
	
	    def swapJsCmd (show : String, hide : String) : JsCmd = Show(show) & Hide(hide)
	
	    def setAndSwap (show : String, showContents : => NodeSeq, hide : String) : JsCmd =
	      (SHtml.ajaxCall(Str("ignore"), {ignore : String => SetHtml(show, showContents)})._2.cmd & swapJsCmd(show,hide))
	
	    val editSrc = "/media/images/edit-off.png"
	    val addSrc = "/media/images/add-on.png"
	    val editClass = "edit"
	    val addClass = "add"
	    def aClass = if(displayContents.text.equals("")) addClass else editClass
	    def imgSrc = if(displayContents.text.equals("")) addSrc else editSrc
	    def displayText = if(displayContents.text.equals("")) defaultValue else displayContents.text
	      
	    def displayMarkup : NodeSeq = {
	      displayContents.text match{
	        case "" => {
	          <div onclick={setAndSwap(editName, editMarkup, dispName).toJsCmd + " return false;"}><a href="#" class={addClass}>{
	        	  " " ++ displayText}</a></div>
	        }
	        case _ => {
	          <div>
	          	<a href="#" class={editClass}  onclick={setAndSwap(editName, editMarkup, dispName).toJsCmd + " return false;"}/>
	          	<span class="text">{displayContents}</span>
	          </div>
	        }
	      }
	    }
	
	    def editMarkup : NodeSeq = {
	      val formData : NodeSeq =
	        editForm ++
	        <input class="submit" type="image" src="/media/images/submit.png" /> ++
	        hidden(onSubmit) ++
	        <input type="image" src="/media/images/cancel.png" onclick={swapJsCmd(dispName,editName).toJsCmd + " return false;"} />
	
	      ajaxForm(formData,
	               Noop,
	               setAndSwap(dispName, displayMarkup, editName))
	    }
	
	    <div>
	      <div id={dispName}>
	      {displayMarkup}
	      </div>
	      <div id={editName} style="display: none;">
	      	{editMarkup}
	      </div>
        </div>
      }
    }
    
    def getMostUpToDateOtherAccount(holder: String) = {
    	currentAccount.otherAccounts.get.find(o => {
    	  o.holder.get.equals(holder)
    	})
    }
    
    def editablePublicAlias(initialValue : String, holder: String) = {
      def alterPublicAlias = (oAccount: OtherAccount, newValue: String) => oAccount.publicAlias(newValue)
      editable(initialValue, holder, alterPublicAlias, "Public Alias")
    }
    
    def editablePrivateAlias(initialValue : String, holder: String) = {
      def alterPrivateAlias = (oAccount: OtherAccount, newValue: String) => oAccount.privateAlias(newValue)
      editable(initialValue, holder, alterPrivateAlias, "Private Alias")
    }
    
    def editableImageUrl(initialValue : String, holder: String) = {
      def alterImageUrl = (oAccount: OtherAccount, newValue: String) => oAccount.imageUrl(newValue)
      editable(initialValue, holder, alterImageUrl, "Image URL")
    }
    
    def editableUrl(initialValue : String, holder: String) = {
      def alterUrl = (oAccount: OtherAccount, newValue: String) => oAccount.url(newValue)
      editable(initialValue, holder, alterUrl, "Website")
    }
    
    def editableMoreInfo(initialValue : String, holder: String) = {
      def moreInfo = (oAccount: OtherAccount, newValue: String) => oAccount.moreInfo(newValue)
      editable(initialValue, holder, moreInfo, "Information")
    }
    
    def editableOpenCorporatesUrl(initialValue : String, holder: String) = {
      def openCorporatesUrl = (oAccount: OtherAccount, newValue: String) => oAccount.openCorporatesUrl(newValue)
      editable(initialValue, holder, openCorporatesUrl, "Open Corporates URL")
    }
    
    def editable(initialValue: String, holder: String,  alterOtherAccount: (OtherAccount, String) => OtherAccount,
        defaultValue: String) = {
      var currentValue = initialValue
      
      def saveValue() = {
        val otherAcc = getMostUpToDateOtherAccount(holder)
        if(otherAcc.isDefined){
          val newOtherAcc = alterOtherAccount(otherAcc.get, currentValue)
          val newOtherAccs = currentAccount.otherAccounts.get -- List(otherAcc.get) ++ List(newOtherAcc) 
          currentAccount.otherAccounts(newOtherAccs).save
        }
      }
      
      CustomSHtml.ajaxEditable(<span class="text">{currentValue}</span>, SHtml.text(currentValue, currentValue = _), () =>{
        saveValue()
        Noop
      }, defaultValue)
    }
    
    currentAccount.otherAccounts.get.flatMap(other => {
      
      val account = other.holder.get
      val publicAlias = other.publicAlias.get
      val privateAlias = other.privateAlias.get
      val moreInfo = other.moreInfo.get
      val website = other.url.get
      val openCorporates = other.openCorporatesUrl.get
      val imageURL = other.imageUrl.get
      
      val accountSelector = ".account *" #> account
      
      val publicSelector = ".public *" #> editablePublicAlias(publicAlias, account)
      
      val privateSelector = ".private *" #> editablePrivateAlias(privateAlias, account)
      
      val websiteSelector = ".website *" #> editableUrl(website, account)
      
      val openCorporatesSelector = ".open_corporates *" #> editableOpenCorporatesUrl(openCorporates, account)
      
      val moreInfoSelector = ".information *" #> editableMoreInfo(moreInfo, account)
      
      val imageURLSelector = ".image *" #> editableImageUrl(imageURL, account)
      
      (accountSelector &
       publicSelector &
       privateSelector &
       websiteSelector &
       openCorporatesSelector &
       moreInfoSelector &
       imageURLSelector).apply(xhtml)
    })
  }
  
  def listAll(xhtml: NodeSeq) : NodeSeq  = {
    
    //temporary way to retrieve the account
    val accJObj = JObject(List(JField("holder", JString("Music Pictures Limited"))))
    val currentAccount = Account.find(accJObj) getOrElse Account.createRecord
    
    def getMostUpToDateOtherAccount(holder: String) = {
    	currentAccount.otherAccounts.get.find(o => {
    	  o.holder.get.equals(holder)
    	})
    }
    
    def editable(initialValue: String, holder: String,  alterOtherAccount: (OtherAccount, String) => OtherAccount) = {
      var currentValue = initialValue
      
      def saveValue() = {
        val otherAcc = getMostUpToDateOtherAccount(holder)
        if(otherAcc.isDefined){
          val newOtherAcc = alterOtherAccount(otherAcc.get, currentValue)
          val newOtherAccs = currentAccount.otherAccounts.get -- List(otherAcc.get) ++ List(newOtherAcc) 
          currentAccount.otherAccounts(newOtherAccs).save
        }
        
      }
      
      SHtml.ajaxEditable(Text(currentValue), SHtml.text(currentValue, currentValue = _), () =>{
        saveValue()
        Noop
      })
    }
    
    def editablePublicAlias(initialValue : String, holder: String) = {
      def alterPublicAlias = (oAccount: OtherAccount, newValue: String) => oAccount.publicAlias(newValue)
      editable(initialValue, holder, alterPublicAlias)
    }
    
    def editablePrivateAlias(initialValue : String, holder: String) = {
      def alterPrivateAlias = (oAccount: OtherAccount, newValue: String) => oAccount.privateAlias(newValue)
      editable(initialValue, holder, alterPrivateAlias)
    }
    
    def editableImageUrl(initialValue : String, holder: String) = {
      def alterImageUrl = (oAccount: OtherAccount, newValue: String) => oAccount.imageUrl(newValue)
      editable(initialValue, holder, alterImageUrl)
    }
    
    def editableUrl(initialValue : String, holder: String) = {
      def alterUrl = (oAccount: OtherAccount, newValue: String) => oAccount.url(newValue)
      editable(initialValue, holder, alterUrl)
    }
    
    def editableMoreInfo(initialValue : String, holder: String) = {
      def moreInfo = (oAccount: OtherAccount, newValue: String) => oAccount.moreInfo(newValue)
      editable(initialValue, holder, moreInfo)
    }
    
    def editableOpenCorporatesUrl(initialValue : String, holder: String) = {
      def openCorporatesUrl = (oAccount: OtherAccount, newValue: String) => oAccount.openCorporatesUrl(newValue)
      editable(initialValue, holder, openCorporatesUrl)
    }
    
    currentAccount.otherAccounts.get.flatMap(other => {
      (".image *" #> editableImageUrl(other.imageUrl.get, other.holder.get) &
       ".real_name *" #> Text(other.holder.get) &
       ".public_alias_name *" #> editablePublicAlias(other.publicAlias.get, other.holder.get) &
       ".private_alias_name *" #> editablePrivateAlias(other.privateAlias.get, other.holder.get) &
       ".more_info *" #> editableMoreInfo(other.moreInfo.get, other.holder.get) &
       ".website_url *" #> editableUrl(other.url.get, other.holder.get) &
       ".open_corporates_url *" #> editableOpenCorporatesUrl(other.openCorporatesUrl.get, other.holder.get)).apply(xhtml)
    })
    
  }
  
}