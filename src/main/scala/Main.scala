import ch.qos.logback.classic.LoggerContext

import java.nio.file.Paths
import java.util.{Timer, TimerTask}

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