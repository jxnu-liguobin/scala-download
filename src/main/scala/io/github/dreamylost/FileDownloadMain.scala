package io.github.dreamylost

import java.io.RandomAccessFile
import java.net.URL
import java.util.concurrent.CountDownLatch

import com.typesafe.scalalogging.LazyLogging
import io.github.dreamylost.FileUtils._

import scala.concurrent.duration.Duration

/**
 * 文件下载
 *
 * @author liguobin@growingio.com
 * @version 1.0,2020/5/11
 */
object FileDownloadMain extends App {

  override def main(args: Array[String]): Unit = {
    val files = args.toSeq
    if (files.isEmpty) {
      FileDownload.batchDownload(Constants.DEFAULT_FILE_URLS)
    } else {
      FileDownload.batchDownload(files)
    }
  }
}

object FileDownload extends LazyLogging {

  import Constants._

  private[this] val threadCount: Long = Constants.threadCount
  private[this] var blockSize: Long = _
  val countDownLatch: CountDownLatch = new CountDownLatch(threadCount.toInt)

  def batchDownload(fileUrls: Seq[String]): Unit = {
    fileUrls.foreach(download)
  }

  /**
   * 根据文件的URL下载
   *
   * @param fileUrl
   */
  def download(fileUrl: String): Unit = {
    val startTime: Long = System.currentTimeMillis()
    val url = new URL(fileUrl)
    val connection = url.openConnection().asHttpUrlConnection()
    // 通过获取连接定义文件名
    val fileName = getDownloadFileName(fileUrl)
    // 获取下载文件大小
    if (connection.getResponseCode == 200) {
      val fileLength = connection.getContentLength
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
        // 最后一块的结束是文件大小
        if (threadCount == threadId) {
          endPos = fileLength
        }
        val thread = new Thread(new FileDownloadThread(threadId, startPos, endPos, fileUrl))
        thread.setName(s"download-threadId-$threadId")
        thread.start()
      }
      countDownLatch.await()
      printSpeed(fileLength, startTime, System.currentTimeMillis(), prefix = s"total speed", suffix = "all download finished, download successfully")
      //删除临时用于记录的文件
      clearTempFiles()
    } else {
      logger.error(s"network error or file not found: ${connection.getResponseCode}")
    }
    connection.closeConnect()
  }

  /**
   * 获取缓冲区大小
   *
   * @return
   */
  def calculationAndGetBufferSize(): Int = {
    val b = if (bufferSize > 256) 256 else if (bufferSize < 8) 8 else bufferSize
    b * 1024 * 1024
  }

  /**
   * 将配置的超时时间转化为毫秒
   *
   * @return
   */
  def durationToMillis(): Int = {
    Duration.create(timeOut).toMillis.asInstanceOf[Int]
  }

  /**
   * 获取下载的文件名称
   *
   * @param urlPath
   * @return
   */
  def getDownloadFileName(urlPath: String): String = {
    savePath + urlPath.substring(urlPath.lastIndexOf('/'))
  }

  /**
   * 计算并打印大概的下载速度
   *
   * @param fileSize
   * @param start
   * @param end
   * @param prefix
   * @param suffix
   */
  def printSpeed(fileSize: Long, start: Long, end: Long, prefix: String = "", suffix: String = ""): Unit = {
    val speed = fileSize.asInstanceOf[Double] / (end - start).asInstanceOf[Double]
    val s = speed * 1000 / 1024
    logger.info(s"$prefix: ${s.formatted("%.2f")} kb/s, total time: ${end - start}ms")
    logger.info(suffix)
  }
}
