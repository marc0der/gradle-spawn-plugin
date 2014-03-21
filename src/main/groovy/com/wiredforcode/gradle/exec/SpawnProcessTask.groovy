package com.wiredforcode.gradle.exec

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

class SpawnProcessTask extends DefaultTask {

    public static final String LOCK_FILE = '.pid.lock'
    public static final String PID_FIELD = 'pid'

    String command
    String ready
    String directory

    SpawnProcessTask(){
        description = "Spawn a new server process in the background."
    }

    @TaskAction
    void spawn(){
        def pidFile = new File(directory, LOCK_FILE)
        if(pidFile.exists()) throw new GradleException("Server already running!")

        def builder = new ProcessBuilder(command.split(' '))
        builder.redirectErrorStream(true)
        builder.directory(new File(directory))
        def process = builder.start()

        def f = process.class.getDeclaredField(PID_FIELD)
        f.accessible = true
        def pid = f.getInt(process)
        pidFile << pid

        def line
        def reader = new BufferedReader(new InputStreamReader(process.getInputStream()))
        while ((line = reader.readLine()) != null) {
            println line
            if (line.contains(ready)) {
                logger.quiet "$command is ready."
                break;
            }
        }
    }
}
