package com.wiredforcode.gradle.spawn

import java.util.concurrent.TimeUnit

import org.gradle.api.tasks.TaskAction

class KillProcessTask extends DefaultSpawnTask {
    @TaskAction
    void kill() {
        def pidFile = getPidFile()
        if(!pidFile.exists()) {
            logger.quiet "No server running!"
            return
        }

        def pid = pidFile.text
        def process = "kill $pid".execute()

        try {
            if (timeout <= 0){
                process.waitFor()
            } else {
                killWithTimeOut(process, pid)
            }
        } finally {
            pidFile.delete()
        }
    }
    
    void killWithTimeOut(Process process, String pid){
      boolean success = process.waitFor(timeout, TimeUnit.SECONDS)
      if (!success){
        logger.info "Soft stop timed out, executing 'kill -s 9 ${pid}"
        def hardKillProcess = "kill -s 9 $pid".execute()
        hardKillProcess.waitFor()
      }
    }
    
    
}
