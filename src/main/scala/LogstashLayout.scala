package mojolly.logback

import scala.reflect.BeanProperty
import java.util.Locale
import scala.util.matching.Regex
import collection.JavaConversions._
import collection.mutable
import collection.JavaConverters._
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.LayoutBase
import org.joda.time.{DateTimeZone, DateTime}
import net.liftweb.json._
import net.liftweb.json.JsonDSL._

import org.joda.time.format.ISODateTimeFormat

class LogstashLayout[E] extends LayoutBase[E] {

  implicit var formats = DefaultFormats
  //Match on #tag
  private val TAG_REGEX: Regex = """(?iu)\B#([^,#=!\s./]+)([\s,.]|$)""".

  @BeanProperty
  var applicationName: String = _

  @BeanProperty
  var tags: String = _

  lazy val sourceHost = try {
    java.net.InetAddress.getLocalHost.toString
  } catch {
    case _ => "unable to obtain hostname"
  }

  def doLayout(p1: E) = {
    try {
      val event = p1.asInstanceOf[ILoggingEvent]
      val msg = event.getFormattedMessage

      val extractedJson: JValue = try   { parse(msg) }
      catch { case _ => ("@text" -> msg) }

      val tags = parseTags(msg)
      val jv: JValue =
        ("@timestamp" -> new DateTime(event.getTimeStamp).toString(ISODateTimeFormat.dateTime.withZone(DateTimeZone.UTC))) ~
          ("@source_host" -> sourceHost) ~
          ("@message" -> extractedJson) ~
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
              ("application" -> applicationName)
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
    TAG_REGEX.findAllIn(msg).matchData.map(_.group(1)).toSet
  }
}
