package com.wiredforcode.gradle.spawn

import org.gradle.api.DefaultTask

class DefaultSpawnTask extends DefaultTask {
    String pidLockFileName = '.pid.lock'
    String directory = '.'
    /**
     * Time to wait for process to start/finish in seconds.
     */
    int timeout
    

    File getPidFile() {
        return new File(directory, pidLockFileName)
    }
}
