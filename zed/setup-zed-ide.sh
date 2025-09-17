#!/bin/bash

# ZED IDE Complete Setup Script for OBP-API
# This script provides a unified setup for ZED IDE with full Scala language server support

set -e

echo "🚀 Setting up ZED IDE for OBP-API Scala development..."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Get the project root directory (parent of zed folder)
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
echo "📁 Project root: $PROJECT_ROOT"

# Check if we're in the zed directory and project structure exists
if [[ ! -f "$PROJECT_ROOT/pom.xml" ]] || [[ ! -d "$PROJECT_ROOT/obp-api" ]] || [[ ! -d "$PROJECT_ROOT/obp-commons" ]]; then
    echo -e "${RED}❌ Error: Could not find OBP-API project structure${NC}"
    echo "Make sure you're running this from the zed/ folder of the OBP-API project"
    exit 1
fi

# Change to project root for Maven operations
cd "$PROJECT_ROOT"

echo "📁 Working directory: $(pwd)"

# Check prerequisites
echo "🔍 Checking prerequisites..."

# Check Java
if ! command -v java &> /dev/null; then
    echo -e "${RED}❌ Java not found. Please install Java 11 or 17${NC}"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1-2)
echo -e "${GREEN}✅ Java found: ${JAVA_VERSION}${NC}"

# Check Maven
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}❌ Maven not found. Please install Maven${NC}"
    exit 1
fi

MVN_VERSION=$(mvn -version 2>&1 | head -1 | cut -d' ' -f3)
echo -e "${GREEN}✅ Maven found: ${MVN_VERSION}${NC}"

# Check Coursier
if ! command -v cs &> /dev/null; then
    echo -e "${YELLOW}⚠️  Coursier not found. Installing...${NC}"
    curl -fL https://github.com/coursier/coursier/releases/latest/download/cs-x86_64-pc-linux.gz | gzip -d > cs
    chmod +x cs
    sudo mv cs /usr/local/bin/
    echo -e "${GREEN}✅ Coursier installed${NC}"
else
    echo -e "${GREEN}✅ Coursier found${NC}"
fi

# Check/Install Bloop
if ! command -v bloop &> /dev/null; then
    echo -e "${YELLOW}⚠️  Bloop not found. Installing...${NC}"
    cs install bloop
    echo -e "${GREEN}✅ Bloop installed${NC}"
else
    echo -e "${GREEN}✅ Bloop found: $(bloop about | head -1)${NC}"
fi

# Start Bloop server if not running
if ! pgrep -f "bloop.*server" > /dev/null; then
    echo "🔧 Starting Bloop server..."
    bloop server &
    sleep 3
    echo -e "${GREEN}✅ Bloop server started${NC}"
else
    echo -e "${GREEN}✅ Bloop server already running${NC}"
fi

# Compile the project to ensure dependencies are resolved
echo "🔨 Compiling Maven project (this may take a few minutes)..."
if mvn compile -q; then
    echo -e "${GREEN}✅ Maven compilation successful${NC}"
else
    echo -e "${RED}❌ Maven compilation failed. Please fix compilation errors first.${NC}"
    exit 1
fi

# Copy ZED configuration files to project root
echo "📋 Setting up ZED IDE configuration..."
ZED_DIR="$PROJECT_ROOT/.zed"
ZED_SRC_DIR="$PROJECT_ROOT/zed"

# Create .zed directory if it doesn't exist
if [ ! -d "$ZED_DIR" ]; then
    echo "📁 Creating .zed directory..."
    mkdir -p "$ZED_DIR"
else
    echo "📁 .zed directory already exists"
fi

# Copy settings.json
if [ -f "$ZED_SRC_DIR/settings.json" ]; then
    echo "⚙️  Copying settings.json..."
    cp "$ZED_SRC_DIR/settings.json" "$ZED_DIR/settings.json"
    echo -e "${GREEN}✅ settings.json copied successfully${NC}"
else
    echo -e "${RED}❌ Error: settings.json not found in zed folder${NC}"
    exit 1
fi

# Copy tasks.json
if [ -f "$ZED_SRC_DIR/tasks.json" ]; then
    echo "📋 Copying tasks.json..."
    cp "$ZED_SRC_DIR/tasks.json" "$ZED_DIR/tasks.json"
    echo -e "${GREEN}✅ tasks.json copied successfully${NC}"
else
    echo -e "${RED}❌ Error: tasks.json not found in zed folder${NC}"
    exit 1
fi

# Copy .metals-config.json if it exists
if [[ -f "$ZED_SRC_DIR/.metals-config.json" ]]; then
    echo "🔧 Copying Metals configuration..."
    cp "$ZED_SRC_DIR/.metals-config.json" "$PROJECT_ROOT/.metals-config.json"
    echo -e "${GREEN}✅ Metals configuration copied${NC}"
