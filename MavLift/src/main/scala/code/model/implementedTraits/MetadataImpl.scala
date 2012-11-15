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
package code.model.implementedTraits

import code.model.traits.{Comment, OtherBankAccountMetadata, TransactionMetadata}
import code.model.dataAccess.{OtherAccount, OBPComment}
import net.liftweb.common.Loggable
import java.util.Date

class OtherBankAccountMetadataImpl(_publicAlias : String, _privateAlias : String,_moreInfo : String,
_url : String, _imageUrl : String, _openCorporatesUrl : String) extends OtherBankAccountMetadata {

   def publicAlias : String = _publicAlias
   def privateAlias : String = _privateAlias
   def moreInfo : String = _moreInfo
   def url : String = _url
   def imageUrl : String = _imageUrl
   def openCorporatesUrl : String = _openCorporatesUrl
}
class TransactionMetadataImpl(narative : String, comments_ : List[Comment], 
  saveOwnerComment : String => Unit, addCommentFunc : (Long, String, Date) => Unit ) 
  extends TransactionMetadata with Loggable
{
  def ownerComment = if(! narative.isEmpty) Some(narative) else None
  def ownerComment(comment : String) = saveOwnerComment(comment)
  def comments : List[Comment] = comments_
  def addComment(userId: Long, text: String, datePosted : Date) : Unit = 
  {
    println("--> received values : " + userId + " "+ text + " " + datePosted)
    addCommentFunc(userId, text, datePosted)
  }  
}

