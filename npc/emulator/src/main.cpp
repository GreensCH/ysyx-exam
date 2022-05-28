#include "include.h"




static char *log_file = NULL;
static char *diff_so_file = NULL;
static char *img_file = NULL;
static char *elf_file = NULL;
static int difftest_port = 1234;

#include <getopt.h>
static int parse_args(int argc, char *argv[]) {
  std::cout<<"argc:"<<argc<<std::endl;
  for(int i = 0; i < argc; i++) 
    std::cout<<argv[i]<<std::endl;
  const struct option table[] = {
    {"batch"    , no_argument      , NULL, 'b'},
    {"log"      , required_argument, NULL, 'l'},
    {"diff"     , required_argument, NULL, 'd'},
    {"port"     , required_argument, NULL, 'p'},
    {"help"     , no_argument      , NULL, 'h'},
    {"elf"      , required_argument, NULL, 'e'},
    {0          , 0                , NULL,  0 },
  };
  int o;
  while ( (o = getopt_long(argc, argv, "-bhl:d:p:e:", table, NULL)) != -1) {
    switch (o) {
      // case 'b': sdb_set_batch_mode(); break;
      // case 'p': sscanf(optarg, "%d", &difftest_port); break;
      case 'l': log_file = optarg; break;
      // case 'd': diff_so_file = optarg; break;
      case 'e': elf_file = optarg; break;
      case 1: img_file = optarg; return 0;
      default:
        printf("Usage: %s [OPTION...] IMAGE [args]\n\n", argv[0]);
        printf("\t-b,--batch              run with batch mode\n");
        printf("\t-l,--log=FILE           output log to FILE\n");
        printf("\t-d,--diff=REF_SO        run DiffTest with reference REF_SO\n");
        printf("\t-p,--port=PORT          run DiffTest with port PORT\n");
        printf("\t-e,--elf=FILE           input elf\n");
        printf("\n");
        exit(0);
    }
  }
  return 0;
}


extern "C" word_t pmem_read(paddr_t addr, int len) {
  // printf("VLT@READ addr:0x%016lx, len:%d\t",addr, len);
  if(addr < 0x80000000){
    // printf("read fail\n");
    return 0;
  }
  // printf("\n");
  word_t ret = host_read(guest_to_host(addr), len);
  return ret;
}

extern "C" void  pmem_write(paddr_t addr, int len, word_t data) {
 printf("VLT@WRITE addr0x%016lx, len:%d ,data0x%016lx\t",addr, len, data);
  if(addr < 0x80000000){
    // printf("write fail\n");
    return;
  }
  // printf("\n");
  host_write(guest_to_host(addr), len, data);
}

static const uint32_t img [] = {
  0x800002b7,  // lui t0,0x80000 0
  0x0002a023,  // sw  zero,0(t0) 
  0x0002a503,  // lw  a0,0(t0)   
  0x00100073,  // ebreak 
};

// static void restart() {
//   reset(1);
// }

// void init_isa() {
//   memcpy(guest_to_host(RESET_VECTOR), img, sizeof(img));
// }

int main(int argc, char *argv[], char** env) {

    parse_args(argc, argv);
    printf("log_file:%s\n",log_file);
    printf("elf_file:%s\n",elf_file);
    printf("img_file:%s\n",img_file);
    
    sim_init(argc,argv);
    // init_isa();
    reset(1);
    printf("start npc\n");
    while (!contextp->gotFinish() && sc_time_stamp()<10){ 
      step_and_dump_wave();
      // printf("@vlt:finish=%d\n",contextp->gotFinish());
      // printf("pc: 0x%lx  inst: 0x%lx\n",(word_t)top->io_pc , (word_t)top->io_inst);
    }
    step_and_dump_wave();
    printf( "quiting verilator\n");
    sim_exit();
    
    
    return 0;
}


