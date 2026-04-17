/**
 * koda-process.c — JNI native subprocess management for Koda.
 *
 * Provides two modes:
 *   1. PTY mode (createSubprocess): allocates a pseudo-terminal,
 *      returns the master fd. For interactive sessions.
 *   2. Pipe mode (createSubprocessPipe): uses stdout pipe,
 *      returns the read-end fd. For script execution with clean output.
 *
 * Uses fork/execvp at C level so LD_LIBRARY_PATH and LD_PRELOAD
 * are set BEFORE the dynamic linker runs — essential on Android
 * where linker namespaces ignore env vars set via ProcessBuilder.
 */

#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <sys/wait.h>
#include <sys/ioctl.h>
#include <termios.h>
#include <android/log.h>

#define LOG_TAG "KodaProcess"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static char** jstring_array_to_cstrings(JNIEnv *env, jobjectArray arr) {
    if (!arr) return NULL;
    int len = (*env)->GetArrayLength(env, arr);
    char **result = (char**)malloc((len + 1) * sizeof(char*));
    for (int i = 0; i < len; i++) {
        jstring str = (jstring)(*env)->GetObjectArrayElement(env, arr, i);
        const char *utf = (*env)->GetStringUTFChars(env, str, NULL);
        result[i] = strdup(utf);
        (*env)->ReleaseStringUTFChars(env, str, utf);
        (*env)->DeleteLocalRef(env, str);
    }
    result[len] = NULL;
    return result;
}

static void free_cstrings(char **arr) {
    if (!arr) return;
    for (int i = 0; arr[i]; i++) free(arr[i]);
    free(arr);
}

/**
 * PTY mode: creates a subprocess with a pseudo-terminal.
 * Returns the master fd. Stores pid in pidOut[0].
 *
 * Class: dev.koda.KodaProcess
 * Method: createSubprocess
 * Signature: (Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;[I)I
 */
JNIEXPORT jint JNICALL
Java_dev_koda_KodaProcess_createSubprocess(
    JNIEnv *env, jclass clazz,
    jstring cmd, jobjectArray args, jobjectArray envVars, jintArray pidOut)
{
    (void)clazz;

    int ptm = open("/dev/ptmx", O_RDWR | O_CLOEXEC);
    if (ptm < 0) {
        LOGE("open /dev/ptmx failed: %s", strerror(errno));
        return -1;
    }

    if (grantpt(ptm) || unlockpt(ptm)) {
        LOGE("grantpt/unlockpt failed: %s", strerror(errno));
        close(ptm);
        return -1;
    }

    char devname[64];
    if (ptsname_r(ptm, devname, sizeof(devname))) {
        LOGE("ptsname_r failed: %s", strerror(errno));
        close(ptm);
        return -1;
    }

    // Set terminal size
    struct winsize ws = { .ws_row = 40, .ws_col = 120 };
    ioctl(ptm, TIOCSWINSZ, &ws);

    const char *cmd_str = (*env)->GetStringUTFChars(env, cmd, NULL);
    char **argv = jstring_array_to_cstrings(env, args);
    char **envp = jstring_array_to_cstrings(env, envVars);

    pid_t pid = fork();
    if (pid < 0) {
        LOGE("fork failed: %s", strerror(errno));
        (*env)->ReleaseStringUTFChars(env, cmd, cmd_str);
        free_cstrings(argv);
        free_cstrings(envp);
        close(ptm);
        return -1;
    }

    if (pid == 0) {
        // Child
        close(ptm);
        setsid();

        int pts = open(devname, O_RDWR);
        if (pts < 0) _exit(127);

        dup2(pts, 0);
        dup2(pts, 1);
        dup2(pts, 2);
        if (pts > 2) close(pts);

        // Set environment
        if (envp) {
            for (int i = 0; envp[i]; i++) {
                putenv(envp[i]);
            }
        }

        if (argv) {
            execvp(cmd_str, argv);
        } else {
            execlp(cmd_str, cmd_str, NULL);
        }
        _exit(127);
    }

    // Parent
    (*env)->ReleaseStringUTFChars(env, cmd, cmd_str);
    free_cstrings(argv);
    // Don't free envp strings — child's putenv uses them

    if (pidOut) {
        jint pidVal = pid;
        (*env)->SetIntArrayRegion(env, pidOut, 0, 1, &pidVal);
    }

    LOGI("PTY subprocess started: pid=%d fd=%d", pid, ptm);
    return ptm;
}

/**
 * Pipe mode: creates a subprocess with stdout piped.
 * Returns the read-end fd. Stores pid in pidOut[0].
 *
 * Class: dev.koda.KodaProcess
 * Method: createSubprocessPipe
 * Signature: (Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;[I)I
 */
JNIEXPORT jint JNICALL
Java_dev_koda_KodaProcess_createSubprocessPipe(
    JNIEnv *env, jclass clazz,
    jstring cmd, jobjectArray args, jobjectArray envVars, jintArray pidOut)
{
    (void)clazz;

    int pipefd[2];
    if (pipe(pipefd) < 0) {
        LOGE("pipe failed: %s", strerror(errno));
        return -1;
    }

    const char *cmd_str = (*env)->GetStringUTFChars(env, cmd, NULL);
    char **argv = jstring_array_to_cstrings(env, args);
    char **envp = jstring_array_to_cstrings(env, envVars);

    pid_t pid = fork();
    if (pid < 0) {
        LOGE("fork failed: %s", strerror(errno));
        (*env)->ReleaseStringUTFChars(env, cmd, cmd_str);
        free_cstrings(argv);
        free_cstrings(envp);
        close(pipefd[0]);
        close(pipefd[1]);
        return -1;
    }

    if (pid == 0) {
        // Child
        close(pipefd[0]);
        dup2(pipefd[1], 1);
        dup2(pipefd[1], 2);
        close(pipefd[1]);

        // Redirect stdin from /dev/null
        int devnull = open("/dev/null", O_RDONLY);
        if (devnull >= 0) {
            dup2(devnull, 0);
            close(devnull);
        }

        if (envp) {
            for (int i = 0; envp[i]; i++) {
                putenv(envp[i]);
            }
        }

        if (argv) {
            execvp(cmd_str, argv);
        } else {
            execlp(cmd_str, cmd_str, NULL);
        }
        _exit(127);
    }

    // Parent
    close(pipefd[1]);
    (*env)->ReleaseStringUTFChars(env, cmd, cmd_str);
    free_cstrings(argv);

    if (pidOut) {
        jint pidVal = pid;
        (*env)->SetIntArrayRegion(env, pidOut, 0, 1, &pidVal);
    }

    LOGI("Pipe subprocess started: pid=%d fd=%d", pid, pipefd[0]);
    return pipefd[0];
}

/**
 * Wait for a process to finish.
 * Returns the exit status.
 */
JNIEXPORT jint JNICALL
Java_dev_koda_KodaProcess_waitFor(JNIEnv *env, jclass clazz, jint pid) {
    (void)env; (void)clazz;
    int status;
    waitpid(pid, &status, 0);
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    return -1;
}

/**
 * Close a file descriptor.
 */
JNIEXPORT void JNICALL
Java_dev_koda_KodaProcess_close(JNIEnv *env, jclass clazz, jint fd) {
    (void)env; (void)clazz;
    close(fd);
}
