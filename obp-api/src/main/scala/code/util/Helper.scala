/**
Open Bank Project - API
Copyright (C) 2011-2026, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */

package code.util

import org.json4s._
import code.api.cache.{Redis, RedisLogger}

import java.net.{Socket, SocketException, URL}
import java.util.UUID.randomUUID
import java.util.Date
import code.api.util.{APIUtil, CallContext, CallContextLight, CustomJsonFormats}
import code.api.{APIFailureNewStyle, Constant}
import code.api.util.APIUtil.fullBoxOrException
import code.customer.internalMapping.MappedCustomerIdMappingProvider
import code.model.dataAccess.internalMapping.MappedAccountIdMappingProvider
import code.transaction.internalMapping.MappedTransactionIdMappingProvider
import net.liftweb.common._
import org.json4s.Extraction._
import org.apache.commons.lang3.StringUtils
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{AccountBalance, AccountBalances, AccountHeld, AccountId, CoreAccount, Customer, CustomerId, Transaction, TransactionCore, TransactionId}
import com.openbankproject.commons.util.{ReflectUtils, RequiredFieldValidation, RequiredInfo}
import com.tesobe.CacheKeyFromArguments

import net.liftweb.util.Helpers
import net.liftweb.util.Helpers.tryo
import java.text.SimpleDateFormat
import scala.concurrent.Future
import scala.util.Random
import scala.reflect.runtime.universe.Type
import scala.reflect.runtime.universe._
import scala.concurrent.duration._
import com.openbankproject.commons.util.JsonAliases.prettyRender



object Helper extends Loggable {

  /**
    *
    *
    */

  // If we need to return a string and all good, return an empty string
  // rule of silence http://www.linfo.org/rule_of_silence.html
  val SILENCE_IS_GOLDEN = ""

  def generatePermalink(name: String): String = {
    name.trim.toLowerCase.replace("-","").replaceAll(" +", " ").replaceAll(" ", "-")
  }

  /**
   * Useful for integrating failure message in for comprehensions.
   *
   * Normally a for comprehension might look like:
   *
   * for {
   *   account <- Account.find(...) ?~ "Account not found"
   *   if(account.isPublic)
   * } yield account
   *
   * The issue here is that we can't easily add an error message to describe why this might fail (i.e
   * if the account not public)
   *
   * Using this function, we can instead write
   *
   * for {
   *   account <- Account.find(...) ?~ "Account not found"
   *   accountIsPublic <- booleanToBox(account.isPublic, "Account is not public")
   * } yield account
   *
   * It's not ideal, but it works.
   *
   * @param statement A boolean condition
   * @param msg The message to give the Failure option if "statement" is false
   * @return A box that is Full if the condition was met, and a Failure(msg) if not
   */
  def booleanToBox(statement: => Boolean, msg: String): Box[Unit] = {
    if(statement)
      Full(())
    else
      Failure(msg)
  }

  def booleanToBox(statement: => Boolean): Box[Unit] = {
    if(statement)
      Full(())
    else
      Empty
  }

  /**
    * Helper function which wrap some statement into Future.
    * The function is curried i.e.
    * use this parameter syntax ---> (failMsg: String)(statement: => Boolean)
    * instead of this one ---------> (failMsg: String, statement: => Boolean)
    * Below is an example of recommended usage.
    * Please note that the second parameter is provided in curly bracket in order to mimics body of a function.
    *   booleanToFuture(failMsg = UserHasMissingRoles + CanGetAnyUser) {
    *     hasEntitlement("", u.userId, ApiRole.CanGetAnyUser)
    *   }
    * @param failMsg is used in case that result of call of function booleanToBox returns Empty
    * @param statement is call by name parameter.
    * @return In case the statement is false the function returns Future[Failure(failMsg)].
    *         Otherwise returns Future[Full()].
    */
  def booleanToFuture(failMsg: String, failCode: Int = 400, cc: Option[CallContext])(statement: => Boolean): Future[Box[Unit]] = {
    Future{
      booleanToBox(statement)
    } map {
      x => fullBoxOrException(x ~> APIFailureNewStyle(failMsg, failCode, cc.map(_.toLight)))
    }
  }

