class ICache extends Module{
  val io = IO(new Bundle{
    val prev  = Flipped(new PCUOut)
    val master = new AXI4
    val next  = new IFUOut
  })
  // module interface
  private val prev = io.prev
  private val memory = io.master
  private val next = io.next
  // Miss register
  val temp_data_in = WireDefault(0.U.asTypeOf((new IFUOut).bits))
  val temp_data = RegNext(init = 0.U.asTypeOf((new IFUOut).bits), next = temp_data_in)
  val temp_valid_in = WireDefault(false.B)
  val temp_valid = RegNext(init = false.B, next = temp_valid_in)
  // AXI interface
  val axi_ar_out = memory.ar
  val axi_r_in = memory.r
  memory.aw <> 0.U.asTypeOf(new AXI4BundleA) // useless port use default signal
  memory.w <> 0.U.asTypeOf(new AXI4BundleW)
  memory.b <> 0.U.asTypeOf(new AXI4BundleB)
  val trans_id = 1.U(AXI4Parameters.idBits)
  // SRAM & SRAM Sig
  val data_array_0 = SRAM()
  val data_array_1 = SRAM()
  val tag_array_0  = SRAM()
  val tag_array_1  = SRAM()
  val da_0_rdata  = Wire(UInt(CacheCfg.ram_width.W))
  val da_1_rdata  = Wire(UInt(CacheCfg.ram_width.W))
  val ta_0_rdata  = Wire(UInt(CacheCfg.ram_width.W))
  val ta_1_rdata  = Wire(UInt(CacheCfg.ram_width.W))
  val lru_list = RegInit(VecInit(Seq.fill(CacheCfg.ram_depth)(0.U(1.W))))
  // Main Signal
  val resp_okay = (trans_id === axi_r_in.bits.id) & (AXI4Parameters.RESP_OKAY === axi_r_in.bits.resp) & (axi_r_in.valid)
  val last = (axi_r_in.bits.last & resp_okay)
  val read_data = axi_r_in.bits.data
  val miss = Wire(Bool())
  val tag0_hit = Wire(Bool())
  val tag1_hit = Wire(Bool())
  // FSM States
  protected val sIDLE :: sLOOKUP :: sRISSUE :: sRCATCH :: sRWRITE :: Nil = Enum(5) //sIDLEUInt<3>(0) sLOOKUPUInt<3>(1)
  protected val next_state = Wire(UInt(sIDLE.getWidth.W))
  protected val curr_state = RegEnable(init = sRISSUE, next = next_state, enable = next.ready)
  // States change
  next_state := sRISSUE //default option
  switch(curr_state){
    is(sIDLE){ next_state := sLOOKUP }
    is(sLOOKUP){
      when(!prev.valid){ next_state := sLOOKUP }
        .elsewhen(miss) { next_state := sRISSUE }
        .otherwise { next_state := sLOOKUP }
    }
    is(sRISSUE){
      when(resp_okay) { next_state := sRCATCH }
        .otherwise      { next_state := sRISSUE }
    }
    is(sRCATCH){
      when(last) { next_state := sRWRITE }
        .otherwise { next_state := sRCATCH }
    }
    is(sRWRITE){
      when(!miss){ next_state := sLOOKUP }
        .elsewhen(next.ready){ next_state := sIDLE }
        .otherwise{ next_state := sRWRITE }
    }
  }
  /* Output */
  // Cache-Pipeline Control Signal(note: miss_reg_valid is prev-valid ctrl sig)
  val ctrl_valid = Wire(Bool())
  val ctrl_ready = Wire(Bool())
  ctrl_valid := false.B
  ctrl_ready := false.B
  when(curr_state === sIDLE){
    ctrl_valid := false.B
    ctrl_ready := true.B
  }.elsewhen(curr_state === sLOOKUP){
    when(prev.valid === false.B){
      ctrl_valid := false.B
      when(miss){ ctrl_ready := false.B }
        .otherwise{ ctrl_ready := true.B}
    }.elsewhen(miss){
      ctrl_valid := false.B
      ctrl_ready := false.B
    }.otherwise{
      ctrl_valid := prev.valid
      ctrl_ready := true.B
    }
  }
    .elsewhen(curr_state === sRISSUE) {
      ctrl_valid:= false.B
      ctrl_ready := false.B
    }
    .elsewhen(curr_state === sRCATCH){
      ctrl_valid := false.B
      when(last) { ctrl_ready := true.B }
        .otherwise{  ctrl_ready := false.B }
    }
    .elsewhen(curr_state === sRWRITE){
      ctrl_valid := temp_valid// this may be same as prev.valid, but could cause unpredicted problem
      when(!miss){  ctrl_ready := true.B }
        .otherwise{  ctrl_ready := false.B }
    }
  // AXI Control Signal
  axi_r_in.ready := true.B
  when(next_state === sRISSUE){
    axi_ar_out.valid := true.B
    axi_ar_out.bits.id := trans_id//prev.bits.pc2if.pc
    axi_ar_out.bits.addr := Cat(prev.bits.pc2if.pc(63/*AXI4Parameters.addrBits - 1*/, 4), 0.U(4.W))// [PARA]
    axi_ar_out.bits.size := 3.U // soc datasheet [PARA]
    axi_ar_out.bits.len  := 1.U // cache line / (axi_size * 8) [CAL]
    axi_ar_out.bits.burst := AXI4Parameters.BURST_INCR
  }
    .otherwise{
      axi_ar_out.valid := false.B
      axi_ar_out.bits.id := 0.U
      axi_ar_out.bits.addr := 0.U
      axi_ar_out.bits.size := 0.U
      axi_ar_out.bits.len  := 0.U
      axi_ar_out.bits.burst := AXI4Parameters.BURST_INCR
    }
  // Shift Register
  val shift_reg_out = RegNext(next = read_data)//ShiftRegister(in = shift_reg_in, n = 1, en = shift_reg_en) // n = cache line / (axi_size * 8) [CAL]
  // Data Convert & Data Out(sRWrite final output data)
  val rw_data = MuxLookup(key = temp_data.if2id.pc(3, 2), default = 0.U(32.W), mapping = Array(//  val pc_index = addr(3, 2)
    "b00".U(2.W) -> shift_reg_out(31, 0),
    "b01".U(2.W) -> shift_reg_out(63, 32),
    "b10".U(2.W) -> read_data(31, 0),
    "b11".U(2.W) -> read_data(63, 32),
  ))
  dontTouch(rw_data)
  // Temp Save Register
  when(curr_state === sLOOKUP){
    when(!miss) {
      temp_valid_in := prev.valid
      temp_data_in.if2id.pc := prev.bits.pc2if.pc
    }
  }.elsewhen(curr_state === sRISSUE){
    temp_valid_in := prev.valid
  }
    .elsewhen(curr_state === sRCATCH && last/* next_state === sRWRITE */){
      temp_data_in.if2id.pc := prev.bits.pc2if.pc
      temp_data_in.if2id.inst := rw_data
    }
    .elsewhen(curr_state === sRWRITE){
      temp_valid := prev.valid
      temp_data_in.if2id.pc := prev.bits.pc2if.pc
    }
  //  .otherwise{
  //    temp_valid_reg := temp_valid_reg
  //    temp_data_reg.if2id.pc := temp_data_reg.if2id.pc
  //    temp_data_reg.if2id.inst := 0.U(32.W)
  //  }
  next.valid := ctrl_valid
  prev.ready := ctrl_ready
  // Cache Read
  val index_border_up = CacheCfg.cache_offset_bits + CacheCfg.cache_line_index_bits - 1
  val index_border_down = CacheCfg.cache_offset_bits
  val index = prev.bits.pc2if.pc(index_border_up, index_border_down)
  dontTouch(index)
  val tag_border_up = 31
  val tag_border_down = CacheCfg.cache_offset_bits + CacheCfg.cache_line_index_bits
  val data_array_in = Cat(read_data, shift_reg_out)
  val tag_array_in = prev.bits.pc2if.pc(tag_border_up, tag_border_down)
  val data_array_out = Wire(UInt(CacheCfg.cache_line_bits.W))
  when(curr_state === sRCATCH && last){
    when(lru_list(index) === 0.U){// last is 0
      lru_list(index) := 1.U//now the last is 1
      SRAM.write(data_array_0, da_0_rdata)
      SRAM.write(tag_array_0, ta_0_rdata)
      SRAM.write(data_array_1, index, data_array_in, da_1_rdata)
      SRAM.write(tag_array_1 , index, tag_array_in, ta_1_rdata)
    }
      .otherwise{
        lru_list(index) := 0.U//now the last is 0
        SRAM.write(data_array_0, index, data_array_in, da_0_rdata)
        SRAM.write(tag_array_0 , index, tag_array_in, ta_0_rdata )
        SRAM.write(data_array_1, da_1_rdata)
        SRAM.write(tag_array_1, ta_1_rdata)
      }
  }.otherwise{
    SRAM.read(data_array_0, index, da_0_rdata)//read=index
    SRAM.read(data_array_1, index, da_1_rdata)
    SRAM.read(tag_array_0, index, ta_0_rdata )
    SRAM.read(tag_array_1, index, ta_1_rdata )
  }
  val tag = prev.bits.pc2if.pc(tag_border_up, tag_border_down)
  dontTouch(tag)
  tag0_hit := (curr_state =/= sIDLE) & (ta_0_rdata === prev.bits.pc2if.pc(tag_border_up, tag_border_down))
  tag1_hit := (curr_state =/= sIDLE) & (ta_1_rdata === prev.bits.pc2if.pc(tag_border_up, tag_border_down))
  when(tag0_hit){
    miss := false.B
    data_array_out := da_0_rdata
  }
    .elsewhen(tag1_hit){
      miss := false.B
      data_array_out := da_1_rdata
    }
    .otherwise{
      miss := true.B
      data_array_out := 0.U
    }
  // Data Output
  val da_data = MuxLookup(key = temp_data.if2id.pc(3, 2), default = 0.U(32.W), mapping = Array(//  val pc_index = addr(3, 2)
    "b00".U(2.W) -> data_array_out(31,0),
    "b01".U(2.W) -> data_array_out(63,32),
    "b10".U(2.W) -> data_array_out(95,64),
    "b11".U(2.W) -> data_array_out(127,96)
  ))
  next.bits.if2id := 0.U.asTypeOf(next.bits.if2id)
  when(curr_state === sRWRITE){
    next.bits.if2id := temp_data.if2id
  }
    .elsewhen(curr_state === sLOOKUP){
      next.bits.if2id.pc := temp_data.if2id.pc
      next.bits.if2id.inst := da_data
    }

}