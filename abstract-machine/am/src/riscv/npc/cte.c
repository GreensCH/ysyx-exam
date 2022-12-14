#include <am.h>
#include <riscv/riscv.h>
#include <klib.h>

static Context* (*user_handler)(Event, Context*) = NULL;

Context* __am_irq_handle(Context *c) {
  if (user_handler) {
    Event ev = {0};
    uint64_t mtime = *((int *)0x0200BFF8);
    switch (c->mcause) {
      case 0x8000000000000007:
        *((uint64_t *)0x02004000) = mtime + 50000;
        printf("time :%d cmp:%d\n", mtime, *((uint64_t *)0x02004000));
        ev.event = EVENT_IRQ_TIMER;
        break;
      case 0x8000000000000003:
        printf("s");
        break;
      case 11:
        if(c->GPR1 == -1){
          ev.event = EVENT_YIELD;
        }else{
          ev.event = EVENT_SYSCALL;
        }
        c->mepc += 4;
        break;
      break;
      default: ev.event = EVENT_ERROR  ; break;
    }
    c = user_handler(ev, c);
    assert(c != NULL);
  }

  return c;
}

extern void __am_asm_trap(void);

bool cte_init(Context*(*handler)(Event, Context*)) {
  // initialize exception entry
  asm volatile("csrw mtvec, %0" : : "r"(__am_asm_trap));

  // register event handler
  user_handler = handler;

  return true;
}

Context *kcontext(Area kstack, void (*entry)(void *), void *arg) {
  return NULL;
}

void yield() {
  asm volatile("li a7, -1; ecall");
}

bool ienabled() {
  return false;
}

void iset(bool enable) {
  ;
  // if(enable){
  //   asm volatile("csrsi mstatus, 8");
  //   set_csr(mie, MIP_MTIP);
  // }
  // else{
  //   asm volatile("csrci mstatus, 8");
  //   clear_csr(mie, MIP_MTIP);
  // }
}

