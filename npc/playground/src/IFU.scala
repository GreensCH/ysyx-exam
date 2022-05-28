import chisel3._



class IF2Memory extends Bundle{
  val rd_addr  =  Output (UInt(64.W))
  val rd_data  =  Input (UInt(64.W))
}

class IF2ID extends Bundle{
  val inst  =   Output(UInt(32.W))
  val pc = Output(UInt(64.W))
}

class IFU extends Module {
  val io = IO(new Bundle {
    val id2pc   =   Flipped(new ID2PC)
    val if2id   =   new IF2ID
  })
//  printf("NPC@IFU\n")
  /* PC instance */
  val PC = Module(new PC)
  /* pre IF (PC) interface*/
  val pc = PC.io.pc
  PC.io.is_jump := io.id2pc.is_jump
  PC.io.offset := io.id2pc.offset

  /* memory bus instance */
  val memory_inf = Module(new MemoryInf).io
  /* memory interface */
  memory_inf.id :=1.U
  memory_inf.rd_en   := true.B
  memory_inf.rd_addr := pc
  val memory_rd_data = memory_inf.rd_data
  memory_inf.we_en   := false.B
  memory_inf.we_addr := 0.U(64.W)
  memory_inf.we_data := 0.U(64.W)
  memory_inf.we_mask := "b00000000".U

  /* if2id interface */
  io.if2id.inst := memory_rd_data
  io.if2id.pc := pc
}
