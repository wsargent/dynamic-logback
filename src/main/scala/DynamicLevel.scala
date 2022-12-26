import ch.qos.logback.classic._

import java.io.File

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

  private def debugConfig(msg: String): Unit = {
    println(msg)
  }

  private def debugLogger(l: String, value: Option[String]): Unit = {
    println(s"$l -> $value")
  }

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

  private class Loader(file: File) {
    import com.typesafe.config._
    import scala.jdk.CollectionConverters._

    private def loadConfig: Config = {
      val systemProperties = ConfigFactory.systemProperties
      val fileConfig = ConfigFactory.parseFileAnySyntax(file)

      systemProperties // Look for a property from system properties first...
        .withFallback(fileConfig) // then look in logback.conf...
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
}
