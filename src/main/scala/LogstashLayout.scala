package mojolly.logback

import scala.reflect.BeanProperty
import java.util.Locale
import scala.util.matching.Regex
import collection.JavaConversions._
import collection.mutable
import collection.JavaConverters._
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.LayoutBase
import net.liftweb.json._
import net.liftweb.json.JsonDSL._
import org.joda.time.format.ISODateTimeFormat
import org.joda.time.{DateTimeZone, DateTime}

class LogstashLayout[E] extends LayoutBase[E] {

  implicit var formats = DefaultFormats

  private val TAG_REGEX: Regex = """(?iu)\B#([^,#=!\s./]+)([\s,.]|$)""".r

  @BeanProperty
  var applicationName: String = _
  @BeanProperty
  var eventType: String = _

  @BeanProperty
  var tags: String = _

  def doLayout(p1: E) = {
    try {
      val event = p1.asInstanceOf[ILoggingEvent]
      val msg = event.getFormattedMessage
      val tags = parseTags(msg)
      val jv: JValue =
        ("@timestamp" -> new DateTime(event.getTimeStamp).toString(ISODateTimeFormat.dateTime.withZone(DateTimeZone.UTC))) ~
        ("@source_host" -> "pg-timapp-005.pgldn.youdevise.com") ~
        ("@message" -> event.getFormattedMessage) ~
        ("@tags" -> tags) ~
        ("@type" -> "string") ~
        ("@source" -> event.getLoggerName)

      val fields = {
        exceptionFields(event) merge {
          val mdc = {
            if (event.getMDCPropertyMap == null) JNothing else Extraction.decompose(event.getMDCPropertyMap.asScala)
          }
          (mdc merge
            ("thread_name" -> event.getThreadName) ~
              ("level" -> event.getLevel.toString) ~
              ("application" -> applicationName) ~
              ("event_type" -> eventType)
            )
        }
      }

      Printer.compact {
        render {
          val flds: JValue = ("@fields" -> fields)
          jv merge flds
        }
      }
    } catch {
      case e ⇒ {
        addError("There was a problem formatting the event:", e)
        ""
      }
    }
  }

  private def exceptionFields(event: ILoggingEvent): JValue = {
    if (event.getThrowableProxy == null) {
      JNothing
    } else {
      val th = event.getThrowableProxy
      val stea: Seq[StackTraceElement] = if (th.getStackTraceElementProxyArray != null) {
        th.getStackTraceElementProxyArray.map(_.getStackTraceElement)
      } else {
        List.empty[StackTraceElement]
      }
      ("error_message" -> th.getMessage) ~
        ("error" -> th.getClassName) ~
        ("stack_trace" -> (stea map {
          stl ⇒
            val jv: JValue =
              ("line" -> stl.getLineNumber) ~
                ("file" -> stl.getFileName) ~
                ("method_name" -> stl.getMethodName)
            jv
        }))
    }
  }

  private def parseTags(msg: String) = {
    TAG_REGEX.findAllIn(msg).matchData.map(_.group(1).toUpperCase(Locale.ENGLISH)).toSet
  }

}