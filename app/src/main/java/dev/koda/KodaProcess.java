package dev.koda;

/**
 * JNI wrapper for native subprocess management.
 * Links to libkoda-process.so (built in app/src/main/cpp/).
 *
 * Two modes:
 * - PTY: for interactive sessions (OpenClaude)
 * - Pipe: for script execution (install, commands)
 */
public class KodaProcess {

    static {
        System.loadLibrary("koda-process");
    }

    /**
     * Create a subprocess with a PTY (pseudo-terminal).
     * @param cmd     Command to execute (e.g., "/data/data/com.termux/files/usr/bin/bash")
     * @param args    Arguments array (including argv[0])
     * @param envVars Environment variables as "KEY=VALUE" strings
     * @param pidOut  int[1] array — pid is stored here
     * @return master fd of the PTY, or -1 on error
     */
    public static native int createSubprocess(String cmd, String[] args, String[] envVars, int[] pidOut);

    /**
     * Create a subprocess with stdout piped (no PTY).
     * @param cmd     Command to execute
     * @param args    Arguments array
     * @param envVars Environment variables as "KEY=VALUE" strings
     * @param pidOut  int[1] array — pid is stored here
     * @return read-end fd of the stdout pipe, or -1 on error
     */
    public static native int createSubprocessPipe(String cmd, String[] args, String[] envVars, int[] pidOut);

    /**
     * Wait for a process to exit.
     * @return exit code, or -1 if killed by signal
     */
    public static native int waitFor(int pid);

    /**
     * Close a file descriptor.
     */
    public static native void close(int fd);
}
