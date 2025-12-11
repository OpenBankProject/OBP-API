# Lift and http4s Coexistence Strategy

## Question
Can http4s and Lift coexist in the same project to convert endpoints one by one?

## Answer: Yes, on Different Ports

## OBP-API-Dispatch

OBP-API-Dispatch already exists:

- **Location:** `workspace_2024/OBP-API-Dispatch`
- **GitHub:** https://github.com/OpenBankProject/OBP-API-Dispatch
- **Technology:** http4s (Cats Effect 3, Ember server)
- **Purpose:** Routes requests between different OBP-API backends
- **Current routing:** Based on API version (v1.3.0 → backend 2, others → backend 1)

It needs minor enhancements to support version-based routing for the Lift → http4s migration.

## Answers to Your Three Questions

### Q1: Could we use OBP-API-Dispatch to route between two ports?

**YES - it already exists**

OBP-API-Dispatch can be used for this:
- Single entry point for clients
- Route by API version: v4/v5 → Lift, v6/v7 → http4s (might want to route based on resource docs but not sure if we really need this - NGINX might therefore be an alternative but OBP-Dispatch would have OBP specific routing out of the box and potentially other features)
- No client configuration changes needed
- Rollback by changing routing config

### Q2: Running http4s in Jetty until migration complete?

**Possible but not recommended**

Running http4s in Jetty (servlet mode) loses:
- True non-blocking I/O
- HTTP/2, WebSockets, efficient streaming
- Would need to refactor again later to standalone

Use standalone http4s on port 8081 from the start.

### Q3: How would the developer experience be?

**IDE and Project Setup:**

You'll work in **one IDE window** with the OBP-API codebase:
- Same project structure
- Same database (Lift Boot continues to handle DB creation/migrations)
- Both Lift and http4s code in the same `obp-api` module
- Edit both Lift endpoints and http4s endpoints in the same IDE

**Running the Servers:**

You'll run **three separate terminal processes**:

**Terminal 1: Lift Server (existing)**
```bash
cd workspace_2024/OBP-API-C/OBP-API
sbt "project obp-api" run
```
- Runs Lift Boot (handles DB initialization)
- Starts on port 8080
- Keep this running as long as you have Lift endpoints

**Terminal 2: http4s Server (new)**
```bash
cd workspace_2024/OBP-API-C/OBP-API
sbt "project obp-api" "runMain code.api.http4s.Http4sMain"
```
- Starts on port 8081
- Separate process from Lift
- Uses same database connection pool

**Terminal 3: OBP-API-Dispatch (separate project)**
```bash
cd workspace_2024/OBP-API-Dispatch
mvn clean package
java -jar target/OBP-API-Dispatch-1.0-SNAPSHOT-jar-with-dependencies.jar
```
- Separate IDE window or just a terminal
- Routes requests between Lift (8080) and http4s (8081)
- Runs on port 8088

**Editing Workflow:**

1. **Adding new http4s endpoint:**
   - Create endpoint in `obp-api/src/main/scala/code/api/http4s/`
   - Edit in same IDE as Lift code
   - Restart Terminal 2 only (http4s server)

2. **Fixing Lift endpoint:**
   - Edit existing Lift code in `obp-api/src/main/scala/code/api/`
   - Restart Terminal 1 only (Lift server)

3. **Updating routing (which endpoints go where):**
   - Edit `OBP-API-Dispatch/src/main/resources/application.conf`
   - Restart Terminal 3 only (Dispatch)

**Database:**

Lift Boot continues to handle:
- Database connection setup
- Schema migrations
- Table creation

Both Lift and http4s use the same database connection pool and Mapper classes.

### Architecture with OBP-API-Dispatch

```
┌────────────────────────────────────────────┐
│            API Clients                      │
└────────────────────────────────────────────┘
                    ↓
              Port 8088/443
                    ↓
┌────────────────────────────────────────────┐
│        OBP-API-Dispatch (http4s)           │
│                                            │
│  Routing Rules:                            │
│  • /obp/v4.0.0/* → Lift (8080)            │
│  • /obp/v5.0.0/* → Lift (8080)            │
│  • /obp/v5.1.0/* → Lift (8080)            │
│  • /obp/v6.0.0/* → http4s (8081) ✨       │
│  • /obp/v7.0.0/* → http4s (8081) ✨       │
└────────────────────────────────────────────┘
         ↓                        ↓
    ┌─────────┐             ┌─────────┐
    │  Lift   │             │ http4s  │
    │  :8080  │             │  :8081  │
    └─────────┘             └─────────┘
         ↓                        ↓
    ┌────────────────────────────────┐
    │   Shared Resources:            │
    │   - Database                   │
    │   - Business Logic             │
    │   - Authentication             │
    │   - ResourceDocs               │
    └────────────────────────────────┘
```

