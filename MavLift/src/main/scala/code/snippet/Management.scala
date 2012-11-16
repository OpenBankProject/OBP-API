/** 
Open Bank Project

Copyright 2011,2012 TESOBE / Music Pictures Ltd.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and 
limitations under the License.      

Open Bank Project (http://www.openbankproject.com)
      Copyright 2011,2012 TESOBE / Music Pictures Ltd

      This product includes software developed at
      TESOBE (http://www.tesobe.com/)
    by 
    Simon Redfern : simon AT tesobe DOT com
    Everett Sochowski: everett AT tesobe DOT com
    Benali Ayoub : ayoub AT tesobe DOT com

 */

package code.snippet
import net.liftweb.json.JsonAST._
import code.model.dataAccess.{Account,OtherAccount}
import net.liftweb.util.Helpers._
import scala.xml.NodeSeq
import scala.xml.Text
import net.liftweb.http.{S, SHtml}
import net.liftweb.http.js.JsCmds.Noop
import net.liftweb.common.Full
import net.liftweb.common.Empty
import net.liftweb.widgets.tablesorter.{TableSorter, DisableSorting, Sorting, Sorter}
import net.liftweb.http.js.JsCmd

class Management(currentAccount : Account) {

  val headers = (0, Sorter("text")) :: (5, DisableSorting()) :: (6, DisableSorting()) :: Nil
  val sortList = (0, Sorting.DSC) :: Nil
  
  val options = TableSorter.options(headers, sortList)
  
  def tableSorter(xhtml: NodeSeq) : NodeSeq = {
    TableSorter("#other_acc_management", options)
  }
  
  def showAll(xhtml: NodeSeq) : NodeSeq = {
    
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
      
      CustomEditable.editable(currentValue, SHtml.text(currentValue, currentValue = _), () =>{
        saveValue()
        Noop
      }, defaultValue)
    }
    
    currentAccount.otherAccounts.get.sortBy(_.holder.get).flatMap(other => {
      
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
      
      val imageURLSelector = ".imageURL *" #> editableImageUrl(imageURL, account)
      
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