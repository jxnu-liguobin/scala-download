Scala 多线程断点下载
--

## 使用

启动FileDownloadSpec.scala

超大文件线程数量较多，需要修改系统最大打开文件数量

```bash
ulimit -n 2048
```

修改application.conf
```
download {
    #使用缓冲区和文件大小，计算所需线程数量
    buffer-size = 64
    #HTTP超时
    timeout = "10 s"
    file {
        #文件保存地址
        save-path = "/Users/Downloads/scala-download"
        #默认带下载文件路径
        urls = ["https://downloads.lightbend.com/scala/2.13.2/scala-2.13.2.deb"]
        #记录下载状态的临时文件
        tmp-suffix = ".tmp"
    }
}
```

目前写入是使用seek，因为写入还是单线程IO所以比较慢。如不需要将下载写入文件，可以考虑引入生产者消费者(后续用Akka)，通过记录序号，还原数据。

```
/Library/Java/JavaVirtualMachines/jdk1.8.0_181.jdk/Contents/Home/bin/java "-javaagent:/Applications/IntelliJ IDEA.app/Contents/lib/idea_rt.jar=56802:/Applications/IntelliJ IDEA.app/Contents/bin" ......
[2020-05-11T19:43:15,497] [INFO ] [main#1] io.github.dreamylost.FileDownload$#95 - file name: /Users/Downloads/scala-download/scala-2.13.2.deb, file length: 644878994, each block: 61MB
[2020-05-11T19:43:15,580] [INFO ] [download-threadId-3#18] io.github.dreamylost.FileDownload$#137 - create temp file: /Users/Downloads/scala-download/3.txt
[2020-05-11T19:43:15,580] [INFO ] [download-threadId-1#16] io.github.dreamylost.FileDownload$#137 - create temp file: /Users/Downloads/scala-download/1.txt
[2020-05-11T19:43:15,580] [INFO ] [download-threadId-7#22] io.github.dreamylost.FileDownload$#137 - create temp file: /Users/Downloads/scala-download/7.txt
[2020-05-11T19:43:15,580] [INFO ] [download-threadId-5#20] io.github.dreamylost.FileDownload$#137 - create temp file: /Users/Downloads/scala-download/5.txt
[2020-05-11T19:43:15,580] [INFO ] [download-threadId-6#21] io.github.dreamylost.FileDownload$#137 - create temp file: /Users/Downloads/scala-download/6.txt
[2020-05-11T19:43:15,581] [INFO ] [download-threadId-9#24] io.github.dreamylost.FileDownload$#137 - create temp file: /Users/Downloads/scala-download/9.txt
[2020-05-11T19:43:15,581] [INFO ] [download-threadId-4#19] io.github.dreamylost.FileDownload$#137 - create temp file: /Users/Downloads/scala-download/4.txt
[2020-05-11T19:43:15,581] [INFO ] [download-threadId-8#23] io.github.dreamylost.FileDownload$#137 - create temp file: /Users/Downloads/scala-download/8.txt
[2020-05-11T19:43:15,580] [INFO ] [download-threadId-2#17] io.github.dreamylost.FileDownload$#137 - create temp file: /Users/Downloads/scala-download/2.txt
[2020-05-11T19:43:15,580] [INFO ] [download-threadId-10#25] io.github.dreamylost.FileDownload$#137 - create temp file: /Users/Downloads/scala-download/10.txt
[2020-05-11T19:43:16,026] [INFO ] [download-threadId-1#16] io.github.dreamylost.FileDownload$#159 - thread: 1, startPos: 0, endPos: 64487898
[2020-05-11T19:43:16,644] [INFO ] [download-threadId-2#17] io.github.dreamylost.FileDownload$#159 - thread: 2, startPos: 64487899, endPos: 128975797
[2020-05-11T19:43:16,649] [INFO ] [download-threadId-6#21] io.github.dreamylost.FileDownload$#159 - thread: 6, startPos: 322439495, endPos: 386927393
[2020-05-11T19:43:16,659] [INFO ] [download-threadId-8#23] io.github.dreamylost.FileDownload$#159 - thread: 8, startPos: 451415293, endPos: 515903191
[2020-05-11T19:43:16,660] [INFO ] [download-threadId-10#25] io.github.dreamylost.FileDownload$#159 - thread: 10, startPos: 580391091, endPos: 644878994
[2020-05-11T19:43:16,666] [INFO ] [download-threadId-4#19] io.github.dreamylost.FileDownload$#159 - thread: 4, startPos: 193463697, endPos: 257951595
[2020-05-11T19:43:16,724] [INFO ] [download-threadId-9#24] io.github.dreamylost.FileDownload$#159 - thread: 9, startPos: 515903192, endPos: 580391090
[2020-05-11T19:43:16,748] [INFO ] [download-threadId-5#20] io.github.dreamylost.FileDownload$#159 - thread: 5, startPos: 257951596, endPos: 322439494
[2020-05-11T19:43:16,764] [INFO ] [download-threadId-7#22] io.github.dreamylost.FileDownload$#159 - thread: 7, startPos: 386927394, endPos: 451415292
[2020-05-11T19:43:16,781] [INFO ] [download-threadId-3#18] io.github.dreamylost.FileDownload$#159 - thread: 3, startPos: 128975798, endPos: 193463696
[2020-05-11T19:44:21,537] [INFO ] [download-threadId-1#16] io.github.dreamylost.FileDownload$#173 - current thread 1, startPos: 0, endPos: 64487898, current total: 64487899
[2020-05-11T19:44:21,549] [INFO ] [download-threadId-1#16] io.github.dreamylost.FileDownload$#65 - thread 1 speed: : 954.38 kb/s
[2020-05-11T19:44:21,549] [INFO ] [download-threadId-1#16] io.github.dreamylost.FileDownload$#66 - thread 1 finished
[2020-05-11T19:46:51,424] [INFO ] [download-threadId-6#21] io.github.dreamylost.FileDownload$#173 - current thread 6, startPos: 322439495, endPos: 386927393, current total: 64487899
[2020-05-11T19:46:51,425] [INFO ] [download-threadId-6#21] io.github.dreamylost.FileDownload$#65 - thread 6 speed: : 291.73 kb/s
[2020-05-11T19:46:51,425] [INFO ] [download-threadId-6#21] io.github.dreamylost.FileDownload$#66 - thread 6 finished
[2020-05-11T19:47:09,832] [INFO ] [download-threadId-10#25] io.github.dreamylost.FileDownload$#173 - current thread 10, startPos: 580391091, endPos: 644878994, current total: 64487903
[2020-05-11T19:47:09,833] [INFO ] [download-threadId-10#25] io.github.dreamylost.FileDownload$#65 - thread 10 speed: : 268.81 kb/s
[2020-05-11T19:47:09,833] [INFO ] [download-threadId-10#25] io.github.dreamylost.FileDownload$#66 - thread 10 finished
[2020-05-11T19:47:36,324] [INFO ] [download-threadId-3#18] io.github.dreamylost.FileDownload$#173 - current thread 3, startPos: 128975798, endPos: 193463696, current total: 64487899
[2020-05-11T19:47:36,325] [INFO ] [download-threadId-3#18] io.github.dreamylost.FileDownload$#65 - thread 3 speed: : 241.50 kb/s
[2020-05-11T19:47:36,325] [INFO ] [download-threadId-3#18] io.github.dreamylost.FileDownload$#66 - thread 3 finished
[2020-05-11T19:47:58,121] [INFO ] [download-threadId-8#23] io.github.dreamylost.FileDownload$#173 - current thread 8, startPos: 451415293, endPos: 515903191, current total: 64487899
[2020-05-11T19:47:58,121] [INFO ] [download-threadId-8#23] io.github.dreamylost.FileDownload$#65 - thread 8 speed: : 222.87 kb/s
[2020-05-11T19:47:58,122] [INFO ] [download-threadId-8#23] io.github.dreamylost.FileDownload$#66 - thread 8 finished
[2020-05-11T19:48:00,121] [INFO ] [download-threadId-2#17] io.github.dreamylost.FileDownload$#173 - current thread 2, startPos: 64487899, endPos: 128975797, current total: 64487899
[2020-05-11T19:48:00,122] [INFO ] [download-threadId-2#17] io.github.dreamylost.FileDownload$#65 - thread 2 speed: : 221.30 kb/s
[2020-05-11T19:48:00,122] [INFO ] [download-threadId-2#17] io.github.dreamylost.FileDownload$#66 - thread 2 finished
[2020-05-11T19:48:00,337] [INFO ] [download-threadId-4#19] io.github.dreamylost.FileDownload$#173 - current thread 4, startPos: 193463697, endPos: 257951595, current total: 64487899
[2020-05-11T19:48:00,338] [INFO ] [download-threadId-4#19] io.github.dreamylost.FileDownload$#65 - thread 4 speed: : 221.14 kb/s
[2020-05-11T19:48:00,338] [INFO ] [download-threadId-4#19] io.github.dreamylost.FileDownload$#66 - thread 4 finished
[2020-05-11T19:48:01,842] [INFO ] [download-threadId-5#20] io.github.dreamylost.FileDownload$#173 - current thread 5, startPos: 257951596, endPos: 322439494, current total: 64487899
[2020-05-11T19:48:01,843] [INFO ] [download-threadId-5#20] io.github.dreamylost.FileDownload$#65 - thread 5 speed: : 219.97 kb/s
[2020-05-11T19:48:01,843] [INFO ] [download-threadId-5#20] io.github.dreamylost.FileDownload$#66 - thread 5 finished
[2020-05-11T19:48:07,775] [INFO ] [download-threadId-9#24] io.github.dreamylost.FileDownload$#173 - current thread 9, startPos: 515903192, endPos: 580391090, current total: 64487899
[2020-05-11T19:48:07,776] [INFO ] [download-threadId-9#24] io.github.dreamylost.FileDownload$#65 - thread 9 speed: : 215.51 kb/s
[2020-05-11T19:48:07,776] [INFO ] [download-threadId-9#24] io.github.dreamylost.FileDownload$#66 - thread 9 finished
[2020-05-11T19:48:13,094] [INFO ] [download-threadId-7#22] io.github.dreamylost.FileDownload$#173 - current thread 7, startPos: 386927394, endPos: 451415292, current total: 64487899
[2020-05-11T19:48:13,095] [INFO ] [download-threadId-7#22] io.github.dreamylost.FileDownload$#65 - thread 7 speed: : 211.65 kb/s
[2020-05-11T19:48:13,095] [INFO ] [download-threadId-7#22] io.github.dreamylost.FileDownload$#66 - thread 7 finished
[2020-05-11T19:48:13,096] [INFO ] [main#1] io.github.dreamylost.FileDownload$#65 - total speed: : 2100.36 kb/s
[2020-05-11T19:48:13,096] [INFO ] [main#1] io.github.dreamylost.FileDownload$#66 - all download finished, download successfully

Process finished with exit code 0
```