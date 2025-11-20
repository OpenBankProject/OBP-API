OBP Documentation PDF Generation - Font Technical Note
=======================================================

FONT ISSUE: Plus Jakarta Sans Variable Font Incompatibility with XeLaTeX

The Open Bank Project brand guidelines specify Plus Jakarta Sans as the official font.
However, this font cannot be used in the current PDF generation pipeline due to a
technical incompatibility between variable font formats and XeLaTeX/xdvipdfmx.

TECHNICAL ROOT CAUSE:

The Plus Jakarta Sans fonts available from Google Fonts are provided as variable fonts
(.ttf files with weight axis [wght]). When XeLaTeX attempts to resolve font variants
(specifically Bold and BoldItalic), it fails with:

  Error: "xdvipdfmx:fatal: Invalid font: -1 (0)"

This occurs because:
1. Variable fonts store multiple weights in a single file using OpenType variation tables
2. XeLaTeX's font resolver (fontspec package) attempts to find discrete font files for
   Bold (/B), Italic (/I), and BoldItalic (/BI) variants
3. The variable font doesn't expose these as separate font instances
4. xdvipdfmx (the backend that converts XeTeX's XDV to PDF) cannot map the font ID (-1)

ATTEMPTED SOLUTIONS (All Failed):

1. Using variable fonts with explicit paths and FakeBold features - syntax errors
2. Installing static font variants - these don't exist in Google Fonts repository
3. Font name resolution via fc-list - system finds font but XeLaTeX cannot load variants
4. Explicit fontspec configuration with BoldFont/ItalicFont parameters - path resolution failures

CURRENT SOLUTION:

DejaVu Sans is used as a reliable alternative because:
- It's a standard PostScript/TrueType font with discrete weight files
- Pre-installed on most Linux systems
- Full XeLaTeX compatibility
- All OBP brand colors (#1BA563, #0A281E, #5F8E82) are correctly implemented

FUTURE RESOLUTION OPTIONS:

- Upgrade to TeX Live 2023+ with improved variable font support
- Switch to LuaLaTeX engine (better variable font handling)
- Convert variable fonts to static instances using fonttools
- Use HTML/CSS to PDF tools (wkhtmltopdf, weasyprint) that handle variable fonts natively

For questions, contact: TESOBE GmbH Development Team
