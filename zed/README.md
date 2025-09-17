# ZED IDE Setup for OBP-API Development

> **Complete ZED IDE integration for the Open Bank Project API**

This folder contains everything needed to set up ZED IDE with full Scala language server support, automated build tasks, and streamlined development workflows for OBP-API.

## 🚀 Quick Setup (5 minutes)

### Prerequisites

- **Java 17+** (OpenJDK recommended)
- **Maven 3.6+**
- **ZED IDE** (latest version)

### Single Setup Script

```bash
cd OBP-API
./zed/setup-zed-ide.sh
```

This unified script automatically:

- ✅ Installs missing dependencies (Coursier, Bloop)
- ✅ Compiles the project and resolves dependencies
- ✅ Generates dynamic Bloop configurations
- ✅ Sets up Metals language server
- ✅ Copies ZED configuration files to `.zed/` folder
- ✅ Configures build and run tasks
- ✅ Sets up manual-only code formatting

## 📁 What's Included

```
zed/
├── README.md                   # This comprehensive guide
├── setup-zed-ide.sh           # Single unified setup script
├── generate-bloop-config.sh    # Dynamic Bloop config generator
├── settings.json               # ZED IDE settings template
├── tasks.json                  # Pre-configured build/run tasks
├── .metals-config.json         # Metals language server config
└── setup-zed.bat              # Windows setup script
```

## ⌨️ Essential Keyboard Shortcuts

| Action               | Linux          | macOS/Windows | Purpose                       |
| -------------------- | -------------- | ------------- | ----------------------------- |
| **Command Palette**  | `Ctrl+Shift+P` | `Cmd+Shift+P` | Access all tasks              |
| **Go to Definition** | `F12`          | `F12`         | Navigate to symbol definition |
| **Find References**  | `Shift+F12`    | `Shift+F12`   | Find all symbol usages        |
| **Quick Open File**  | `Ctrl+P`       | `Cmd+P`       | Fast file navigation          |
| **Format Code**      | `Ctrl+Shift+I` | `Cmd+Shift+I` | Auto-format Scala code        |
| **Symbol Search**    | `Ctrl+T`       | `Cmd+T`       | Search symbols project-wide   |

## 🛠️ Available Development Tasks

Access via Command Palette (`Ctrl+Shift+P` on Linux, `Cmd+Shift+P` on macOS/Windows) → `"task: spawn"` (Linux) or `"Tasks: Spawn"` (macOS/Windows):

### Core Development Tasks

| Task                         | Purpose                  | Duration  | When to Use                          |
| ---------------------------- | ------------------------ | --------- | ------------------------------------ |
| **Quick Build Dependencies** | Build only dependencies  | 1-3 min   | First step, after dependency changes |
| **[1] Run OBP-API Server**   | Start development server | 3-5 min   | Daily development                    |
| **🔨 Build OBP-API**         | Full project build       | 2-5 min   | After code changes                   |
| **Run Tests**                | Execute test suite       | 5-15 min  | Before commits                       |
| **[3] Compile Only**         | Quick syntax check       | 30s-1 min | During development                   |

### Utility Tasks

| Task                              | Purpose                   |
| --------------------------------- | ------------------------- |
| **[4] Clean Target Folders**      | Remove build artifacts    |
| **🔄 Continuous Compile (Scala)** | Auto-recompile on changes |
| **[2] Test API Root Endpoint**    | Verify server status      |
| **🔧 Kill Server on Port 8080**   | Stop stuck processes      |
| **🔍 Check Dependencies**         | Verify Maven dependencies |

## 🏗️ Development Workflow

### Daily Development

1. **Start Development Session**
   - Linux: `Ctrl+Shift+P` → `"task: spawn"` → `"Quick Build Dependencies"`
   - macOS: `Cmd+Shift+P` → `"Tasks: Spawn"` → `"Quick Build Dependencies"`

2. **Start API Server**
   - Use task `"[1] Run OBP-API Server"`
   - Server runs on: `http://localhost:8080`
   - Test endpoint: `http://localhost:8080/obp/v5.1.0/root`

3. **Code Development**
   - Edit Scala files in `obp-api/src/main/scala/`
   - Use `F12` for Go to Definition
   - Auto-completion with `Ctrl+Space`
   - Real-time error highlighting
   - Format code with `Ctrl+Shift+I`

4. **Testing & Validation**
   - Quick compile: `"[3] Compile Only"` task
   - Run tests: `"Run Tests"` task
   - API testing: `"[2] Test API Root Endpoint"` task

## 🔧 Configuration Details

### ZED IDE Settings (`settings.json`)

- **Format on Save**: DISABLED (manual formatting only - use `Ctrl+Shift+I`)
- **Scala LSP**: Optimized Metals configuration
- **Maven Integration**: Proper MAVEN_OPTS for Java 17+
- **UI Preferences**: One Dark theme, consistent layout
- **Inlay Hints**: Enabled for better code understanding

### Build Tasks (`tasks.json`)

All tasks include proper environment variables:

```bash
MAVEN_OPTS="-Xss128m --add-opens=java.base/java.util.jar=ALL-UNNAMED --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.lang.reflect=ALL-UNNAMED"
```

### Metals LSP (`.metals-config.json`)

- **Build Tool**: Maven
- **Bloop Integration**: Dynamic configuration generation
- **Scala Version**: 2.12.20
- **Java Target**: Java 11 (compatible with Java 17)

