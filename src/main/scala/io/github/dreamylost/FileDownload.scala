package io.github.dreamylost

import java.io.{ RandomAccessFile, _ }
import java.net.{ HttpURLConnection, URL }
import java.util.concurrent.CountDownLatch

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import io.github.dreamylost.FileUtils._

import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration
import scala.util.Try

/**
 *
 * @author liguobin@growingio.com
 * @version 1.0,2020/5/11
 */

object FileDownloadSpec extends App {

  override def main(args: Array[String]): Unit = {
    val files = args.toSeq
    if (files.isEmpty) {
      FileDownload.batchDownload(FileDownload.DEFAULT_FILE_URLS)
    } else {
      FileDownload.batchDownload(files)
    }
  }
}

object FileDownload extends LazyLogging {

  val DEFAULT_FILE_URLS = ConfigFactory.load().getStringList("download.file.urls").asScala.distinct

  private[this] val saveAddress = ConfigFactory.load().getString("download.file.save-address")
  private[this] val bufferSize = Try(ConfigFactory.load().getInt("download.buffer-size")).getOrElse(8)
  private[this] val timeOut = Try(ConfigFactory.load().getString("download.timeout")).getOrElse("10 s")
  private[this] val tmpSufix = Try(ConfigFactory.load().getString("download.file.tmp-suffix")).getOrElse(".tmp")

  private[this] var threadCount: Long = _
  private[this] var blockSize: Long = _
  private[this] var countDownLatch: CountDownLatch = _

  def batchDownload(fileUrls: Seq[String]): Unit = {
    fileUrls.foreach(download)
  }

  def download(fileUrl: String): Unit = {
    val startTime: Long = System.currentTimeMillis()
    val url = new URL(fileUrl)
    val connection = url.openConnection().asInstanceOf[HttpURLConnection]
    // 通过获取连接定义文件名
    val fileName = getFileName(fileUrl)
    // 获取下载文件大小
    if (connection.getResponseCode == 200) {
      val fileLength = connection.getContentLength
      calculationThreadCount(fileLength)
      // 在本地创建一个与服务器大小一致的可随机写入文件
      usingIgnore(new RandomAccessFile(fileName, "rwd")) {
        randomAccessFile => randomAccessFile.setLength(fileLength)
      }
      blockSize = fileLength / threadCount
      logger.info(s"file name: $fileName, file length: $fileLength, each block: ${blockSize / 1024 / 1024}MB")
      for (threadId <- 1L to threadCount) {
        // 定义每个线程开始以及结束的下载位置
        val startPos: Long = (threadId - 1) * blockSize // 开始下载的位置
        var endPos: Long = (threadId * blockSize) - 1 // 结束下载的位置（不包含最后一块）
        //最后一块的结束是文件大小
        if (threadCount == threadId) {
          endPos = fileLength
        }
        val thread = new Thread(new DownloadThread(threadId, startPos, endPos, fileUrl))
        thread.setName(s"download-threadId-$threadId")
        thread.start()
      }
      countDownLatch.await()
      val endTime: Long = System.currentTimeMillis()
      printSpeed(fileLength, startTime, endTime, prefix = s"total speed: ", suffix = "all download finished, download successfully")
      //删除临时用于记录的文件
      val file = new File(saveAddress)
      if (file.isDirectory) {
        val cfs = file.listFiles()
        for (f <- cfs) {
          if (f.getName.endsWith(".tmp")) f.delete()
        }
      }

    } else {
      logger.error(s"network error or file not found: ${connection.getResponseCode}")
    }
    closeConnect(connection)
  }

  private class DownloadThread(threadId: Long, var startPos: Long, endPos: Long, url: String) extends Runnable {
    val currentBlockSize: Long = endPos - startPos
    var total = 0

    def run(): Unit = {
      val startTime: Long = System.currentTimeMillis()
      val fileName = getFileName(url)
      val connection = new URL(url).openConnection().asInstanceOf[HttpURLConnection]
      connection.setRequestMethod("GET")
      connection.setReadTimeout(getUnWrapperTimeOut())
      val downloadProgressTempFile = getTempFileName(url, threadId)
      logger.info(s"create temp file: $downloadProgressTempFile")
      val file = new File(downloadProgressTempFile)
      val printStream: PrintStream = new PrintStream(downloadProgressTempFile)
      if (file.exists() && file.length() > 0) {
        val saveStartPos = reader(file, "utf8")
        if (saveStartPos != null && saveStartPos.length() > 0) {
          startPos = Integer.parseInt(saveStartPos)
        }
      }

      // 注意双引号内的格式，不能包含空格（等其他字符），否则报416
      connection.setRequestProperty("Range", "bytes=" + startPos + "-" + endPos)
      if (connection.getResponseCode == 206) {
        // 存储下载文件的随机写入文件
        //TODO 生产消费模式，使用独立线程写
        usingIgnore(new RandomAccessFile(fileName, "rwd")) { randomAccessFile =>
          // 设置开始下载的位置
          randomAccessFile.seek(startPos)
          logger.info(s"thread: $threadId, startPos: $startPos, endPos: $endPos")
          usingIgnore(connection.getInputStream) { is =>
            val buffer = new Array[Byte](getBufferSize())
            var len = -1
            var newPos = startPos;
            while (total < currentBlockSize && (len = is.read(buffer)) != -1) {
              total += len
              randomAccessFile.write(buffer, 0, len)
              // 更新存储的下载标记文件
              newPos += len
              val savePoint = String.valueOf(newPos)
              printStream.println(savePoint)
            }
            val endTime: Long = System.currentTimeMillis()
            logger.info(s"current thread $threadId, startPos: $startPos, endPos: $endPos, current total: $total")
            closeConnect(connection)
            printSpeed(currentBlockSize, startTime, endTime, prefix = s"thread $threadId speed: ", suffix = s"thread $threadId finished")
            countDownLatch.countDown()
          }
        }
      }
      else {
        logger.error("network error")
      }

    }
  }

  private[this] def getBufferSize() = {
    val b = if (bufferSize > 256) 256 else if (bufferSize < 8) 8 else bufferSize
    b * 1024 * 1024
  }

  private[this] def calculationThreadCount(fileSize: Long) = {
    threadCount = fileSize / getBufferSize() + 1
    countDownLatch = new CountDownLatch(threadCount.toInt)
  }

  private[this] def getFileName(urlPath: String) = {
    saveAddress + urlPath.substring(urlPath.lastIndexOf('/'))
  }

  private[this] def getUnWrapperTimeOut() = {
    Duration.create(timeOut).toMillis.asInstanceOf[Int]
  }

  private[this] def getTempFileName(urlPath: String, threadId: Long) = {
    saveAddress + "/" + threadId + tmpSufix
  }

  private[this] def closeConnect(connection: HttpURLConnection): Unit = {
    if (connection != null) connection.disconnect()
  }

  private[this] def printSpeed(fileSize: Long, start: Long, end: Long, prefix: String = "", suffix: String = ""): Unit = {
    val speed = fileSize.asInstanceOf[Double] / (end - start).asInstanceOf[Double]
    val s = speed * 1000 / 1024
    logger.info(s"$prefix: ${s.formatted("%.2f")} kb/s, total time: ${end - start}ms")
    logger.info(suffix)

  }
}
