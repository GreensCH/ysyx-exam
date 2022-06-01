#include "include.h"
#include "memory.h"

#if   defined(CONFIG_PMEM_MALLOC)
static uint8_t *pmem = NULL;
#else // CONFIG_PMEM_GARRAY
static uint8_t pmem[CONFIG_MSIZE] PG_ALIGN = {};
#endif
#ifdef CONFIG_MTRACE
  void mtrace_rd_log(word_t data, word_t addr);
  void mtrace_we_log(word_t data, word_t addr);
#endif

uint8_t* guest_to_host(paddr_t paddr) { return pmem + paddr - CONFIG_MBASE; }
paddr_t host_to_guest(uint8_t *haddr) { return haddr - pmem + CONFIG_MBASE; }

static void out_of_bound(paddr_t addr) {
  panic("address = " FMT_PADDR " is out of bound of pmem [" FMT_PADDR ", " FMT_PADDR ") at pc = " FMT_WORD,
      addr, CONFIG_MBASE, CONFIG_MBASE + CONFIG_MSIZE, cpu.pc);
}
enum {PROGRAM_MEMORY, DATA_MEMORY};
extern "C" void pmem_read(paddr_t addr, int len, word_t* data) {
  // printf("\33[1;34mVLT\tREAD addr:0x%016lx, len:%d\33[0m \n" ,(word_t)addr, len);
  if(addr < 0x8000000){
    printf("read fail\n");
    return;
  }
  (*data) = paddr_read(addr, len);//host_read(guest_to_host(addr), len);
}

extern "C" void  pmem_write(paddr_t addr, int len, word_t data) {
  // printf("\33[1;34mVLT\tWRITE addr0x%016lx, len:%d ,data0x%016lx \33[0m \n" ,(word_t)addr, len, data);
  if(addr < 0x8000000){
    printf("write fail\n");
    return;
  }
  paddr_write(addr, len, data);//host_write(guest_to_host(addr), len, data);
}


void init_mem() {
#if   defined(CONFIG_PMEM_MALLOC)
  pmem = malloc(CONFIG_MSIZE);
  assert(pmem);
#endif
#ifdef CONFIG_MEM_RANDOM
  uint32_t *p = (uint32_t *)pmem;
  int i;
  for (i = 0; i < (int) (CONFIG_MSIZE / sizeof(p[0])); i ++) {
    p[i] = rand();
  }
#endif
  Log("physical memory area [" FMT_PADDR ", " FMT_PADDR "]",
      (paddr_t)CONFIG_MBASE, (paddr_t)CONFIG_MBASE + CONFIG_MSIZE);
}


word_t paddr_read(paddr_t addr, int len) {
  IFDEF(CONFIG_MTRACE, mtrace_rd_log(host_read(guest_to_host(addr), len), addr););//{  mtrace_rd_log(pmem_read(addr, len), (word_t)addr);  };
  if (likely(in_pmem(addr))) return host_read(guest_to_host(addr), len);
  // IFDEF(CONFIG_DEVICE, return mmio_read(addr, len));
  out_of_bound(addr);
  return 0;
}

void paddr_write(paddr_t addr, int len, word_t data) {
  IFDEF(CONFIG_MTRACE,  mtrace_we_log(data, addr););//if(MTRACE_COND) {  mtrace_we_log(data, addr);  };
  if (likely(in_pmem(addr))) { host_write(guest_to_host(addr), len, data); return; }
  // IFDEF(CONFIG_DEVICE, mmio_write(addr, len, data); return);
  out_of_bound(addr);
}

