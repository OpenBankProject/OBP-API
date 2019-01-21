package code.api.util

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
  
  def convertMarkdownToHtml(description: String): String = {
    val options = new MutableDataSet()
    val parser = Parser.builder(options).build
    val renderer = HtmlRenderer.builder(options).build
    val document = parser.parse(description.stripMargin)
    renderer.render(document)
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
  }
}