  // final: 2.13 requires an annotation argument to be a compile-time constant, and only a
  // final val of a literal qualifies. It is used as @deprecated(deprecatedJsonGenerationMessage).
  final val deprecatedJsonGenerationMessage = "json generation handled elsewhere as it changes from api version to api version"

  /**
   * Converts a number representing the smallest unit of a currency into a big decimal formatted according to the rules of
   * that currency. E.g. JPY: 1000 units (yen) => 1000, EUR: 1000 units (cents) => 10.00
   */
  def smallestCurrencyUnitToBigDecimal(units : Long, currencyCode : String) = {
    BigDecimal(units, currencyDecimalPlaces(currencyCode))
  }

  /**
   * Returns the number of decimal places a currency has. E.g. "EUR" -> 2, "JPY" -> 0
    *
    * @param currencyCode
   * @return
   */
  def currencyDecimalPlaces(currencyCode : String) = {
    //this data was sourced from Wikipedia, so it might not all be correct,
    //and some banking systems may still retain different units (e.g. CZK?)
    //notable it doesn't cover non-traditional currencies (e.g. cryptocurrencies)
    currencyCode match {
      //TODO: handle MRO and MGA, which are non-decimal
      case "CZK" | "JPY" | "KRW" => 0
      case "KWD" | "OMR" => 3
      case _ => 2
    }
  }

  /**
   * E.g.
   * amount: BigDecimal("12.45"), currencyCode : "EUR" => 1245
   * amount: BigDecimal("9034"), currencyCode : "JPY" => 9034
   */
  def convertToSmallestCurrencyUnits(amount : BigDecimal, currencyCode : String) : Long = {
    val decimalPlaces = Helper.currencyDecimalPlaces(currencyCode)

    (amount * BigDecimal("10").pow(decimalPlaces)).toLong
  }


  /*
  Returns a pretty json representation of the input
   */
  def prettyJson(input: JValue) : String = {
    implicit val formats = CustomJsonFormats.formats
    prettyRender(decompose(input))
  }


  /**
   * @param redirectUrl eg: http://localhost:8082/callback?foo=bar
   * @return http://localhost:8082/callback
   */
  def getStaticPortionOfRedirectURL(redirectUrl: String): Box[String] = {
    tryo(redirectUrl.split("\\?")(0)) //return everything before the "?"
  }

  /**
   * extract the host-only portion of a redirect URL.
   *
   * @param redirectUrl -> http://localhost:8082/callback?foo=bar
   * @return hostOnlyOfRedirectURL -> http://localhost:8082
   */
  @deprecated("We can not only use hostname as the redirectUrl, now add new method `getStaticPortionOfRedirectURL` ","05.12.2023")
  def getHostOnlyOfRedirectURL(redirectUrl: String): Box[String] = {
    val url = new URL(redirectUrl)
    val protocol = url.getProtocol() // http
    val authority = url.getAuthority()// localhost:8082, this will contain the port.
    tryo(s"$protocol://$authority") // http://localhost:8082
  }

  /**
    * check the redirect url is valid with default values.
    */
  def isValidInternalRedirectUrl(url: String) : Boolean = {
    val internalRedirectUrlsWhiteList = List(
      "/",
      "/dummy-user-tokens","/create-sandbox-account",
      "/add-user-auth-context-update-request","/otp",
      "/terms-and-conditions", "/privacy-policy",
      "/confirm-bg-consent-request",
      "/confirm-bg-consent-request-sca",
      "/confirm-vrp-consent-request",
      "/confirm-vrp-consent",
      "/consent-screen",
      "/consent",
    )

    val extractCleanURL = StringUtils.substringBefore(url, "?")

    internalRedirectUrlsWhiteList.contains(extractCleanURL)
  }

   /**
    * Used for version extraction from props string
    */
  val matchAnyStoredProcedure = "stored_procedure.*|star".r

  def getHostname(): String = {
    Constant.HostName match {
      case s: String if s.nonEmpty => s.split(":").lift(1) match {
        case Some(s) => s.replaceAll("\\/", "").replaceAll("\\.", "-")
        case None => "unknown"
      }
      case _ => "unknown"
    }
  }

