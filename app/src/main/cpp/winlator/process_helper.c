#define _GNU_SOURCE  
#include <jni.h>
#include <sched.h>
#include <errno.h>
#include <string.h>
#include <sys/types.h>
#include <unistd.h>

JNIEXPORT jint JNICALL
Java_com_winlator_cmod_core_ProcessHelper_nativeSetProcessAffinity(
        JNIEnv *env, jclass clazz, jint pid, jint mask) {
    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);

    for (int i = 0; i < 32; i++) {
        if (mask & (1 << i))
            CPU_SET(i, &cpuset);
    }

    int result = sched_setaffinity((pid_t) pid, sizeof(cpuset), &cpuset);

    return result == 0 ? 0 : errno;
}

JNIEXPORT jint JNICALL
Java_com_winlator_cmod_core_ProcessHelper_nativeGetProcessAffinity(
        JNIEnv *env, jclass clazz, jint pid) {
    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);

    if (sched_getaffinity((pid_t) pid, sizeof(cpuset), &cpuset) != 0)
        return -1; 

    int mask = 0;
    for (int i = 0; i < 32; i++) {
        if (CPU_ISSET(i, &cpuset))
            mask |= (1 << i);
    }
    return mask;
}
