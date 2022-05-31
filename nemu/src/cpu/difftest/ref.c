#include <isa.h>
#include <cpu/cpu.h>
#include <difftest-def.h>
#include <memory/paddr.h>

// `direction`指定拷贝的方向, 
// `DIFFTEST_TO_DUT`表示往DUT拷贝, 
// `DIFFTEST_TO_REF`表示往REF拷贝

void difftest_memcpy(paddr_t addr, void *buf, size_t n, bool direction) {
  uint8_t *p = buf;
  if(direction == DIFFTEST_TO_REF){
    for (size_t i = 0; i < n; i++) {
      *guest_to_host(addr) = p[i];
    }
  }
  else{
    assert(0);
  }
}
// `direction`为`DIFFTEST_TO_DUT`时, 获取REF的寄存器状态到`dut`;
// `direction`为`DIFFTEST_TO_REF`时, 设置REF的寄存器状态为`dut`;
void difftest_regcpy(void *dut, bool direction) {
  riscv64_CPU_state* p = dut;
  if(direction == DIFFTEST_TO_DUT){
    for(int i = 0; i < 32; i++){
      p->gpr[i] = cpu.gpr[i];
    }
    p->pc = cpu.pc;
  }
  else{
    for(int i = 0; i < 32; i++){
      cpu.gpr[i] = p->gpr[i];
    }
    cpu.pc = p->pc;
  }
}
#include <cpu/decode.h>
void difftest_exec(uint64_t n) {
  Decode s;
  for (;n > 0; n --) {
    if(cpu.pc < 0x8000000)
      return;
    s.pc = cpu.pc;
    s.snpc = cpu.pc;
    // printf("From NEMU:\n");
    // isa_reg_display();
    // isa_exec_once(&s);
    cpu.pc = s.dnpc;
  }
}

void difftest_raise_intr(word_t NO) {
  assert(0);
}

void difftest_init(int port) {
  /* Perform ISA dependent initialization. */
  init_isa();
}
