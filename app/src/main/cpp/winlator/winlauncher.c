#ifndef _WIN32_WINNT
#define _WIN32_WINNT 0x0600
#endif

#include <windows.h>
#include <tlhelp32.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>

static DWORD WINAPI affinityWatcherThread(LPVOID param) {
    DWORD_PTR mask  = (DWORD_PTR)(ULONG_PTR)param;
    DWORD     myPid = GetCurrentProcessId();
    for (;;) {
        HANDLE hSnap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
        if (hSnap != INVALID_HANDLE_VALUE) {
            PROCESSENTRY32 pe;
            pe.dwSize = sizeof(pe);
            if (Process32First(hSnap, &pe)) {
                do {
                    DWORD pid = pe.th32ProcessID;
                    if (pid > myPid) {
                        HANDLE hProc = OpenProcess(PROCESS_SET_INFORMATION, FALSE, pid);
                        if (hProc) {
                            SetProcessAffinityMask(hProc, mask);
                            CloseHandle(hProc);
                        }
                    }
                } while (Process32Next(hSnap, &pe));
            }
            CloseHandle(hSnap);
        }
        Sleep(1000);
    }
    return 0;
}

int WINAPI WinMain(HINSTANCE hInstance, HINSTANCE hPrevInstance,
                   LPSTR lpCmdLine, int nCmdShow) {

    int    argc      = __argc;
    char **argv      = __argv;
    
    if (argc == 4 && strcmp(argv[1], "/setaffinity") == 0) {
        const char *name = argv[2];
        DWORD_PTR   mask = (DWORD_PTR)strtoul(argv[3], NULL, 16);
        HANDLE hSnap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
        if (hSnap != INVALID_HANDLE_VALUE) {
            PROCESSENTRY32 pe;
            pe.dwSize = sizeof(pe);
            if (Process32First(hSnap, &pe)) {
                do {
                    if (_stricmp(pe.szExeFile, name) == 0) {
                        HANDLE hProc = OpenProcess(PROCESS_SET_INFORMATION,
                                                   FALSE, pe.th32ProcessID);
                        if (hProc) {
                            SetProcessAffinityMask(hProc, mask);
                            CloseHandle(hProc);
                        }
                    }
                } while (Process32Next(hSnap, &pe));
            }
            CloseHandle(hSnap);
        }
        return 0;
    }
    
    if (argc >= 3 && strcmp(argv[1], "/exec") == 0) {
        const char *program = argv[2];
        char params[2048]   = {0};
        for (int i = 3; i < argc; i++) {
            if (i > 3) strncat(params, " ",      sizeof(params) - strlen(params) - 1);
            strncat(params, argv[i], sizeof(params) - strlen(params) - 1);
        }
        ShellExecuteA(NULL, "open", program,
                      params[0] ? params : NULL, NULL, SW_SHOW);
        return 0;
    }

    char  *directory = NULL;
    char  *executable = NULL;
    int    affinity  = 0;

    int argIdx = 1;
    while (argIdx < argc) {
        if (strcmp(argv[argIdx], "/affinity") == 0) {
            if (argIdx + 1 < argc) {
                affinity = (int)strtol(argv[argIdx + 1], NULL, 16);
                argIdx += 2;
            } else {
                argIdx++;
            }
        } else if (strcmp(argv[argIdx], "/dir") == 0) {
            if (argIdx + 1 < argc) {
                directory = argv[argIdx + 1];
                argIdx += 2;
            } else {
                argIdx++;
            }
        } else {
            executable = argv[argIdx];
            argIdx++;
            break;
        }
    }

    if (executable == NULL)
        return 1;

    char execArgs[2048] = {0};
    for (int i = argIdx; i < argc; i++) {
        strcat(execArgs, "\"");
        strcat(execArgs, argv[i]);
        strcat(execArgs, "\" ");
    }

    HANDLE hProcess = NULL;

    BOOL hasBrackets = directory &&
                       (strchr(directory, '[') || strchr(directory, ']'));

    if (hasBrackets) {
        char fullPath[MAX_PATH];
        snprintf(fullPath, sizeof(fullPath), "%s\\%s", directory, executable);

        char cmdLine[4096];
        snprintf(cmdLine, sizeof(cmdLine), "\"%s\"", executable);
        if (execArgs[0]) {
            strncat(cmdLine, " ",       sizeof(cmdLine) - strlen(cmdLine) - 1);
            strncat(cmdLine, execArgs,  sizeof(cmdLine) - strlen(cmdLine) - 1);
        }

        STARTUPINFOA si = {0};
        si.cb          = sizeof(si);
        si.dwFlags     = STARTF_USESHOWWINDOW;
        si.wShowWindow = SW_SHOW;

        PROCESS_INFORMATION pi = {0};
        if (CreateProcessA(fullPath, cmdLine, NULL, NULL,
                           FALSE, 0, NULL, directory, &si, &pi)) {
            CloseHandle(pi.hThread);
            hProcess = pi.hProcess;
        }
        
    }

    if (hProcess == NULL) {
        SHELLEXECUTEINFOA sei = {0};
        sei.cbSize       = sizeof(SHELLEXECUTEINFOA);
        sei.fMask        = SEE_MASK_NOCLOSEPROCESS;
        sei.lpVerb       = "open";
        sei.lpFile       = executable;
        sei.lpParameters = execArgs[0] ? execArgs : NULL;
        sei.lpDirectory  = directory;
        sei.nShow        = SW_SHOW;

        ShellExecuteExA(&sei);
        hProcess = sei.hProcess;
    }

    if (affinity > 0) {
        HANDLE hThread = CreateThread(NULL, 0, affinityWatcherThread,
                                      (LPVOID)(ULONG_PTR)affinity, 0, NULL);
        if (hThread) CloseHandle(hThread);
    }

    if (hProcess) {
        WaitForSingleObject(hProcess, INFINITE);
        CloseHandle(hProcess);
    }

    return 0;
}
