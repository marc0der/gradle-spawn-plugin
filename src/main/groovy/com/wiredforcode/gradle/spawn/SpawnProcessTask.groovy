package com.wiredforcode.gradle.spawn

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

class SpawnProcessTask extends DefaultTask {

    public static final String LOCK_FILE = '.pid.lock'
    public static final String PID_FIELD = 'pid'

    String command
    String ready
    String directory = '.'

    SpawnProcessTask(){
        description = "Spawn a new server process in the background."
    }

    @TaskAction
    void spawn(){
        if(!(command && ready)) {
            throw new GradleException("Ensure that mandatory fields command, directory and ready are set.")
        }

        def pidFile = new File(directory, LOCK_FILE)
        if(pidFile.exists()) throw new GradleException("Server already running!")

        Process process = buildProcess(directory, command)
        int pid = extractPidFromProcess(process)
        stampLockFile(pidFile, pid)
        waitFor(process)
    }

    private waitFor(Process process) {
        def line
        def reader = new BufferedReader(new InputStreamReader(process.getInputStream()))
        while ((line = reader.readLine()) != null) {
            logger.quiet line
            if (line.contains(ready)) {
                logger.quiet "$command is ready."
                break;
            }
        }
    }

    private int extractPidFromProcess(Process process) {
        def pidField = process.class.getDeclaredField(PID_FIELD)
        pidField.accessible = true
        def pid = pidField.getInt(process)
        pid
    }

    private Process buildProcess(String directory, String command) {
        def builder = new ProcessBuilder(command.split(' '))
        builder.redirectErrorStream(true)
        builder.directory(new File(directory))
        def process = builder.start()
        process
    }

    private File stampLockFile(File pidFile, int pid) {
        pidFile << pid
    }
}