### How It Works

1. **Two HTTP Servers Running Simultaneously**
   - Lift/Jetty continues on port 8080
   - http4s starts on port 8081
   - Both run in the same JVM process

2. **Shared Components**
   - Database connections
   - Business logic layer
   - Authentication/authorization
   - Configuration
   - Connector layer

3. **Gradual Migration**
   - Start: All endpoints on Lift (port 8080)
   - During: Some on Lift, some on http4s
   - End: All on http4s (port 8081), Lift removed

## Using OBP-API-Dispatch for Routing

### Current Status

OBP-API-Dispatch already exists and is functional:
- **Location:** `workspace_2024/OBP-API-Dispatch`
- **GitHub:** https://github.com/OpenBankProject/OBP-API-Dispatch
- **Build:** Maven-based
- **Technology:** http4s with Ember server
- **Current routing:** Routes v1.3.0 to backend 2, others to backend 1

### Current Configuration

```hocon
# application.conf
app {
  dispatch_host = "127.0.0.1"
  dispatch_dev_port = 8088
  obp_api_1_base_uri = "http://localhost:8080"
  obp_api_2_base_uri = "http://localhost:8086"
}
```

### Enhancement for Migration

Update configuration to support version-based routing:

```hocon
# application.conf
app {
  dispatch_host = "0.0.0.0"
  dispatch_port = 8088
  
  # Lift backend (legacy endpoints)
  lift_backend_uri = "http://localhost:8080"
  lift_backend_uri = ${?LIFT_BACKEND_URI}
  
  # http4s backend (modern endpoints)
  http4s_backend_uri = "http://localhost:8081"
  http4s_backend_uri = ${?HTTP4S_BACKEND_URI}
  
  # Routing strategy
  routing {
    # API versions that go to http4s backend
    http4s_versions = ["v6.0.0", "v7.0.0"]
    
    # Specific endpoint overrides (optional)
    overrides = [
      # Example: migrate specific v5.1.0 endpoints early
      # { path = "/obp/v5.1.0/banks", target = "http4s" }
    ]
  }
}
```

### Enhanced Routing Logic

Update `ObpApiDispatch.scala` to support version-based routing:

```scala
private def selectBackend(path: String, method: Method): String = {
  // Extract API version from path: /obp/v{version}/...
  val versionPattern = """/obp/(v\d+\.\d+\.\d+)/.*""".r
  
  path match {
    case versionPattern(version) =>
      if (http4sVersions.contains(version)) {
        logger.info(s"Version $version routed to http4s")
        "http4s"
      } else {
        logger.info(s"Version $version routed to Lift")
        "lift"
      }
    case _ =>
      logger.debug(s"Path $path routing to Lift (default)")
      "lift"
  }
}
```

### Local Development Workflow

**Terminal 1: Start Lift**
```bash
cd workspace_2024/OBP-API-C/OBP-API
sbt "project obp-api" run
# Starts on port 8080
```

**Terminal 2: Start http4s**
```bash
cd workspace_2024/OBP-API-C/OBP-API
sbt "project obp-api" "runMain code.api.http4s.Http4sMain"
# Starts on port 8081
```

**Terminal 3: Start OBP-API-Dispatch**
```bash
cd workspace_2024/OBP-API-Dispatch
mvn clean package
java -jar target/OBP-API-Dispatch-1.0-SNAPSHOT-jar-with-dependencies.jar
# Starts on port 8088
```

**Terminal 4: Test**
```bash
# Old endpoint (goes to Lift)
curl http://localhost:8088/obp/v5.1.0/banks

# New endpoint (goes to http4s)
curl http://localhost:8088/obp/v6.0.0/banks

# Health checks
curl http://localhost:8088/health
```

## Implementation Approach

### Step 1: Add http4s to Project

```scala
// build.sbt
libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-dsl" % "0.23.x",
  "org.http4s" %% "http4s-ember-server" % "0.23.x",
  "org.http4s" %% "http4s-ember-client" % "0.23.x",
  "org.http4s" %% "http4s-circe" % "0.23.x"
)
```

### Step 2: Create http4s Server

