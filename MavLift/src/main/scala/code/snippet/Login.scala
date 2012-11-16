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

import code.model.dataAccess.{OBPUser,Account}

import scala.xml.NodeSeq
import net.liftweb.util.Helpers
import net.liftweb.util.Helpers._
import net.liftweb.util.CssSel
import net.liftweb.http.S

class Login {

  def loggedIn = {
    if(!OBPUser.loggedIn_?){
      "*" #> NodeSeq.Empty
    }else{
      ".logout [href]" #> {
        OBPUser.logoutPath.foldLeft("")(_ + "/" + _)
      } &
      ".username *" #> OBPUser.currentUser.get.email.get 
    }
  }
  
  def loggedOut = {
    if(OBPUser.loggedIn_?){
      "*" #> NodeSeq.Empty
    } else {
      ".login [action]" #> OBPUser.loginPageURL &
      ".forgot [href]" #> {
        val href = for {
          menu <- OBPUser.resetPasswordMenuLoc
        } yield menu.loc.calcDefaultHref
        href getOrElse "#"
      } & {
        ".signup [href]" #> {
         OBPUser.signUpPath.foldLeft("")(_ + "/" + _)
        }
      }
    }
  }

}