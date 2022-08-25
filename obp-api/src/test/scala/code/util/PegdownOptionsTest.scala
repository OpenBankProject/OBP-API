package code.util

import code.api.util.PegdownOptions
import code.api.util.PegdownOptions.convertPegdownToHtmlTweaked
import net.liftweb.util.Html5
import org.scalatest.{FlatSpec, Matchers, Tag}

import scala.xml.NodeSeq

class PegdownOptionsTest extends FlatSpec with Matchers {
  /**
   * this is the method from api_explorer to show the description filed to browser.
   * @param html
   * @return
   */
  def stringToNodeSeq(html : String) : NodeSeq = {
    val newHtmlString =scala.xml.XML.loadString("<div>" + html + "</div>").toString()
    //Note: `parse` method: We much enclose the div, otherwise only the first element is returned. 
    Html5.parse(newHtmlString).head
  }
  object FunctionsTag extends Tag("PegdownOptions")

  "description string" should "be transfer to proper html, no exception is good" taggedAs FunctionsTag in {

    val descriptionString =
      """Get basic information about the Adapter listening on behalf of this bank.
        |
        |Authentication is Optional**URL Parameters:**
        |
        |[BANK_ID](/glossary#Bank.bank_id):gh.29.uk
        |
        |""".stripMargin
    val descriptionString2 ="""Get basic information about the Adapter listening on behalf of this bank.
        |
        |Authentication is Optional
        |
        |      **URL Parameters:**
        |
        |* [BANK_ID](/glossary#Bank.bank_id):gh.29.uk
        |
        |""".stripMargin
    val descriptionHtml= convertPegdownToHtmlTweaked(descriptionString)
    val descriptionHtml2= convertPegdownToHtmlTweaked(descriptionString2)
    
    val descriptionApiExplorer = stringToNodeSeq(descriptionHtml)
    val descriptionApiExplorer2 = stringToNodeSeq(descriptionHtml2)
    
    val markdownText ="""Privacy policy
                |====================
                |
                |Privacy policy
                |====================
                |
                |Learn more about our website
                |
                |On this page
                |
                |*   [About this site](#Top)
                |*   [Definitions](#Definitions)
                |*   [Access and licenses](#Access)
                |*   [Intellectual property rights](#Intellectual-property)
                |*   [Prohibitions on use](#Prohibitions)
                |*   [No warranties](#warranties)
                |*   [Suspension](#Suspension)
                |*   [Liability and Indemnities](#Liability)
                |*   [General](#General)
                |*   [Contact details](#Contact)
                |*   [Appendix](#Appendix)
                |
                |* * *
                |
                |About this Site
                |---------------
                |
                |The Site is provided by ABCD Bank Plc (“**ABCD**”, “**we**” and “**us**”) and provides documentation for certain Testing Facilities and various ABCD Group APIs.
                |
                |ABCD Bank plc is authorised by the Prudential Regulation Authority and regulated by the Financial Conduct Authority and the Prudential Regulation Authority. It is listed with the registration number 114216. ABCD Bank Plc is a company incorporated under the laws of England and Wales with company registration number 14259  and its registered office at 8 Canada Square,  ABCD Bank Plc’s registered VAT number is GB 365684514.
                |
                |This document sets out the terms and conditions ("**Terms**") of this Site. These Terms govern your use of this Site together with the provision and use of any interface or data obtained through on or accessed via this Site including the APIs.
                |
                |By accessing this Site, you agree to be bound by these Terms so please read these Terms carefully. The [Privacy Notice](/privacy-notice "Privacy notice") and [Cookie Notice](/cookie-notice "Cookie notice") explain how we collect, use and share your personal information and what information we store on or read from the device you use to access this Site. We may change the content or services found on this Site at any time without notice, and / or these Terms, the Cookies Notice or the Privacy Notice at any time without notice. You should visit this page regularly to check for any changes, and by continuing to use this Site you are agreeing to any changes to these Terms.  These Terms were last amended on 29th March 2021.
                |
                |We will not charge you for access to this Site and we do not guarantee that the Site, or any content on it, will always be available or uninterrupted.   We may suspend or restrict access to the Site or any part of it for business, regulatory or operational purposes.    We will endeavour to give reasonable notice of any suspension .      
                |
                |**If you do not agree with these Terms, any relevant Licence Terms, the Privacy Notice or our use of cookies as set out in the Cookies Notice, please do not use this Site or the APIs.** 
                |
                |* * *
                |
                |Definitions
                |-----------
                |
                |1.  **APIs** means the API provided by ABCD Group and made available on the Site. For the avoidance of doubt APIs does not include any third party APIs which may be offered through this Site.
                |2.  **API Documentation** means any information, guidance, specifications or other documentation and information provided via this Site or otherwise to you about the APIs, this Site or the Testing Facilities.
                |3.  **Developer** means a third party who provides technology services to you: (i) to enable you to access and use any element of the APIs or Testing Facilities; (ii) to support your API Testing and / or (iii) to facilitate your integration of and / or to maintain the integration of any of the APIs into your application.
                |4.  **ABCD** means ABCD UK Bank plc, and references to "we", "us" or "our" are references to ABCD and / or the ABCD Group, as applicable.
                |5.  **API Terms** means any terms and conditions that apply to your use of any API, or to the integration of any API with a system or service.
                |6.  **API Testing** means your (or a Developer acting on your behalf’s) testing of any application developed or used by you, or developed on your behalf, for the purposes of enabling your access to, benefit from, reliance on and / or interaction or integration with the APIs;
                |7.  **ABCD Group** ABCD Holdings plc, its subsidiaries, related bodies corporate, associated entities and undertakings and any of their branches. full details of all relevant members are available at Appendix 1.
                |8.  **Intellectual Property Rights** means any right, title or interest in: copyrights, rights in databases, patents, inventions, patents, trademarks, trade names, goodwill, rights in internet domain names and website addresses, designs, know how, trade secrets and other rights in confidential information, whether registered, unregistered or not capable of being registered in any country or jurisdiction including all other rights having equivalent or similar affects which may now or in the future subsist anywhere in the world.
                |9.  **Licence Terms** means any licence applying to the use of any third party APIs (other than those provided by ABCD available on this Site) including but not  limited to the Open Banking Open Licence (available: [https://www.openbanking.org.uk/wp-content/uploads/Open-Licence.pdf](https://www.openbanking.org.uk/wp-content/uploads/Open-Licence.pdf)).
                |10.  **Site** means https://develop.ABCD.com.
                |11.  **Site Account** means any user account created on this Site.
                |12.  **Terms** means these Terms and Conditions.
                |13.  **Testing Documentation** means  any information, guidance, specifications or other documentation and information provided via this Site about the Testing Facilities.
                |14.  Testing Facilities means one or more testing environments, activities, or sandboxes that we provide or make available to you on this Site to: (i) access the API Documentation; and / or (ii) enable you to carry out API Testing.
                |15.  **You or you** means the person accessing this Site or using the APIs available via this Site.
                |
                |* * *
                |
                |Access and licenses
                |-------------------
                |
                |1.  In consideration of your compliance with the Terms, we grant you a non-exclusive, royalty free, revocable, non-transferable and non-sub licensable licence to access and use the Site, the Testing Facilities and the Testing Documentation.
                |2.  Access to certain restricted parts of the Site, the Testing Facilities, the Testing Documentation, the API Documentation and the APIs may be granted at our absolute discretion (or that of another member of the ABCD Group) and/or your satisfaction of regulatory requirements. Where such access is granted, it may be subject to your creation of a Site Account, your agreement to further terms or other obligations as we may require and may be withdrawn at any time without notice or liability (to the extent permissible by applicable law).
                |3.  Your use of any third party APIs  are subject to any relevant API Terms and any Additional Licence Terms. You are responsible for your access to the Site, use of the Testing Facilities and Testing Documentation and for all activities that occur whilst you are logged into your Site Account. If you know or suspect that anyone other than you knows your Site Account user name or password or any details relating to your organisation that might allow your Site Account to be used by others, you must notify us immediately by using the Support form found under the Resources menu.
                |4.  Your use of any third party links contained on this Site is at your own risk. We are not responsible for the content of any other web sites or pages linked to or from this Site and have not verified the content of any such web sites, or pages. 
                |5.  You must ensure compliance with all applicable law in connection with your use of this Site, the Testing Facilities, the Testing Documentation, the API Documentation and the APIs. 
                |
                |* * *
                |
                |Intellectual property rights
                |----------------------------
                |
                |1.  We or other members of the ABCD Group are the owner or licensee of all Intellectual Property Rights subsisting in the APIs, API Documentation, the Testing Facilities, the Testing Documentation and this Site. All rights in and to the APIs, the API Documentation, the Testing Facilities, the Testing Documentation and this Site are reserved.
                |2.  You acknowledge that we are the owner or the licensee of all Intellectual Property Rights in material published on this Site (including for the avoidance of doubt any user-generated information and comments posted by third parties on the Site). All such rights are reserved. 
                |3.  You may print off one copy, and may download extracts, of any page(s) from this Site for your own use.
                |4.  Nothing in these Terms shall be construed as granting any rights for you to use any ABCD logos, trademarks or branding.
                |
                |* * *
                |
                |Prohibitions on use
                |-------------------
                |
                |Except as may be permitted under applicable law, you must not use the APIs, API Documentation, the Testing Facilities, the Testing Documentation or this Site:
                |
                |1.  for any purpose which is unlawful, abusive, libelous, obscene or threatening;
                |2.  except to the extent expressly permitted under these Terms and under any API Terms, attempt to adapt, copy, modify, download, display, duplicate, create derivative works from, republish, transmit, or distribute all or any portion of the APIs, API Documentation, the Testing Facilities, the Testing Documentation or this Site.
                |3.  either in whole or part, attempt to reverse engineer, decompile, disassemble or otherwise reduce to human-perceivable form all or any part of the APIs, the Testing Facilities or this Site;
                |4.  for purposes of misuse by knowingly or unknowingly introducing viruses, trojans, worms, logic bombs or other material which is malicious or technologically harmful;
                |5.  for any purpose that could, or could potentially infringe the Intellectual Property Rights, privacy, confidentiality or other rights of any third party; or
                |6.  for any other purpose other than that for which your access was granted.
                |
                |* * *
                |
                |No warranties
                |-------------
                |
                |To the fullest extent permitted by law, and unless expressly set out to the contrary in these Terms: 
                |
                |1.  all warranties and terms which would otherwise be implied by law, custom or usage are excluded from these Terms;
                |2.  all access to this Site and (where you have been granted access) to the APIs and the Testing Facilities is made available to you on an "as is" and "as available" basis. We may suspend your access to, suspend, withdraw, discontinue or change, all or any of the Site, your Site Account, the Testing Facilities, the Testing Documentation, the API Documentation or the APIs at any time without notice or liability; 
                |3.  we make no representations, warranties or guarantees, whether express or implied that this Site, the Testing Facilities, the Testing Documentation, the API Documentation or the APIs (including any data obtained through the APIs) is accurate, complete or up-to-date although we do use reasonable care to ensure that this Site, the Testing Facilities, the Testing Documentation, the API Documentation and the APIs are accurate in so far as this is within our control.
                |4.  We do not guarantee that our Site will be secure or free from bugs or viruses, you are responsible for configuring and securing your own information technology, computer programmes, platforms and equipment and should use your own virus protection software.
                |
                |* * *
                |
                |Suspension
                |----------
                |
                |We may suspend your access to the Site, your Site Account, or terminate these Terms at any time if in our sole discretion:
                |
                |1.  you are in breach of any of the terms and conditions of these Terms;
                |2.  you or your Site Account suffer a security breach that impacts on the confidentiality or integrity of this Site; or
                |3.  we have a legitimate concern about your use of this Site or your Site Account.
                |
                |* * *
                |
                |Liability and Indemnities
                |-------------------------
                |
                |1.  Nothing in these Terms excludes or limits our liability for death or personal injury arising from our negligence, or our fraud or fraudulent misrepresentation, or any other liability that cannot be excluded or limited by law.
                |2.  To the full extent permitted by law, in no event will we, any other member of the ABCD Group, or any third party API providers be liable to you for any loss or damage (whether direct or indirect), whether in contract, tort (including negligence), breach of statutory duty, or otherwise, even if foreseeable, arising under or in connection with:
                |    1.  your use, or inability to use this Site, your Site Account, the Testing Facilities, the Testing Documentation, the API Documentation or the APIs; or
                |    2.  your use of or reliance on any content accessed through this Site or the APIs.
                |3.  Notwithstanding the remainder of this Clause 8, the total aggregate liability of the ABCD Group, whether in contract, tort (including negligence and breach of statutory duty, howsoever arising), misrepresentation (whether negligent or innocent), restitution or otherwise, arising under or in connection with these Terms shall be limited to £500 in aggregate.
                |
                |* * *
                |
                |General
                |-------
                |
                |1.  **Relationship**: Nothing in, and no action taken under, these Terms creates a relationship of principal and agent between you and ABCD or any other ABCD Group member, or otherwise authorises you to bind ABCD or any other ABCD Group member.
                |2.  **Third Party Rights**: A person who is not party to these Terms of Use may not enforce any term of these Terms under the Contracts (Rights of Third Parties) Act 1999.
                |3.  **Assignment**: you may not assign, transfer or permit the exercise by any other party of your rights under these Terms. We may subcontract, assign or novate these Terms or any of our rights to any member of the ABCD Group or third party.
                |4.  **Entire Agreement**: These Terms, together with the documents referred to in it, constitutes the entire agreement and understanding between the parties in respect of the matters dealt with in it and supersedes any previous agreement between the parties in relation to such matters.
                |5.  **Governing Law and Jurisdiction**: The Terms are governed by and interpreted in accordance with the laws of England and Wales and the courts of the above jurisdiction will have exclusive jurisdiction in respect of any dispute, which may arise.
                |
                |* * *
                |
                |Contact Details
                |---------------
                |
                |If you have any questions in respect of these terms, please send us a message using the Support form found under the Resources menu or contact your Integration Manager (Corporate customers).  
                | 
                |
                |[Return to top](#Top)
                |
                |* * *
                |
                |Appendix
                |--------
                |
                |Jurisdiction
                |
                |Legal entity
                |
                |Full details
                |
                |UK
                |
                |ABCD UK Bank plc
                |
                |  
                |ABCD Bank plc  
                | 
                |
                |Marks & Spencer Financial Services plc
                |
                |ABCD UK Bank plc is authorised by the Prudential Regulation Authority and regulated by the Financial Conduct Authority and the Prudential Regulation Authority. It is listed with the registration number 765112. ABCD UK Bank plc is a company incorporated under the laws of England and wales with company registration number 9928412 and its registered office at 1 Centenary Square, Birmingham, B1 1HQ. ABCD UK Bank plc’s registered VAT number is GB 365684514. first direct is a division of ABCD UK Bank plc  
                |  
                |ABCD Bank plc is authorised by the Prudential Regulation Authority and regulated by the Financial Conduct Authority and the Prudential Regulation Authority. It is listed with the registration number 114216. ABCD Bank plc is a company incorporated under the laws of England and Wales with company registration number 14259 and its registered office at 8 Canada Square, London E14 5HQ. ABCD Bank plc’s registered VAT number is GB 365684514  
                |  
                |M&S Bank is a trading name of Marks & Spencer Financial Services plc. Registered in England No. 1772585. Registered Office: Kings Meadow, Chester, CH99 9FB. Authorised by the Prudential Regulation Authority and regulated by the Financial Conduct Authority and the Prudential Regulation Authority. Marks & Spencer Financial Services plc is entered in the Financial Services Register under reference number 151427. M&S Bank is part of the ABCD Group. Marks & Spencer is a registered trademark of Marks and Spencer plc and is used under licence. © Marks & Spencer Financial Services plc 2018. All rights reserved
                |
                |Germany
                |
                |ABCD Trinkaus & Burkhardt AG
                |
                |Regulatory authority:  
                |  
                |German Federal Financial Supervisory Authority (Bundesanstalt für Finanzdienstleistungsaufsicht), Graurheindorfer Str. 108, 53117 Bonn, Germany and Marie-Curie-Straße 24-28, 60439 Frankfurt am Main, Germany  
                |  
                |European Central Bank, Sonnemannstraße 20, 60314 Frankfurt am Main, Germany  
                |  
                |Commercial Register entries:  
                |  
                |Dusseldorf District Court, commercial register no. HRB 54447  
                |  
                |VAT ID No.:  
                |  
                |DE 121310482  
                |  
                |BIC: TUBDDEDDXXX  
                |  
                |LEI: JUNT405OW8OY5GN4DX16  
                | 
                |
                |France
                |
                |ABCD Continental Europe (previously ABCD France)
                |
                |ABCD Continental Europe is supervised by the European Central Bank (ECB), as part of the Single Supervisory Mechanism (SSM), the French Prudential Supervisory and Resolution Authority (l’Autorité de Contrôle Prudentiel et de Résolution 4, place de Budapest, CS 92459, 75436 Paris Cedex 09, France) (ACPR) as the French National Competent Authority and the French Financial Markets Authority (l’Autorité des Marchés Financiers (AMF) for the activities carried out over financial instruments or in financial markets. Further, ABCD France is registered as an insurance broker with the French Organisation for the Registration of financial intermediaries (Organisme pour le Registre unique des Intermédiaires en Assurance, banque et finance – www.orias.fr) under nr.07005894.
                |
                |Czech Republic
                |
                |ABCD Continental Europe, pobočka Praha
                |
                |ABCD Continental Europe a company incorporated under the laws of France as a société anonyme (registered number 775 670 284 RCS Paris), having its registered office at 103, avenue des Champs-Elysées, 75008 Paris, France, is authorised as a credit institution and investment services provider by the Autorité de Contrôle Prudentiel et de Résolution (ACPR), regulated by the Autorité des Marchés Financiers (AMF) and the ACPR and controlled by the European Central Bank and is lawfully established in the Czech Republic through a branch ABCD France - pobočka Praha with its registered office at Na Florenci 2116/15, Nové Město, 110 00 Praha, the Czech Republic, identification number 07482728, registered in the Commercial Register kept by the Municipal Court in Prague, Section A, Insert 78901.
                |
                |Mexico
                |
                |ABCD Mexico plc., Multiple Banking Institution Financial Group ABCD
                |
                |ABCD México, S.A., Institución de Banca  
                |Múltiple, Grupo Financiero ABCD, having its registered office at en Paseo de la Reforma No.347,   
                |Col. Cuauhtémoc, C.P. 06500, México D.F.
                |
                |Malta
                |
                |ABCD Bank Malta plc
                |
                |ABCD Bank Malta p.l.c. (ABCD Bank) having registration number C 3177 and its registered office at 116, Archbishop Street, Valletta, VLT 1444, Malta.
                |
                |Greece
                |
                |ABCD Continental Europe, Athens Branch
                |
                |ABCD Continental Europe is lawfully established in Greece as a branch, duly registered with the General Commercial Registry (GEMI), with registered office at 109-111 Messoghion Ave., Athens. ABCD Continental Europe, Athens branch is authorized by the ECB, the ACPR and the Bank of Greece; its banking activities in Greece are further subject to limited supervision by the Bank of Greece (21 Eleftheriou Venizelou, Athens) and the Hellenic Capital Market Commission (1 Kolokotroni, Athens) exclusively with regard to the issues provided for by the applicable legislation.
                |
                |Ireland
                |
                |ABCD Continental Europe, Dublin Branch
                |
                |ABCD Continental Europe, Dublin Branch which is a registered business name of ABCD Continental Europe, a branch registered in Ireland (registration number 908966) having its registered office at 1 Grand Canal Square, Grand Canal Harbour, Dublin 2, D02 P820 and regulated by the Central Bank of Ireland for conduct of business rules.
                |
                |Poland
                |
                |ABCD Continental Europe (Spółka Akcyjna) Oddział w Polsce
                |
                |ABCD Continental Europe (Spółka Akcyjna) Oddział w Polsce, with its seat in Warsaw, at Rondo ONZ 1, 00-124 Warsaw, and registered in the register of entrepreneurs of the National Court Register maintained by the District Court in Warsaw, XII Commercial Department of the National Court Register under KRS No. 0000757904, with a tax identification number NIP 107-00-41-832, the branch of ABCD Continental Europe, a French société anonyme with a share capital of EUR 428368915, fully paid up, whose corporate seat is 103 Avenue des Champs Elysées, 75008 Paris, registered with the Trade and Companies Registry of Paris (Registre du Commerce et des Societas) under number 775 670 284, with a Polish NIP number 107-00-41-803  
                | 
                |
                |Italy
                |
                |ABCD Continental Europe, Milan Branch
                |
                |ABCD Continental Europe is authorised as a credit institution and investment services provider by the Autorité de Contrôle Prudentiel et de Résolution (ACPR), regulated by the Autorité des Marchés Financiers (AMF) and the ACPR and controlled by the European Central Bank and is lawfully established in Italy through a branch with its registered office at Via Mike Bongiorno 13, 20124 Milan, registered at the Italian Chamber of Commerce under number 10470920967.
                |
                |Belgium
                |
                |ABCD Continental Europe, Brussels Branch
                |
                |ABCD Continental Europe, Brussels Branch is a branch of ABCD Continental Europe.  
                |  
                |ABCD France is incorporated under the laws of France as a société anonyme (SIREN number 775 670 284 RCS Paris), having its registered office at 103, avenue des Champs-Elysées, 75008 Paris, France  
                |  
                |ABCD France, Brussels Branch is located Square de Meeûs 23, 1000 Brussels with registered number 0708.865.310.  
                | 
                |
                |Netherlands
                |
                |ABCD Continental Europe, Amsterdam Branch
                |
                |ABCD Continental Europe, Amsterdam Branch is located De Entree 236, 1101 EE Amsterdam Z.O., the Netherlands, and is registered in the Trade Register of the Amsterdam Chamber of Commerce under number 000040776689.  
                |  
                |ABCD Continental Europe, Amsterdam Branch is a branch of ABCD Continental Europe.  
                |  
                |ABCD Continental Europe is incorporated under the laws of France as a société anonyme (SIREN number 775 670 284 RCS Paris), having its registered office at 103, avenue des Champs-Elysées, 75008 Paris, France.  
                | 
                |
                |Spain
                |
                |ABCD Continental Europe, Sucursal en España
                |
                |ABCD Continental Europe, Sucursal en España, Spanish Tax ID number: W-2502598-B. – Number of Register in Bank of Spain (Branches of Foreign EU Credit Entities Register) under the number 0162), with registered address at Torre Picasso, Planta 33, Pza. Pablo Ruiz Picasso, nº 1 - 28020 Madrid. Registered in the Mercantile Registry of Madrid under the codes T.38.314, Secc. 8 (L.Sociedades), F.1, M-681702, Ins, 1ª. and supervised by the Bank of Spain and the National Securities Market Commission (“CNMV”).  
                |  
                |ABCD Continental Europe, Sucursal en España is a full branch of ABCD France, S.A. - SIREN number 775 670 284 and based in 103, avenue des Champs-Elysées, 75008 Paris, (France) and is supervised by the European Central Bank (“ECB”) and regulated by the Autorité de Contrôle Prudentiel et de Résolution (“ACPR”) and the Autorité des Marchés Financiers (“AMF”).  
                | 
                |
                |CIIOM
                |
                |ABCD Bank plc Jersey Branch  
                |  
                |ABCD Bank plc Guernsey Branch  
                |  
                |Isle of Man ABCD Bank plc  
                | 
                |
                |ABCD Bank plc Jersey Branch is regulated by the Jersey Financial Services Commission for Banking, General Insurance Mediation and Investment Business.  
                |  
                |ABCD Bank plc Guernsey Branch is licensed by the Guernsey Financial Services Commission for Banking, Insurance and Investment Business.  
                |  
                |In the Isle of Man ABCD Bank plc is licensed by the Isle of Man Financial Services Authority.  
                | 
                |
                |Luxembourg
                |
                |ABCD Private Bank (Luxembourg) S.A.
                |
                |ABCD Private Bank (Luxembourg) S.A., having its address at 16, Boulevard d’Avranches, L-1160 Luxembourg, Luxembourg.
                |
                |Bahrain
                |
                |ABCD Bank Middle East Limited Bahrain Branch
                |
                |ABCD Bank Middle East Limited Bahrain Branch, P.O. Box 57, Manama, Kingdom of Bahrain, licensed and regulated by the Central Bank of Bahrain as a Conventional Retail Bank for the purpose of this promotion and lead regulated by the Dubai Financial Services Authority.
                |
                |[Return to top](#Top)""".stripMargin
    val html = PegdownOptions.convertGitHubDocMarkdownToHtml(markdownText)
  }

