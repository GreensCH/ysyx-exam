#include <isa.h>
#include "../local-include/reg.h"

word_t isa_raise_intr(word_t NO, vaddr_t epc) {
  /* TODO: Trigger an interrupt/exception with ``NO''.
   * Then return the address of the interrupt/exception vector.
   */
  //my original isa_raise_intr is
//                                                                                         I/E         code                                                     MPP                    MPIE                        MIE
//mepc = s->pc; mcause = 0x0000000000000000 | 11; mstatus = 0xa00000000 | (0b11 << 11) | (BITS(mstatus, 3, 3) << 7) | (0b0 << 3); s->dnpc = mtvec
  mepc = cpu.pc;
  mcause = NO;
  //参考pa3.1中的介绍，应该是没有mstatus的设置
  //mstatus = 0xa00000000 | (0b11 << 11) | (BITS(mstatus, 3, 3) << 7) | (0b0 << 3);
#ifdef CONFIG_ETRACE
  Log("Exception (%lx) throw out at 0x%lx, jump to 0x%lx", NO, cpu.pc, mtvec);
#endif
  return mtvec;
}

word_t isa_query_intr() {
  return INTR_EMPTY;
}

void isa_intr_init(){
  mcause = 0xa00000000;
}