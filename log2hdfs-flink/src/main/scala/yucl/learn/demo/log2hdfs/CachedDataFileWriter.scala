package yucl.learn.demo.log2hdfs

import java.io.{BufferedWriter, OutputStreamWriter}
import java.util
import java.util.UUID
import java.util.concurrent._

import com.google.common.util.concurrent.ThreadFactoryBuilder
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FSDataOutputStream, Path}
import org.apache.hadoop.hdfs.DFSOutputStream
import org.apache.hadoop.hdfs.client.HdfsDataOutputStream.SyncFlag
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.concurrent.TrieMap

object CachedDataFileWriter {
  val logger: Logger = LoggerFactory.getLogger(CachedDataFileWriter.getClass)
  val conf = new Configuration()
  private val fileCache: TrieMap[String, CachedWriterEntity] = new TrieMap[String, CachedWriterEntity]
  val scheduledExecutorService: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setDaemon(true).build())

  def write(rawLog: String, fileFullName: String): Unit = {
    val targetFileName = fileFullName
    try {
      val cacheWriterEntity = getDataFileWriter(targetFileName)
      this.synchronized {
        val dataFileWriter = cacheWriterEntity.dataFileWriter
        dataFileWriter.write(rawLog + "\n")
        cacheWriterEntity.needSyncDFS = true
        cacheWriterEntity.lastWriteTime = System.currentTimeMillis()
      }
    } catch {
      case e: Exception =>
        fileCache.remove(targetFileName)
        logger.error(targetFileName, e)
    }
  }

  def getDataFileWriter(fileName: String): CachedWriterEntity = {
    this.synchronized {
      var cacheWriterEntity: CachedWriterEntity = fileCache.getOrElse(fileName, null)
      if (cacheWriterEntity == null) {
        val filePath = new Path(fileName + "." + UUID.randomUUID().toString)
        val fileSystem = filePath.getFileSystem(conf)
        var fsDataOutputStream: FSDataOutputStream = null
        if (fileSystem.exists(filePath)) {
          fsDataOutputStream = fileSystem.append(filePath)
        } else {
          fsDataOutputStream = fileSystem.create(filePath, false)
        }
        val dfw: BufferedWriter = new BufferedWriter(new OutputStreamWriter(fsDataOutputStream, "UTF-8"))
        cacheWriterEntity = new CachedWriterEntity(dfw, fsDataOutputStream)
        fileCache.put(fileName, cacheWriterEntity)
      }
      cacheWriterEntity
    }
  }

  def syncDFS(cachedWriterEntity: CachedWriterEntity): Unit = {
    this.synchronized {
      if (cachedWriterEntity.needSyncDFS) {
        cachedWriterEntity.dataFileWriter.flush()
        val fsDataOutputStream = cachedWriterEntity.fsDataOutputStream.getWrappedStream()
        val dFSOutputStream = fsDataOutputStream.asInstanceOf[DFSOutputStream]
        dFSOutputStream.hsync(util.EnumSet.of(SyncFlag.UPDATE_LENGTH))
        cachedWriterEntity.needSyncDFS = false
      }
    }
  }

  def syncAllDFS(): Unit = {
    for ((fileName, cachedWriterEntity) <- fileCache) {
      try {
        syncDFS(cachedWriterEntity)
      } catch {
        case e: Exception => {
          fileCache.remove(fileName)
          logger.error("call sync dfs failed", e)
        }
      }
    }
  }

  scheduledExecutorService.scheduleAtFixedRate(new Runnable {
    override def run() = {
      syncAllDFS()
    }
  }, 30, 30, TimeUnit.SECONDS)

  def closeTimeoutFiles(): Unit = {
    for ((fileName, cachedWriterEntity) <- fileCache) {
      if (System.currentTimeMillis() - cachedWriterEntity.lastWriteTime >  60 * 60 * 1000) {
        try {
          fileCache.remove(fileName)
          syncDFS(cachedWriterEntity)
          cachedWriterEntity.dataFileWriter.close()
          cachedWriterEntity.fsDataOutputStream.close()
          logger.info(fileName + " remove from writer cache")
        } catch {
          case e: Exception => logger.error(fileName, e)
        }
      }
    }
  }


  scheduledExecutorService.scheduleWithFixedDelay(new Runnable {
    override def run() = {
      closeTimeoutFiles()
    }
  }, 59, 59, TimeUnit.MINUTES)

  def closeAllFiles(): Unit = {
    logger.info("app is stopping, close all files")
    for ((fileName, cachedWriterEntity) <- fileCache) {
      try {
        fileCache.remove(fileName)
        syncDFS(cachedWriterEntity)
        if (cachedWriterEntity.needSyncDFS) {
          cachedWriterEntity.dataFileWriter.close()
          cachedWriterEntity.fsDataOutputStream.close()
        }
        logger.info(fileName + " remove from writer cache")
      } catch {
        case e: Exception => logger.error(fileName, e)
      }
    }
  }

}