#include <stdint.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include <fixedptc.h>

int main() {

  fixedpt f1 = fixedpt_rconst(1.2);
  if(fixedpt_toint(fixedpt_ceil(f1)) != 2)
    printf("ceil fail\n");
  else
    printf("ceil success\n");

  fixedpt f2 = fixedpt_rconst(3.9);
  if(fixedpt_toint(fixedpt_floor(f2)) != 3)
    printf("floor fail\n");
  else
    printf("floor success\n");
  
  return 0;
}
