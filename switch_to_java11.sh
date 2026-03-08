#!/bin/bash

# Script to switch system Java to Java 11
# This will update your shell configuration to use Java 11 by default

echo "=== Switching System Java to Java 11 ==="
echo ""

# Detect Java 11 path
JAVA11_PATH="/Users/zhanghongwei/Library/Java/JavaVirtualMachines/azul-11.0.24/Contents/Home"

if [ ! -d "$JAVA11_PATH" ]; then
    echo "❌ Error: Java 11 not found at $JAVA11_PATH"
    echo "Please install Java 11 first"
    exit 1
fi

echo "✅ Found Java 11 at: $JAVA11_PATH"
echo ""

# Detect shell configuration file
if [ -f ~/.zshrc ]; then
    SHELL_CONFIG=~/.zshrc
    SHELL_NAME="zsh"
elif [ -f ~/.bash_profile ]; then
    SHELL_CONFIG=~/.bash_profile
    SHELL_NAME="bash"
elif [ -f ~/.bashrc ]; then
    SHELL_CONFIG=~/.bashrc
    SHELL_NAME="bash"
else
    echo "❌ Error: Could not find shell configuration file"
    exit 1
fi

echo "📝 Detected shell: $SHELL_NAME"
echo "📝 Configuration file: $SHELL_CONFIG"
echo ""

# Backup existing configuration
BACKUP_FILE="${SHELL_CONFIG}.backup.$(date +%Y%m%d_%H%M%S)"
cp "$SHELL_CONFIG" "$BACKUP_FILE"
echo "✅ Backed up configuration to: $BACKUP_FILE"
echo ""

# Remove existing JAVA_HOME settings
echo "🔧 Removing existing JAVA_HOME settings..."
sed -i.tmp '/export JAVA_HOME=/d' "$SHELL_CONFIG"
sed -i.tmp '/JAVA_HOME=/d' "$SHELL_CONFIG"
rm -f "${SHELL_CONFIG}.tmp"

# Add Java 11 configuration
echo "🔧 Adding Java 11 configuration..."
cat >> "$SHELL_CONFIG" << 'EOF'

# Java 11 Configuration (added by switch_to_java11.sh)
export JAVA_HOME=/Users/zhanghongwei/Library/Java/JavaVirtualMachines/azul-11.0.24/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
EOF

echo "✅ Java 11 configuration added to $SHELL_CONFIG"
echo ""

# Apply changes to current session
export JAVA_HOME="$JAVA11_PATH"
export PATH="$JAVA_HOME/bin:$PATH"

# Verify
echo "=== Verification ==="
java -version
echo ""

echo "✅ Java 11 is now active in this terminal session!"
echo ""
echo "📌 To apply changes to all future terminal sessions:"
echo "   1. Close this terminal"
echo "   2. Open a new terminal"
echo "   3. Run: java -version"
echo ""
echo "📌 Or reload configuration in current terminal:"
echo "   source $SHELL_CONFIG"
echo ""
echo "📌 To revert changes:"
echo "   cp $BACKUP_FILE $SHELL_CONFIG"
echo "   source $SHELL_CONFIG"