```scala
// code/api/http4s/Http4sServer.scala
package code.api.http4s

import cats.effect._
import org.http4s.ember.server.EmberServerBuilder
import com.comcast.ip4s._

object Http4sServer {
  
  def start(port: Int = 8081): IO[Unit] = {
    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(Port.fromInt(port).get)
      .withHttpApp(routes.orNotFound)
      .build
      .useForever
  }
  
  def routes = ???  // Define routes here
}
```

### Step 3: THE JETTY PROBLEM

If you start http4s from Lift's Bootstrap, it runs INSIDE Jetty's servlet container. This defeats the purpose of using http4s.

```
❌ WRONG APPROACH:
┌─────────────────────────────┐
│ Jetty Servlet Container     │
│  ├─ Lift (port 8080)        │
│  └─ http4s (port 8081)      │  ← Still requires Jetty
└─────────────────────────────┘
```

**The Problem:**
- http4s would still require Jetty to run
- Can't remove servlet container later
- Defeats the goal of eliminating Jetty

### Step 3: CORRECT APPROACH - Separate Main

**Solution:** Start http4s as a STANDALONE server, NOT from Lift Bootstrap.

```
✓ CORRECT APPROACH:
┌──────────────────┐  ┌─────────────────┐
│ Jetty Container  │  │ http4s Server   │
│  └─ Lift         │  │  (standalone)   │
│  Port 8080       │  │  Port 8081      │
└──────────────────┘  └─────────────────┘
     Same JVM Process
```

#### Option A: Two Separate Processes (Simpler)

```scala
// Run Lift as usual
sbt "jetty:start"  // Port 8080

// Run http4s separately
sbt "runMain code.api.http4s.Http4sMain"  // Port 8081
```

**Deployment:**
```bash
# Start Lift/Jetty
java -jar obp-api-jetty.jar &

# Start http4s standalone
java -jar obp-api-http4s.jar &
```

**Pros:**
- Complete separation
- Easy to understand
- Can stop/restart independently
- No Jetty dependency for http4s

**Cons:**
- Two separate processes to manage
- Two JVMs (more memory)

### Two Separate Processes

For actual migration:

1. **Keep Lift/Jetty running as-is** on port 8080
2. **Create standalone http4s server** on port 8081 with its own Main class
3. **Use reverse proxy** (nginx/HAProxy) to route requests
4. **Migrate endpoints one by one** to http4s
5. **Eventually remove Lift/Jetty** completely

```
Phase 1-3 (Migration):
┌─────────────┐
│   Nginx     │  Port 443
│   (Proxy)   │
└──────┬──────┘
       │
       ├──→ Jetty/Lift (Process 1)      Port 8080  ← Old endpoints
       └──→ http4s standalone (Process 2) Port 8081  ← New endpoints

Phase 4 (Complete):
┌─────────────┐
│   Nginx     │  Port 443
│   (Proxy)   │
└──────┬──────┘
       │
       └──→ http4s standalone            Port 8080  ← All endpoints
            (Jetty removed)
```

**This way http4s is NEVER dependent on Jetty.**

### Is http4s Non-Blocking?

**YES - http4s is fully non-blocking and asynchronous.**

#### Architecture Comparison

**Lift/Jetty (Blocking):**
```
Thread-per-request model:
┌─────────────────────────────┐
│ Request 1 → Thread 1 (busy) │  Blocks waiting for DB
│ Request 2 → Thread 2 (busy) │  Blocks waiting for HTTP call
│ Request 3 → Thread 3 (busy) │  Blocks waiting for file I/O
│ Request 4 → Thread 4 (busy) │
│ ...                          │
│ Request N → Thread pool full │  ← New requests wait
└─────────────────────────────┘

Problem: 
- 1 thread per request
- Thread blocks on I/O
- Limited by thread pool size (e.g., 200 threads)
- More requests = more memory
```

**http4s (Non-Blocking):**
```
Async/Effect model:
┌─────────────────────────────┐
│ Thread 1:                   │
│   Request 1 → DB call (IO)  │  ← Doesn't block - Fiber suspended
│   Request 2 → API call (IO) │  ← Continues processing
│   Request 3 → Processing    │
│   Request N → ...           │
└─────────────────────────────┘

Benefits:
- Few threads (typically = CPU cores)
- Thousands of concurrent requests
- Much lower memory usage
- Scales better
```

#### Performance Impact

**Lift/Jetty:**
- 200 threads × ~1MB stack = ~200MB just for threads
- Max ~200 concurrent blocking requests
- Each blocked thread = wasted resources

