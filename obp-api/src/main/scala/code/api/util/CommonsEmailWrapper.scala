package code.api.util

import code.util.Helper.MdcLoggable
import jakarta.activation.{DataHandler, FileDataSource, URLDataSource}
import jakarta.mail._
import jakarta.mail.internet._
import net.liftweb.common.{Box, Empty, Full}

import java.io.File
import java.net.URL
import java.util.Properties

/**
 * Jakarta Mail Wrapper for OBP-API
 * This wrapper provides a simple interface to send emails using Jakarta Mail
 */
object CommonsEmailWrapper extends MdcLoggable {

  case class EmailConfig(
    smtpHost: String,
    smtpPort: Int,
    username: String,
    password: String,
    useTLS: Boolean = true,
    useSSL: Boolean = false,
    debug: Boolean = false,
    tlsProtocols: String = "TLSv1.2"
  )

  case class EmailContent(
    from: String,
    to: List[String],
    cc: List[String] = List.empty,
    bcc: List[String] = List.empty,
    subject: String,
    textContent: Option[String] = None,
    htmlContent: Option[String] = None,
    attachments: List[EmailAttachment] = List.empty
  )

  case class EmailAttachment(
    filePath: Option[String] = None,
    url: Option[String] = None,
    name: Option[String] = None
  )

  def getDefaultEmailConfig(): EmailConfig = {
    EmailConfig(
      smtpHost = APIUtil.getPropsValue("mail.smtp.host", "localhost"),
      smtpPort = APIUtil.getPropsValue("mail.smtp.port", "1025").toInt,
      username = APIUtil.getPropsValue("mail.smtp.user", ""),
      password = APIUtil.getPropsValue("mail.smtp.password", ""),
      useTLS = APIUtil.getPropsValue("mail.smtp.starttls.enable", "false").toBoolean,
      useSSL = APIUtil.getPropsValue("mail.smtp.ssl.enable", "false").toBoolean,
      debug = APIUtil.getPropsValue("mail.debug", "false").toBoolean,
      tlsProtocols = APIUtil.getPropsValue("mail.smtp.ssl.protocols", "TLSv1.2")
    )
  }

  def sendTextEmail(content: EmailContent): Box[String] = {
    sendTextEmail(getDefaultEmailConfig(), content)
  }

  def sendHtmlEmail(content: EmailContent): Box[String] = {
    sendHtmlEmail(getDefaultEmailConfig(), content)
  }

  def sendEmailWithAttachments(content: EmailContent): Box[String] = {
    sendEmailWithAttachments(getDefaultEmailConfig(), content)
  }

  def sendTextEmail(config: EmailConfig, content: EmailContent): Box[String] = {
    try {
      logger.debug(s"Sending text email from ${content.from} to ${content.to.mkString(", ")}")
      val session = createSession(config)
      val message = new MimeMessage(session)
      setCommonHeaders(message, content)
      message.setText(content.textContent.getOrElse(""), "UTF-8")
      Transport.send(message)
      Full(message.getMessageID)
    } catch {
      case e: Exception =>
        logger.error(s"Failed to send text email: ${e.getMessage}", e)
        Empty
    }
  }

  def sendHtmlEmail(config: EmailConfig, content: EmailContent): Box[String] = {
    try {
      logger.debug(s"Sending HTML email from ${content.from} to ${content.to.mkString(", ")}")
      val session = createSession(config)
      val message = new MimeMessage(session)
      setCommonHeaders(message, content)
      val multipart = {
        new MimeMultipart("alternative")
      }
      content.textContent.foreach { text =>
        val textPart = new MimeBodyPart()
        textPart.setText(text, "UTF-8")
        multipart.addBodyPart(textPart)
      }
      content.htmlContent.foreach { html =>
        val htmlPart = new MimeBodyPart()
        htmlPart.setContent(html, "text/html; charset=UTF-8")
        multipart.addBodyPart(htmlPart)
      }
      message.setContent(multipart)
      Transport.send(message)
      Full(message.getMessageID)
    } catch {
      case e: Exception =>
        logger.error(s"Failed to send HTML email: ${e.getMessage}", e)
        Empty
    }
  }

  def sendEmailWithAttachments(config: EmailConfig, content: EmailContent): Box[String] = {
    try {
      logger.debug(s"Sending email with attachments from ${content.from} to ${content.to.mkString(", ")}")
      val session = createSession(config)
      val message = new MimeMessage(session)
      setCommonHeaders(message, content)
      val multipart = new MimeMultipart()
      // Add text or HTML part
      (content.htmlContent, content.textContent) match {
        case (Some(html), _) =>
          val htmlPart = new MimeBodyPart()
          htmlPart.setContent(html, "text/html; charset=UTF-8")
          multipart.addBodyPart(htmlPart)
        case (None, Some(text)) =>
          val textPart = new MimeBodyPart()
          textPart.setText(text, "UTF-8")
          multipart.addBodyPart(textPart)
        case _ =>
          val textPart = new MimeBodyPart()
          textPart.setText("", "UTF-8")
          multipart.addBodyPart(textPart)
      }
      // Add attachments
      content.attachments.foreach { att =>
        val attachPart = new MimeBodyPart()
        if (att.filePath.isDefined) {
          val fds = new FileDataSource(new File(att.filePath.get))
          attachPart.setDataHandler(new DataHandler(fds))
          attachPart.setFileName(att.name.getOrElse(new File(att.filePath.get).getName))
        } else if (att.url.isDefined) {
          val uds = new URLDataSource(new URL(att.url.get))
          attachPart.setDataHandler(new DataHandler(uds))
          attachPart.setFileName(att.name.getOrElse(att.url.get.split('/').last))
        }
        multipart.addBodyPart(attachPart)
      }
      message.setContent(multipart)
      Transport.send(message)
      Full(message.getMessageID)
    } catch {
      case e: Exception =>
        logger.error(s"Failed to send email with attachments: ${e.getMessage}", e)
        Empty
    }
  }

  private def createSession(config: EmailConfig): Session = {
    val props = new Properties()
    props.put("mail.smtp.host", config.smtpHost)
    props.put("mail.smtp.port", config.smtpPort.toString)
    props.put("mail.smtp.auth", "true")
    props.put("mail.smtp.starttls.enable", config.useTLS.toString)
    props.put("mail.smtp.ssl.enable", config.useSSL.toString)
    props.put("mail.debug", config.debug.toString)
    props.put("mail.smtp.ssl.protocols", config.tlsProtocols)
    val authenticator = new Authenticator() {
      override def getPasswordAuthentication: PasswordAuthentication =
        new PasswordAuthentication(config.username, config.password)
    }
    Session.getInstance(props, authenticator)
  }

  private def setCommonHeaders(message: MimeMessage, content: EmailContent): Unit = {
    message.setFrom(new InternetAddress(content.from))
    content.to.foreach(addr => message.addRecipient(Message.RecipientType.TO, new InternetAddress(addr)))
    content.cc.foreach(addr => message.addRecipient(Message.RecipientType.CC, new InternetAddress(addr)))
    content.bcc.foreach(addr => message.addRecipient(Message.RecipientType.BCC, new InternetAddress(addr)))
    message.setSubject(content.subject, "UTF-8")
  }

  def createFileAttachment(filePath: String, name: Option[String] = None): EmailAttachment =
    EmailAttachment(filePath = Some(filePath), url = None, name = name)

  def createUrlAttachment(url: String, name: String): EmailAttachment =
    EmailAttachment(filePath = None, url = Some(url), name = Some(name))

} 