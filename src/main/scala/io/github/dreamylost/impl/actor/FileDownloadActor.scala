package io.github.dreamylost.impl.actor

import java.io.{ File, FileWriter, RandomAccessFile }
import java.lang
import java.net.URL

import akka.actor.typed.{ ActorRef, Behavior }
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.LazyLogging
import io.github.dreamylost.Constants.{ savePath, _ }
import io.github.dreamylost.FileDownload._
import io.github.dreamylost.FileUtils.{ reader, usingIgnore }

/**
 * 文件下载
 *
 * @author liguobin@growingio.com
 * @version 1.0,2020/5/12
 */
object FileDownloadActor extends LazyLogging {


  sealed trait FileTask

  final case class DownloadTask(actorId: Long, taskBeginTime: Long, fileTotalLength: Long, startPos: Long, endPos: Long,
    url: String, replyTo: ActorRef[Result]) extends FileTask

  final case class StartDownloadTask(actorId: Long, taskBeginTime: Long, fileTotalLength: Long, startPos: Long,
    endPos: Long, url: String) extends FileTask

  final case class ShutdownSystem(msg: Option[String]) extends FileTask

  sealed trait Result

  final case class DownloadResult(actorId: Long, perBlockLength: Long, startTime: Long,
    endTime: Long, msg: Option[String] = None, from: ActorRef[FileTask]) extends Result

  final case class DownloadErrorResult(actorId: Long, taskBeginTime: Long, fileTotalLength: Long, startPos: Long, endPos: Long,
    url: String, error: Option[String] = None, replyTo: ActorRef[FileTask]) extends Result

  final case class DownloadDoneResult(taskBeginTime: Long, fileTotalLength: Long, from: ActorRef[FileTask]) extends Result

  final case class DownloadDoingResult(actorId: Long, speedOfProgress: String, from: ActorRef[FileTask]) extends Result

  //下载actor
  def apply(): Behavior[FileTask] = Behaviors.receive { (context, message) =>
    message match {
      case task@DownloadTask(actorId, taskBeginTime, fileTotalLength, startPos, endPos, url, replyTo) =>
        logger.info(s"receive download task: \n[$task]")
        val currentBlockSize: Long = endPos - startPos
        var realStart = startPos
        var total = 0L
        var totalFileLen = 0L

        val createFileUpdateTempPos = (tempFilePath: String, isPerRequest: Boolean) => {
          val file = new File(tempFilePath)
          if (file.exists()) {
            logger.info(s"open temp file: $tempFilePath")
            val saveStartPos = reader(file)
            val savePos = saveStartPos.trim
            if (isPerRequest) {
              if (saveStartPos != null && saveStartPos.length() > 0) {
                realStart = java.lang.Long.parseLong(savePos)
                total = realStart - startPos
              }
            } else {
              if (saveStartPos != null && saveStartPos.length() > 0) {
                totalFileLen = java.lang.Long.parseLong(savePos)
              }
            }
          }
        }

        val requestFilePartAndUpdateStartPos = () => {
          val connection = new URL(url).openConnection().asHttpUrlConnection()
          connection.setRequestMethod("GET")
          connection.setReadTimeout(durationToMillis())
          val tempFileSavePos = savePath + "/" + actorId + tmpSuffix
          createFileUpdateTempPos(tempFileSavePos, true)
          connection.setRequestProperty("Range", "bytes=" + realStart + "-" + endPos)
          (connection, tempFileSavePos)
        }

        val startTime: Long = System.currentTimeMillis()
        val downloadFileName = getDownloadFileName(url)
        val (connection, tempFile) = requestFilePartAndUpdateStartPos()
        try {
          if (connection.getResponseCode == 206) {
            usingIgnore(new RandomAccessFile(downloadFileName, "rwd")) { randomAccessFile =>
              randomAccessFile.seek(startPos)
              usingIgnore(connection.getInputStream) { is =>
                val buffer = new Array[Byte](calculationAndGetBufferSize())
                var len = -1
                var newStartPos = startPos
                while (total < currentBlockSize && (len = is.read(buffer)) != -1) {
                  total += len
                  randomAccessFile.write(buffer, 0, len)
                  val processPercent = new lang.Double(total / currentBlockSize.asInstanceOf[Double] * 100).formatted("%.2f") + "%"
                  replyTo ! DownloadDoingResult(actorId, processPercent, context.self)
                  newStartPos += len
                  val fileWriter = new FileWriter(tempFile, false)
                  fileWriter.write(String.valueOf(newStartPos))
                  fileWriter.flush()
                  fileWriter.close()
                }
                val totalTempPath = savePath + "/" + "totalLength" + tmpSuffix
                createFileUpdateTempPos(totalTempPath, false)
                totalFileLen += total
                logger.info(s"totalFileLen: $totalFileLen")
                val fileWriter1 = new FileWriter(totalTempPath, false)
                fileWriter1.write(String.valueOf(totalFileLen))
                fileWriter1.flush()
                fileWriter1.close()
                connection.closeConnect()
                if (totalFileLen == fileTotalLength) {
                  replyTo ! DownloadDoneResult(taskBeginTime, fileTotalLength, context.self)
                } else {
                  replyTo ! DownloadResult(actorId, currentBlockSize, startTime, System.currentTimeMillis(), msg = Some(s"actorId $actorId finished"), context.self)
                }
              }
            }
          }
          else {
            replyTo ! DownloadErrorResult(actorId, taskBeginTime, fileTotalLength, startPos, endPos, url, error = Some("response code is not 206"), context.self)
          }
        } catch {
          case exception: Exception => {
            logger.warn(exception.getLocalizedMessage)
            replyTo ! DownloadErrorResult(actorId, taskBeginTime, fileTotalLength, startPos, endPos, url, error = Some(exception.getLocalizedMessage), context.self)
          }
        }
        Behaviors.same
    }
  }

}
