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
import net.liftweb.http.SHtml
import scala.xml.NodeSeq
import net.liftweb.http.js.JsCmd
import scala.xml.Text

object CustomEditable {

  //Borrows very heavily from SHtml.ajaxEditable
  //TODO: This should go. There is too much presentation stuff living here in the code
  def editable(label : => String, editForm: => NodeSeq, onSubmit: () => JsCmd, defaultValue: String): NodeSeq = {
    import net.liftweb.http.js
    import net.liftweb.http.S
    import js.{ jquery, JsCmd, JsCmds, JE }
    import jquery.JqJsCmds
    import JsCmds.{ Noop, SetHtml }
    import JE.Str
    import JqJsCmds.{ Hide, Show }
    import net.liftweb.util.Helpers

    val divName = Helpers.nextFuncName
    val dispName = divName + "_display"
    val editName = divName + "_edit"

    def swapJsCmd(show: String, hide: String): JsCmd = Show(show) & Hide(hide)

    def setAndSwap(show: String, showContents: => NodeSeq, hide: String): JsCmd =
      (SHtml.ajaxCall(Str("ignore"), { ignore: String => SetHtml(show, showContents) })._2.cmd & swapJsCmd(show, hide))

    val editClass = "edit"
    val addClass = "add"
    def aClass = if (label.equals("")) addClass else editClass
    def displayText = if (label.equals("")) defaultValue else label

    def displayMarkup: NodeSeq = {
      label match {
        case "" => {
          <div onclick={ setAndSwap(editName, editMarkup, dispName).toJsCmd + " return false;" }><a href="#" class={ addClass }>{
            " " ++ displayText
          }</a></div>
        }
        case _ => {
          <div>
            <a href="#" class={ editClass } onclick={ setAndSwap(editName, editMarkup, dispName).toJsCmd + " return false;" }/>
            <span class="text">{ label }</span>
          </div>
        }
      }
    }

    def editMarkup: NodeSeq = {
      val formData: NodeSeq =
        editForm ++ <br />
          <input class="submit" style="float:left;" type="image" src="/media/images/submit.png"/> ++
          SHtml.hidden(onSubmit, ("float", "left")) ++
          <input type="image" src="/media/images/cancel.png" onclick={ swapJsCmd(dispName, editName).toJsCmd + " return false;" }/>

      SHtml.ajaxForm(formData,
        Noop,
        setAndSwap(dispName, displayMarkup, editName))
    }

    <div>
      <div id={ dispName }>
        { displayMarkup }
      </div>
      <div id={ editName } style="display: none;">
        { editMarkup }
      </div>
    </div>
  }
  
}