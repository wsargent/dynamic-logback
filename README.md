# Dynamic Logback

This is a Scala project that reads log level configuration from a file periodically, and sets logging levels in Logback.

Changing a logging level in Logback is simple and straightforward, and it is easy to tie the changing of log levels to an outside source and then change logging levels in an application dynamically without restarting the application.

It's really straightforward, and involves no Scala trickery -- it's the holidays and I wanted to do something easy.  I'm going to describe the whole thing at a low level to show there's no magic and that you can easily work this into your own applications.

## Example

The simplest way to do it is to keep configuration in a file, and periodically read from that file.  Here, we'll use `java.util.Timer` and refresh every 5 seconds from the filesystem: we can change the `logback.conf` file:

```scala
object Main {
  private val loggerContext = org.slf4j.LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]

  private val logger = loggerContext.getLogger(getClass)

  def main(args: Array[String]): Unit = {
    try {
      val file = Paths.get("logback").toFile
      val dynamicLevel = new DynamicLevel(loggerContext, file)
      val timer = new Timer()
      timer.scheduleAtFixedRate(new TimerTask {
        override def run(): Unit = dynamicLevel.refresh()
      }, 5000L, 5000L)

      for (i <- 1 until 100000) {
        logger.info("Hello world")
        logger.debug("Debug message")
        Thread.sleep(1000L)
      }

    } finally {
      loggerContext.stop()
    }
  }
}
```

The logback configuration file is in `logback.conf`, and has the `ROOT` logger set to `INFO`, so the `logger.debug` messages will not show:

```hocon
levels {
  # Root level is INFO.
  ROOT = INFO
}
```

Assuming you have sbt installed, you run it with:

```shell
sbt run
```

Once we start the application, we can go to `logback.conf`, change the level to `DEBUG`, save the file... and in a bit, we'll see debug output.

## DynamicLevel

There's two bits to the `DynamicLevel`, the `leveller` and the `loader`.  The loader gets the levels, the leveller sets them.

```scala
class DynamicLevel(loggerContext: LoggerContext, file: File, debug: Boolean = false) {
  private val leveller = new Leveller(loggerContext)
  private val loader = new Loader(file)

  def refresh(): Unit = {
    val levels = loader.load
    leveller.set(levels)
    if (debug) {
      leveller.getLoggerList.foreach { l =>
        val value = leveller.get(loggerContext.getLogger(l))
        debugLogger(l, value)
      }
    }
  }
}
```

The loader is probably the more interesting piece as it has to turn some HOCON into a Java map.  This needs some [Typesafe Config](https://github.com/lightbend/config) API calls and some collection logic, but 


```scala
private class Loader(file: File) {
  import com.typesafe.config._
  import scala.jdk.CollectionConverters._

  private def loadConfig: Config = {
    val systemProperties = ConfigFactory.systemProperties
    val fileConfig = ConfigFactory.parseFileAnySyntax(file)

    systemProperties // Look for a property from system properties first...
      .withFallback(fileConfig) // then look for file...
      .resolve(); // Tell config that we want to use ${?ENV_VAR} type stuff.
  }

  def load: Map[String, String] = {
    val config = loadConfig.getConfig("levels")
    if (debug) {
      val output = config.root.render(ConfigRenderOptions.defaults)
      debugConfig(output)
    }
    configAsMap(config)
  }

  private def configAsMap(levelsConfig: Config): Map[String, String] = {
    var levelsMap = Map.empty[String, String]
    val levelsEntrySet = levelsConfig.entrySet.asScala
    for (entry <- levelsEntrySet) {
      val name: String = entry.getKey
      try {
        val levelFromConfig: String = entry.getValue.unwrapped.toString
        levelsMap += (name -> levelFromConfig)
      } catch {
        case e: ConfigException.Missing =>
          // do nothing
        case e: Exception =>
          e.printStackTrace()
      }
    }
    levelsMap
  }
}
```

The Leveller is even simpler it calls a Logback logger, which has a `setLevel` value:

```scala
private class Leveller(private val context: LoggerContext) {
  import scala.jdk.CollectionConverters._

  def set(levelsMap: Map[String, String]): Unit = {
    levelsMap.foreach {
      case (name, level) =>
        val logger = context.getLogger(name)
        set(logger, level)
    }
  }

  def set(logger: Logger, levelName: String): Unit = {
    if (levelName == null || "null".equalsIgnoreCase(levelName)) logger.setLevel(null)
    else logger.setLevel(Level.toLevel(levelName))
  }

  def get(logger: Logger): Option[String] = Option(logger.getLevel).map(f => f.toString)

  def getEffective(logger: Logger): String = logger.getEffectiveLevel.toString

  def getLoggerList: Seq[String] = {
    context.getLoggerList.asScala.map(_.getName).toSeq
  }
}
```

That's it!  That's all you need to add dynamic logging to your application.