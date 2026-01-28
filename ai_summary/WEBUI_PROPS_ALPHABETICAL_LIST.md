# WebUI Props - Alphabetical List

Complete list of all `webui_*` properties used in OBP-API, sorted alphabetically.

These properties can be:
- Set in props files (e.g., `default.props`)
- Stored in the database via WebUiProps table
- Retrieved via API: `GET /obp/v6.0.0/webui-props/{PROP_NAME}`

---

## Complete List (56 properties)

1. `webui_agree_terms_url`
2. `webui_api_documentation_bottom_url`
3. `webui_api_documentation_url`
4. `webui_api_explorer_url`
5. `webui_api_manager_url`
6. `webui_customer_user_invitation_email_from`
7. `webui_customer_user_invitation_email_html_text`
8. `webui_customer_user_invitation_email_subject`
9. `webui_customer_user_invitation_email_text`
10. `webui_developer_user_invitation_email_from`
11. `webui_developer_user_invitation_email_html_text`
12. `webui_developer_user_invitation_email_subject`
13. `webui_developer_user_invitation_email_text`
14. `webui_direct_login_documentation_url`
15. `webui_dummy_user_logins`
16. `webui_external_consumer_registration_url`
17. `webui_faq_data_text`
18. `webui_faq_email`
19. `webui_faq_url`
20. `webui_favicon_link_url`
21. `webui_featured_sdks_external_link`
22. `webui_footer2_logo_left_url`
23. `webui_footer2_middle_text`
24. `webui_get_started_text`
25. `webui_header_logo_left_url`
26. `webui_header_logo_right_url`
27. `webui_index_page_about_section_background_image_url`
28. `webui_index_page_about_section_text`
29. `webui_legal_notice_html_text`
30. `webui_login_button_text`
31. `webui_login_page_instruction_title`
32. `webui_login_page_special_instructions`
33. `webui_main_faq_external_link`
34. `webui_main_partners`
35. `webui_main_style_sheet`
36. `webui_oauth_1_documentation_url`
37. `webui_oauth_2_documentation_url`
38. `webui_obp_cli_url`
39. `webui_override_style_sheet`
40. `webui_page_title_prefix`
41. `webui_post_consumer_registration_more_info_text`
42. `webui_post_consumer_registration_more_info_url`
43. `webui_post_consumer_registration_submit_button_value`
44. `webui_post_user_invitation_submit_button_value`
45. `webui_post_user_invitation_terms_and_conditions_checkbox_value`
46. `webui_privacy_policy`
47. `webui_privacy_policy_url`
48. `webui_sandbox_introduction`
49. `webui_sdks_url`
50. `webui_show_dummy_user_tokens`
51. `webui_signup_body_password_repeat_text`
52. `webui_signup_form_submit_button_value`
53. `webui_signup_form_title_text`
54. `webui_social_handle`
55. `webui_social_logo_url`
56. `webui_social_title`
57. `webui_social_url`
58. `webui_subscriptions_button_text`
59. `webui_subscriptions_invitation_text`
60. `webui_subscriptions_url`
61. `webui_support_email`
62. `webui_support_platform_url`
63. `webui_terms_and_conditions`
64. `webui_top_text`
65. `webui_user_invitation_notice_text`
66. `webui_vendor_support_html_url`

---

## Properties by Category

### Branding & UI
- `webui_favicon_link_url`
- `webui_footer2_logo_left_url`
- `webui_footer2_middle_text`
- `webui_header_logo_left_url`
- `webui_header_logo_right_url`
- `webui_index_page_about_section_background_image_url`
- `webui_index_page_about_section_text`
- `webui_main_style_sheet`
- `webui_override_style_sheet`
- `webui_page_title_prefix`
- `webui_top_text`

### Documentation & Links
- `webui_agree_terms_url`
- `webui_api_documentation_bottom_url`
- `webui_api_documentation_url`
- `webui_api_explorer_url`
- `webui_api_manager_url`
- `webui_direct_login_documentation_url`
- `webui_external_consumer_registration_url`
- `webui_faq_url`
- `webui_featured_sdks_external_link`
- `webui_main_faq_external_link`
- `webui_oauth_1_documentation_url`
- `webui_oauth_2_documentation_url`
- `webui_obp_cli_url`
- `webui_privacy_policy_url`
- `webui_sdks_url`
- `webui_support_platform_url`
- `webui_vendor_support_html_url`

