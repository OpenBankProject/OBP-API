#!/usr/bin/env bash
# security_check.sh — lightweight static scan for high-signal security issues.
#
# Usage:
#   ./security_check.sh              # scan src + config, exit 1 on any HIGH finding
#   ./security_check.sh --all        # include tests
#   ./security_check.sh --strict     # exit 1 on any finding (HIGH or MEDIUM)
#
# Scope: grep-based heuristics. False positives are expected — triage findings,
# don't treat a clean run as proof of no vulnerabilities. Pair with real tools
# (Semgrep, OWASP Dependency-Check, gitleaks) for depth.
#
# Requires GNU grep (or compatible) with -P (PCRE) support — standard on Linux.

set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

INCLUDE_TESTS=0
STRICT=0
for arg in "$@"; do
  case "$arg" in
    --all) INCLUDE_TESTS=1 ;;
    --strict) STRICT=1 ;;
    -h|--help) sed -n '2,12p' "$0"; exit 0 ;;
    *) echo "unknown flag: $arg" >&2; exit 2 ;;
  esac
done

SCAN_PATHS=(obp-api/src/main obp-commons/src/main obp-http4s-runner/src/main)
if [[ $INCLUDE_TESTS -eq 1 ]]; then
  SCAN_PATHS+=(obp-api/src/test obp-commons/src/test)
fi
CONFIG_PATHS=(obp-api/src/main/resources)

GREP_EXCLUDES=(
  --exclude-dir=target
  --exclude-dir=.git
  --exclude-dir=node_modules
  --exclude='*.min.js'
)

# Verify grep -P works. ugrep, GNU grep (built with PCRE), and modern BSD grep all support it.
if ! echo x | grep -P 'x' >/dev/null 2>&1; then
  echo "error: grep -P (PCRE) not supported by $(grep --version | head -1)" >&2
  echo "install GNU grep, ripgrep, or ugrep." >&2
  exit 2
fi

HIGH=0
MEDIUM=0

# scan <severity> <title> <regex> [-- <path>...]
#   default paths are SCAN_PATHS. To override: pass `--` then the paths.
scan() {
  local sev="$1" title="$2" pattern="$3"; shift 3
  local paths=("${SCAN_PATHS[@]}")
  if [[ "${1:-}" == "--" ]]; then
    shift
    paths=("$@")
  fi

  local matches
  matches=$(grep -rnHP "${GREP_EXCLUDES[@]}" -e "$pattern" "${paths[@]}" 2>/dev/null || true)
  [[ -z "$matches" ]] && return 0

  local count; count=$(printf '%s\n' "$matches" | wc -l | tr -d ' ')
  printf '\n[%s] %s — %s match(es)\n' "$sev" "$title" "$count"
  printf '%s\n' "$matches" | sed 's/^/  /'
  if [[ "$sev" == "HIGH" ]]; then HIGH=$((HIGH+count)); else MEDIUM=$((MEDIUM+count)); fi
}

echo "security_check.sh — scanning ${SCAN_PATHS[*]}"

# --- Secrets & credentials ----------------------------------------------
scan HIGH "Hardcoded password literal" \
  '(?i)(password|passwd|pwd)\s*[:=]\s*"[^"$\{<]{4,}"'

scan HIGH "Hardcoded secret/token literal" \
  '(?i)(api[_-]?key|secret|auth[_-]?token|access[_-]?token|bearer)\s*[:=]\s*"[^"$\{<]{8,}"'

scan HIGH "AWS-style access key" \
  'AKIA[0-9A-Z]{16}'

# Excludes the string literal "-----BEGIN PRIVATE KEY-----" — we want embedded PEM, not code that references the header.
scan HIGH "Private key block" \
  '(?<!")-----BEGIN (RSA |EC |DSA |OPENSSH |PGP )?PRIVATE KEY-----(?!")'

scan MEDIUM "Props file with literal password" \
  '(?i)^[^#]*(password|secret|api[_-]?key)\s*=\s*\S+' \
  -- "${CONFIG_PATHS[@]}"

# --- Weak crypto ---------------------------------------------------------
scan HIGH "Weak hash (MD5/SHA1) used for security" \
  '(?i)MessageDigest\.getInstance\(\s*"(MD5|SHA-?1)"'

scan HIGH "Weak cipher (DES / RC4 / ECB)" \
  '(?i)Cipher\.getInstance\(\s*"(DES|RC4|[^"]*?/ECB/)'

scan MEDIUM "java.util.Random used (not SecureRandom)" \
  '\bnew\s+java\.util\.Random\b|\bnew\s+Random\s*\('

# --- TLS weakening -------------------------------------------------------
scan HIGH "Trust-all / disabled cert validation" \
  'X509TrustManager|TrustAllCerts|setHostnameVerifier\s*\(\s*\(.*\)\s*->\s*true|NoopHostnameVerifier'

# Excludes common false-positive hosts in fixtures and XML namespace URIs.
scan MEDIUM "HTTP (non-TLS) URL in source" \
  '"http://(?!localhost|127\.0\.0\.1|0\.0\.0\.0|example\.com|www\.example\.com|www\.w3\.org|schemas\.xmlsoap|xmlns)[^"]+"'

# --- Injection risks -----------------------------------------------------
scan HIGH "SQL built with string concat" \
  '(?i)"(SELECT|INSERT|UPDATE|DELETE|DROP)\b[^"]*"\s*\+\s*\w'

scan MEDIUM "Runtime.exec / ProcessBuilder with variable" \
  'Runtime\.getRuntime\(\)\.exec\s*\(\s*\w|new\s+ProcessBuilder\s*\(\s*\w'

# --- Deserialization / reflection hazards --------------------------------
scan MEDIUM "Java native deserialization" \
  'new\s+ObjectInputStream\s*\(|\breadObject\s*\('

# --- Summary -------------------------------------------------------------
printf '\n---\nSummary: %d HIGH, %d MEDIUM\n' "$HIGH" "$MEDIUM"

if [[ $HIGH -gt 0 ]]; then
  exit 1
fi
if [[ $STRICT -eq 1 && $MEDIUM -gt 0 ]]; then
  exit 1
fi
exit 0
