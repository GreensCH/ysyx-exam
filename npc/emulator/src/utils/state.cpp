#include "utils.h"

NPCState nemu_state = { .state = NPC_STOP };

int is_exit_status_bad() {
  int good = (nemu_state.state == NPC_END && nemu_state.halt_ret == 0) ||
    (nemu_state.state == NPC_QUIT);
  return !good;
}
