package com.wiredforcode.gradle.spawn

import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

class SpawnProcessTask extends DefaultSpawnTask {
    String command
    String ready
    List<Closure> outputActions = new ArrayList<Closure>()

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
        if (isReady) {
            stampLockFile(pidFile, process)
        } else {
            checkForAbnormalExit(process)
        }
    }

    private void checkForAbnormalExit(Process process) {
        try {
            def exitValue = process.exitValue()
            if (exitValue) {
                throw new GradleException("The process terminated unexpectedly - status code ${exitValue}")
            }
        } catch (IllegalThreadStateException ignored) {
                throw new GradleException("Process failed to finish starting before timeout ${timeout}sec")
        }
    }

    private boolean waitUntilIsReadyOrEnd(final Process process) {
        final def currentThread = Thread.currentThread()
        final ReaderWorker reader = new ReaderWorker();
        reader.waiter = currentThread
        Thread worker = new Thread(new Runnable(){
          public void run(){
            reader.waitUntilIsReadyOrEnd(process)
          }
        });
        worker.start();
        long startTime = System.currentTimeMillis()
        def started = false
        try {
          Thread.sleep(timeout <= 0 ? Long.MAX_VALUE : timeout * 1000)
          logger.warn "Timed out after: " + timeout + " seconds"
        } catch (InterruptedException e) {
          //Should just be the reader thread waking up because it found the success message.
          logger.quiet "Finished after: " + (System.currentTimeMillis() - startTime) + "ms"
          //Clear interrupt status
          Thread.interrupted()
          started = true
        }
        if (reader.e != null){
          throw e;
        }//else
        worker.interrupt();
        //Timeout in 10sec, or timeout.
        if (worker.isAlive()){
          //Timeout failed.  Clear thread
          reader.waiter = null;
        }
        logger.debug "Spawn Post Processing.  Ready = ${reader.isReady}"
        started && reader.isReady
    }

    
    private Process buildProcess(String directory, String command) {
        def builder = new ProcessBuilder(command.split(' '))
        builder.redirectErrorStream(true)
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
    
    class ReaderWorker {
      boolean isReady = false
      Thread waiter
      Exception e;
      
      void waitUntilIsReadyOrEnd(Process process){
        def line
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))
        def currentThread = Thread.currentThread()
        try {
          while (!currentThread.isInterrupted() && !isReady 
            && (line = reader.readLine()) != null) {
              logger.quiet line
              runOutputActions(line)
              if (line.contains(ready)) {
                  logger.quiet "$command is ready."
                  isReady = true
              }
          }
        } catch (Exception e){
          this.e = e;
          logger.warn("Exception starting process", e)
        } finally {
          try {
            reader.close()
          } catch (IOException e){
            logger.info("Exception closing process inputstream: ${e.message}", e)
          }//end catch
        }//end finally
        logger.debug "Wake listeners. Ready = ${isReady}"
        if (waiter != null && !currentThread.isInterrupted()) {
          waiter.interrupt()
        } //else, waiter has abandoned listening
      }//end waitUntilIsReadyOrEnd
      
      def runOutputActions(String line) {
          outputActions.each { Closure<String> outputAction ->
              outputAction.call(line)
          }
      }
    }
}
