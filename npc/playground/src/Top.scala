import chisel3._


/**
  * Spark CPU: A Single Cycle Processor,
  * CPU powered by RV64IM instruction set 
  * 
  */

class Top extends Module {
  val io = IO(new Bundle {
    val inst = Input(UInt(32.W))
  })

  val regfile = Module(new RegFile)
  val ifu = Module(new IFU)
  val idu = Module(new IDU)
  val bru = Module(new BRU)
  val fwu = Module(new FWU)
  val exu = Module(new EXU)
  val memu = Module(new MEMU)
  val wbu = Module(new WBU)

  val pcu   = Module(new PC)
  val reg_id = Module(new IDReg)
  val reg_ex = Module(new EXReg)
  val reg_mem = Module(new MEMReg)
  val reg_wb = Module(new WBReg)

 /* CPU Interconnection */
  /* IF(PC) from ID also branch transfer path*/
  /* PCU */
  pcu.io.stall := fwu.io.fw2pc.stall // FWUnit in to PCUnit
  pcu.io.br2pc := bru.io.br2pc // BRHUnit in to PCUnit
  ifu.io.pc2if := pcu.io.pc2if // PCUnit out to IFUnit
  reg_id.io.in.if2id := ifu.io.if2id  // IFUnit out to NextReg
  /* ID from IF */
  idu.io.fw2id := fwu.io.fw2id          // FWUnit in to IDUnit
  idu.io.if2id := reg_id.io.out.if2id   // PreReg in to IDUnit
  bru.io.id2br := idu.io.id2br          // IDUnit out to BRHUnit
  reg_ex.io.in.id2ex := idu.io.id2ex    // IDUnit out to NextReg
  reg_ex.io.in.id2mem := idu.io.id2mem  // IDUnit out to NextReg
  reg_ex.io.in.id2wb := idu.io.id2wb    // IDUnit out to NextReg
  /* EX from ID */
  exu.io.id2ex := reg_ex.io.out.id2ex           // PreReg in to EXUnit (exu.io.id2ex := idu.io.id2ex)
  reg_mem.io.in.ex2mem := exu.io.ex2mem         // EXUnit out to NextReg
  reg_mem.io.in.ex2wb  := exu.io.ex2wb          // EXUnit out to NextReg
  reg_mem.io.in.id2mem := reg_ex.io.out.id2mem  // PreReg out to NextReg
  reg_mem.io.in.id2wb := reg_ex.io.out.id2wb    // PreReg out to NextReg
  /* MEM from ID EX */
  memu.io.id2mem := reg_mem.io.out.id2mem     // MEMUnit in to PreReg(memu.io.id2mem := idu.io.id2mem)
  memu.io.ex2mem := reg_mem.io.out.ex2mem     // MEMUnit in to PreReg(memu.io.ex2mem := exu.io.ex2mem)
  reg_wb.io.in.mem2wb := memu.io.mem2wb       // MEMUnit out to NextReg
  reg_wb.io.in.id2wb  := reg_mem.io.out.id2wb // PreReg out to NextReg
  reg_wb.io.in.ex2wb  := reg_mem.io.out.ex2wb // PreReg out to NextReg
  /* WB from ID EX MEM */
  wbu.io.id2wb := reg_wb.io.out.id2wb  //PreReg in to WBUnit（wbu.io.id2wb := idu.io.id2wb）
  wbu.io.ex2wb := reg_wb.io.out.ex2wb  //PreReg in to WBUnit（wbu.io.ex2wb := exu.io.ex2wb）
  wbu.io.mem2wb := reg_wb.io.out.mem2wb//PreReg in to WBUnit（wbu.io.mem2wb:= memu.io.mem2wb）
  /* Branch unit interface */
  reg_id.io.bubble := bru.io.br2regid.bubble
  /* Forwarding unit interface */
   /* in */
  fwu.io.id2fw := idu.io.id2fw //IDUnit
  fwu.io.ex2fw.is_load := reg_ex.io.out.id2mem.memory_rd_en
  fwu.io.ex2fw.dst_addr := reg_ex.io.out.id2wb.regfile_we_addr//EXUnit
  fwu.io.ex2fw.dst_data := exu.io.ex2wb.result_data
  fwu.io.mem2fw.dst_addr := reg_mem.io.out.id2wb.regfile_we_addr//MEMUnit
  fwu.io.mem2fw.dst_data := Mux(reg_mem.io.out.id2mem.memory_rd_en, memu.io.mem2wb.memory_data, reg_mem.io.out.ex2wb.result_data)
  fwu.io.wb2fw.dst_addr := reg_wb.io.out.id2wb.regfile_we_addr//WBUnit
  fwu.io.wb2fw.dst_data := wbu.io.wb2regfile.data
   /* out */
  reg_ex.io.bubble := fwu.io.fw2regex.bubble
  reg_id.io.stall := fwu.io.fw2regid.stall
  pcu.io.stall := fwu.io.fw2pc.stall
  /* Regfile Connection */
  regfile.io.idu.en := idu.io.regfile2id.en
  regfile.io.idu.addr1 := idu.io.regfile2id.addr1
  regfile.io.idu.addr2 := idu.io.regfile2id.addr2
  idu.io.regfile2id.data1 := regfile.io.idu.data1
  idu.io.regfile2id.data2 := regfile.io.idu.data2
  regfile.io.wbu.data := wbu.io.wb2regfile.data
  regfile.io.wbu.addr := wbu.io.wb2regfile.addr
  regfile.io.wbu.en := wbu.io.wb2regfile.en

  val in2 = Wire(new PC2IF)
  val out2 = Wire(new PC2IF)
  in2 := 0.U.asTypeOf(new PC2IF)
  val ifu2 = IFU2(in = in2, out = out2)
  printf(s"${out2}")
}

  /* monitor and top interface */
//  io.inst := ifu.io.if2id.inst
//  io.pc := ifu.io.if2id.pc
//  val monitor = Module(new Monitor)
//  monitor.io.pc := ifu.io.if2id.pc
//  monitor.io.inst :=ifu.io.if2id.inst


