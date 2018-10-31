# Branding of the OBP API landing page

Look and feel of the API landing page can be modified via css and with a number of variables in the props file (production.default.props).

## CSS

The default css is located here:

OBP-API/src/main/webapp/media/css/website.css

## Props

The following props variables can be used (see also  OBP-API/src/main/resources/props/sample.props.template ):

```
# Main style sheet
webui_main_style_sheet = /media/css/website.css

# Override certain elements (with important styles)
webui_override_style_sheet = https://path.to/override.css

webui_header_logo_left_url = /media/images/logo.png
webui_header_logo_right_url =
webui_index_page_about_section_background_image_url = /media/images/about-background.jpg
webui_index_page_about_section_text = <p class="about-text"> \
                                        Welcome to the API Sandbox powered by the Open Bank Project! <br/> \
                                      </p>
webui_index_page_start_building_text = <h1>Start building your ideas now!</h1>
webui_get_started_text = Start building your ideas now!


# Show the "For Banks: API Manager / API Tester" buttons
webui_display_for_banks_section = true 

# Top text appears on default.html For branding next to logo(s)
webui_top_text = ""

# Bottom Footer logo
#webui_footer2_logo_left_url =
# Bottom Footer middle text
#webui_footer2_middle_text =


# API Explorer URL, change to your instance
webui_api_explorer_url = https://apiexplorer.openbankproject.com

# Sofit URL, change to your instance
webui_sofi_url = https://sofi.openbankproject.com

# API Manager URL, change to your instance
webui_api_manager_url = https://apimanager.openbankproject.com

# API Tester URL, change to your instance
webui_api_manager_url = https://apitester.openbankproject.com

# Starting page of documentation. Change this if you have a specific landing page.
webui_api_documentation_url = https://github.com/OpenBankProject/OBP-API/wiki


# To display a custom message above the username / password box
# We currently use this to display example customer login in sandbox etc.
webui_login_page_special_instructions =

# Link for SDKs
webui_sdks_url = https://github.com/OpenBankProject/OBP-API/wiki/OAuth-Client-SDKS


# Text about data in FAQ
webui_faq_data_text = We use real data and customer profiles which have been anonymized.

# Link to FAQ
webui_faq_url = https://openbankproject.com/faq/

# Email address in FAQ for further inquiries
webui_faq_email = contact@openbankproject.com

# Link to support platform
webui_support_platform_url = https://slack.openbankproject.com/

# Link to Direct Login glossary on api explorer
webui_faq_direct_login_url =

# Link to Privacy Policy on signup page
webui_agree_privacy_policy_url = https://openbankproject.com/privacy-policy

# For partner logos and links
webui_main_partners=[\
{"logoUrl":"http://www.example.com/images/logo.png", "homePageUrl":"http://www.example.com", "altText":"Example 1"},\
{"logoUrl":"http://www.example.com/images/logo.png", "homePageUrl":"http://www.example.com", "altText":"Example 2"}]

# Prefix for all page titles (note the trailing space!)
webui_page_title_prefix = Open Bank Project: 

# Link to agree to Terms & Conditions, shown on signup page
webui_agree_terms_url =

# Additional html content at page bottom (between "For technical support" and footer)
webui_about_vendor_content_url = https://path.to/file.html


# If we want to gather more information about an Application / Startup fill this url and text
# Will be displayed on the post Consumer Registration page.
#webui_post_consumer_registration_more_info_url =
#webui_post_consumer_registration_more_info_text = Please tell us more your Application and / or Startup using this link.
```
