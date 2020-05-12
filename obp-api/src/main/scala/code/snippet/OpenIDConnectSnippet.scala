package code.snippet

import code.api.util.APIUtil
import code.util.Helper.MdcLoggable
import net.liftweb.util.{CssSel, PassThru}
import net.liftweb.util.Helpers._

import scala.xml.NodeSeq

class OpenIDConnectSnippet extends MdcLoggable{

  @transient protected val log = logger
  
  def getFirstButtonText: CssSel = {
    val text = APIUtil.getPropsValue("openid_connect_1.button_text", "OIDC 1")
    "#open-id-connect-button-1 *" #> scala.xml.Unparsed(text)
  }  
  def getSecondButtonText: CssSel = {
    val text = APIUtil.getPropsValue("openid_connect_2.button_text", "OIDC 2")
    "#open-id-connect-button-2 *" #> scala.xml.Unparsed(text)
  }

  def showFirstButton =
    if (APIUtil.getPropsValue("openid_connect_1.client_id").isEmpty) 
      "*" #> NodeSeq.Empty
    else 
      PassThru
  
  def showSecondButton =
    if (APIUtil.getPropsValue("openid_connect_2.client_id").isEmpty) 
      "*" #> NodeSeq.Empty
    else 
      PassThru
  
}
