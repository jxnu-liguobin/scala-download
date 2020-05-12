package io.github.dreamylost

import java.io.File
import java.net.{ HttpURLConnection, URLConnection }

import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._
import scala.util.Try

/**
 *
 * @author 梦境迷离
 * @since 2020-05-11
 * @version v1.0
 */
object Constants {

  final val DEFAULT_FILE_URLS = ConfigFactory.load().getStringList("download.file.urls").asScala.distinct
  final val savePath = ConfigFactory.load().getString("download.file.save-path")
  final val bufferSize = Try(ConfigFactory.load().getInt("download.buffer-size")).getOrElse(8)
  final val threadCount = Try(ConfigFactory.load().getInt("download.file.thread-count")).getOrElse(10)
  final val timeOut = Try(ConfigFactory.load().getString("download.timeout")).getOrElse("10 s")
  final val tmpSuffix = Try(ConfigFactory.load().getString("download.file.tmp-suffix")).getOrElse(".tmp")
  implicit final val default_charset = "utf8"

  implicit class WrapperHttpUrlConnection(connection: HttpURLConnection) {
    def closeConnect(): Unit = {
      if (connection != null) connection.disconnect()
    }
  }

  implicit class WrapperUrlConnection(connection: URLConnection) {
    def asHttpUrlConnection(): HttpURLConnection = {
      connection.asInstanceOf[HttpURLConnection]
    }
  }

  def clearTempFiles(): Unit = {
    val file = new File(savePath)
    if (file.isDirectory) {
      val cfs = file.listFiles()
      for (f <- cfs) {
        if (f.getName.endsWith(tmpSuffix)) f.delete()
      }
    }
  }

}
