package io.github.dreamylost

import java.io.{ RandomAccessFile, _ }
import java.net.{ HttpURLConnection, URL }

import com.typesafe.scalalogging.LazyLogging
import io.github.dreamylost.Constants._
import io.github.dreamylost.FileDownload._
import io.github.dreamylost.FileUtils._

/**
 * 文件下载线程
 *
 * @author liguobin@growingio.com
 * @version 1.0,2020/5/11
 */
class FileDownloadThread(threadId: Long, var startPos: Long, endPos: Long, url: String) extends Runnable with LazyLogging {

  /**
   * 获取临时文件的名称
   *
   * @param filePath
   * @param threadId
   * @return
   */
  private[this] def getTempFileName(filePath: String, threadId: Long): String = {
    savePath + "/" + threadId + tmpSuffix
  }

  /**
   * 请求分块，按需更新记录的每个线程的startPos
   *
   * @return
   */
  private[this] def requestFilePartAndUpdateStartPos(): (HttpURLConnection, PrintStream) = {
    val connection = new URL(url).openConnection().asHttpUrlConnection()
    connection.setRequestMethod("GET")
    connection.setReadTimeout(durationToMillis())
    val tempFileSavePos = getTempFileName(url, threadId)
    logger.info(s"create temp file: $tempFileSavePos")
    val file = new File(tempFileSavePos)
    val printStream: PrintStream = new PrintStream(tempFileSavePos)
    if (file.exists() && file.length() > 0) {
      val saveStartPos = reader(file)
      if (saveStartPos != null && saveStartPos.length() > 0) {
        startPos = Integer.parseInt(saveStartPos)
      }
    }
    connection.setRequestProperty("Range", "bytes=" + startPos + "-" + endPos)
    (connection, printStream)
  }

  val currentBlockSize: Long = endPos - startPos
  var total = 0

  def run(): Unit = {
    val startTime: Long = System.currentTimeMillis()
    val downloadFileName = getDownloadFileName(url)
    val (connection, printStream) = requestFilePartAndUpdateStartPos()
    // 注意双引号内的格式，不能包含空格（等其他字符），否则报416
    if (connection.getResponseCode == 206) {
      // 存储下载文件的随机写入文件
      // TODO 生产消费模式，使用独立线程写
      usingIgnore(new RandomAccessFile(downloadFileName, "rwd")) { randomAccessFile =>
        // 设置开始下载的位置
        randomAccessFile.seek(startPos)
        logger.info(s"thread: $threadId, startPos: $startPos, endPos: $endPos")
        usingIgnore(connection.getInputStream) { is =>
          val buffer = new Array[Byte](calculationAndGetBufferSize())
          var len = -1
          var newPos = startPos
          while (total < currentBlockSize && (len = is.read(buffer)) != -1) {
            total += len
            randomAccessFile.write(buffer, 0, len)
            // 更新存储的下载标记文件
            newPos += len
            val savePoint = String.valueOf(newPos)
            printStream.println(savePoint)
          }
          logger.info(s"current thread $threadId, startPos: $startPos, endPos: $endPos, current total: $total")
          connection.closeConnect()
          printSpeed(currentBlockSize, startTime, System.currentTimeMillis(), prefix = s"thread $threadId speed: ", suffix = s"thread $threadId finished")
          countDownLatch.countDown()
        }
      }
    }
    else {
      logger.error("network error")
    }

  }
}