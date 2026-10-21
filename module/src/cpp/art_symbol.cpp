#include "memory_dex.h"
#include <link.h>
#include <elf.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>
#include <string.h>
#include <stdint.h>

static int find_art(struct dl_phdr_info* info, size_t size, void* data) {
    (void)size;
    if (!info->dlpi_name) return 0;
    const char* base = strrchr(info->dlpi_name, '/');
    if (strcmp(base ? base + 1 : info->dlpi_name, "libart.so")) return 0;
    *(uintptr_t*)data = info->dlpi_addr;
    return 1;
}
void* artSymbol(const char* name) {
    uintptr_t base = 0;
    dl_iterate_phdr(find_art, &base);
    if (!base) return NULL;
    int fd = open(sizeof(void*) == 8 ? "/system/lib64/libart.so" : "/system/lib/libart.so", O_RDONLY | O_CLOEXEC);
    if (fd < 0) return NULL;
    struct stat st;
    if (fstat(fd, &st) || st.st_size < (off_t)sizeof(ElfW(Ehdr))) { close(fd); return NULL; }
    size_t size = (size_t)st.st_size;
    const auto* bytes = static_cast<const unsigned char*>(mmap(nullptr, size, PROT_READ, MAP_PRIVATE, fd, 0));
    close(fd);
    if (bytes == MAP_FAILED) return NULL;
    const ElfW(Ehdr)* header = (const ElfW(Ehdr)*)bytes;
    void* result = NULL;
    const ElfW(Shdr)* sections = nullptr;
    if (memcmp(header->e_ident, ELFMAG, SELFMAG) || header->e_shentsize != sizeof(ElfW(Shdr))
            || header->e_shoff > size || header->e_shnum > (size - header->e_shoff) / sizeof(ElfW(Shdr))) goto done;
    sections = reinterpret_cast<const ElfW(Shdr)*>(bytes + header->e_shoff);
    for (unsigned i = 0; i < header->e_shnum && !result; ++i) {
        const ElfW(Shdr)* section = &sections[i];
        if (section->sh_type != SHT_DYNSYM || section->sh_link >= header->e_shnum || section->sh_entsize != sizeof(ElfW(Sym))) continue;
        const ElfW(Shdr)* strings = &sections[section->sh_link];
        if (section->sh_offset > size || section->sh_size > size - section->sh_offset || strings->sh_offset > size || strings->sh_size > size - strings->sh_offset) continue;
        const ElfW(Sym)* symbols = (const ElfW(Sym)*)(bytes + section->sh_offset);
        for (size_t j = 0; j < section->sh_size / sizeof(ElfW(Sym)); ++j) {
            if (symbols[j].st_name >= strings->sh_size || symbols[j].st_shndx == SHN_UNDEF) continue;
            const char* candidate = (const char*)(bytes + strings->sh_offset + symbols[j].st_name);
            if (!memchr(candidate, 0, strings->sh_size - symbols[j].st_name)) continue;
            if (!strcmp(candidate, name)) { result = (void*)(base + symbols[j].st_value); break; }
        }
    }
done:
    munmap((void*)bytes, size);
    return result;
}