## 🚨 Troubleshooting

### Common Issues

| Problem                         | Symptom                              | Solution                                         |
| ------------------------------- | ------------------------------------ | ------------------------------------------------ |
| **Language Server Not Working** | No go-to-definition, no autocomplete | Restart ZED, wait for Metals initialization      |
| **Compilation Errors**          | Red squiggly lines, build failures   | Check Problems panel, run "Clean Target Folders" |
| **Server Won't Start**          | Port 8080 busy                       | Run "Kill Server on Port 8080" task              |
| **Out of Memory**               | Build fails with heap space error    | Already configured in tasks                      |
| **Missing Dependencies**        | Import errors                        | Run "Check Dependencies" task                    |

### Recovery Procedures

1. **Full Reset**:

   ```bash
   ./zed/setup-zed-ide.sh  # Re-run complete setup
   ```

2. **Regenerate Bloop Configurations**:

   ```bash
   ./zed/generate-bloop-config.sh  # Regenerate configs
   ```

3. **Clean Restart**:
   - Clean build with "Clean Target Folders" task
   - Restart ZED IDE
   - Wait for Metals to reinitialize (2-3 minutes)

### Platform-Specific Notes

#### Linux Users

- Use `"task: spawn"` in command palette (not `"Tasks: Spawn"`)
- Ensure proper Java permissions for Maven

#### macOS/Windows Users

- Use `"Tasks: Spawn"` in command palette
- Windows users can also use `setup-zed.bat`

## 🌐 API Development

### Project Structure

```
OBP-API/
├── obp-api/                    # Main API application
│   └── src/main/scala/         # Scala source code
│       └── code/api/           # API endpoint definitions
│           ├── v5_1_0/         # Latest API version
│           ├── v4_0_0/         # Previous versions
│           └── util/           # Utility functions
├── obp-commons/                # Shared utilities and models
│   └── src/main/scala/         # Common Scala code
└── .zed/                       # ZED IDE configuration (generated)
```

### Adding New API Endpoints

1. Navigate to `obp-api/src/main/scala/code/api/v5_1_0/`
2. Find appropriate API trait (e.g., `OBPAPI5_1_0.scala`)
3. Follow existing endpoint patterns
4. Use `F12` to navigate to helper functions
5. Test with API test task

### Testing Endpoints

```bash
# Root API information
curl http://localhost:8080/obp/v5.1.0/root

# Health check
curl http://localhost:8080/obp/v5.1.0/config

# Banks list (requires proper setup)
curl http://localhost:8080/obp/v5.1.0/banks
```

## 🎯 Pro Tips

### Code Navigation

- **Quick file access**: `Ctrl+P` then type filename
- **Symbol search**: `Ctrl+T` then type function/class name
- **Project-wide text search**: `Ctrl+Shift+F`

### Efficiency Shortcuts

- `Ctrl+/` - Toggle line comment
- `Ctrl+D` - Select next occurrence
- `Ctrl+Shift+L` - Select all occurrences
- `F2` - Rename symbol
- `Alt+←/→` - Navigate back/forward

### Performance Optimization

- Close unused files to reduce memory usage
- Use "Continuous Compile" for faster feedback
- Limit test runs to specific modules during development

## 📚 Additional Resources

### Documentation

- **OBP-API Project**: https://github.com/OpenBankProject/OBP-API
- **API Documentation**: https://apiexplorer.openbankproject.com
- **Community Forums**: https://openbankproject.com

### Learning Resources

- **Scala**: https://docs.scala-lang.org/
- **Lift Framework**: https://liftweb.net/
- **Maven**: https://maven.apache.org/guides/
- **ZED IDE**: https://zed.dev/docs

## 🆘 Getting Help

### Diagnostic Commands

```bash
# Check Java version
java -version

# Check Maven
mvn -version

# Check Bloop status
bloop projects

# Test compilation
bloop compile obp-commons obp-api

# Check ZED configuration
ls -la .zed/
```

### Common Error Messages

| Error                       | Cause                         | Solution                      |
| --------------------------- | ----------------------------- | ----------------------------- |
| "Java module system" errors | Java 17+ module restrictions  | Already handled in MAVEN_OPTS |
| "Port 8080 already in use"  | Previous server still running | Use "Kill Server" task        |
| "Metals not responding"     | Language server crashed       | Restart ZED IDE               |
| "Compilation failed"        | Dependency issues             | Run "Check Dependencies"      |

---

## 🎉 Getting Started Checklist

- [ ] Install Java 17+, Maven 3.6+, ZED IDE
- [ ] Clone OBP-API repository
- [ ] Run `./zed/setup-zed-ide.sh` (single setup script)
- [ ] Open project in ZED IDE
- [ ] Wait for Metals initialization (2-3 minutes)
- [ ] Run "Quick Build Dependencies" task
- [ ] Start server with "[1] Run OBP-API Server" task
- [ ] Test API at http://localhost:8080/obp/v5.1.0/root
- [ ] Try "Go to Definition" (F12) on Scala symbol
- [ ] Format code manually with `Ctrl+Shift+I` (auto-format disabled)
- [ ] Make a small code change and test compilation

**Welcome to productive OBP-API development with ZED IDE! 🚀**

---

_This setup provides a complete, optimized development environment for the Open Bank Project API using ZED IDE with full Scala language server support._
