package com.wiredforcode.gradle.spawn

import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

class SpawnProcessTask extends DefaultSpawnTask {
    String command
    String ready
    List<Closure> outputActions = new ArrayList<Closure>()
    String logFileName = null;

    @Input
    Map<String, String> environmentVariables = new HashMap<String, String>()

    void environmentVariable(String key, Object value) {
        environmentVariables.put(key, String.valueOf(value))
    }

    SpawnProcessTask() {
        description = "Spawn a new server process in the background."
    }

    void withOutput(Closure outputClosure) {
        outputActions.add(outputClosure)
    }

    File getLogFile() {
        return logFileName == null ? null : new File(directory, logFileName)
    }

    @TaskAction
    void spawn() {
        if (!(command && ready)) {
            throw new GradleException("Ensure that mandatory fields command and ready are set.")
        }

        def pidFile = getPidFile()
        if (pidFile.exists()) throw new GradleException("Server already running!")
        //if (logFile != null && logFile.exists()) logFile.delete()

        def process = buildProcess(directory, command)
        waitToProcessReadyOrClosed(process)
    }

    private void waitToProcessReadyOrClosed(Process process) {
        boolean isReady = waitUntilIsReadyOrEnd(logFile, process)
        if (isReady) {
            stampLockFile(pidFile, process)
        } else {
            checkForAbnormalExit(process)
        }
    }

    private void checkForAbnormalExit(Process process) {
        try {
            process.waitFor()
            def exitValue = process.exitValue()
            if (exitValue) {
                throw new GradleException("The process terminated unexpectedly - status code ${exitValue}")
            }
        } catch (IllegalThreadStateException ignored) {
        }
    }

    private boolean waitUntilIsReadyOrEnd(File logFile, Process process) {
        def line
        def reader = logFile == null ?
                new BufferedReader(new InputStreamReader(process.getInputStream())) :
                new BufferedReader(new InputStreamReader(new FileInputStream(logFile)))
        boolean isReady = false
        while (!isReady) {
            line = reader.readLine()
            if (line == null){
                if (logFile != null && process.alive)
                    Thread.sleep(10)
                else
                    break
            } else {
                logger.quiet line
                runOutputActions(line)
                if (line.contains(ready)) {
                    logger.quiet "$command is ready."
                    isReady = true
                }
            }
        }
        if (logFile != null) reader.close()
        isReady
    }

    def runOutputActions(String line) {
        outputActions.each { Closure<String> outputAction ->
            outputAction.call(line)
        }
    }

    private Process buildProcess(String directory, String ... command) {
        def builder = new ProcessBuilder(command)
        builder.redirectErrorStream(true)
        if (logFile != null){
            builder.redirectOutput(logFile)
        }
        builder.environment().putAll(environmentVariables)
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
