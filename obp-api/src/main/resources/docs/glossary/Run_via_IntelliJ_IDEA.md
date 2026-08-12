## To run via IntelliJ IDEA

### Prerequisites

* **JDK 25.** `pom.xml` sets `<java.version>25</java.version>`, so anything older will not compile. Any OpenJDK 25 distribution works (Eclipse Temurin, Azul Zulu, …). `<scalac.release>` is 25 as well, so the IDE SDK, the command-line build and CI all sit on the same Java level — there is no second, lower level to account for.

  If a build ever fails with `'25' is not a valid choice for '-release'`, it means scalac is running on a JDK older than 25, not that the Scala version is too old: the highest `-release` scalac accepts is the version of the JDK running it. `maven-enforcer-plugin` catches that up front, so from the command line you would see `Detected JDK version … is not in the allowed range [25,)` first.

* **Scala 2.12.21** (`<scala.compiler>` in `pom.xml`).

* **The IntelliJ Scala plugin.**

* **Redis**, running and reachable. Configure it in your props file if it is not on the default port at localhost without password protection. The test suite really does use it — pointing it at a dead port fails around 135 tests.

* **PostgreSQL**, for anything beyond compiling.

### Importing the project

* Create a folder, e.g. `OpenBankProject`, and `cd` there.

* `git clone https://github.com/OpenBankProject/OBP-API.git`

* In IntelliJ IDEA, **File → New → Project from existing sources**, navigate to the folder and select **`pom.xml`** — not the folder itself.

  Alternatively **File → New → Project from Version Control** and check the project out directly from GitHub.

* When prompted for an SDK, choose your JDK 25. Keep the other defaults and use the Maven options. Do not change the project name.

* If you see a message about an unmanaged `pom.xml`, click the option to let Maven manage it.

* If a **Setup Scala SDK** link appears, click it and select Scala 2.12.

### Configuring props

In `obp-api/src/main/resources/props` create `<yourloginname>.props` (or `default.props`) for development. Start from `sample.props.template` in the same directory. At minimum set `connector=mapped` for a self-contained local instance.

`hostname` and `dev.port` decide where the server binds — they default to `http://127.0.0.1` and `8080`.

These files are gitignored, so they do not travel with the repository and are not created for you.

### Before your first Rebuild: turn off parallel compilation

**Both** of these must be off, or `Rebuild Project` intermittently fails with a handful of `Zinc Resource Compiler: Error` lines carrying a bare path and no reason:

1. **Settings → Build, Execution, Deployment → Compiler** → uncheck **Compile independent modules in parallel**.
2. **Settings → Build, Execution, Deployment → Compiler → Scala Compiler → Scala Compile Server** → uncheck **Compile independent modules in parallel, in up to N threads**.

Step 2 is the one that takes effect. The Scala plugin appends its own `-Dcompile.parallel=…` to the build process command line *after* the platform setting's, and the JVM honours the last one — so turning off only the Compiler-page setting silently does nothing. Note that step 2 is an application-level setting and affects every project.

The failure itself is a race, not a problem with your checkout: for a Maven + Scala module, JPS registers two independent resource-copy builders (`MavenResourcesBuilder` and `ZincResourceBuilder`), and in parallel mode they write the same destination paths concurrently. The bytes on disk still end up correct — the other builder's copy won — which is why the errors look inexplicable.

### Building

**Build → Rebuild Project.** Expect roughly 1–2 minutes and a few hundred warnings; it should report **0 errors**.

If you have trouble (re)building, `mvn clean test-compile` from a terminal is a useful cross-check. Run it outside the IDE and make sure the shell is on JDK 25 as well — a shell left on an older JDK is a common source of "works in the IDE, fails on the command line":

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 25)   # macOS
```

### Running the API

The server is **http4s**; Jetty, `web.xml` and the old `RunWebApp` / `RunTLSWebApp` / `RunMTLSWebApp` launchers were removed. There is one entry point for every mode:

**`bootstrap.http4s.Http4sServer`** (in `obp-api/src/main/scala`)

Right-click it and choose Run or Debug. TLS and mutual TLS are no longer separate launchers — they are props, so the mode is decided by configuration rather than by which class you start.

Browse to the configured host and port (`http://127.0.0.1:8080` by default) once it is up.

You can also run the packaged jar instead of the IDE launcher; see the README for that.

#### TLS and mutual TLS

Set these in your props file to put the listener behind TLS. mTLS is what the UK Open Banking standard requires, and OpenID Connect needs an `https` redirect URI, so both matter for local work on those features:

| Prop | Meaning |
|---|---|
| `mtls.enabled` | `true` turns on the TLS listener (default `false`) |
| `mtls.keystore.path` / `mtls.keystore.password` | server certificate |
| `mtls.truststore.path` / `mtls.truststore.password` | which client certificates are accepted |
| `mtls.client_auth` | `need` (default) rejects clients without a certificate; `want` accepts them |

Every one of these can also be supplied as an `OBP_`-prefixed environment variable (`OBP_MTLS_KEYSTORE_PATH`, …), which takes precedence over the props file.

Development certificates live in `obp-api/src/test/resources/cert/` — `localhost_san_dns_ip.pfx` for the server, `dev-truststore.p12` and `dev-ca.crt` for the client side. Import the client certificate into your browser before calling an mTLS endpoint from it.

Also set `hostname` to an `https://` URL when you enable TLS, so that generated links match the listener. The server logs a warning if you forget.

### Running the tests from the IDE

IntelliJ's ScalaTest runner launches the JVM itself instead of going through Maven, so it inherits nothing from the surefire configuration. Two things are missing by default, and both look like broken code rather than missing setup.

**1. Without `--add-opens`, nothing runs at all.** You get `Test framework quit unexpectedly` and `Tests passed: 0`, because JDK 16+ strong encapsulation stops CGLib from reflecting into `ClassLoader.defineClass` while the connector proxy is built.

Copy the `<argLine>` value verbatim from the surefire configuration in `obp-api/pom.xml` into the run configuration's **VM options**. `--add-opens java.base/java.lang=ALL-UNNAMED` is the load-bearing one; keep the rest so the IDE matches CI.

**2. Without a few extra props, the dynamic-code tests fail.** `DynamicUtilJsEngineTest`, `DynamicCodeKillSwitchTest`, `DynamicMessageDocTest`, `DynamicResourceDocTest`, `ConnectorMethodTest` and `AbacRuleTests` all report `OBP-50020: User-generated dynamic code execution is disabled on this API instance`.

CI and `run_tests_parallel.sh` inject these at run time as `OBP_*` environment variables rather than writing them into the file. The IDE passes no environment, so add them to `test.default.props` (it is gitignored) or to the run configuration's **Environment variables**:

```
allow_user_generated_scala_code
dynamic_code_sandbox_permissions
mail.api.test.mode
api_instance_id
```

Setting `api_instance_id` to something unique also keeps your local run's Redis keys out of the namespace other local runs use.

Because IntelliJ regenerates right-click run configurations with empty VM options and environment, both fixes are worth putting in **Settings → Build, Execution, Deployment → Compiler → Scala Compiler → Edit configuration templates… → ScalaTest**, so every future configuration inherits them.

**When to use which runner.** The IDE is far quicker for iterating on one suite — it skips the Maven lifecycle, the jar build and the forked JVM, so rerunning a small suite takes seconds instead of a minute or two. For a result you intend to trust, use the full test script: it runs the real build, matches CI, and on the whole suite it is not slower anyway.
