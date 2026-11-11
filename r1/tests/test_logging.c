/* R1 logging regression tests. Copyright (c) 2026 XHYN_PH. MIT. */
#include "../native-logging/logcontrol.c"
#include <assert.h>
#include <sys/stat.h>
#include <sys/wait.h>
static int checks;
#define CHECK(condition) do { ++checks; if (!(condition)) { fprintf(stderr, "FAIL line %d: %s\n", __LINE__, #condition); abort(); } } while (0)
static int module0_calls, module1_calls;
static FILE *chain0(const char *path, const char *mode) { ++module0_calls; return fopen(path, mode); }
static FILE *chain1(const char *path, const char *mode) { ++module1_calls; return fopen(path, mode); }
static int original_open2(const char *path, int flags) { return open(path, flags); }
static void bind_originals(int module) {
    modules[module].original[SLOT_FOPEN] = (uintptr_t)(module ? chain1 : chain0);
    modules[module].original[SLOT_FOPEN64] = (uintptr_t)fopen;
    modules[module].original[SLOT_FREOPEN] = (uintptr_t)freopen;
    modules[module].original[SLOT_OPEN] = (uintptr_t)open;
    modules[module].original[SLOT_OPEN2] = (uintptr_t)original_open2;
    modules[module].original[SLOT_OPENAT] = (uintptr_t)openat;
}
static bool exists(const char *path) { struct stat s; return stat(path, &s) == 0; }
static void write_file(FILE *file) { CHECK(file != NULL); CHECK(fputs("sentinel", file) >= 0); CHECK(fclose(file) == 0); }
static void write_fd(int fd) { CHECK(fd >= 0); CHECK(write(fd, "sentinel", 8) == 8); CHECK(close(fd) == 0); }
static void test_file_output(void) {
    bind_originals(0); bind_originals(1);
    atomic_store(&enabled, false);
    write_file(quiet_fopen_0("naruto.log", "w")); CHECK(!exists("naruto.log"));
    write_file(quiet_fopen64_0("xenia.log", "a")); CHECK(!exists("xenia.log"));
    write_fd(quiet_open_0("open.log", O_WRONLY | O_CREAT | O_EXCL, 0600)); CHECK(!exists("open.log"));
    write_fd(quiet_openat_0(AT_FDCWD, "openat.log", O_WRONLY | O_CREAT, 0600)); CHECK(!exists("openat.log"));
    FILE *stream = tmpfile(); CHECK(stream != NULL);
    write_file(quiet_freopen_0("plume_stderr.log", "w", stream)); CHECK(!exists("plume_stderr.log"));
    write_file(quiet_fopen_0("gpu_caps.txt", "w")); CHECK(exists("gpu_caps.txt"));
    write_file(quiet_fopen_0("save.dat", "w")); CHECK(exists("save.dat"));
    write_file(quiet_fopen_1("driver-metadata.txt", "w")); CHECK(exists("driver-metadata.txt"));
    CHECK(module0_calls > 0 && module1_calls == 1);
    atomic_store(&enabled, true);
    write_file(quiet_fopen_0("naruto.log", "w")); CHECK(exists("naruto.log"));
    write_file(quiet_fopen_1("xenia.log", "w")); CHECK(exists("xenia.log"));
    CHECK(module1_calls == 2);
    atomic_store(&enabled, false);
    write_fd(quiet_open2_0("naruto.log", O_WRONLY | O_TRUNC));
    FILE *readback = quiet_fopen_0("naruto.log", "r"); CHECK(readback != NULL);
    char text[16] = {0}; CHECK(fread(text, 1, 8, readback) == 8); CHECK(!strcmp(text, "sentinel")); fclose(readback);
    atomic_store(&enabled, true);
    write_fd(quiet_open_0("new.log", O_WRONLY | O_CREAT, 0600)); CHECK(exists("new.log"));
    CHECK(!is_log_path(NULL)); CHECK(!is_log_path("/logs/status.txt")); CHECK(is_log_path("/logs/test.log"));
}
static void test_streams(void) {
    int output[2]; CHECK(pipe(output) == 0); fflush(NULL);
    pid_t child = fork(); CHECK(child >= 0);
    if (child == 0) {
        close(output[0]); assert(dup2(output[1], STDOUT_FILENO) >= 0); assert(dup2(output[1], STDERR_FILENO) >= 0); close(output[1]);
        real_open = open;
        assert(redirect_streams(false));
        fputs("HIDDEN_OUT", stdout); fputs("HIDDEN_ERR", stderr); fflush(NULL);
        assert(redirect_streams(true));
        fputs("RESTORED_OUT", stdout); fputs("RESTORED_ERR", stderr); fflush(NULL); _exit(0);
    }
    close(output[1]); char buffer[256] = {0}; ssize_t n, used = 0;
    while ((n = read(output[0], buffer + used, sizeof(buffer)-1-(size_t)used)) > 0) used += n;
    close(output[0]); int status; CHECK(waitpid(child, &status, 0) == child); CHECK(WIFEXITED(status) && WEXITSTATUS(status) == 0);
    CHECK(!strstr(buffer, "HIDDEN_")); CHECK(strstr(buffer, "RESTORED_OUT") && strstr(buffer, "RESTORED_ERR"));
}
static void test_relro(void) {
    size_t size = (size_t)sysconf(_SC_PAGESIZE);
    uintptr_t *page = mmap(NULL, size, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0); CHECK(page != MAP_FAILED);
    page[0] = 123;
    ElfW(Phdr) headers[2] = {{0}};
    headers[0].p_type = PT_LOAD; headers[0].p_flags = PF_R | PF_W; headers[0].p_memsz = size;
    headers[1].p_type = PT_GNU_RELRO; headers[1].p_memsz = size;
    struct dl_phdr_info info = {0}; info.dlpi_addr = (uintptr_t)page; info.dlpi_phdr = headers; info.dlpi_phnum = 2;
    CHECK(mprotect(page, size, PROT_READ) == 0);
    struct install_state state = {0}; uintptr_t original = 0;
    patch_slot(&info, page, 456, &original, &state);
    CHECK(page[0] == 456 && original == 123 && state.hooks == 1 && state.failures == 0);
    patch_slot(&info, page, 456, &original, &state);
    CHECK(original == 123 && state.hooks == 2 && state.failures == 0);
    FILE *maps = fopen("/proc/self/maps", "r"); CHECK(maps != NULL); char line[512]; bool read_only = false;
    while (fgets(line, sizeof(line), maps)) {
        unsigned long start, end; char permissions[5];
        if (sscanf(line, "%lx-%lx %4s", &start, &end, permissions) == 3 && (uintptr_t)page >= start && (uintptr_t)page < end)
            read_only = permissions[0] == 'r' && permissions[1] == '-';
    }
    fclose(maps); CHECK(read_only); munmap(page, size);
}
static void test_early_level(void) {
    CHECK(unsetenv("XNR_LOG_LEVEL") == 0);
    CHECK(configure_early_logging(false)); CHECK(!strcmp(getenv("XNR_LOG_LEVEL"), "5"));
    CHECK(Java_com_naruto_port_LoggingControl_nativeEarlyCutoff(NULL, NULL) == JNI_TRUE);
    CHECK(configure_early_logging(false)); CHECK(!strcmp(getenv("XNR_LOG_LEVEL"), "5"));
    CHECK(configure_early_logging(true)); CHECK(getenv("XNR_LOG_LEVEL") == NULL);
    CHECK(Java_com_naruto_port_LoggingControl_nativeEarlyCutoff(NULL, NULL) == JNI_FALSE);
    level_saved = false;
    CHECK(setenv("XNR_LOG_LEVEL", "3", 1) == 0);
    CHECK(configure_early_logging(false)); CHECK(!strcmp(getenv("XNR_LOG_LEVEL"), "5"));
    CHECK(configure_early_logging(true)); CHECK(!strcmp(getenv("XNR_LOG_LEVEL"), "3"));
    free(original_level); original_level = NULL; level_saved = false;
    CHECK(unsetenv("XNR_LOG_LEVEL") == 0);
}
int main(void) {
    char directory[] = "/tmp/storm-logging-test-XXXXXX"; CHECK(mkdtemp(directory) != NULL); CHECK(chdir(directory) == 0);
    test_file_output(); test_streams(); test_relro(); test_early_level();
    printf("PASS: %d logging regression assertions (file suppression, status/save preservation, per-library hook chains, stdout/stderr restoration, RELRO and idempotency).\n", checks);
    return 0;
}
