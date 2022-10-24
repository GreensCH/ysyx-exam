#ifndef AXI4_MEM
#define AXI4_MEM

#include "axi4_slave.hpp"
#include "memory.h"
#include <fstream>
#include <iostream>


template <unsigned int A_WIDTH = 64, unsigned int D_WIDTH = 64, unsigned int ID_WIDTH = 4>
class axi4_mem : public axi4_slave<A_WIDTH,D_WIDTH,ID_WIDTH>  {
    public:
        axi4_mem() {
        }
        ~axi4_mem() {
        }

    protected:
        axi_resp do_read(uint64_t start_addr, uint64_t size, uint8_t* buffer) {
            // printf("do_read%x %x %s\n",start_addr,size, buffer);
            if(size == 16){
                word_t data = paddr_read(start_addr, 8);
                memcpy(buffer,&data,8);
                data = paddr_read(start_addr+8, 8);
                memcpy(buffer+8,&data,8);
                return RESP_OKEY;
            }else{
                if(start_addr < 100) return RESP_OKEY;
                word_t data = paddr_read(start_addr, (int)size);
                memcpy(buffer,&data,size);
                return RESP_OKEY;
            }
        }
        axi_resp do_write(uint64_t start_addr, uint64_t size, const uint8_t* buffer) {
            // printf("do_write at addr: %lx, size:%d %s\n",start_addr, size, buffer);
            if(size == 16){
                word_t data;
                memcpy(&data,buffer,size);
                paddr_write(start_addr, 8, data);

                memcpy(&data,buffer+8,size);
                paddr_write(start_addr+8, 8, data);
                return RESP_OKEY;
            }else{
                // printf("do_write at addr: %lx, size:%d %s\n",start_addr, size, buffer);
                word_t data;
                memcpy(&data,buffer,size);
                paddr_write(start_addr, (int)size, data);
                return RESP_OKEY;
            }
        }
};

#endif