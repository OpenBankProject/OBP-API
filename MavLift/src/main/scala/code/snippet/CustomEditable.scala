/** 
Open Bank Project - Transparency / Social Finance Web Application
Copyright (C) 2011, 2012, TESOBE / Music Pictures Ltd

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com 
TESOBE / Music Pictures Ltd 
Osloerstrasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)
  by 
  Simon Redfern : simon AT tesobe DOT com
  Stefan Bethge : stefan AT tesobe DOT com
  Everett Sochowski : everett AT tesobe DOT com
  Ayoub Benali: ayoub AT tesobe DOT com

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