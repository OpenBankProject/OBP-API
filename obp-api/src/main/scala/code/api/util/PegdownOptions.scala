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
}
