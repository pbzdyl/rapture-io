package rapture.io

import scala.collection.immutable.HashMap
import scala.actors._

/** Basic logging functionality, introducing the concept of logging zones. Note that this is almost
  * certainly not as efficient as it ought to be, so use log4j if efficiency matters to you. */

case class Zone(name: String)

case class Level(level: Int, name: String)

trait Logger { def log(msg: String) }

case class FileLogger(file: FileUrl) extends Logger {
  // FIXME: Here to workaround annoying import bug
  implicit val sw = rapture.io.FileStreamCharAppender
  def log(msg: String) = StringInput(msg) >> file
}

case object StdoutLogger extends Logger {
  def log(msg: String) = println(msg)
}

object log {

  object Trace extends Level(6, "trace")
  object Debug extends Level(5, "debug")
  object Info extends Level(4, "info")
  object Warn extends Level(3, "warn")
  object Error extends Level(2, "error")
  object Fatal extends Level(1, "fatal")

  trait ActorMessage
  case class RegisterListener(listener: Logger, spec: Map[Zone, Level]) extends ActorMessage
  case class LogMsg(level: Level, zone: Zone, msg: String, time: Long = System.currentTimeMillis) extends ActorMessage

  def listen(logger: Logger): Unit =
    listen(logger, Info)

  def listen(logger: Logger, level: Level): Unit =
    listen(logger, new HashMap[Zone, Level] { override def default(k: Zone) = level })

  def listen(logger: Logger, logSpec: Map[Zone, Level]): Unit =
    LogActor ! RegisterListener(logger, logSpec)

  @inline def trace(msg: => String)(implicit zone: Zone) =
    LogActor ! LogMsg(Trace, zone, msg)
  
  @inline def debug(msg: => String)(implicit zone: Zone) =
    LogActor ! LogMsg(Debug, zone, msg)
  
  @inline def info(msg: => String)(implicit zone: Zone) =
    LogActor ! LogMsg(Info, zone, msg)
  
  @inline def warn(msg: => String)(implicit zone: Zone) =
    LogActor ! LogMsg(Warn, zone, msg)
  
  @inline def error(msg: => String)(implicit zone: Zone) =
    LogActor ! LogMsg(Error, zone, msg)
  
  @inline def fatal(msg: => String)(implicit zone: Zone) =
    LogActor ! LogMsg(Fatal, zone, msg)
  
  @inline def exception(e: => Throwable)(implicit zone: Zone) =
    LogActor ! LogMsg(Error, zone, e.toString)

  object LogActor extends Actor {

    implicit val zone = Zone("logger")

    var listeners: List[(Logger, Map[Zone, Level])] = Nil

    val df = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
    var dateString = ""
    var dateCreated = 0L

    def act(): Unit = loop { react {
      case RegisterListener(listener, spec) =>
        log.info("Registering listener")
        listeners ::= (listener, spec)

      case LogMsg(level, zone, msg, time) =>
        if(time != dateCreated) {
          dateString = df.format(time)
          dateCreated = time
        }
        val formattedMsg = "%1$23s %2$5s %3$7s %4$s\n".format(dateString, level.name, zone.name, msg)
        for((lgr, spec) <- listeners if spec.getOrElse(zone, Error).level >= level.level) lgr.log(formattedMsg)
    } }
  }
  LogActor.start()

}