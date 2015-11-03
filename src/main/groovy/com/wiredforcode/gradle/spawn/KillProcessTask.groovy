package com.wiredforcode.gradle.spawn

import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

class KillProcessTask extends DefaultSpawnTask {
    @TaskAction
    void kill() {
        def pidFile = getPidFile()
        if(!pidFile.exists()) throw new GradleException("No server running!")

        def pid = pidFile.text
        def process = "kill $pid".execute()

        try {
            process.waitFor()
        } finally {
            pidFile.delete()
        }
    }
}
