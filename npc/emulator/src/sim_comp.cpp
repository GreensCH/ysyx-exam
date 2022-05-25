#include "../include/include.h"

VerilatedContext* contextp = NULL;
VerilatedVcdC* tfp = NULL;
VTop* top = NULL;


vluint64_t main_time = 0;       // Current simulation time
// This is a 64-bit integer to reduce wrap over issues and
// allow modulus.  This is in units of the timeprecision
// used in Verilog (or from --timescale-override)
double sc_time_stamp() {        // Called by $time in Verilog
    return main_time;           // converts to double, to match
                                // what SystemC does
}

void step_and_dump_wave(){
  top->clock = 0; top->eval();
  top->clock = 1; top->eval();
  main_time += 1;
  contextp->timeInc(1);
  // tfp->dump(contextp->time());
}

void reset(int n){
  step_and_dump_wave();
  top->reset = 1;
  while(n>0){
    step_and_dump_wave();
    n--;
  }
  top->reset = 0;
}

void sim_exit(){
  step_and_dump_wave();
  tfp->close();
  delete top;
  delete contextp;
}

void sim_init(int argc, char** argv){
    contextp  = new VerilatedContext;
    top = new VTop{contextp};// Create model
    contextp->commandArgs(argc, argv);// Remember args
    contextp->commandArgs(argc, argv);
    contextp->traceEverOn(true);// Enable wave trace
}
