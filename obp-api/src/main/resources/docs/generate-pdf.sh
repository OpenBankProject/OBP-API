#!/bin/bash
# generate-pdf.sh - Generate professional PDF from markdown documentation
# Usage:
#   ./generate-pdf.sh              # Process all .md files in current directory
#   ./generate-pdf.sh [input.md]   # Process specific file
#   ./generate-pdf.sh [input.md] [output.pdf]  # Process with custom output name
#
# This script uses Open Bank Project brand guidelines:
# - Font: DejaVu Sans (for compatibility)
# - Colors: OBP Green (#1BA563), Dark Green (#0A281E), Light Green (#5F8E82)

# Note: Not using 'set -e' to allow batch processing to continue on errors

# Check if pandoc is installed
if ! command -v pandoc &> /dev/null; then
    echo "Error: pandoc is not installed"
    echo "Install with: sudo apt-get install pandoc texlive-xetex"
    exit 1
fi

# Check if xelatex is available
if ! command -v xelatex &> /dev/null; then
    echo "Error: xelatex is not installed"
    echo "Install with: sudo apt-get install texlive-xetex texlive-fonts-extra"
    exit 1
fi

# OBP Brand Colors (defined in LaTeX header)
OBP_GREEN="1BA563"        # Primary green: RGB(27, 165, 99)
OBP_DARK_GREEN="0A281E"   # Dark green: RGB(10, 40, 30)
OBP_LIGHT_GREEN="5F8E82"  # Secondary green: RGB(95, 142, 130)

# Create LaTeX header with OBP branding
LATEX_HEADER=$(cat <<'EOF'
\usepackage{xcolor}
\definecolor{OBPGreen}{HTML}{1BA563}
\definecolor{OBPDarkGreen}{HTML}{0A281E}
\definecolor{OBPLightGreen}{HTML}{5F8E82}
\usepackage{fontspec}
\defaultfontfeatures{Ligatures=TeX}
\setmainfont{DejaVu Sans}
\setsansfont{DejaVu Sans}
\setmonofont{DejaVu Sans Mono}[Scale=0.9]
\usepackage{fancyhdr}
\pagestyle{fancy}
\fancyhf{}
\fancyhead[L]{\textcolor{OBPDarkGreen}{\small\leftmark}}
\fancyhead[R]{\textcolor{OBPDarkGreen}{\small\thepage}}
\fancyfoot[C]{\textcolor{OBPLightGreen}{\tiny Copyright © TESOBE GmbH 2025, License: AGPLv3}}
\renewcommand{\headrulewidth}{0.5pt}
\renewcommand{\footrulewidth}{0.5pt}
\renewcommand{\headrule}{\hbox to\headwidth{\color{OBPGreen}\leaders\hrule height \headrulewidth\hfill}}
\renewcommand{\footrule}{\hbox to\headwidth{\color{OBPLightGreen}\leaders\hrule height \footrulewidth\hfill}}
\usepackage{titlesec}
\titleformat{\chapter}[display]
  {\normalfont\huge\bfseries\color{OBPGreen}}
  {\chaptertitlename\ \thechapter}{20pt}{\Huge}
\titleformat{\section}
  {\normalfont\Large\bfseries\color{OBPGreen}}
  {\thesection}{1em}{}
\titleformat{\subsection}
  {\normalfont\large\bfseries\color{OBPDarkGreen}}
  {\thesubsection}{1em}{}
\titleformat{\subsubsection}
  {\normalfont\normalsize\bfseries\color{OBPDarkGreen}}
  {\thesubsubsection}{1em}{}
\usepackage{listings}
\lstset{
  basicstyle=\ttfamily\small,
  frame=single,
  rulecolor=\color{OBPLightGreen},
  backgroundcolor=\color{gray!5},
  breaklines=true,
  postbreak=\mbox{\textcolor{OBPGreen}{$\hookrightarrow$}\space},
  showstringspaces=false,
  commentstyle=\color{OBPDarkGreen},
  keywordstyle=\color{OBPGreen}\bfseries
}
EOF
)

# Function to generate PDF from a markdown file
generate_pdf() {
    local INPUT_FILE="$1"
    local OUTPUT_FILE="$2"
    local DOC_TITLE="$3"

    echo "  Processing: $INPUT_FILE"
    echo "  Output: $OUTPUT_FILE"

    pandoc "$INPUT_FILE" \
      -o "$OUTPUT_FILE" \
      --pdf-engine=xelatex \
      --highlight-style=tango \
      -V geometry:margin=1in \
      -V fontsize=11pt \
      -V documentclass=report \
      -V papersize=a4 \
      -V colorlinks=true \
      -V linkcolor="[HTML]{$OBP_GREEN}" \
      -V urlcolor="[HTML]{$OBP_GREEN}" \
      -V toccolor="[HTML]{$OBP_DARK_GREEN}" \
      -V header-includes="$LATEX_HEADER" \
      --metadata title="$DOC_TITLE" \
      --metadata author="TESOBE GmbH" \
      --metadata date="Generated: $(date '+%Y-%m-%d %H:%M:%S %Z')" \
      2>&1 | grep -v "^$" || true

    if [ -f "$OUTPUT_FILE" ]; then
        echo "  ✓ Success ($(du -h "$OUTPUT_FILE" | cut -f1))"
        return 0
    else
        echo "  ✗ Failed"
        return 1
    fi
}

