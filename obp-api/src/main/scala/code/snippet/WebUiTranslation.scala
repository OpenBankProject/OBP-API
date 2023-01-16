/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH

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
TESOBE GmbH
Osloerstrasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)


*/

package code.snippet

import code.api.util.APIUtil
import code.util.Helper
import code.util.Helper.MdcLoggable
import net.liftweb.http.S
import net.liftweb.util.CssSel
import net.liftweb.util.Helpers._


class WebUiTranslation extends MdcLoggable {

  @transient protected val log = logger //Logger(this.getClass)

  def apiExplorer: CssSel = {
    "#api_explorer *" #> scala.xml.Unparsed(Helper.i18n("api_explorer", Some("API Explorer")))
  }
  def introduction: CssSel = {
    "#introduction *" #> scala.xml.Unparsed(Helper.i18n("introduction", Some("Introduction")))
  }
  def support: CssSel = {
    "#support *" #> scala.xml.Unparsed(Helper.i18n("support", Some("Support")))
  }
  def apiHost: CssSel = {
    "#api_host *" #> scala.xml.Unparsed(Helper.i18n("api_host", Some("This API Host")))
  }
  def openBankProjectIs: CssSel = {
    val year = APIUtil.currentYear
    val text = s"${Helper.i18n("open_bank_project_is", Some("Open Bank Project is &copy;2011 - "))} $year"
    "#open_bank_project_is *" #> scala.xml.Unparsed(text)
  }
  def andCommercialLicenses: CssSel = {
    "#and_commercial_licenses *" #> scala.xml.Unparsed(Helper.i18n("and_commercial_licenses", Some("TESOBE and distributed under the AGPL and commercial licenses. ")))
  }
  def termsConditions: CssSel = {
    "#terms_conditions *" #> scala.xml.Unparsed(Helper.i18n("terms_conditions", Some("Terms and Conditions")))
  }
  def privacyPolicy: CssSel = {
    "#privacy_policy *" #> scala.xml.Unparsed(Helper.i18n("privacy_policy", Some("Privacy Policy")))
  }
  def apiDocumentation: CssSel = {
    "#api_documentation *" #> scala.xml.Unparsed(Helper.i18n("api_documentation", Some("Privacy Policy")))
  }
  def register: CssSel = {
    "#register *" #> scala.xml.Unparsed(Helper.i18n("register", Some("Register")))
  }
  
}
