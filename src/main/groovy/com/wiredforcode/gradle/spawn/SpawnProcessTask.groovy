package com.wiredforcode.gradle.spawn

import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

class SpawnProcessTask extends DefaultSpawnTask {
    String command
    String ready
    /**
     * If set, do NOT abort reading and close the pipe from the process stdout.  There
     * are applications that will pick this up as a "Shutdown" signal.  Instead, continue
     * "siphoning" or reading from the InputStream, but just "chuck" the data.
     */
    boolean siphon
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
        worker.setDaemon(true)
        worker.setName(name + "-worker")
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
        //Signal worker that it is done.
        worker.interrupt();
        //Clear listening.
        reader.waiter = null;
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
        boolean interruptSent = false
        try {
          //Data to read, no EOF
          while (((line = reader.readLine()) != null) 
            //Can run and not yet ready
            && (isRunnable(currentThread) && !isReady
              //If there is no data, nothing to siphon
              || (line != null && siphon))) {
              //provision siphoning for process that dump when stdout is closed
              //This applies to golang based code specifically
              if (siphon && isReady){
                //done processing
                logger.quiet currentThread.name + ':' + line
                continue;
              }
              logger.quiet line
              runOutputActions(line)
              if (line.contains(ready)) {
                  logger.quiet "$command is ready."
                  isReady = true
                  logger.debug "Wake listeners. Ready = ${isReady}"
                  if (waiter != null && !currentThread.isInterrupted() && !interruptSent) {
                    waiter.interrupt()
                    interruptSent = true
                  } //else, waiter has abandoned listening
              }
          }
        } catch (Exception e){
          this.e = e;
          logger.warn("Exception starting process", e)
        } finally {
          try {
            if (!siphon){
              //If siphoning, don't close.  Otherwise the launched process will shutdown.
              reader.close()
            }
          } catch (IOException e){
            logger.info("Exception closing process inputstream: ${e.message}", e)
          }//end catch
          if (waiter != null && !interruptSent) {
            waiter.interrupt()
          } //else, waiter has abandoned listening
        }//end finally
        logger.trace "Finished reading stdout"
      }//end waitUntilIsReadyOrEnd
      
      boolean isRunnable(Thread currentThread){
        return !currentThread.isInterrupted() && waiter != null & waiter.isAlive()
      }
      
      def runOutputActions(String line) {
          outputActions.each { Closure<String> outputAction ->
              outputAction.call(line)
          }
      }
    }
}
