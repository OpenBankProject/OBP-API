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
        editForm ++
          <input class="submit" type="image" src="/media/images/submit.png"/> ++
          SHtml.hidden(onSubmit) ++
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
  
  def editable2(label : => String, editForm: => NodeSeq, onSubmit: () => JsCmd, defaultValue: String): NodeSeq = {
    val standardAjaxEditable = SHtml.ajaxEditable(Text(label), editForm, onSubmit)
    //val transformer = "
    NodeSeq.Empty
  }
}