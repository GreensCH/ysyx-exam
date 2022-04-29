#include <isa.h>
#include <cpu/cpu.h>
#include <readline/readline.h>
#include <readline/history.h>
#include "sdb.h"
#include <common.h>

static int is_batch_mode = false;

void init_regex();
void init_wp_pool();

/* We use the `readline' library to provide more flexibility to read from stdin. */
static char* rl_gets() {
  static char *line_read = NULL;

  if (line_read) {
    free(line_read);
    line_read = NULL;
  }

  line_read = readline("(nemu) ");

  if (line_read && *line_read) {
    add_history(line_read);
  }

  return line_read;
}

static int cmd_c(char *args) {
  cpu_exec(-1);
  return 0;
}


static int cmd_q(char *args) {
  cpu_exec(-1);//added by chang
  return -1;
}

static int cmd_help(char *args);

static int cmd_si(char *args);

static int cmd_info(char *args);

static int cmd_x(char *args);


static struct {
  const char *name;
  const char *description;
  int (*handler) (char *);
} cmd_table [] = {
  { "help", "Display informations about all supported commands", cmd_help },
  { "c", "Continue the execution of the program", cmd_c },
  { "q", "Exit NEMU", cmd_q },

  /* TODO: Add more commands */
  {"si", "Single step", cmd_si },
  {"info", "Single step", cmd_info },
  {"x", "Single step", cmd_x },
};

#define NR_CMD ARRLEN(cmd_table)

static int cmd_help(char *args) {
  /* extract the first argument */
  char *arg = strtok(NULL, " ");
  int i;

  if (arg == NULL) {
    /* no argument given */
    for (i = 0; i < NR_CMD; i ++) {
      printf("%s - %s\n", cmd_table[i].name, cmd_table[i].description);
    }
  }
  else {
    for (i = 0; i < NR_CMD; i ++) {
      if (strcmp(arg, cmd_table[i].name) == 0) {
        printf("%s - %s\n", cmd_table[i].name, cmd_table[i].description);
        return 0;
      }
    }
    printf("Unknown command '%s'\n", arg);
  }
  return 0;
}

static int cmd_si(char *args) {
  if(args!=NULL){
    // printf("run %d",atoi(args));
    cpu_exec(atoi(args));
  }//可以再添加一个非法字串匹配的if
  else {
    cpu_exec(1);
  }
  return 0;
}

static int cmd_info(char *args) {
  if(args[0]=='r')
    isa_reg_display();
  else
    printf("Invalid parameter %s\n",args);
  return 0;
}


static int cmd_x(char *args) {
  
  char *token_N;//token1
  char *token_EXPR;//token2
  //vaddr_t ram_addr_base = 0;
  //vaddr_t ram_addr_offset = 0;
  int64_t ram_addr_base = 0;
  int64_t ram_addr_offset = 0;
  //1
  token_N = strtok(args," ");
  if(token_N!=NULL){
    sscanf(token_N, "%ld", &ram_addr_offset);//get ram offset
    printf("offset:%ld\n",ram_addr_offset);
  }
  else{
    cpu_exec(-1);
    return 0;
  }
  //2
  token_EXPR = strtok(NULL," ");
  if(token_EXPR!=NULL){
    sscanf(token_EXPR, "%lx", &ram_addr_base);//get ram addr
    printf("base:%ld\n",ram_addr_base);
  }
  else{
    cpu_exec(-1);
    return 0;
  }
  //DO ADDR CONVERT

  cpu_exec(-1);
  return 0;
}

void sdb_set_batch_mode() {
  is_batch_mode = true;
}

void sdb_mainloop() {
  if (is_batch_mode) {
    cmd_c(NULL);
    return;
  }

  for (char *str; (str = rl_gets()) != NULL; ) {
    char *str_end = str + strlen(str);

    /* extract the first token as the command */
    char *cmd = strtok(str, " ");
    if (cmd == NULL) { continue; }

    /* treat the remaining string as the arguments,
     * which may need further parsing
     */
    char *args = cmd + strlen(cmd) + 1;
    if (args >= str_end) {
      args = NULL;
    }

#ifdef CONFIG_DEVICE
    extern void sdl_clear_event_queue();
    sdl_clear_event_queue();
#endif

    int i;
    for (i = 0; i < NR_CMD; i ++) {
      if (strcmp(cmd, cmd_table[i].name) == 0) {
        if (cmd_table[i].handler(args) < 0) { return; }
        break;
      }
    }

    if (i == NR_CMD) { printf("Unknown command '%s'\n", cmd); }
  }
}

void init_sdb() {
  /* Compile the regular expressions. */
  init_regex();

  /* Initialize the watchpoint pool. */
  init_wp_pool();
}