**http4s:**
- 8 threads (on 8-core machine) × ~1MB = ~8MB for threads
- Can handle 10,000+ concurrent requests
- Threads never block, always doing work

#### Code Example - Blocking vs Non-Blocking

**Lift (Blocking):**
```scala
// This BLOCKS the thread while waiting for DB
lazy val getBank: OBPEndpoint = {
  case "banks" :: bankId :: Nil JsonGet _ => {
    cc => implicit val ec = EndpointContext(Some(cc))
    for {
      // Thread blocks here waiting for database
      bank <- Future { Connector.connector.vend.getBank(BankId(bankId)) }
    } yield {
      (bank, HttpCode.`200`(cc))
    }
  }
}

// Under the hood:
// Thread 1: Wait for DB... (blocked, not doing anything else)
// Thread 2: Wait for DB... (blocked)
// Thread 3: Wait for DB... (blocked)
// Eventually: No threads left → requests queue up
```

**http4s (Non-Blocking):**
```scala
// This NEVER blocks - thread is freed while waiting
def getBank[F[_]: Concurrent](bankId: String): F[Response[F]] = {
  for {
    // Thread is released while waiting for DB
    // Can handle other requests in the meantime
    bank <- getBankFromDB(bankId)  // Returns F[Bank], doesn't block
    response <- Ok(bank.asJson)
  } yield response
}

// Under the hood:
// Thread 1: Start DB call → release thread → handle other requests
//           DB returns → pick up continuation → send response
// Same thread handles 100s of requests while others wait for I/O
```

#### Real-World Impact

**Scenario:** 1000 concurrent requests, each needs 100ms of DB time

**Lift/Jetty (200 thread pool):**
- First 200 requests: start immediately
- Requests 201-1000: wait in queue
- Total time: ~500ms (because of queuing)
- Memory: 200MB for threads

**http4s (8 threads):**
- All 1000 requests: start immediately
- Process concurrently on 8 threads
- Total time: ~100ms (no queuing)
- Memory: 8MB for threads

#### Why This Matters for Migration

1. **Better Resource Usage**
   - Same machine can handle more requests
   - Lower memory footprint
   - Can scale vertically better

2. **No Thread Pool Tuning**
   - Lift: Need to tune thread pool size (too small = slow, too large = OOM)
   - http4s: Set to CPU cores, done

3. **Database Connections**
   - Lift: Need thread pool ≤ DB connections (e.g., 200 threads = 200 DB connections)
   - http4s: 8 threads can share smaller DB pool (e.g., 20 connections)

4. **Modern Architecture**
   - http4s uses cats-effect (like Akka, ZIO, Monix)
   - Industry standard for Scala backends
   - Better ecosystem and tooling

#### The Blocking Problem in Current OBP-API

```scala
// Common pattern in OBP-API - BLOCKS thread
for {
  bank <- Future { /* Get from DB - BLOCKS */ }
  accounts <- Future { /* Get from DB - BLOCKS */ }
  transactions <- Future { /* Get from Connector - BLOCKS */ }
} yield result

// Each Future ties up a thread waiting
// If 200 requests do this, 200 threads blocked
```

#### http4s Solution - Truly Async

```scala
// Non-blocking version
for {
  bank <- IO { /* Get from DB - thread released */ }
  accounts <- IO { /* Get from DB - thread released */ }
  transactions <- IO { /* Get from Connector - thread released */ }
} yield result

// IO suspends computation, releases thread
// Thread can handle other work while waiting
// 8 threads can handle 1000s of these concurrently
```

### Conclusion on Non-Blocking

**Yes, http4s is non-blocking and this is a MAJOR reason to migrate:**

- Better performance (10-50x more concurrent requests)
- Lower memory usage
- Better resource utilization
- Scales much better
- Removes need for thread pool tuning

**However:** To get full benefits, you'll need to:
1. Use `IO` or `F[_]` instead of blocking `Future`
2. Use non-blocking database libraries (Doobie, Skunk)
3. Use non-blocking HTTP clients (http4s client)

But the migration can be gradual - even blocking code in http4s is still better than Lift/Jetty's servlet model.

### Step 4: Shared Business Logic

```scala
// Keep business logic separate from HTTP layer
package code.api.service

object UserService {
  // Pure business logic - no Lift or http4s dependencies
  def createUser(username: String, email: String, password: String): Box[User] = {
    // Implementation
  }
}

// Use in Lift endpoint
class LiftEndpoints extends RestHelper {
  serve("obp" / "v6.0.0" prefix) {
    case "users" :: Nil JsonPost json -> _ => {
      val result = UserService.createUser(...)
      // Return Lift response
    }
  }
}

// Use in http4s endpoint
class Http4sEndpoints[F[_]: Concurrent] {
  def routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ POST -> Root / "obp" / "v6.0.0" / "users" =>
      val result = UserService.createUser(...)
      // Return http4s response
  }
}
```

