package code.snippet

import scala.collection.mutable.ArrayBuffer

case class WebUIDoc(webUiPropsName: String, defaultValue: String, typeOfValue: String)

object WebUITemplate {
  val webUIDoc = ArrayBuffer[WebUIDoc]()


  webUIDoc += WebUIDoc(
    webUiPropsName = "webui_developer_user_invitation_email_text", 
    defaultValue = webUiDeveloperUserInvitationEmailText, 
    typeOfValue = "plain_text"
  )
  val webUiDeveloperUserInvitationEmailText =
    """
      |Hi _EMAIL_RECIPIENT_,
      |Welcome to Open Bank Project API. Your account has been registered. Please use the below link to activate it.
      |
      |Activate your account: _ACTIVATE_YOUR_ACCOUNT_
      |
      |Our operations team has granted you the appropriate access to the API Playground. If you have any questions, or you need any assistance, please contact our support.
      |
      |Thanks, 
      |Your OBP API team
      |
      |
      |
      |Please do not reply to this email. Should you wish to contact us, please raise a ticket at our support page. We maintain strict security standards and procedures to prevent unauthorised access to information about you. We will never contact you by email or otherwise and ask you to validate personal information such as your user ID, password or account numbers. This e-mail is confidential. It may also be legally privileged. If you are not the addressee you may not copy, forward, disclose or use any part of it. If you have received this message in error, please delete it and all copies from your system. Internet communications cannot be guaranteed to be timely, secure, error or virus-free. The sender does not accept liability for any errors or omissions.
      |""".stripMargin


  webUIDoc += WebUIDoc(
    webUiPropsName = "webui_developer_user_invitation_email_html_text",
    defaultValue = webUiDeveloperUserInvitationEmailHtmlText,
    typeOfValue = "html"
  )
  val webUiDeveloperUserInvitationEmailHtmlText =
    """<!DOCTYPE html>
      |<html>
      |<head>
      |<style>
      |.a {
      |  border: none;
      |  color: white;
      |  padding: 15px 32px;
      |  text-align: center;
      |  text-decoration: none;
      |  display: inline-block;
      |  font-size: 16px;
      |  margin: 4px 2px;
      |  cursor: pointer;
      |}
      |
      |.a1 {background-color: #4CAF50;} /* Green */
      |.a2 {background-color: #008CBA;} /* Blue */
      |</style>
      |</head>
      |<body>
      |<img src="https://static.openbankproject.com/images/OBP_full_web_25pc.png"></img>
      |<hr></hr><br></br>
      |<p>Hi _EMAIL_RECIPIENT_,<br></br>
      |Welcome to Open Bank Project API. Your account has been registered. Please use the below link to activate it.</p>
      |<a href="_ACTIVATE_YOUR_ACCOUNT_" class="a a1">Activate your account</a>
      |<p>Our operations team has granted you the appropriate access to the API Playground. If you have any questions, or you need any assistance, please contact our support.</p>
      |<p>Thanks,<br></br> Your OBP API team</p><br></br>
      |<hr></hr>
      |<p>
      |Please do not reply to this email. Should you wish to contact us, please raise a ticket at our support page. We maintain strict security standards and procedures to prevent unauthorised access to information about you. We will never contact you by email or otherwise and ask you to validate personal information such as your user ID, password or account numbers. This e-mail is confidential. It may also be legally privileged. If you are not the addressee you may not copy, forward, disclose or use any part of it. If you have received this message in error, please delete it and all copies from your system. Internet communications cannot be guaranteed to be timely, secure, error or virus-free. The sender does not accept liability for any errors or omissions.
      |</p>
      |</body>
      |</html>
      |
      |""".stripMargin
}