# Main script logic
if [ $# -eq 0 ]; then
    # No arguments - process all .md files in current directory
    echo "=========================================="
    echo "OBP Documentation PDF Generator"
    echo "=========================================="
    echo ""
    echo "Processing all markdown files in current directory..."
    echo ""

    MD_FILES=(*.md)

    if [ ${#MD_FILES[@]} -eq 0 ] || [ ! -f "${MD_FILES[0]}" ]; then
        echo "No markdown files found in current directory"
        exit 1
    fi

    SUCCESS_COUNT=0
    FAIL_COUNT=0
    TOTAL_COUNT=${#MD_FILES[@]}

    for MD_FILE in "${MD_FILES[@]}"; do
        if [ -f "$MD_FILE" ]; then
            # Generate output filename
            OUTPUT_FILE="${MD_FILE%.md}.pdf"

            # Generate document title from filename
            DOC_TITLE=$(echo "${MD_FILE%.md}" | sed 's/_/ /g' | sed 's/\b\(.\)/\u\1/g')

            echo "[$((SUCCESS_COUNT + FAIL_COUNT + 1))/$TOTAL_COUNT]"

            if generate_pdf "$MD_FILE" "$OUTPUT_FILE" "$DOC_TITLE"; then
                ((SUCCESS_COUNT++))
            else
                ((FAIL_COUNT++))
            fi
            echo ""
        fi
    done

    echo "=========================================="
    echo "Generation Complete"
    echo "=========================================="
    echo ""
    echo "Summary:"
    echo "  Total files: $TOTAL_COUNT"
    echo "  Successful: $SUCCESS_COUNT"
    echo "  Failed: $FAIL_COUNT"
    echo ""

    if [ $SUCCESS_COUNT -gt 0 ]; then
        echo "Generated PDFs:"
        ls -lh *.pdf 2>/dev/null | awk '{print "  " $9 " (" $5 ")"}'
        echo ""
    fi

    echo "PDF styling:"
    echo "  - Font: DejaVu Sans"
    echo "  - Colors: OBP brand palette"
    echo "    * Links: OBP Green (#$OBP_GREEN)"
    echo "    * Headers: Dark Green (#$OBP_DARK_GREEN)"
    echo "    * Accents: Light Green (#$OBP_LIGHT_GREEN)"
    echo "  - Table of contents: 3 levels"
    echo "  - Section numbering: From source markdown"
    echo "  - Headers/footers: OBP branded"
    echo ""

    if [ $FAIL_COUNT -gt 0 ]; then
        exit 1
    fi

elif [ $# -eq 1 ]; then
    # One argument - process specific file
    INPUT="$1"
    OUTPUT="${INPUT%.md}.pdf"

    if [ ! -f "$INPUT" ]; then
        echo "Error: Input file '$INPUT' not found"
        exit 1
    fi

    DOC_TITLE=$(echo "${INPUT%.md}" | sed 's/_/ /g' | sed 's/\b\(.\)/\u\1/g')

    echo "=========================================="
    echo "OBP Documentation PDF Generator"
    echo "=========================================="
    echo ""

    if generate_pdf "$INPUT" "$OUTPUT" "$DOC_TITLE"; then
        echo ""
        echo "=========================================="
        echo "[SUCCESS] PDF generated successfully!"
        echo "=========================================="
        echo ""
        ls -lh "$OUTPUT"
        echo ""
    else
        echo ""
        echo "=========================================="
        echo "[FAIL] Error generating PDF"
        echo "=========================================="
        echo ""
        exit 1
    fi

elif [ $# -eq 2 ]; then
    # Two arguments - custom input and output
    INPUT="$1"
    OUTPUT="$2"

    if [ ! -f "$INPUT" ]; then
        echo "Error: Input file '$INPUT' not found"
        exit 1
    fi

    DOC_TITLE=$(echo "${INPUT%.md}" | sed 's/_/ /g' | sed 's/\b\(.\)/\u\1/g')

    echo "=========================================="
    echo "OBP Documentation PDF Generator"
    echo "=========================================="
    echo ""

    if generate_pdf "$INPUT" "$OUTPUT" "$DOC_TITLE"; then
        echo ""
        echo "=========================================="
        echo "[SUCCESS] PDF generated successfully!"
        echo "=========================================="
        echo ""
        ls -lh "$OUTPUT"
        echo ""
    else
        echo ""
        echo "=========================================="
        echo "[FAIL] Error generating PDF"
        echo "=========================================="
        echo ""
        exit 1
    fi

else
    echo "Usage:"
    echo "  $0                    # Process all .md files in current directory"
    echo "  $0 [input.md]         # Process specific file"
    echo "  $0 [input.md] [out.pdf]  # Process with custom output name"
    exit 1
fi