### Login & Signup
- `webui_login_button_text`
- `webui_login_page_instruction_title`
- `webui_login_page_special_instructions`
- `webui_signup_body_password_repeat_text`
- `webui_signup_form_submit_button_value`
- `webui_signup_form_title_text`

### Legal & Terms
- `webui_legal_notice_html_text`
- `webui_privacy_policy`
- `webui_terms_and_conditions`

### User Invitations - Customer
- `webui_customer_user_invitation_email_from`
- `webui_customer_user_invitation_email_html_text`
- `webui_customer_user_invitation_email_subject`
- `webui_customer_user_invitation_email_text`

### User Invitations - Developer
- `webui_developer_user_invitation_email_from`
- `webui_developer_user_invitation_email_html_text`
- `webui_developer_user_invitation_email_subject`
- `webui_developer_user_invitation_email_text`

### User Invitations - General
- `webui_post_user_invitation_submit_button_value`
- `webui_post_user_invitation_terms_and_conditions_checkbox_value`
- `webui_user_invitation_notice_text`

### Consumer Registration
- `webui_external_consumer_registration_url` (defaults to `webui_api_explorer_url` + `/consumers/register`)
- `webui_post_consumer_registration_more_info_text`
- `webui_post_consumer_registration_more_info_url`
- `webui_post_consumer_registration_submit_button_value`

### Developer Tools
- `webui_dummy_user_logins`
- `webui_show_dummy_user_tokens`

### Support & Contact
- `webui_faq_data_text`
- `webui_faq_email`
- `webui_support_email`

### Social Media
- `webui_social_handle`
- `webui_social_logo_url`
- `webui_social_title`
- `webui_social_url`

### Subscriptions
- `webui_subscriptions_button_text`
- `webui_subscriptions_invitation_text`
- `webui_subscriptions_url`

### Other
- `webui_get_started_text`
- `webui_main_partners`
- `webui_sandbox_introduction`

---

## Environment Variable Mapping

WebUI props can be set via environment variables with the `OBP_` prefix:

```bash
# Props file format:
webui_api_explorer_url=https://apiexplorer.example.com

# Environment variable format:
OBP_WEBUI_API_EXPLORER_URL=https://apiexplorer.example.com
```

**Conversion rule:** 
- Add `OBP_` prefix
- Convert to UPPERCASE
- Replace `.` with `_`

---

## API Endpoints

### Get Single WebUI Prop
```
GET /obp/v6.0.0/webui-props/{WEBUI_PROP_NAME}
GET /obp/v6.0.0/webui-props/{WEBUI_PROP_NAME}?active=true
```

### Get All WebUI Props
```
GET /obp/v6.0.0/management/webui_props
GET /obp/v6.0.0/management/webui_props?what=active
GET /obp/v6.0.0/management/webui_props?what=database
GET /obp/v6.0.0/management/webui_props?what=config
```

---

## Usage Examples

### In Props File
```properties
webui_api_explorer_url=https://apiexplorer.openbankproject.com
webui_header_logo_left_url=https://static.openbankproject.com/logo.png
webui_override_style_sheet=https://static.openbankproject.com/css/custom.css
```

### As Environment Variables
```yaml
env:
  - name: OBP_WEBUI_API_EXPLORER_URL
    value: 'https://apiexplorer.openbankproject.com'
  - name: OBP_WEBUI_HEADER_LOGO_LEFT_URL
    value: 'https://static.openbankproject.com/logo.png'
  - name: OBP_WEBUI_OVERRIDE_STYLE_SHEET
    value: 'https://static.openbankproject.com/css/custom.css'
```

---

## Notes

- All webui props are **optional** - the system has default values
- Database values take **precedence** over props file values
- Use `?active=true` query parameter to get database value OR fallback to default
- Props are case-sensitive (always use lowercase `webui_`)
- User invitation email props were renamed in Sept 2021 (see release notes)

---

## Related Documentation

- API Glossary: `webui_props` entry
- User Invitation Guide: `USER_INVITATION_API_ENDPOINTS.md`
- WebUI Props Endpoint: `WEBUI_PROP_SINGLE_GET_ENDPOINT.md`

---

**Total Count:** 65 webui properties