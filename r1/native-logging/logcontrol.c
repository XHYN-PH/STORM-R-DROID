#define _GNU_SOURCE
#include <dlfcn.h>
#include <elf.h>
#include <errno.h>
#include <fcntl.h>
#include <link.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdatomic.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <unistd.h>
#ifdef __ANDROID__
#include <jni.h>
#else
/* Host syntax checking only; no host or Android binary is produced by that check. */
#define JNIEXPORT __attribute__((visibility("default")))
#define JNICALL
typedef void JNIEnv;
typedef void *jclass;
typedef unsigned char jboolean;
#define JNI_TRUE 1
#define JNI_FALSE 0
#endif

/* All policy is per process. Both launcher and :game initialize this helper. */
struct __android_log_message;
typedef void (*logger_fn)(const struct __android_log_message *);
static void (*set_logger)(logger_fn);
static logger_fn logd_logger;
static int (*set_priority)(int);
static FILE *(*real_fopen)(const char *, const char *);
static FILE *(*real_fopen64)(const char *, const char *);
static FILE *(*real_freopen)(const char *, const char *, FILE *);
static int (*real_open)(const char *, int, ...);
static int (*real_open2)(const char *, int);
static int (*real_openat)(int, const char *, int, ...);
static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;
static atomic_bool enabled = true;
static bool symbols_ready, priority_saved;
static int previous_priority, saved_out = -1, saved_err = -1;
static bool level_saved;
static char *original_level;

/* The pinned engine reads this before SDL_main rendering initialization. Its
 * predicate accepts a message when configured_level <= message_level (0..4).
 * Level 5 avoids the guarded message construction, without patching instructions.
 * Keep any pre-existing environment override when capture is restored. */
static bool configure_early_logging(bool capture) {
    if (!level_saved) {
        const char *value = getenv("XNR_LOG_LEVEL");
        if (value) { original_level = strdup(value); if (!original_level) return false; }
        level_saved = true;
    }
    if (!capture) return setenv("XNR_LOG_LEVEL", "5", 1) == 0;
    return original_level ? setenv("XNR_LOG_LEVEL", original_level, 1) == 0 : unsetenv("XNR_LOG_LEVEL") == 0;
}

static void discard_android_log(const struct __android_log_message *message) {
    (void)message;
}
static bool is_log_path(const char *path) {
    if (!path) return false;
    const char *name = strrchr(path, '/');
    name = name ? name + 1 : path;
    size_t length = strlen(name);
    return length >= 4 && strcmp(name + length - 4, ".log") == 0;
}
static bool suppress_path(const char *path) {
    return !atomic_load_explicit(&enabled, memory_order_acquire) && is_log_path(path);
}
static bool write_mode(const char *mode) {
    return mode && (strchr(mode, 'w') || strchr(mode, 'a') || strchr(mode, '+'));
}
static int null_flags(int flags) {
    return flags & ~(O_CREAT | O_EXCL | O_TRUNC | O_APPEND);
}
static mode_t open_mode(int flags, va_list args) {
    if ((flags & O_CREAT) || ((flags & O_TMPFILE) == O_TMPFILE))
        return (mode_t)va_arg(args, int);
    return 0;
}
enum { SLOT_FOPEN, SLOT_FOPEN64, SLOT_FREOPEN, SLOT_OPEN, SLOT_OPEN2, SLOT_OPENAT, SLOT_COUNT };
struct module_hooks { const char *name; uintptr_t original[SLOT_COUNT]; };
static struct module_hooks modules[] = {
    {"librevolution.so", {0}}, {"libffmpeg.so", {0}}, {"libhook_impl.so", {0}},
    {"libfile_redirect_hook.so", {0}}, {"libgsl_alloc_hook.so", {0}}, {"libmain_hook.so", {0}}
};
/* Each library retains its own original import targets. In particular, do not
 * bypass AdrenoTools' existing hook_fopen/file-redirection chain. */