## Migration Strategy

### Phase 1: Setup
- Add http4s dependencies
- Create http4s server infrastructure
- Start http4s on port 8081
- Keep all endpoints on Lift

### Phase 2: Convert New Endpoints
- All NEW endpoints go to http4s only
- Existing endpoints stay on Lift
- Share business logic between both

### Phase 3: Migrate Existing Endpoints
Priority order:
1. Simple GET endpoints (read-only, no sessions)
2. POST endpoints with simple authentication
3. Endpoints with complex authorization
4. Admin/management endpoints
5. OAuth/authentication endpoints (last)

### Phase 4: Deprecation
- Announce Lift endpoints deprecated
- Run both servers (port 8080 and 8081)
- Redirect/proxy 8080 -> 8081
- Update documentation

### Phase 5: Removal
- Remove Lift dependencies
- Remove Jetty dependency
- Single http4s server on port 8080
- No servlet container needed

## Request Routing During Migration

### OBP-API-Dispatch

```
Clients → OBP-API-Dispatch (8088/443)
           ├─→ Lift (8080) - v4, v5, v5.1
           └─→ http4s (8081) - v6, v7
```

**OBP-API-Dispatch:**
- Already exists in workspace
- Already http4s-based
- Designed for routing between backends
- Has error handling, logging
- Needs routing logic updates
- Single entry point
- Route by version or endpoint

**Migration Phases:**

**Phase 1: Setup**
```hocon
routing {
  http4s_versions = []  # All traffic to Lift
}
```

**Phase 2: First Migration**
```hocon
routing {
  http4s_versions = ["v6.0.0"]  # v6 to http4s
}
```

**Phase 3: Progressive**
```hocon
routing {
  http4s_versions = ["v5.1.0", "v6.0.0", "v7.0.0"]
}
```

**Phase 4: Complete**
```hocon
routing {
  http4s_versions = ["v4.0.0", "v5.0.0", "v5.1.0", "v6.0.0", "v7.0.0"]
}
```

### Alternative: Two Separate Ports

```
Clients → Load Balancer
           ├─→ Port 8080 (Lift)
           └─→ Port 8081 (http4s)
```
Clients need to know which port to use

## Database Access Migration

### Current: Lift Mapper
```scala
class AuthUser extends MegaProtoUser[AuthUser] {
  // Mapper ORM
}
```

### During Migration: Keep Mapper
```scala
// Both Lift and http4s use Mapper
// No need to migrate DB layer immediately
import code.model.dataAccess.AuthUser

// In http4s endpoint
def getUser(id: String): IO[Option[User]] = IO {
  AuthUser.find(By(AuthUser.id, id)).map(_.toUser)
}
```

### Future: Replace Mapper (Optional)
```scala
// Use Doobie or Skunk
import doobie._
import doobie.implicits._

def getUser(id: String): ConnectionIO[Option[User]] = {
  sql"SELECT * FROM authuser WHERE id = $id"
    .query[User]
    .option
}
```

## Configuration

```properties
# props/default.props

# Enable http4s server
http4s.enabled=true
http4s.port=8081

# Lift/Jetty (keep running)
jetty.port=8080

# Migration mode
# - "dual" = both servers running
# - "http4s-only" = only http4s
migration.mode=dual
```

## Testing Strategy

### Test Both Implementations
```scala
class UserEndpointTest extends ServerSetup {
  
  // Test Lift version
  scenario("Create user via Lift (port 8080)") {
    val request = (v6_0_0_Request / "users").POST
    val response = makePostRequest(request, userJson)
    response.code should equal(201)
  }
  
  // Test http4s version  
  scenario("Create user via http4s (port 8081)") {
    val request = (http4s_v6_0_0_Request / "users").POST
    val response = makePostRequest(request, userJson)
    response.code should equal(201)
  }
  
  // Test both give same result
  scenario("Both implementations return same result") {
    val liftResult = makeLiftRequest(...)
    val http4sResult = makeHttp4sRequest(...)
    liftResult should equal(http4sResult)
  }
}
```

## Resource Docs Compatibility