  def getAkkaConnectorHostname(): String = {
    APIUtil.getPropsValue("akka_connector.hostname", "") match {
      case s: String if s.nonEmpty => s.replaceAll("\\/", "").replaceAll("\\.", "-")
      case _ => "unknown"
    }
  }

  def findAvailablePort(): Int = {
    val PORT_RANGE_MIN = 2552
    val PORT_RANGE_MAX = 2661
    val random = new Random(System.currentTimeMillis())

    def findRandomPort() = {
			val portRange = PORT_RANGE_MAX - PORT_RANGE_MIN
			PORT_RANGE_MIN + random.nextInt(portRange + 1)
		}

    def isPortAvailable(port: Int): Boolean = {
      var result = true
      try {
        new Socket("localhost", port).close()
        result = false
      }
      catch {
        case e: SocketException =>
      }
      result
    }

    var candidatePort = -1
    do {
      candidatePort = findRandomPort()
    }
    while (!isPortAvailable(candidatePort))
    candidatePort
  }



  // Shared across every class mixing in MdcLoggable below -- created once at object-init,
  // not per mixing instance. Masking (SecureLogging.maskSensitive, ~19 regex passes) and
  // Redis-shipping serialization are CPU-bound work that used to run inline on whatever
  // thread called logger.debug/info/etc. On this codebase's http4s/cats-effect request path
  // that thread is often a fiber-managed worker; running non-yielding CPU work on it directly
  // is indistinguishable, from the runtime's own fairness/starvation detector, from genuine
  // blocking I/O -- cats-effect's "your CPU is probably starving" warning fires the same way
  // either way, and its response is to compensate by spinning up additional worker/blocker
  // threads that are never reclaimed. Dispatching this work onto a small dedicated pool
  // instead means the calling thread (fiber or otherwise) returns immediately, regardless of
  // what kind of thread it happens to be -- this trait is mixed into ~260 classes, many of
  // them called from contexts (actors, scheduled jobs, Lift-era code) that have no IO runtime
  // at all, so wrapping in cats.effect.IO.blocking isn't an option here: nothing would ever
  // run it in those contexts. A plain background ExecutionContext works everywhere the
  // trait itself is used. Same pattern RedisLogger already uses for its own async shipping.
  //
  // Trade-off worth being explicit about: log output for a given logger is no longer
  // strictly write-ordered relative to other concurrent callers (each dispatched entry lands
  // whenever its turn on this small pool comes up). That's the same trade-off any async
  // logging setup makes (Logback's own AsyncAppender, Log4j2's AsyncLogger); this was never a
  // strict global ordering guarantee to begin with once Redis shipping (already async) was in
  // the picture.
  // lazy, not val: Helper's own static initializer transitively touches other objects
  // (APIUtil/Constant among them) that log during THEIR initialization, which can re-enter
  // here before a plain val declared at this point in the object body would have run yet --
  // observed as a NullPointerException on this executor during Helper's own <clinit>, caught
  // by MdcLoggableDispatchTest. `lazy val` computes on first real use instead of at a fixed
  // point in top-to-bottom initialization order, which is what this needs given how
  // entangled this codebase's early object initialization already is (not something
  // introduced here).
  // Bounded on purpose. An unbounded queue in front of this pool would turn a burst of log
  // calls -- each one holding the message closure and everything it captured -- into heap
  // growth, i.e. the failure this pool exists to avoid. Failure mode when the queue is full,
  // documented here because it is a deliberate choice:
  //   * DEBUG/TRACE/INFO entries are dropped and counted (`mdcLogDroppedCount`);
  //   * WARN/ERROR entries are run inline on the calling thread, so a warning or error is
  //     never silently lost. That only happens while the pool is saturated, which bounds the
  //     extra work on request threads to the overload window itself.
  // A drop is reported on stderr for the first occurrence and then once per
  // `MdcLogDropReportEvery`, so a sustained overload cannot turn into a stderr flood either.
  private val MdcLogDropReportEvery = 10000L
  private val mdcLogDropped = new java.util.concurrent.atomic.AtomicLong(0)