static bool resolve_symbols(void) {
    if (symbols_ready) return true;
    void *log = dlopen("liblog.so", RTLD_NOW | RTLD_LOCAL);
    if (!log) return false;
    set_logger = (void (*)(logger_fn))dlsym(log, "__android_log_set_logger");
    logd_logger = (logger_fn)dlsym(log, "__android_log_logd_logger");
    set_priority = (int (*)(int))dlsym(log, "__android_log_set_minimum_priority");
    real_fopen = (FILE *(*)(const char *, const char *))dlsym(RTLD_DEFAULT, "fopen");
    real_fopen64 = (FILE *(*)(const char *, const char *))dlsym(RTLD_DEFAULT, "fopen64");
    real_freopen = (FILE *(*)(const char *, const char *, FILE *))dlsym(RTLD_DEFAULT, "freopen");
    real_open = (int (*)(const char *, int, ...))dlsym(RTLD_DEFAULT, "open");
    real_open2 = (int (*)(const char *, int))dlsym(RTLD_DEFAULT, "__open_2");
    real_openat = (int (*)(int, const char *, int, ...))dlsym(RTLD_DEFAULT, "openat");
    if (!real_fopen64) real_fopen64 = real_fopen;
    symbols_ready = set_logger && logd_logger && set_priority && real_fopen && real_freopen && real_open && real_openat;
    return symbols_ready;
}
static bool redirect_streams(bool capture) {
    fflush(stdout); fflush(stderr);
    if (capture) {
        bool ok = true;
        if (saved_out >= 0) { if (dup2(saved_out, STDOUT_FILENO) < 0) ok = false; }
        if (saved_err >= 0) { if (dup2(saved_err, STDERR_FILENO) < 0) ok = false; }
        return ok;
    }
    if (saved_out < 0) saved_out = dup(STDOUT_FILENO);
    if (saved_err < 0) saved_err = dup(STDERR_FILENO);
    if (saved_out < 0 || saved_err < 0) return false;
    int sink = real_open("/dev/null", O_WRONLY | O_CLOEXEC);
    if (sink < 0) return false;
    bool ok = dup2(sink, STDOUT_FILENO) >= 0;
    if (dup2(sink, STDERR_FILENO) < 0) ok = false;
    close(sink);
    return ok;
}

/* Hook only imports in this APK's libraries, before the SDL game thread starts.
 * No instruction patches or changes to the original ELF files are made.
 * Status/config/cache files (including GPU/driver recovery metadata) are untouched.
 */