fi

echo -e "${GREEN}✅ ZED configuration files copied to .zed/ folder${NC}"

# Generate Bloop configuration files dynamically
echo "🔧 Generating Bloop configuration files..."
if [[ -f "$ZED_SRC_DIR/generate-bloop-config.sh" ]]; then
    chmod +x "$ZED_SRC_DIR/generate-bloop-config.sh"
    "$ZED_SRC_DIR/generate-bloop-config.sh"
    echo -e "${GREEN}✅ Bloop configuration files generated${NC}"
else
    # Fallback: Check if existing configurations are present
    if [[ -f "$PROJECT_ROOT/.bloop/obp-commons.json" && -f "$PROJECT_ROOT/.bloop/obp-api.json" ]]; then
        echo -e "${GREEN}✅ Bloop configuration files already exist${NC}"
    else
        echo -e "${RED}❌ Bloop configuration files missing and generator not found.${NC}"
        echo "Please ensure .bloop/*.json files exist or run zed/generate-bloop-config.sh manually"
        exit 1
    fi
fi

# Restart Bloop server to pick up new configurations
echo "🔄 Restarting Bloop server to detect new configurations..."
pkill -f bloop 2>/dev/null || true
sleep 1
bloop server &
sleep 2

# Verify Bloop can see projects
echo "🔍 Verifying Bloop projects..."
BLOOP_PROJECTS=$(bloop projects 2>/dev/null || echo "")
if [[ "$BLOOP_PROJECTS" == *"obp-api"* && "$BLOOP_PROJECTS" == *"obp-commons"* ]]; then
    echo -e "${GREEN}✅ Bloop projects detected:${NC}"
    echo "$BLOOP_PROJECTS" | sed 's/^/  /'
else
    echo -e "${YELLOW}⚠️  Bloop projects not immediately detected. This is normal for fresh setups.${NC}"
    echo "The configuration should work when you open ZED IDE."
fi

# Test Bloop compilation
echo "🧪 Testing Bloop compilation..."
if bloop compile obp-commons > /dev/null 2>&1; then
    echo -e "${GREEN}✅ Bloop compilation test successful${NC}"
else
    echo -e "${YELLOW}⚠️  Bloop compilation test failed, but setup is complete. Try restarting ZED IDE.${NC}"
fi

# Check ZED configuration
if [[ -f "$PROJECT_ROOT/.zed/settings.json" ]]; then
    echo -e "${GREEN}✅ ZED configuration found${NC}"
else
    echo -e "${YELLOW}⚠️  ZED configuration not found in .zed/settings.json${NC}"
fi

echo ""
echo -e "${GREEN}🎉 ZED IDE setup completed successfully!${NC}"
echo ""
echo "Your ZED configuration includes:"
echo "  • Format on save: DISABLED (manual formatting only - use Ctrl+Shift+I)"
echo "  • Scala/Metals LSP configuration optimized for OBP-API"
echo "  • Pre-configured build and run tasks"
echo "  • Dynamic Bloop configuration for language server support"
echo ""
echo "📋 Next steps:"
echo "1. Open ZED IDE"
echo "2. Open the OBP-API project directory in ZED"
echo "3. Wait for Metals to initialize (may take a few minutes)"
echo "4. Try 'Go to Definition' on a Scala symbol (F12 or Cmd+Click)"
echo ""
echo "🛠️  Available tasks (access with Cmd/Ctrl + Shift + P → 'task: spawn'):"
echo "  • [1] Run OBP-API Server - Start development server"
echo "  • [2] Test API Root Endpoint - Quick health check"
echo "  • [3] Compile Only - Fast syntax check"
echo "  • [4] Clean Target Folders - Remove build artifacts"
echo "  • Quick Build Dependencies - Build deps only (for onboarding)"
echo "  • Run Tests - Execute full test suite"
echo ""
echo "💡 Troubleshooting:"
echo "• If 'Go to Definition' doesn't work immediately, restart ZED IDE"
echo "• Use 'ZED: Reload Window' from the command palette if needed"
echo "• Check zed/README.md for comprehensive documentation"
echo "• Run './zed/generate-bloop-config.sh' to regenerate configurations if needed"
echo ""
echo "🔗 Resources:"
echo "• Complete ZED setup guide: zed/README.md"
echo "• Bloop projects: bloop projects"
echo "• Bloop compilation: bloop compile obp-commons obp-api"
echo ""
echo "Note: The .zed folder is in .gitignore, so you can customize settings"
echo "      without affecting other developers."
echo ""
echo -e "${GREEN}Happy coding! 🚀${NC}"
