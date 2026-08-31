#include <jni.h>
#include <stdlib.h>
#include <string.h>

extern char *GoTsnetStart(char *stateDir, char *authKey, char *hostname, long long advertisePort, long long localPort);
extern void GoTsnetStop(void);
extern int GoTsnetRunning(void);
extern char *GoTsnetSelfIP(void);
extern long long GoTsnetSocksPort(void);
extern char *GoTsnetLastError(void);

static char *dup_jstr(JNIEnv *env, jstring s) {
    if (!s) {
        char *empty = (char *)malloc(1);
        if (empty) empty[0] = 0;
        return empty;
    }
    const char *utf = (*env)->GetStringUTFChars(env, s, NULL);
    if (!utf) {
        char *empty = (char *)malloc(1);
        if (empty) empty[0] = 0;
        return empty;
    }
    char *copy = strdup(utf);
    (*env)->ReleaseStringUTFChars(env, s, utf);
    return copy;
}

static jstring jstr_from_c(JNIEnv *env, char *s) {
    if (!s) {
        return (*env)->NewStringUTF(env, "");
    }
    jstring js = (*env)->NewStringUTF(env, s);
    free(s);
    return js;
}

JNIEXPORT jstring JNICALL
Java_cn_kosync_tsnet_Tsnetbind_start(JNIEnv *env, jclass clazz,
                                     jstring jStateDir, jstring jAuthKey, jstring jHostname,
                                     jlong advertisePort, jlong localPort) {
    char *stateDir = dup_jstr(env, jStateDir);
    char *authKey = dup_jstr(env, jAuthKey);
    char *hostname = dup_jstr(env, jHostname);
    char *err = GoTsnetStart(stateDir, authKey, hostname, advertisePort, localPort);
    free(stateDir);
    free(authKey);
    free(hostname);
    if (!err) {
        return NULL;
    }
    jstring js = (*env)->NewStringUTF(env, err);
    free(err);
    return js;
}

JNIEXPORT void JNICALL
Java_cn_kosync_tsnet_Tsnetbind_stop(JNIEnv *env, jclass clazz) {
    GoTsnetStop();
}

JNIEXPORT jboolean JNICALL
Java_cn_kosync_tsnet_Tsnetbind_running(JNIEnv *env, jclass clazz) {
    return GoTsnetRunning() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_cn_kosync_tsnet_Tsnetbind_selfIP(JNIEnv *env, jclass clazz) {
    return jstr_from_c(env, GoTsnetSelfIP());
}

JNIEXPORT jlong JNICALL
Java_cn_kosync_tsnet_Tsnetbind_socksPort(JNIEnv *env, jclass clazz) {
    return (jlong)GoTsnetSocksPort();
}

JNIEXPORT jstring JNICALL
Java_cn_kosync_tsnet_Tsnetbind_lastError(JNIEnv *env, jclass clazz) {
    return jstr_from_c(env, GoTsnetLastError());
}