static bool target_library(const char *path) {
    const char *name = strrchr(path, '/'); name = name ? name + 1 : path;
    const char *targets[] = {"librevolution.so", "libffmpeg.so", "libhook_impl.so", "libfile_redirect_hook.so", "libgsl_alloc_hook.so", "libmain_hook.so"};
    for (size_t i = 0; i < sizeof(targets)/sizeof(targets[0]); ++i)
        if (strcmp(name, targets[i]) == 0) return true;
    return false;
}
static uintptr_t runtime_pointer(const struct dl_phdr_info *info, uintptr_t value) {
    for (ElfW(Half) i = 0; i < info->dlpi_phnum; ++i) {
        const ElfW(Phdr) *p = &info->dlpi_phdr[i];
        if (p->p_type != PT_LOAD) continue;
        uintptr_t start = info->dlpi_addr + p->p_vaddr;
        if (value >= start && value < start + p->p_memsz) return value;
    }
    return info->dlpi_addr + value;
}
static FILE *quiet_fopen_0(const char *path, const char *mode) {
    FILE *(*original)(const char *, const char *) = (void *)modules[0].original[SLOT_FOPEN];
    return original(suppress_path(path) && write_mode(mode) ? "/dev/null" : path, mode);
}
static FILE *quiet_fopen64_0(const char *path, const char *mode) {
    FILE *(*original)(const char *, const char *) = (void *)modules[0].original[SLOT_FOPEN64];
    return original(suppress_path(path) && write_mode(mode) ? "/dev/null" : path, mode);
}
static FILE *quiet_freopen_0(const char *path, const char *mode, FILE *stream) {
    FILE *(*original)(const char *, const char *, FILE *) = (void *)modules[0].original[SLOT_FREOPEN];
    return original(suppress_path(path) && write_mode(mode) ? "/dev/null" : path, mode, stream);
}
static int quiet_open_0(const char *path, int flags, ...) {
    int (*original)(const char *, int, ...) = (void *)modules[0].original[SLOT_OPEN];
    va_list args; va_start(args, flags); mode_t mode = open_mode(flags, args); va_end(args);
    if ((flags & O_ACCMODE) != O_RDONLY && suppress_path(path)) { path = "/dev/null"; flags = null_flags(flags); }
    return original(path, flags, mode);
}
static int quiet_open2_0(const char *path, int flags) {
    int (*original)(const char *, int) = (void *)modules[0].original[SLOT_OPEN2];
    if ((flags & O_ACCMODE) != O_RDONLY && suppress_path(path)) { path = "/dev/null"; flags = null_flags(flags); }
    return original(path, flags);
}
static int quiet_openat_0(int directory, const char *path, int flags, ...) {
    int (*original)(int, const char *, int, ...) = (void *)modules[0].original[SLOT_OPENAT];
    va_list args; va_start(args, flags); mode_t mode = open_mode(flags, args); va_end(args);
    if ((flags & O_ACCMODE) != O_RDONLY && suppress_path(path)) { directory = AT_FDCWD; path = "/dev/null"; flags = null_flags(flags); }
    return original(directory, path, flags, mode);
}
static FILE *quiet_fopen_1(const char *path, const char *mode) {
    FILE *(*original)(const char *, const char *) = (void *)modules[1].original[SLOT_FOPEN];
    return original(suppress_path(path) && write_mode(mode) ? "/dev/null" : path, mode);
}
static FILE *quiet_fopen64_1(const char *path, const char *mode) {
    FILE *(*original)(const char *, const char *) = (void *)modules[1].original[SLOT_FOPEN64];
    return original(suppress_path(path) && write_mode(mode) ? "/dev/null" : path, mode);
}
static FILE *quiet_freopen_1(const char *path, const char *mode, FILE *stream) {
    FILE *(*original)(const char *, const char *, FILE *) = (void *)modules[1].original[SLOT_FREOPEN];
    return original(suppress_path(path) && write_mode(mode) ? "/dev/null" : path, mode, stream);
}
static int quiet_open_1(const char *path, int flags, ...) {
    int (*original)(const char *, int, ...) = (void *)modules[1].original[SLOT_OPEN];
    va_list args; va_start(args, flags); mode_t mode = open_mode(flags, args); va_end(args);
    if ((flags & O_ACCMODE) != O_RDONLY && suppress_path(path)) { path = "/dev/null"; flags = null_flags(flags); }
    return original(path, flags, mode);
}
static int quiet_open2_1(const char *path, int flags) {
    int (*original)(const char *, int) = (void *)modules[1].original[SLOT_OPEN2];
    if ((flags & O_ACCMODE) != O_RDONLY && suppress_path(path)) { path = "/dev/null"; flags = null_flags(flags); }
    return original(path, flags);
}
static int quiet_openat_1(int directory, const char *path, int flags, ...) {
    int (*original)(int, const char *, int, ...) = (void *)modules[1].original[SLOT_OPENAT];
    va_list args; va_start(args, flags); mode_t mode = open_mode(flags, args); va_end(args);
    if ((flags & O_ACCMODE) != O_RDONLY && suppress_path(path)) { directory = AT_FDCWD; path = "/dev/null"; flags = null_flags(flags); }
    return original(directory, path, flags, mode);
}
static FILE *quiet_fopen_2(const char *path, const char *mode) {
    FILE *(*original)(const char *, const char *) = (void *)modules[2].original[SLOT_FOPEN];
    return original(suppress_path(path) && write_mode(mode) ? "/dev/null" : path, mode);
}
static FILE *quiet_fopen64_2(const char *path, const char *mode) {
    FILE *(*original)(const char *, const char *) = (void *)modules[2].original[SLOT_FOPEN64];
    return original(suppress_path(path) && write_mode(mode) ? "/dev/null" : path, mode);
}
static FILE *quiet_freopen_2(const char *path, const char *mode, FILE *stream) {
    FILE *(*original)(const char *, const char *, FILE *) = (void *)modules[2].original[SLOT_FREOPEN];
    return original(suppress_path(path) && write_mode(mode) ? "/dev/null" : path, mode, stream);
}
static int quiet_open_2(const char *path, int flags, ...) {
    int (*original)(const char *, int, ...) = (void *)modules[2].original[SLOT_OPEN];
    va_list args; va_start(args, flags); mode_t mode = open_mode(flags, args); va_end(args);
    if ((flags & O_ACCMODE) != O_RDONLY && suppress_path(path)) { path = "/dev/null"; flags = null_flags(flags); }
    return original(path, flags, mode);
}
static int quiet_open2_2(const char *path, int flags) {
    int (*original)(const char *, int) = (void *)modules[2].original[SLOT_OPEN2];
    if ((flags & O_ACCMODE) != O_RDONLY && suppress_path(path)) { path = "/dev/null"; flags = null_flags(flags); }
    return original(path, flags);
}
static int quiet_openat_2(int directory, const char *path, int flags, ...) {
    int (*original)(int, const char *, int, ...) = (void *)modules[2].original[SLOT_OPENAT];
    va_list args; va_start(args, flags); mode_t mode = open_mode(flags, args); va_end(args);
    if ((flags & O_ACCMODE) != O_RDONLY && suppress_path(path)) { directory = AT_FDCWD; path = "/dev/null"; flags = null_flags(flags); }
    return original(directory, path, flags, mode);
}
static FILE *quiet_fopen_3(const char *path, const char *mode) {
    FILE *(*original)(const char *, const char *) = (void *)modules[3].original[SLOT_FOPEN];
    return original(suppress_path(path) && write_mode(mode) ? "/dev/null" : path, mode);
}
static FILE *quiet_fopen64_3(const char *path, const char *mode) {
    FILE *(*original)(const char *, const char *) = (void *)modules[3].original[SLOT_FOPEN64];
    return original(suppress_path(path) && write_mode(mode) ? "/dev/null" : path, mode);
}
static FILE *quiet_freopen_3(const char *path, const char *mode, FILE *stream) {
    FILE *(*original)(const char *, const char *, FILE *) = (void *)modules[3].original[SLOT_FREOPEN];
    return original(suppress_path(path) && write_mode(mode) ? "/dev/null" : path, mode, stream);
}
static int quiet_open_3(const char *path, int flags, ...) {
    int (*original)(const char *, int, ...) = (void *)modules[3].original[SLOT_OPEN];
    va_list args; va_start(args, flags); mode_t mode = open_mode(flags, args); va_end(args);
    if ((flags & O_ACCMODE) != O_RDONLY && suppress_path(path)) { path = "/dev/null"; flags = null_flags(flags); }
    return original(path, flags, mode);
}
static int quiet_open2_3(const char *path, int flags) {
    int (*original)(const char *, int) = (void *)modules[3].original[SLOT_OPEN2];
    if ((flags & O_ACCMODE) != O_RDONLY && suppress_path(path)) { path = "/dev/null"; flags = null_flags(flags); }
    return original(path, flags);
}
static int quiet_openat_3(int directory, const char *path, int flags, ...) {
    int (*original)(int, const char *, int, ...) = (void *)modules[3].original[SLOT_OPENAT];
    va_list args; va_start(args, flags); mode_t mode = open_mode(flags, args); va_end(args);
    if ((flags & O_ACCMODE) != O_RDONLY && suppress_path(path)) { directory = AT_FDCWD; path = "/dev/null"; flags = null_flags(flags); }
    return original(directory, path, flags, mode);
}
static FILE *quiet_fopen_4(const char *path, const char *mode) {
    FILE *(*original)(const char *, const char *) = (void *)modules[4].original[SLOT_FOPEN];
    return original(suppress_path(path) && write_mode(mode) ? "/dev/null" : path, mode);
}
static FILE *quiet_fopen64_4(const char *path, const char *mode) {
    FILE *(*original)(const char *, const char *) = (void *)modules[4].original[SLOT_FOPEN64];
    return original(suppress_path(path) && write_mode(mode) ? "/dev/null" : path, mode);
}
static FILE *quiet_freopen_4(const char *path, const char *mode, FILE *stream) {
    FILE *(*original)(const char *, const char *, FILE *) = (void *)modules[4].original[SLOT_FREOPEN];
    return original(suppress_path(path) && write_mode(mode) ? "/dev/null" : path, mode, stream);
}
static int quiet_open_4(const char *path, int flags, ...) {
    int (*original)(const char *, int, ...) = (void *)modules[4].original[SLOT_OPEN];
    va_list args; va_start(args, flags); mode_t mode = open_mode(flags, args); va_end(args);
    if ((flags & O_ACCMODE) != O_RDONLY && suppress_path(path)) { path = "/dev/null"; flags = null_flags(flags); }
    return original(path, flags, mode);
}
static int quiet_open2_4(const char *path, int flags) {
    int (*original)(const char *, int) = (void *)modules[4].original[SLOT_OPEN2];
    if ((flags & O_ACCMODE) != O_RDONLY && suppress_path(path)) { path = "/dev/null"; flags = null_flags(flags); }
    return original(path, flags);
}
static int quiet_openat_4(int directory, const char *path, int flags, ...) {
    int (*original)(int, const char *, int, ...) = (void *)modules[4].original[SLOT_OPENAT];
    va_list args; va_start(args, flags); mode_t mode = open_mode(flags, args); va_end(args);
    if ((flags & O_ACCMODE) != O_RDONLY && suppress_path(path)) { directory = AT_FDCWD; path = "/dev/null"; flags = null_flags(flags); }
    return original(directory, path, flags, mode);
}
static FILE *quiet_fopen_5(const char *path, const char *mode) {
    FILE *(*original)(const char *, const char *) = (void *)modules[5].original[SLOT_FOPEN];
    return original(suppress_path(path) && write_mode(mode) ? "/dev/null" : path, mode);
}
static FILE *quiet_fopen64_5(const char *path, const char *mode) {
    FILE *(*original)(const char *, const char *) = (void *)modules[5].original[SLOT_FOPEN64];
    return original(suppress_path(path) && write_mode(mode) ? "/dev/null" : path, mode);
}
static FILE *quiet_freopen_5(const char *path, const char *mode, FILE *stream) {
    FILE *(*original)(const char *, const char *, FILE *) = (void *)modules[5].original[SLOT_FREOPEN];
    return original(suppress_path(path) && write_mode(mode) ? "/dev/null" : path, mode, stream);
}
static int quiet_open_5(const char *path, int flags, ...) {
    int (*original)(const char *, int, ...) = (void *)modules[5].original[SLOT_OPEN];
    va_list args; va_start(args, flags); mode_t mode = open_mode(flags, args); va_end(args);
    if ((flags & O_ACCMODE) != O_RDONLY && suppress_path(path)) { path = "/dev/null"; flags = null_flags(flags); }
    return original(path, flags, mode);
}
static int quiet_open2_5(const char *path, int flags) {
    int (*original)(const char *, int) = (void *)modules[5].original[SLOT_OPEN2];
    if ((flags & O_ACCMODE) != O_RDONLY && suppress_path(path)) { path = "/dev/null"; flags = null_flags(flags); }
    return original(path, flags);
}
static int quiet_openat_5(int directory, const char *path, int flags, ...) {
    int (*original)(int, const char *, int, ...) = (void *)modules[5].original[SLOT_OPENAT];
    va_list args; va_start(args, flags); mode_t mode = open_mode(flags, args); va_end(args);
    if ((flags & O_ACCMODE) != O_RDONLY && suppress_path(path)) { directory = AT_FDCWD; path = "/dev/null"; flags = null_flags(flags); }
    return original(directory, path, flags, mode);
}
static const uintptr_t replacements[6][SLOT_COUNT] = {
    {(uintptr_t)quiet_fopen_0, (uintptr_t)quiet_fopen64_0, (uintptr_t)quiet_freopen_0, (uintptr_t)quiet_open_0, (uintptr_t)quiet_open2_0, (uintptr_t)quiet_openat_0},
    {(uintptr_t)quiet_fopen_1, (uintptr_t)quiet_fopen64_1, (uintptr_t)quiet_freopen_1, (uintptr_t)quiet_open_1, (uintptr_t)quiet_open2_1, (uintptr_t)quiet_openat_1},
    {(uintptr_t)quiet_fopen_2, (uintptr_t)quiet_fopen64_2, (uintptr_t)quiet_freopen_2, (uintptr_t)quiet_open_2, (uintptr_t)quiet_open2_2, (uintptr_t)quiet_openat_2},
    {(uintptr_t)quiet_fopen_3, (uintptr_t)quiet_fopen64_3, (uintptr_t)quiet_freopen_3, (uintptr_t)quiet_open_3, (uintptr_t)quiet_open2_3, (uintptr_t)quiet_openat_3},
    {(uintptr_t)quiet_fopen_4, (uintptr_t)quiet_fopen64_4, (uintptr_t)quiet_freopen_4, (uintptr_t)quiet_open_4, (uintptr_t)quiet_open2_4, (uintptr_t)quiet_openat_4},
    {(uintptr_t)quiet_fopen_5, (uintptr_t)quiet_fopen64_5, (uintptr_t)quiet_freopen_5, (uintptr_t)quiet_open_5, (uintptr_t)quiet_open2_5, (uintptr_t)quiet_openat_5},
};
static int slot_for_name(const char *name) {
    if (!strcmp(name, "fopen")) return 0;
    if (!strcmp(name, "fopen64")) return 1;
    if (!strcmp(name, "freopen")) return 2;
    if (!strcmp(name, "open")) return 3;
    if (!strcmp(name, "__open_2")) return 4;
    if (!strcmp(name, "openat")) return 5;
    return -1;
}
struct install_state { size_t hooks, failures; bool main_seen; };
static void patch_slot(const struct dl_phdr_info *info, uintptr_t *slot, uintptr_t value, uintptr_t *original, struct install_state *state) {
    uintptr_t current = __atomic_load_n(slot, __ATOMIC_ACQUIRE);
    if (current == value) { ++state->hooks; return; }
    if (*original && *original != current) { ++state->failures; return; }
    long page_size = sysconf(_SC_PAGESIZE);
    if (page_size <= 0) { ++state->failures; return; }
    uintptr_t mask = (uintptr_t)page_size - 1, page = (uintptr_t)slot & ~mask;
    bool relro = false, writable = false;
    for (ElfW(Half) i = 0; i < info->dlpi_phnum; ++i) {
        const ElfW(Phdr) *p = &info->dlpi_phdr[i];
        uintptr_t start = info->dlpi_addr + p->p_vaddr, end = start + p->p_memsz;
        if (p->p_type == PT_LOAD && (uintptr_t)slot >= start && (uintptr_t)slot + sizeof(*slot) <= end)
            writable = (p->p_flags & PF_W) != 0;
        if (p->p_type == PT_GNU_RELRO && page >= (start & ~mask) && page < ((end + mask) & ~mask))
            relro = true;
    }
    if (!writable) { ++state->failures; return; }
    if (relro && mprotect((void *)page, (size_t)page_size, PROT_READ | PROT_WRITE)) { ++state->failures; return; }
    *original = current;
    __atomic_store_n(slot, value, __ATOMIC_RELEASE);
    if (relro && mprotect((void *)page, (size_t)page_size, PROT_READ)) ++state->failures;
    ++state->hooks;
}
static void patch_relocations(const struct dl_phdr_info *info, const ElfW(Rela) *relocs, size_t count,
                              const ElfW(Sym) *symbols, const char *strings, size_t module, struct install_state *state) {
    for (size_t i = 0; i < count; ++i) {
        unsigned type = ELF64_R_TYPE(relocs[i].r_info);
        if (type != R_AARCH64_JUMP_SLOT && type != R_AARCH64_GLOB_DAT) continue;
        unsigned index = (unsigned)ELF64_R_SYM(relocs[i].r_info);
        int slot = slot_for_name(strings + symbols[index].st_name);
        if (slot >= 0) patch_slot(info, (uintptr_t *)(info->dlpi_addr + relocs[i].r_offset), replacements[module][slot], &modules[module].original[slot], state);
    }
}
static int install_for_module(struct dl_phdr_info *info, size_t size, void *opaque) {
    (void)size;
    struct install_state *state = opaque;
    if (!target_library(info->dlpi_name)) return 0;
    const char *name = strrchr(info->dlpi_name, '/'); name = name ? name + 1 : info->dlpi_name;
    if (!strcmp(name, "librevolution.so")) state->main_seen = true;
    size_t module = 0;
    while (module < sizeof(modules)/sizeof(modules[0]) && strcmp(name, modules[module].name)) ++module;
    if (module == sizeof(modules)/sizeof(modules[0])) return 0;
    const ElfW(Dyn) *dynamic = NULL;
    for (ElfW(Half) i = 0; i < info->dlpi_phnum; ++i)
        if (info->dlpi_phdr[i].p_type == PT_DYNAMIC) dynamic = (const void *)(info->dlpi_addr + info->dlpi_phdr[i].p_vaddr);
    if (!dynamic) { ++state->failures; return 0; }
    const ElfW(Sym) *symbols = NULL; const char *strings = NULL;
    const ElfW(Rela) *plt = NULL, *rela = NULL;
    size_t plt_size = 0, rela_size = 0; ElfW(Sword) plt_type = DT_RELA;
    for (const ElfW(Dyn) *d = dynamic; d->d_tag != DT_NULL; ++d) {
        uintptr_t pointer = runtime_pointer(info, d->d_un.d_ptr);
        switch (d->d_tag) {
            case DT_SYMTAB: symbols = (const void *)pointer; break;
            case DT_STRTAB: strings = (const void *)pointer; break;
            case DT_JMPREL: plt = (const void *)pointer; break;
            case DT_PLTRELSZ: plt_size = d->d_un.d_val; break;
            case DT_PLTREL: plt_type = (ElfW(Sword))d->d_un.d_val; break;
            case DT_RELA: rela = (const void *)pointer; break;
            case DT_RELASZ: rela_size = d->d_un.d_val; break;
        }
    }
    if (!symbols || !strings || plt_type != DT_RELA) { ++state->failures; return 0; }
    if (plt) patch_relocations(info, plt, plt_size/sizeof(*plt), symbols, strings, module, state);
    if (rela) patch_relocations(info, rela, rela_size/sizeof(*rela), symbols, strings, module, state);
    return 0;
}
JNIEXPORT jboolean JNICALL Java_com_naruto_port_LoggingControl_nativeSetEnabled(JNIEnv *env, jclass klass, jboolean capture) {
    (void)env; (void)klass;
    pthread_mutex_lock(&lock);
    bool ok = resolve_symbols();
    if (ok) ok = configure_early_logging(capture != 0);
    if (ok) {
        atomic_store_explicit(&enabled, capture != 0, memory_order_release);
        if (!capture) {
            int previous = set_priority(8); /* ANDROID_LOG_SILENT, API 30+. */
            if (!priority_saved) { previous_priority = previous; priority_saved = true; }
            set_logger(discard_android_log);
        } else {
            set_logger(logd_logger);
            if (priority_saved) set_priority(previous_priority);
        }
        ok = redirect_streams(capture != 0);
    }
    pthread_mutex_unlock(&lock);
    return ok ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT jboolean JNICALL Java_com_naruto_port_LoggingControl_nativeInstall(JNIEnv *env, jclass klass) {
    (void)env; (void)klass;
    pthread_mutex_lock(&lock);
    struct install_state state = {0};
    bool ready = resolve_symbols();
    if (ready) dl_iterate_phdr(install_for_module, &state);
    pthread_mutex_unlock(&lock);
    return ready && state.main_seen && state.hooks > 0 && state.failures == 0 ? JNI_TRUE : JNI_FALSE;
}
/* Read inside the same ABI/libc as the engine. Android native-bridge guests can
 * have a different environment from host Java's Os.getenv during CI. */
JNIEXPORT jboolean JNICALL Java_com_naruto_port_LoggingControl_nativeEarlyCutoff(JNIEnv *env, jclass klass) {
    (void)env; (void)klass;
    const char *value = getenv("XNR_LOG_LEVEL");
    return value && strcmp(value, "5") == 0 ? JNI_TRUE : JNI_FALSE;
}