  private lazy val mdcLoggingExecutor: java.util.concurrent.ThreadPoolExecutor = {
    val threadCount = new java.util.concurrent.atomic.AtomicInteger(0)
    val poolSize = APIUtil.getPropsAsIntValue("mdc_logging_dispatch_thread_pool_size", 2)
    val queueSize = APIUtil.getPropsAsIntValue("mdc_logging_dispatch_queue_size", 10000)
    val executor = new java.util.concurrent.ThreadPoolExecutor(
      poolSize, poolSize, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
      new java.util.concurrent.ArrayBlockingQueue[Runnable](queueSize),
      (r: Runnable) => {
        val t = new Thread(r, s"mdc-log-dispatch-${threadCount.incrementAndGet()}")
        t.setDaemon(true)
        t
      },
      new java.util.concurrent.ThreadPoolExecutor.AbortPolicy()
    )
    // The threads are daemons so they never hold the JVM open, which also means anything
    // still queued at exit would be lost. Give the queue a short, bounded chance to drain.
    // Registration is refused once the JVM is already shutting down. That must not make the
    // first log call of a dying process throw, so the pool simply runs without a hook then.
    try Runtime.getRuntime.addShutdownHook(new Thread(() => {
      executor.shutdown()
      try executor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)
      catch { case _: InterruptedException => () }
    }, "mdc-log-dispatch-shutdown"))
    catch { case _: IllegalStateException => () }
    executor
  }

  /** Entries dropped because the dispatch queue was full since start-up. */
  def mdcLogDroppedCount: Long = mdcLogDropped.get()

  /** Entries currently waiting for a dispatch thread. */
  def mdcLogQueueDepth: Int = mdcLoggingExecutor.getQueue.size()

  /**
   * Run `body` on the dispatch pool. When the queue is full, `critical` work runs inline on the
   * caller and anything else is dropped and counted. `body` never throws to the caller.
   */
  private[util] def dispatchLog(clazzName: String, critical: Boolean)(body: => Unit): Unit =
    dispatchOn(mdcLoggingExecutor, clazzName, critical)(body)

  /** MDC key carrying the name of the thread that logged, set while a dispatched entry is written. */
  val MdcCallerThreadKey = "callerThread"

  // The executor is a parameter so a test can drive a tiny queue to saturation.
  private[util] def dispatchOn(executor: java.util.concurrent.Executor, clazzName: String, critical: Boolean)(body: => Unit): Unit = {
    // The write happens on a pool thread, so the thread name Logback and the Redis line format
    // would report is always "mdc-log-dispatch-N". Carry the caller's name in the MDC instead of
    // renaming the pool thread: it costs no native calls, and thread dumps still show what each
    // pool thread really is. The default logback.xml pattern prints it after %t.
    val callerThreadName = Thread.currentThread().getName
    val task: Runnable = () => {
      val previous = org.slf4j.MDC.get(MdcCallerThreadKey)
      org.slf4j.MDC.put(MdcCallerThreadKey, callerThreadName)
      try body
      catch { case e: Throwable => System.err.println(s"[$clazzName] background log dispatch failed: ${e.getMessage}") }
      finally {
        if (previous == null) org.slf4j.MDC.remove(MdcCallerThreadKey) else org.slf4j.MDC.put(MdcCallerThreadKey, previous)
      }
    }
    try executor.execute(task)
    catch {
      case _: java.util.concurrent.RejectedExecutionException =>
        executor match {
          // The pool has been shut down (JVM exit): nothing is overloaded, so write the entry
          // on the caller instead of losing what other shutdown hooks log.
          case s: java.util.concurrent.ExecutorService if s.isShutdown => task.run()
          case _ if critical => task.run()
          case _ =>
            val dropped = mdcLogDropped.incrementAndGet()
            if (dropped == 1L || dropped % MdcLogDropReportEvery == 0L)
              System.err.println(s"[$clazzName] log dispatch queue is full; $dropped non-critical log entries dropped so far")
        }
    }
  }

  trait MdcLoggable extends Loggable {

    // Capture the class name of the component mixing in this trait
    private val clazzName: String = this.getClass.getSimpleName.replaceAll("\\$", "")

    override protected val logger: net.liftweb.common.Logger = {
      val loggerName = this.getClass.getName

      new net.liftweb.common.Logger {

        private val underlyingLogger = net.liftweb.common.Logger(loggerName)

        // SimpleDateFormat is not thread-safe; one instance per thread.
        private val dateFormatTL = ThreadLocal.withInitial(() => {
          val f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssX")
          f.setTimeZone(java.util.TimeZone.getDefault) // force local TZ
          f
        })

        private def toRedisFormat(msg: AnyRef): String = {
          val ts = dateFormatTL.get().format(new Date())
          val thread = Option(org.slf4j.MDC.get(MdcCallerThreadKey)).getOrElse(Thread.currentThread().getName)
          s"[$ts] [$thread] [$clazzName] ${msg.toString}"
        }

      // Every level below builds `maskedMsg` by running the message through ~19 regex
      // passes in SecureLogging.maskSensitive. That cost must only be paid when the
      // result is actually going to be consumed (by the local logger and/or Redis
      // shipping) -- not unconditionally on every call site, which is what made this a
      // hot path under high request volume with DEBUG enabled. And once it is going to be
      // paid, it's dispatched onto mdcLoggingExecutor (see the failure-mode note above it)
      // rather than run inline on whatever thread called into this logger.
      private def dispatch(critical: Boolean)(body: => Unit): Unit = dispatchLog(clazzName, critical)(body)

      // INFO
      override def info(msg: => AnyRef): Unit = {
        if (underlyingLogger.isInfoEnabled || RedisLogger.shouldShip(RedisLogger.LogLevel.INFO)) {
          dispatch(critical = false) {
            val maskedMsg = SecureLogging.maskSensitive(msg)
            if (underlyingLogger.isInfoEnabled) underlyingLogger.info(maskedMsg)
            RedisLogger.logAsync(RedisLogger.LogLevel.INFO, toRedisFormat(maskedMsg))
          }
        }
      }

      override def info(msg: => AnyRef, t: => Throwable): Unit = {
        if (underlyingLogger.isInfoEnabled || RedisLogger.shouldShip(RedisLogger.LogLevel.INFO)) {
          dispatch(critical = false) {
            val maskedMsg = SecureLogging.maskSensitive(msg)
            val capturedT = t
            if (underlyingLogger.isInfoEnabled) underlyingLogger.info(maskedMsg, capturedT)
            RedisLogger.logAsync(RedisLogger.LogLevel.INFO, toRedisFormat(maskedMsg) + "\n" + capturedT.toString)
          }
        }
      }

      // WARN
      override def warn(msg: => AnyRef): Unit = {
        if (underlyingLogger.isWarnEnabled || RedisLogger.shouldShip(RedisLogger.LogLevel.WARNING)) {
          dispatch(critical = true) {
            val maskedMsg = SecureLogging.maskSensitive(msg)
            if (underlyingLogger.isWarnEnabled) underlyingLogger.warn(maskedMsg)
            RedisLogger.logAsync(RedisLogger.LogLevel.WARNING, toRedisFormat(maskedMsg))
          }
        }
      }

      override def warn(msg: => AnyRef, t: Throwable): Unit = {
        if (underlyingLogger.isWarnEnabled || RedisLogger.shouldShip(RedisLogger.LogLevel.WARNING)) {
          dispatch(critical = true) {
            val maskedMsg = SecureLogging.maskSensitive(msg)
            if (underlyingLogger.isWarnEnabled) underlyingLogger.warn(maskedMsg, t)
            RedisLogger.logAsync(RedisLogger.LogLevel.WARNING, toRedisFormat(maskedMsg) + "\n" + t.toString)
          }
        }
      }

      // ERROR
      override def error(msg: => AnyRef): Unit = {
        if (underlyingLogger.isErrorEnabled || RedisLogger.shouldShip(RedisLogger.LogLevel.ERROR)) {
          dispatch(critical = true) {
            val maskedMsg = SecureLogging.maskSensitive(msg)
            if (underlyingLogger.isErrorEnabled) underlyingLogger.error(maskedMsg)
            RedisLogger.logAsync(RedisLogger.LogLevel.ERROR, toRedisFormat(maskedMsg))
          }
        }
      }

      override def error(msg: => AnyRef, t: Throwable): Unit = {
        if (underlyingLogger.isErrorEnabled || RedisLogger.shouldShip(RedisLogger.LogLevel.ERROR)) {
          dispatch(critical = true) {
            val maskedMsg = SecureLogging.maskSensitive(msg)
            if (underlyingLogger.isErrorEnabled) underlyingLogger.error(maskedMsg, t)
            RedisLogger.logAsync(RedisLogger.LogLevel.ERROR, toRedisFormat(maskedMsg) + "\n" + t.toString)
          }
        }
      }

      // DEBUG
      override def debug(msg: => AnyRef): Unit = {
        if (underlyingLogger.isDebugEnabled || RedisLogger.shouldShip(RedisLogger.LogLevel.DEBUG)) {
          dispatch(critical = false) {
            val maskedMsg = SecureLogging.maskSensitive(msg)
            if (underlyingLogger.isDebugEnabled) underlyingLogger.debug(maskedMsg)
            RedisLogger.logAsync(RedisLogger.LogLevel.DEBUG, toRedisFormat(maskedMsg))
          }
        }
      }

      override def debug(msg: => AnyRef, t: Throwable): Unit = {
        if (underlyingLogger.isDebugEnabled || RedisLogger.shouldShip(RedisLogger.LogLevel.DEBUG)) {
          dispatch(critical = false) {
            val maskedMsg = SecureLogging.maskSensitive(msg)
            if (underlyingLogger.isDebugEnabled) underlyingLogger.debug(maskedMsg, t)
            RedisLogger.logAsync(RedisLogger.LogLevel.DEBUG, toRedisFormat(maskedMsg) + "\n" + t.toString)
          }
        }
      }

      // TRACE
      override def trace(msg: => AnyRef): Unit = {
        if (underlyingLogger.isTraceEnabled || RedisLogger.shouldShip(RedisLogger.LogLevel.TRACE)) {
          dispatch(critical = false) {
            val maskedMsg = SecureLogging.maskSensitive(msg)
            if (underlyingLogger.isTraceEnabled) underlyingLogger.trace(maskedMsg)
            RedisLogger.logAsync(RedisLogger.LogLevel.TRACE, toRedisFormat(maskedMsg))
          }
        }
      }

      // Delegate enabled checks
      override def isDebugEnabled: Boolean = underlyingLogger.isDebugEnabled
      override def isErrorEnabled: Boolean = underlyingLogger.isErrorEnabled
      override def isInfoEnabled: Boolean = underlyingLogger.isInfoEnabled
      override def isTraceEnabled: Boolean = underlyingLogger.isTraceEnabled
      override def isWarnEnabled: Boolean = underlyingLogger.isWarnEnabled
    }
  }

    protected def initiate(): Unit = ()

    initiate()
    MDC.put("host" -> getHostname)
  }


  /*
  Return true for Y, YES and true etc.
   */
  def stringToBooleanOption(input : String) : Option[Boolean] = {
    var upperInput = input.toUpperCase()
    upperInput match {
      case "Y" | "YES" | "TRUE" | "1" | "-1" => Full(true)
      case "N" | "NO" | "FALSE" | "0" => Full(false)
      case _ => None
    }
  }

  /*
  Return "Y" for true, "N" for false and "" if None
   */
  def optionBooleanToString(input : Option[Boolean]) : String = {
    val result : String = input match {
      case Some(a) => a match {
        case true => "Y"
        case false => "N"
      }
      case _ => ""
    }
    result
  }

  /**
   * get given type Required Field Info, cache the result
   * @param tpe
   * @return RequiredInfo
   */
  def getRequiredFieldInfo(tpe: Type): RequiredInfo = {
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      code.api.cache.Caching.memoizeSyncWithImMemory (Some(cacheKey.toString())) (100000.days) {

        RequiredFieldValidation.getRequiredInfo(tpe)

      }
    }
  }

  def i18n(message: String, default: Option[String] = None): String = {
    default.getOrElse {
      val words = message.split('.').toList match {
        case x :: Nil => Helpers.capify(x) :: Nil
        case x :: xs  => Helpers.capify(x) :: xs
        case _        => Nil
      }
      words.mkString(" ") + "."
    }
  }

  /**
   * helper function to convert customerId and accountId in a given instance
   * @param obj
   * @param customerIdConverter customerId converter, to or from customerReference
   * @param accountIdConverter accountId converter, to or from accountReference
   * @tparam T type of instance
   * @return modified instance
   */
  private def convertId[T](
    obj: T,
    customerIdConverter: String=> String,
    accountIdConverter: String=> String,
    transactionIdConverter: String=> String
  ): T = {
    //1st: We must not convert when connector == mapped. this will ignore the implicitly_convert_ids props.
    //2rd: if connector != mapped, we still need the `implicitly_convert_ids == true`

    def isCustomerId(fieldName: String, fieldType: Type, fieldValue: Any, ownerType: Type) = {
      ownerType =:= typeOf[CustomerId] ||
        (fieldName.equalsIgnoreCase("customerId") && fieldType =:= typeOf[String]) ||
        (ownerType <:< typeOf[Customer] && fieldName.equalsIgnoreCase("id") && fieldType =:= typeOf[String])
    }

    def isAccountId(fieldName: String, fieldType: Type, fieldValue: Any, ownerType: Type) = {
      ownerType <:< typeOf[AccountId] ||
        (fieldName.equalsIgnoreCase("accountId") && fieldType =:= typeOf[String])||
        (ownerType <:< typeOf[CoreAccount] && fieldName.equalsIgnoreCase("id") && fieldType =:= typeOf[String])||
        (ownerType <:< typeOf[AccountBalance] && fieldName.equalsIgnoreCase("id") && fieldType =:= typeOf[String])||
        (ownerType <:< typeOf[AccountBalances] && fieldName.equalsIgnoreCase("id") && fieldType =:= typeOf[String])||
        (ownerType <:< typeOf[AccountHeld] && fieldName.equalsIgnoreCase("id") && fieldType =:= typeOf[String])
    }

    def isTransactionId(fieldName: String, fieldType: Type, fieldValue: Any, ownerType: Type) = {
      ownerType <:< typeOf[TransactionId] ||
        (fieldName.equalsIgnoreCase("transactionId") && fieldType =:= typeOf[String])||
        (ownerType <:< typeOf[TransactionCore] && fieldName.equalsIgnoreCase("id") && fieldType =:= typeOf[String])||
        (ownerType <:< typeOf[Transaction] && fieldName.equalsIgnoreCase("id") && fieldType =:= typeOf[String])
    }

    if(APIUtil.getPropsValue("connector","mapped") != "mapped" && APIUtil.getPropsAsBoolValue("implicitly_convert_ids",false)){
      ReflectUtils.resetNestedFields(obj){
        case (fieldName, fieldType, fieldValue: String, ownerType) if isCustomerId(fieldName, fieldType, fieldValue, ownerType) => customerIdConverter(fieldValue)
        case (fieldName, fieldType, fieldValue: String, ownerType) if isAccountId(fieldName, fieldType, fieldValue, ownerType) => accountIdConverter(fieldValue)
        case (fieldName, fieldType, fieldValue: String, ownerType) if isTransactionId(fieldName, fieldType, fieldValue, ownerType) => transactionIdConverter(fieldValue)
      }
      obj
    } else
      obj
  }

  /**
   * convert given instance nested CustomerId to customerReference, AccountId to accountReference
   * @param obj
   * @tparam T type of instance
   * @return modified instance
   */
  def convertToReference[T](obj: T): T = {
    import code.api.util.ErrorMessages.{CustomerNotFoundByCustomerId, InvalidAccountIdFormat}
    def customerIdConverter(customerId: String): String = MappedCustomerIdMappingProvider
      .getCustomerPlainTextReference(CustomerId(customerId))
      .openOrThrowException(s"$CustomerNotFoundByCustomerId the invalid customerId is $customerId")
    def accountIdConverter(accountId: String): String = MappedAccountIdMappingProvider
      .getAccountPlainTextReference(AccountId(accountId))
      .openOrThrowException(s"$InvalidAccountIdFormat the invalid accountId is $accountId")
    def transactionIdConverter(transactionId: String): String = MappedTransactionIdMappingProvider
      .getTransactionPlainTextReference(TransactionId(transactionId))
      .openOrThrowException(s"$InvalidAccountIdFormat the invalid transactionId is $transactionId")
    convertId[T](obj, customerIdConverter, accountIdConverter, transactionIdConverter)
  }

  /**
   * convert given instance nested customerReference to CustomerId, accountReference to AccountId
   * @param obj
   * @tparam T type of instance
   * @return modified instance
   */
  def convertToId[T](obj: T): T = {
    import code.api.util.ErrorMessages.{CustomerNotFoundByCustomerId, InvalidAccountIdFormat}
    def customerIdConverter(customerReference: String): String = MappedCustomerIdMappingProvider
      .getOrCreateCustomerId(customerReference)
      .map(_.value)
      .openOrThrowException(s"$CustomerNotFoundByCustomerId the invalid customerReference is $customerReference")
    def accountIdConverter(accountReference: String): String = MappedAccountIdMappingProvider
      .getOrCreateAccountId(accountReference)
      .map(_.value).openOrThrowException(s"$InvalidAccountIdFormat the invalid accountReference is $accountReference")
    def transactionIdConverter(transactionReference: String): String = MappedTransactionIdMappingProvider
      .getOrCreateTransactionId(transactionReference)
      .map(_.value).openOrThrowException(s"$InvalidAccountIdFormat the invalid transactionReference is $transactionReference")
    if(obj.isInstanceOf[EmptyBox]) {
      obj
    } else {
      convertId[T](obj, customerIdConverter, accountIdConverter, transactionIdConverter)
    }
  }

  // Lift's S object is never initialized in the http4s path — all param/uri reads return Empty/default.
  object ObpS {
    def param(name: String): Box[String] = Empty
    def uriAndQueryString: Box[String] = Empty
    def uri: String = ""
    def queryString: Box[String] = Empty
  }

  def addColumnIfNotExists(dbDriver: String, tableName: String, columName: String, default: String) = {
    if (dbDriver.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver"))
      s"""
         |IF NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = '$tableName' AND COLUMN_NAME = '$columName')
         |BEGIN
         |    ALTER TABLE $tableName ADD $columName VARCHAR(255) DEFAULT '$default';
         |END""".stripMargin
    else
      s"""ALTER TABLE $tableName ADD COLUMN IF NOT EXISTS "$columName" character varying(255) DEFAULT '$default';""".stripMargin
  }


  def dropIndexIfExists(dbDriver: String, tableName: String, index: String) = {
    if (dbDriver.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver"))
      s"""
         |IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = '$index' AND object_id = OBJECT_ID('$tableName'))
         |BEGIN
         |    DROP INDEX $tableName.$index;
         |END""".stripMargin
    else
      s"""DROP INDEX IF EXISTS $index;""".stripMargin
  }


  def createIndexIfNotExists(dbDriver: String, tableName: String, index: String) = {
    if (dbDriver.contains("com.microsoft.sqlserver.jdbc.SQLServerDriver"))
      s"""
         |IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = '$index' AND object_id = OBJECT_ID('$tableName'))
         |BEGIN
         |    CREATE INDEX $index on $tableName(${index.split("_").drop(1).mkString(",")});
         |END""".stripMargin
    else
      s"CREATE INDEX IF NOT EXISTS $index on $tableName(${index.split("_").drop(1).mkString(",")});"
  }


  import java.util.Date
  import java.util.Calendar

  def calculateValidTo(
                        validFrom: Option[Date],
                        timeToLive: Long // milliseconds
                      ): Date = {
    val baseTime = validFrom.getOrElse(new Date())

    val calendar = Calendar.getInstance()
    calendar.setTime(baseTime)
    calendar.add(Calendar.SECOND, timeToLive.toInt / 1000)

    calendar.getTime
  }




}