  "description string" should "test the markdown * -> html <li> tag" taggedAs FunctionsTag in {

    // This string is from Foobar Property List: format
    val descriptionString = """Update exists Foo Bar33.

Description of this entity, can be markdown text.


**Property List:**

* name: * description of **name** field, can be markdown text.
* number: * description of **number** field, can be markdown text.



Authentication is Mandatory""".stripMargin
    val descriptionHtml= convertPegdownToHtmlTweaked(descriptionString)
    val descriptionApiExplorer = stringToNodeSeq(descriptionHtml)

    descriptionHtml contains("<li>name: * description of <strong>name</strong> field, can be markdown text.</li>") should be (true)


    //This string is from obp JSON response body fields: format
    val descriptionString2 ="""Get basic information about the Adapter listening on behalf of this bank.
                              |
                              |Authentication is Optional
                              |
                              |      **URL Parameters:**
                              |
                              |* [BANK_ID](/glossary#Bank.bank_id):gh.29.uk
                              |
                              |""".stripMargin
    val descriptionHtml2= convertPegdownToHtmlTweaked(descriptionString2)

    descriptionHtml2 contains("<li><a href=\"/glossary#Bank.bank_id\">BANK_ID</a>:gh.29.uk</li>") should be (true)
    
  }


  "description string" should " Authentication is Mandatory should have more space " taggedAs FunctionsTag in {

    // This string is from Foobar Property List: format
    val descriptionString = """Update exists Foo Bar33.

Description of this entity, can be markdown text.


**Property List:**

* name: * description of **name** field, can be markdown text.
* number: * description of **number** field, can be markdown text.



Authentication is Mandatory""".stripMargin
    val descriptionHtml= convertPegdownToHtmlTweaked(descriptionString)
    val descriptionApiExplorer = stringToNodeSeq(descriptionHtml)

    descriptionHtml contains("<li>name: * description of <strong>name</strong> field, can be markdown text.</li>") should be (true)


    //This string is from obp JSON response body fields: format
    val descriptionString2 ="""See [FPML](http://www.fpml.org/) for more examples.
                              |
                              |The type field must be one of "STRING", "INTEGER", "DOUBLE" or DATE_WITH_DAY"
                              |
                              |Authentication is Mandatory
                              |
                              |**URL Parameters:**
                              |
                              |
                              |
                              |* [ACCOUNT_ID](/glossary#Account.account_id): 8ca8a7e4-6d02-40e3-a129-0b2bf89de9f0
                              |""".stripMargin
    val descriptionHtml2= convertPegdownToHtmlTweaked(descriptionString2)

    descriptionHtml2 contains("<p>Authentication is Mandatory</p>") should be (true)

    
    val descriptionString3 ="""Returns information about:
      |
      |* The default bank_id
      |* Akka configuration
      |* Elastic Search configuration
      |* Cached functions
      |
      |Authentication is Mandatory
      |
      |
      |**JSON response body fields:**
      |
      |
      |
      |* [akka](/glossary#Adapter.Akka.Intro): no-example-provided
      |""".stripMargin

    val descriptionHtml3= convertPegdownToHtmlTweaked(descriptionString3)

    descriptionHtml3 contains("<p>Authentication is Mandatory</p>") should be (true)

  }
}
