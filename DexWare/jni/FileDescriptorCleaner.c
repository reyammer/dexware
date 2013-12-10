#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <dirent.h>
#include <string.h>
#include <jni.h>
#include "FileDescriptorCleaner.h"

JNIEXPORT void JNICALL Java_FileDescriptorCleaner_clean(JNIEnv *env, jobject thisObj) {

    printf("Starting to clean up the fd left by DexClassLoader..\n");

    char buf[1024];

    char fdFp[20];
    char fdDir[20];
    sprintf(fdDir, "/proc/%d/fd", getpid());

    // printf("fdDir: %s", fdDir);

    DIR *d;
    struct dirent *dir;
    d = opendir(fdDir);
    if (d) {
        while ((dir = readdir(d)) != NULL) {
            sprintf(fdFp, "%s/%s", fdDir, dir->d_name);
            ssize_t len = readlink(fdFp, buf, sizeof(buf)-1);
            buf[len] = '\0';
            // printf("FILE: %s ~> %s\n", fdFp, buf);

            if (strstr(buf, "(deleted)") != NULL) {
                int fd = atoi(dir->d_name);
                // printf("closing %d\n", fd);
                close(fd);
            }
        }
        closedir(d);
    }
    return;
}
