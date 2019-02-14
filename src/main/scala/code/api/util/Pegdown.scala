package code.api.util

import com.vladsch.flexmark.convert.html.FlexmarkHtmlParser
import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.profiles.pegdown.Extensions
import com.vladsch.flexmark.profiles.pegdown.PegdownOptionsAdapter
import com.vladsch.flexmark.util.options.{DataHolder, MutableDataSet}


object PegdownOptions {
  private val OPTIONS: DataHolder = PegdownOptionsAdapter.flexmarkOptions(Extensions.ALL)
  private val PARSER: Parser = Parser.builder(OPTIONS).build
  private val RENDERER: HtmlRenderer = HtmlRenderer.builder(OPTIONS).build
  
  // use the PARSER to parse and RENDERER to render with pegdown compatibility
  def convertPegdownToHtml(description: String): String = {
    val document = PARSER.parse(description.stripMargin)
    RENDERER.render(document)
  }
  def convertPegdownToHtmlTweaked(description: String): String = {
    val document = PARSER.parse(description.stripMargin)
    RENDERER.render(document)
      .replaceAll("&ldquo", "&quot")
      .replaceAll("&rdquo", "&quot")
      .replaceAll("&rsquo;", "'")
      .replaceAll("&lsquo;;", "'")
      .replaceAll("&amp;;", "&")
      .replaceAll("&lsquo;", "'")
      .replaceAll("&hellip;", "...")
  }
  
  def convertMarkdownToHtml(description: String): String = {
    val options = new MutableDataSet()
    val parser = Parser.builder(options).build
    val renderer = HtmlRenderer.builder(options).build
    val document = parser.parse(description.stripMargin)
    renderer.render(document)
  }
  
  def convertHtmlMarkdown(html: String): String = {
    //TODO, this is a simple version, may add more options later.
    FlexmarkHtmlParser.parse(html)
  }

  // use the PARSER to parse and RENDERER to render with pegdown compatibility
  def main(args: Array[String]): Unit = { // You can re-use parser and renderer instances
    val input = """Get public accounts at all banks (Anonymous access).
                  |Returns accounts that contain at least one public view (a view where is_public is true)
                  |For each account the API returns the ID and the available views.
                  |
                  |${authenticationRequiredMessage(false)}
                  |
                  |""".stripMargin
    
    System.out.println(convertPegdownToHtml(input))
    
    val html ="<h3>Description</h3>\nIn the mixed detailed consent on accounts\n<ul>\n<li>the AISP captures the consent of the PSU</li>" +
      "\n<li>then it forwards this consent to the ASPSP</li>\n</ul>\nThis consent replaces any prior consent that was previously sent by the AISP." +
      "\n<h3>Prerequisites</h3>\n<ul>\n<li>The TPP has been registered by the Registration Authority for the AISP role.</li>" +
      "\n<li>The TPP and the PSU have a contract that has been enrolled by the ASPSP</li>\n<ul>\n<li>At this step, the ASPSP has delivered an OAUTH2 " +
      "\"Authorization Code\" or \"Resource Owner Password\" access token to the TPP (cf. § 3.4.2).</li>\n</ul>\n<li>The TPP and the ASPSP have successfully " +
      "processed a mutual check and authentication</li>\n<li>The TPP has presented its OAUTH2 \"Authorization Code\" or \"Resource Owner Password\" access " +
      "token which allows the ASPSP to identify the relevant PSU and retrieve the linked PSU context (cf. § 3.4.2) if any.</li>" +
      "\n<li>The ASPSP takes into account the access token that establishes the link between the PSU and the AISP.</li>\n</ul>" +
      "\n<h3>Business Flow</h3>\nThe PSU specifies to the AISP which of his/her accounts will be accessible and which functionalities " +
      "should be available.\nThe AISP forwards these settings to the ASPSP.\nThe ASPSP answers by HTTP201 return code."
    
    System.out.println(convertHtmlMarkdown(html))
    
    
  }
}
