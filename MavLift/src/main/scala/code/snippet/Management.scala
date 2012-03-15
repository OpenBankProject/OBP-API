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
    
        
    object CustomSHtml extends SHtml{
      override def ajaxEditable (displayContents : => NodeSeq, editForm : => NodeSeq, onSubmit : () => JsCmd) : NodeSeq = {
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
	
	    def displayMarkup : NodeSeq =
	      <input type="image" src="/media/images/edit-off.png" onclick={setAndSwap(editName, editMarkup, dispName).toJsCmd + " return false;"} /> ++ 
	      Text(" ") ++ displayContents
	      
	
	    def editMarkup : NodeSeq = {
	      val formData : NodeSeq =
	        editForm ++
	        <input class="submit" type="image" src="/media/images/submit.png" /> ++
	        hidden(onSubmit) ++
	        <input type="button" onclick={swapJsCmd(dispName,editName).toJsCmd + " return false;"} value={S.??("cancel")} />
	
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
    
    def editable(initialValue: String, holder: String) = {
      var currentValue = initialValue
      
      def saveValue() = {
        val otherAcc = getMostUpToDateOtherAccount(holder)
        if(otherAcc.isDefined){
          println("oogabooga!")
        }
        
      }
      
      CustomSHtml.ajaxEditable(Text(currentValue), SHtml.text(currentValue, currentValue = _), () =>{
        saveValue()
        Noop
      })
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
      
      val publicSelector = if(publicAlias.equals("")){
        ".public_set" #> NodeSeq.Empty //remove the edit
      }else{
        ".public_not_set" #> NodeSeq.Empty & //remove the add
        ".public_set" #> editable(publicAlias, account)
      }
      
      val privateSelector = if(privateAlias.equals("")){
        ".private_set" #> NodeSeq.Empty //remove the edit
      }else{
        ".private_not_set" #> NodeSeq.Empty & //remove the add
        ".edit_private *" #> privateAlias
      }
      
      val websiteSelector = if(website.equals("")){
        ".website_set" #> NodeSeq.Empty //remove the edit
      }else{
        ".website_not_set" #> NodeSeq.Empty & //remove the add
        ".edit_website *" #> website
      }
      
      val openCorporatesSelector = if(openCorporates.equals("")){
        ".open_corporates_set" #> NodeSeq.Empty //remove the edit
      }else{
        ".open_corporates_not_set" #> NodeSeq.Empty & //remove the add
        ".edit_open_corporates *" #> openCorporates
      }
      
      val moreInfoSelector = if(moreInfo.equals("")){
        ".information_set" #> NodeSeq.Empty //remove the edit
      }else{
        ".information_not_set" #> NodeSeq.Empty & //remove the add
        ".edit_information *" #> moreInfo
      }
      
      val imageURLSelector = if(imageURL.equals("")){
        ".edit_image" #> NodeSeq.Empty //remove the edit
      }else{
        ".add_image" #> NodeSeq.Empty & //remove the add
        ".edit_image *" #> imageURL
      }
      
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