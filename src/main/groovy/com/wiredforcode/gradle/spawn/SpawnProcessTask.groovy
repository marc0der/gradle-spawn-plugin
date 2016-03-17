package com.wiredforcode.gradle.spawn

import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

class SpawnProcessTask extends DefaultSpawnTask {
    String command
    String ready

    SpawnProcessTask() {
        description = "Spawn a new server process in the background."
    }

    @TaskAction
    void spawn() {
        if (!(command && ready)) {
            throw new GradleException("Ensure that mandatory fields command and ready are set.")
        }

        def pidFile = getPidFile()
        if (pidFile.exists()) throw new GradleException("Server already running!")

        def process = buildProcess(directory, command)
        waitToProcessReadyOrClosed(process)
    }

    private void waitToProcessReadyOrClosed(Process process) {
        boolean isReady = waitUntilIsReadyOrEnd(process)
        if (!isReady) {
            checkForAbnormalExit(process)
        } else {
            stampLockFile(pidFile, process)
        }
    }

    private void checkForAbnormalExit(Process process) {
        try {
            process.waitFor()
            def exitValue = process.exitValue()
            if (exitValue) {
                throw new GradleException("The process terminated unexpectedly - status code ${exitValue}")
            }
        } catch (IllegalThreadStateException ignored) { }
    }

    private boolean waitUntilIsReadyOrEnd(Process process) {
        def inputStream = new InputStreamReader(process.getInputStream())
        def reader = new BufferedReader(inputStream)
        def line
        boolean isReady = false
        try {
            while ((line = reader.readLine()) != null && !isReady) {
                logger.quiet line
                if (line.contains(ready)) {
                    logger.quiet "$command is ready."
                    isReady = true
                }
            }
        } finally {
            inputStream?.close()
            reader?.close()
        }
        isReady
    }

    private Process buildProcess(String directory, String command) {
        def builder = new ProcessBuilder(command.split(' '))
        builder.redirectErrorStream(true)
        builder.directory(new File(directory))
        builder.start()
    }

    private File stampLockFile(File pidFile, Process process) {
        pidFile << extractPidFromProcess(process)
    }

    private int extractPidFromProcess(Process process) {
        def pidField = process.class.getDeclaredField('pid')
        pidField.accessible = true

        return pidField.getInt(process)
    }
}