### Keep Same ResourceDoc Structure
```scala
// Shared ResourceDoc definition
val createUserDoc = ResourceDoc(
  createUser,
  implementedInApiVersion,
  "createUser",
  "POST",
  "/users",
  "Create User",
  """Creates a new user...""",
  postUserJson,
  userResponseJson,
  List(UserNotLoggedIn, InvalidJsonFormat)
)

// Lift endpoint references it
lazy val createUserLift: OBPEndpoint = {
  case "users" :: Nil JsonPost json -> _ => {
    // implementation
  }
}

// http4s endpoint references same doc
def createUserHttp4s[F[_]: Concurrent]: HttpRoutes[F] = {
  case req @ POST -> Root / "users" => {
    // implementation  
  }
}
```

## Advantages of Coexistence Approach

1. **Zero Downtime Migration**
   - Old endpoints keep working
   - New endpoints added incrementally
   - No big-bang rewrite

2. **Risk Mitigation**
   - Test new framework alongside old
   - Easy rollback per endpoint
   - Gradual learning curve

3. **Business Continuity**
   - No disruption to users
   - Features can still be added
   - Migration in background

4. **Shared Resources**
   - Same database
   - Same business logic
   - Same configuration



## Challenges and Solutions

### Challenge 1: Port Management
**Solution:** Use property files to configure ports, allow override

### Challenge 2: Session/State Sharing
**Solution:** Use stateless JWT tokens, shared Redis for sessions

### Challenge 3: Authentication
**Solution:** Keep auth logic separate, callable from both frameworks

### Challenge 4: Database Connections
**Solution:** Shared connection pool, configure max connections appropriately

### Challenge 5: Monitoring
**Solution:** Separate metrics for each server, aggregate in monitoring system

### Challenge 6: Deployment
**Solution:** Single JAR with both servers, configure which to start

## Deployment Considerations

### Development
```bash
# Start with both servers
sbt run
# Lift on :8080, http4s on :8081
```

### Production - Transition Period
```
# Run both servers
java -Dhttp4s.enabled=true \
     -Dhttp4s.port=8081 \
     -Djetty.port=8080 \
     -jar obp-api.jar
```

### Production - After Migration
```
# Only http4s
java -Dhttp4s.enabled=true \
     -Dhttp4s.port=8080 \
     -Dlift.enabled=false \
     -jar obp-api.jar
```

## Example: First Endpoint Migration

### 1. Existing Lift Endpoint
```scala
// APIMethods600.scala
lazy val getBank: OBPEndpoint = {
  case "banks" :: bankId :: Nil JsonGet _ => {
    cc => implicit val ec = EndpointContext(Some(cc))
    for {
      bank <- Future { Connector.connector.vend.getBank(BankId(bankId)) }
    } yield {
      (bank, HttpCode.`200`(cc))
    }
  }
}
```

### 2. Create http4s Version
```scala
// code/api/http4s/endpoints/BankEndpoints.scala
class BankEndpoints[F[_]: Concurrent] {
  
  def routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "obp" / "v6.0.0" / "banks" / bankId =>
      // Same business logic
      val bankBox = Connector.connector.vend.getBank(BankId(bankId))
      
      bankBox match {
        case Full(bank) => Ok(bank.toJson)
        case Empty => NotFound()
        case Failure(msg, _, _) => BadRequest(msg)
      }
  }
}
```

### 3. Both Available
- Lift: `http://localhost:8080/obp/v6.0.0/banks/{bankId}`
- http4s: `http://localhost:8081/obp/v6.0.0/banks/{bankId}`

### 4. Test Both
```scala
scenario("Get bank - Lift version") {
  val response = makeGetRequest(v6_0_0_Request / "banks" / testBankId.value)
  response.code should equal(200)
}

scenario("Get bank - http4s version") {
  val response = makeGetRequest(http4s_v6_0_0_Request / "banks" / testBankId.value)
  response.code should equal(200)
}
```

### 5. Deprecate Lift Version
- Add deprecation warning to Lift endpoint
- Update docs to point to http4s version
- Monitor usage

### 6. Remove Lift Version
- Delete Lift endpoint code
- All traffic to http4s

## Conclusion

**Yes, Lift and http4s can coexist** by running on different ports (8080 and 8081) within the same application. This allows for:

- Gradual, low-risk migration
- Endpoint-by-endpoint conversion
- Shared business logic and resources
- Zero downtime
- Flexible migration pace

The key is to **keep HTTP layer separate from business logic** so both frameworks can call the same underlying functions.

Start with simple read-only endpoints, then gradually migrate more complex ones, finally removing Lift when all endpoints are converted.
