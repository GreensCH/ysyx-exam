#ifndef ARCH_H__
#define ARCH_H__

struct Context {
  // TODO: fix the order of these members to match trap.S
  uintptr_t gpr[32], mcause, mstatus, mepc;
  void *pdir;//可能是地址空间信息？
  // uintptr_t mepc, mstatus, mcause, gpr[32];
  // void *pdir;//可能是地址空间信息？
  // uintptr_t mepc, mcause, gpr[32], mstatus;
};

#define GPR1 gpr[17] // a7
#define GPR2 gpr[10] // a0
#define GPR3 gpr[11] // a1
#define GPR4 gpr[12] // a2
// #define GPR5 gpr[13] // a3
// #define GPR6 gpr[14] // a4
// #define GPR7 gpr[15] // a5
#define GPRx gpr[10] // a0
#endif
