import "DPI-C" function longint pmem_read(input longint addr, input int len);
// import "DPI-C" function void pmem_write(input longint addr, input longint wdata, input byte wmask);
import "DPI-C" function void pmem_write(input longint addr, input int len, input longint data);
// void  pmem_write(paddr_t addr, int len, word_t data)

module dpic_memory (
    input               rd_en,
    input   [63 : 0]    rd_addr,
    output  [63 : 0]    rd_data,
    input               we_en,
    input   [63 : 0]    we_addr,
    input   [63 : 0]    we_data,
    input   [7  : 0]    we_mask
);

  always@(*)
    $display("VER@%1d ,%x", rd_en, rd_addr);
  
  assign rd_data = (rd_en == 0) ? pmem_read(rd_addr, 8) : pmem_read(0, 8) ;
  //assign rd_data = pmem_read(rd_addr, 8);
  always @(*) begin
    // $display("addr:0x%lx data:0x%lx mask:0x%lx\n",we_addr, we_data, we_mask);
    if(we_en)begin
      case(we_mask)
        8'b0000_0001 : pmem_write(we_addr, 1, we_data);
        8'b0000_0011 : pmem_write(we_addr, 2, we_data);
        8'b0000_1111 : pmem_write(we_addr, 4, we_data);
        8'b1111_1111 : pmem_write(we_addr, 8, we_data);
        default: pmem_write(0, 8, we_data);
      endcase
    end
  end
endmodule


