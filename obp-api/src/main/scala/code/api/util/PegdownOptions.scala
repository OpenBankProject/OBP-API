/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

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
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.api.util

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.profiles.pegdown.Extensions
import com.vladsch.flexmark.profiles.pegdown.PegdownOptionsAdapter
import com.vladsch.flexmark.util.options.{DataHolder, MutableDataSet}
import org.jsoup.Jsoup
import org.jsoup.nodes.{Document, Entities}
import org.jsoup.safety.Safelist

import scala.util.matching.Regex


object PegdownOptions {
  // Everything except SMARTYPANTS. That extension rewrites plain quotes, "--", "---" and "..." into
  // typographic HTML entities such as &ndash;, which are not defined in XML. Descriptions are written
  // as plain text and should be served as written.
  private val OPTIONS: DataHolder = PegdownOptionsAdapter.flexmarkOptions(Extensions.ALL & ~Extensions.SMARTYPANTS)
  private val PARSER: Parser = Parser.builder(OPTIONS).build
  private val RENDERER: HtmlRenderer = HtmlRenderer.builder(OPTIONS).build

  // Descriptions of Dynamic Entities, Dynamic Resource Docs, Dynamic Endpoints and Glossary Items are
  // written through the API, and the HTML rendered from them is shown to every API Explorer visitor.
  // Markdown passes raw HTML through, so without this a description could carry a <script> tag, an
  // onerror handler or a javascript: link. The allowlist keeps the formatting that descriptions use
  // on purpose (paragraphs, links, images, lists, tables, code blocks, <details>) and drops the rest.
  private val SAFELIST: Safelist = Safelist.relaxed()
    .addTags("details", "summary", "hr", "del")
    .addAttributes("code", "class")
    .addAttributes("pre", "class")
    .preserveRelativeLinks(true)

  // Relative links such as /glossary#Bank.bank_id are kept only when they resolve against a base URI.
  // This one is used for that check alone; it never appears in the output.
  private val BASE_URI = "http://localhost/"

  // API Explorer parses the HTML as XML, so the output has to be well-formed XML: self-closed empty
  // elements and only the entities XML defines. Pretty printing is off so <pre> blocks keep their
  // indentation and the output keeps the line breaks the renderer produced.
  private val OUTPUT_SETTINGS: Document.OutputSettings = new Document.OutputSettings()
    .syntax(Document.OutputSettings.Syntax.xml)
    .escapeMode(Entities.EscapeMode.xhtml)
    .charset("UTF-8")
    .prettyPrint(false)

  /**
   * This function removes everything from rendered HTML that is not on SAFELIST: script and style
   * elements, event handler attributes, and links or images whose URL is not http, https or mailto.
   */
  def sanitiseHtml(html: String): String =
    Jsoup.clean(html, BASE_URI, SAFELIST, OUTPUT_SETTINGS)

  def convertPegdownToHtmlTweaked(description: String): String = {
    val document = PARSER.parse(convertImgTag(description.stripMargin))
    val html = RENDERER.render(document)
      .replaceAll("&amp;;", "&")
//        not support make text bold that not at beginning of a line, so here manual convert to it to <strong> tag
//      .replaceAll("""\*\*(.+?)\*\*""", "<strong>$1</strong>")
    sanitiseHtml(html)
  }

  private val IMAGE_WITH_SIZE: Regex = """!\[(.*)\]\((.*) =(.*?)x(.*?)\)""".r

  // convertPegdownToHtmlTweaked not support insert image, so here manual convert to html img tag.
  // Each captured value is escaped before it goes into an attribute, so a URL containing a double
  // quote cannot close the attribute and add one of its own.
  private def convertImgTag(markdown: String) =
    IMAGE_WITH_SIZE.replaceAllIn(markdown.stripMargin, imageMatch => Regex.quoteReplacement(
      s"""<img alt="${escapeAttribute(imageMatch.group(1))}" src="${escapeAttribute(imageMatch.group(2))}" width="${escapeAttribute(imageMatch.group(3))}" height="${escapeAttribute(imageMatch.group(4))}" />"""
    ))

  private def escapeAttribute(value: String): String = value
    .replace("&", "&amp;")
    .replace("\"", "&quot;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")

  def convertGitHubDocMarkdownToHtml(description: String): String = {
    val options = new MutableDataSet()
    import com.vladsch.flexmark.parser.ParserEmulationProfile
    options.setFrom(ParserEmulationProfile.GITHUB_DOC)
    val parser = Parser.builder(options).build
    val renderer = HtmlRenderer.builder(options).build
    val document = parser.parse(description.stripMargin)
    sanitiseHtml(renderer.render(document))
  }
}
