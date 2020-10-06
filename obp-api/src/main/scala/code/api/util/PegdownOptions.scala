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
  
  def convertPegdownToHtmlTweaked(description: String): String = {
    val document = PARSER.parse(convertImgTag(description.stripMargin))
    RENDERER.render(document)
      .replaceAll("&ldquo", "&quot")
      .replaceAll("&rdquo", "&quot")
      .replaceAll("&rsquo;", "'")
      .replaceAll("&lsquo;;", "'")
      .replaceAll("&amp;;", "&")
      .replaceAll("&lsquo;", "'")
      .replaceAll("&hellip;", "...")
//        not support make text bold that not at beginning of a line, so here manual convert to it to <strong> tag
//      .replaceAll("""\*\*(.+?)\*\*""", "<strong>$1</strong>")
  }
  // convertPegdownToHtmlTweaked not support insert image, so here manual convert to html img tag
  private def convertImgTag(markdown: String) = markdown.stripMargin
    .replaceAll(
      """!\[(.*)\]\((.*) =(.*?)x(.*?)\)""", 
      """<img alt="$1" src="$2" width="$3" height="$4" />"""
    )

  def convertGitHubDocMarkdownToHtml(description: String): String = {
    val options = new MutableDataSet()
    import com.vladsch.flexmark.parser.ParserEmulationProfile
    options.setFrom(ParserEmulationProfile.GITHUB_DOC)
    val parser = Parser.builder(options).build
    val renderer = HtmlRenderer.builder(options).build
    val document = parser.parse(description.stripMargin)
    renderer.render(document)
  }
  
  def convertHtmlMarkdown(html: String): String = {
    //TODO, this is a simple version, may add more options later.
    FlexmarkHtmlParser.parse(html)
  }
}
