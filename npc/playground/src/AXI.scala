import chisel3._
import chisel3.util._

trait CoreParameter {
  val XLEN = 64
  val HasMExtension = true
  val HasDiv = true
  val AddrBits = 64 // AddrBits is used in some cases
  val VAddrBits = 39 // VAddrBits is Virtual Memory addr bits
  val PAddrBits = 32 // PAddrBits is Phyical Memory addr bits
  val AddrBytes = AddrBits / 8 // unused
  val DataBits = XLEN
}

object AXI4Parameters extends CoreParameter {
  // These are all fixed by the AXI4 standard:
  val lenBits   = 8
  val sizeBits  = 3
  val burstBits = 2
  val respBits  = 2

  // These are not fixed:
  val idBits    = 1
  val addrBits  = PAddrBits
  val dataBits  = DataBits
  val userBits  = 1

  def BURST_FIXED = 0.U(burstBits.W)
  def BURST_INCR  = 1.U(burstBits.W)
  def BURST_WRAP  = 2.U(burstBits.W)

  def RESP_OKAY   = 0.U(respBits.W)
  def RESP_EXOKAY = 1.U(respBits.W)
  def RESP_SLVERR = 2.U(respBits.W)
  def RESP_DECERR = 3.U(respBits.W)
}

class AXI4BundleA extends MyDecoupledIO{
  override val bits = new Bundle {
    val addr = Output(UInt(AXI4Parameters.addrBits.W))
    val burst = Output(UInt(AXI4Parameters.burstBits.W))
    val id = Output(UInt(AXI4Parameters.idBits.W))
    val len = Output(UInt(AXI4Parameters.lenBits.W))
    val size = Output(UInt(AXI4Parameters.sizeBits.W))
  }
}
class AXI4BundleR extends MyDecoupledIO{
  override val bits = new Bundle {
    val data = Output(UInt(AXI4Parameters.dataBits.W))
    val id   = Output(UInt(AXI4Parameters.idBits.W))
    val last = Output(Bool())
    val resp = Output(UInt(AXI4Parameters.respBits.W))
  }
}
class AXI4BundleW extends MyDecoupledIO{
  override val bits = new Bundle {
    val data = Output(UInt(AXI4Parameters.dataBits.W))
    val strb = Output(UInt(8.W))
    val last = Output(Bool())
  }
}
class AXI4BundleB extends MyDecoupledIO{
  override val bits = new Bundle {
    val id = Output(UInt(AXI4Parameters.idBits.W))
    val resp = Output(UInt(AXI4Parameters.respBits.W))
  }
}

class AXI4Master extends Bundle{
  val ar = new AXI4BundleA
  val r = Flipped(new AXI4BundleR)
  val aw = new AXI4BundleA
  val w = new AXI4BundleW
  val b = Flipped(new AXI4BundleB)
}



class Interconnect extends Module{
  val io = IO(new Bundle{
    val s00 = Flipped(new AXI4Master)
    val s01 = Flipped(new AXI4Master)
    val s02 = Flipped(new AXI4Master)
    val m00 = new AXI4Master
    val m01 = new AXI4Master
  })
  /*
   IO Interface
   */
  val s_second   = io.s00
  val s_first    = io.s01
  val s_device   = io.s02
  val memory     = io.m00
  val device     = io.m01
  dontTouch(io.s00)
  dontTouch(io.s01)
  dontTouch(io.s02)
  dontTouch(io.m00)
  dontTouch(io.m01)
  /**** ID allocation ****/
  val zero_id   = 0.U(AXI4Parameters.idBits)
  val icache_id = 1.U(AXI4Parameters.idBits)
  val dcache_id = 2.U(AXI4Parameters.idBits)
  /**** Default Connection ****/
//  AXI4Master.default(s_first)
  s_first <> memory
  AXI4Slave.default(s_second)
  //  AXI4Master.default(memory)
//  s_second <> DontCare
 /**** Arbiter ****/
  /*  read channel */
  s_first.ar.ready := memory.ar.ready
  s_second.ar.ready := memory.ar.ready
  when(s_first.ar.valid){
    memory.ar.valid := true.B
    memory.ar.bits.id := 1.U
    memory.ar.bits <> s_first.ar.bits
  }.elsewhen(s_second.ar.valid){
    memory.ar.valid := true.B
    memory.ar.bits.id := 2.U
    memory.ar.bits <> s_second.ar.bits
  }
  memory.r.ready := (s_first.r.ready & s_second.r.ready)
  when(memory.r.bits.id === 1.U){
    s_first.r.valid := memory.r.valid
    s_first.r.bits <> memory.r.bits
  }.elsewhen(memory.r.bits.id === 2.U){
    s_second.r.valid := memory.r.valid
    s_second.r.bits <> memory.r.bits
  }
  /*  write channel */
  s_first.b <> memory.b
  s_first.w <> memory.w
  s_first.aw <> memory.aw
  /**** Other connection(Route) ****/
  s_device <> device
}

object Interconnect{
  def apply(s00: AXI4Master, s01: AXI4Master, s02: AXI4Master, m00: AXI4Master, m01: AXI4Master):  Interconnect = {
    val interconnect = Module(new Interconnect)
     interconnect.io.s00 <> s00
     interconnect.io.s01 <> s01
     interconnect.io.s02 <> s02
     interconnect.io.m00 <> m00
     interconnect.io.m01 <> m01
    interconnect
  }
}
/*
 Default used by consumer
 Clear used by producer
 */
object AXI4BundleA{
  def apply(): AXI4BundleA = {
    val wire = WireDefault(0.U.asTypeOf(new AXI4BundleA))
    wire
  }
  def default(inf: AXI4BundleA): Unit = {
    inf.ready := true.B
  }
  def clear(inf: AXI4BundleA): Unit = {
    inf.valid := false.B
    inf.bits.id := 0.U
    inf.bits.addr := 0.U
    inf.bits.size := 0.U
    inf.bits.len  := 0.U
    inf.bits.burst := AXI4Parameters.BURST_INCR
  }
  def set(inf: AXI4BundleA, id: UInt, addr: UInt, burst_size: UInt, burst_len: UInt): Unit ={
    inf.valid := true.B
    inf.bits.id := id
    inf.bits.addr := addr
    inf.bits.size := burst_size
    inf.bits.len  := burst_len
    inf.bits.burst := AXI4Parameters.BURST_INCR
  }
}

object AXI4BundleR{
  def apply(): AXI4BundleR = {
    val wire = WireDefault(0.U.asTypeOf(new AXI4BundleR))
    wire
  }
  def default(inf: AXI4BundleR): Unit = {
    inf.ready := true.B
  }
  def clear(inf: AXI4BundleR): Unit = {
    inf.valid := false.B
    inf.bits.id := 0.U
    inf.bits.resp := 0.U
    inf.bits.data := 0.U
    inf.bits.last := false.B
  }
}

object AXI4BundleW{
  def apply(): AXI4BundleW = {
    val wire = WireDefault(0.U.asTypeOf(new AXI4BundleW))
    wire
  }
  def default(inf: AXI4BundleW): Unit ={
    inf.ready := true.B
  }
  def set(inf: AXI4BundleW, data: UInt, strb: UInt, last: Bool): Unit ={
    inf.valid := true.B
    inf.bits.data := data
    inf.bits.strb := strb
    inf.bits.last  := last
  }
  def set(inf: AXI4BundleW, valid: Bool, data: UInt, strb: UInt, last: Bool): Unit ={
    inf.valid := valid
    inf.bits.data := data
    inf.bits.strb := strb
    inf.bits.last  := last
  }
  def clear(inf: AXI4BundleW): Unit ={
    inf.valid := false.B
    inf.bits.data := 0.U
    inf.bits.strb := 0.U
    inf.bits.last := false.B
  }
}

object AXI4BundleB{
  def apply(): AXI4BundleB = {
    val wire = WireDefault(0.U.asTypeOf(new AXI4BundleB))
    wire
  }
  def default(inf: AXI4BundleB): Unit = {
    inf.ready := true.B
  }
  def clear(inf: AXI4BundleB): Unit = {
    inf.valid := false.B
    inf.bits.id := 0.U
    inf.bits.resp := 0.U
  }
}

object AXI4Master{
  def default(maxi: AXI4Master): Unit = {
    AXI4BundleA.clear(maxi.ar)
    AXI4BundleA.clear(maxi.aw)
    AXI4BundleR.default(maxi.r)
    AXI4BundleW.clear(maxi.w)
    AXI4BundleB.default(maxi.b)
  }
}

object AXI4Slave{
  def default(maxi: AXI4Master): Unit = {
    AXI4BundleA.default(maxi.ar)
    AXI4BundleA.default(maxi.aw)
    AXI4BundleR.clear(maxi.r)
    AXI4BundleW.default(maxi.w)
    AXI4BundleB.clear(maxi.b)
  }
}

class AXI4ManagerIn extends Bundle{
  val rd_en  = Input(Bool())
  val we_en  = Input(Bool())
  val size   = Flipped(new SrcSize{ val qword  = Output(Bool()) })
  val addr   = Input(UInt(32.W))
  val data   = Input(UInt(128.W))
  val wmask  = Input(UInt(16.W))
}
class AXI4ManagerOut extends Bundle{
  val finish = Output(Bool())
  val ready  = Output(Bool())
  val data   = Output(UInt(128.W))
}

class AXI4Manager extends Module{
  val io = IO(new Bundle {
    val in   = new AXI4ManagerIn
    val maxi = new AXI4Master
    val out  = new AXI4ManagerOut
  })
  /*
   Reference
   */
//  private val in = io.in
  private val maxi = io.maxi
  private val out = io.out
  private val sADDR :: sREAD1 :: sREAD2 :: sWRITE1 :: sWRITE2 :: Nil = Enum(5)
  private val next_state = Wire(UInt(sADDR.getWidth.W))
  private val curr_state = RegNext(init = sADDR, next = next_state)
  /* Lookup Stage */
  private val stage_en = Wire(Bool())
  private val stage_in = Wire(Output(chiselTypeOf(io.in)))
  stage_in := io.in
  private val stage_out2 = RegEnable(init = 0.U.asTypeOf(stage_in),next = stage_in, enable = stage_en)
  private val _in = Wire(Output(chiselTypeOf(io.in)))
  _in := io.in
  private val in2 = Mux(curr_state === sADDR, _in, stage_out2)
  /* AXI Read Channel Stage */
  private val r_stage_in = Wire(UInt(AXI4Parameters.dataBits.W))
  private val r_stage_out = RegNext(init = 0.U(AXI4Parameters.dataBits.W), next = r_stage_in)
  /* AXI Interface Default Connection(Read-Write) */
  AXI4BundleA.clear   (maxi.ar)
  AXI4BundleR.default (maxi.r)
  AXI4BundleA.clear   (maxi.aw)
  AXI4BundleW.clear   (maxi.w)
  AXI4BundleB.default (maxi.b)
  /*
  Internal Control Signal
  */
  /* axi */
  private val r_last = maxi.r.bits.last  & maxi.r.valid
  /* common */
  private val is_load = in2.rd_en
  private val is_save = in2.we_en
  private val size = in2.size
  private val overborder = MuxCase(false.B, Array(
    size.byte  -> false.B,
    size.hword -> (in2.addr(0)    =/= 0.U),
    size.word  -> (in2.addr(1, 0) =/= 0.U),
    size.dword -> (in2.addr(2, 0) =/= 0.U),
    size.qword -> true.B
  ))
  private val a_waiting = (curr_state === sADDR) & ((is_load & (maxi.ar.ready === false.B)) | (is_save & (maxi.aw.ready === false.B)))
  /* stage */
  stage_en := curr_state === sADDR
  /*
   Internal Data Signal
  */
  /* reference */
  private val a_addr = Mux(overborder, Cat(in2.addr(31, 4), 0.U(4.W)), Cat(in2.addr(31, 3), 0.U(3.W)))
  private val start_byte = Mux(overborder, in2.addr(3, 0), in2.addr(2, 0))
  private val start_bit =  Mux(overborder, in2.addr(3, 0) << 3, in2.addr(2, 0) << 3).asUInt()
  /* read transaction */
  r_stage_in := MuxCase(0.U, Array(
    (curr_state === sREAD1 & !r_last) -> maxi.r.bits.data,
    (curr_state === sREAD2) -> r_stage_out
  )) //    r_stage_in := Mux(curr_state === sREAD_1 & !r_last, maxi.r.bits.data, r_stage_out)
  private val rdata_out_1 = maxi.r.bits.data >> start_bit
  private val rdata_out_2 = Cat(maxi.r.bits.data, r_stage_out) >> start_bit
  private val rdata_out = MuxCase(0.U, Array(
    (curr_state === sREAD1) -> rdata_out_1,
    (curr_state === sREAD2) -> rdata_out_2
  ))
  private val memory_data = MuxCase(0.U,
    Array(
      size.byte   -> rdata_out(7,  0),
      size.hword  -> rdata_out(15, 0),
      size.word   -> rdata_out(31, 0),
      size.dword  -> rdata_out,
    )
  )
  private val memory_data_buffer = RegInit(0.U(128.W))
  /* write transaction */
  private val wdata = (in2.data << start_bit).asTypeOf(0.U(128.W))
  private val wmask = MuxCase(0.U(8.W), Array(
    size.byte  -> (in2.wmask  << start_byte),
    size.hword -> (in2.wmask  << start_byte),
    size.word  -> (in2.wmask  << start_byte),
    size.dword -> (in2.wmask  << start_byte),
  )).asUInt()
  /*
   States Change Rule
   */
  next_state := sADDR
  switch(curr_state){
    is(sADDR){
      when(is_load){
        when(maxi.ar.ready) { next_state := sREAD1 } .otherwise  { next_state := sADDR }
      }.elsewhen(is_save){
        when(maxi.aw.ready) { next_state := sWRITE1 } .otherwise { next_state := sADDR }
      }.otherwise           { next_state := sADDR }
    }
    is(sREAD1){
      when(r_last)                            { next_state := sADDR }
        .elsewhen(overborder & maxi.r.ready)  { next_state := sREAD2 }
        .otherwise                            { next_state := sREAD1 }
    }
    is(sWRITE1){
      when(overborder & maxi.w.ready )        { next_state := sWRITE2  }
        .elsewhen(maxi.b.valid)               { next_state := sADDR    }
        .otherwise                            { next_state := sWRITE1 }
    }
    is(sREAD2){
      when(r_last)                            { next_state := sADDR }
        .otherwise                            { next_state := sREAD2 }
    }
    is(sWRITE2){
      when(maxi.b.valid)                      { next_state := sADDR }
        .otherwise                            { next_state := sWRITE2 }
    }
  }
  /*
    AXI
   */
  private val burst_len = Mux(overborder, 1.U, 0.U)
  //    private val w_stay = RegInit(0.U.asTypeOf((new AXI4BundleW).bits))
  AXI4BundleA.clear(maxi.ar)
  when(curr_state === sADDR & next_state === sREAD1){
    AXI4BundleA.set(inf = maxi.ar, id = 0.U, addr = a_addr, burst_size = 3.U, burst_len = burst_len)
  }
  AXI4BundleA.clear(maxi.aw)
  when(curr_state === sADDR & next_state === sWRITE1){
    AXI4BundleA.set(inf = maxi.aw, id = 0.U, addr = a_addr, burst_size = 3.U, burst_len = burst_len)
  }
  AXI4BundleW.clear(maxi.w)
  when(curr_state === sWRITE1){
    AXI4BundleW.set(inf = maxi.w, data = wdata(63, 0), strb = wmask, last = !overborder)
  }
  when(curr_state === sWRITE2){
    AXI4BundleW.set(inf = maxi.w, data = wdata(127, 64), strb = wmask, last = true.B)
  }
  AXI4BundleB.default(maxi.b)
  dontTouch(maxi.b)
  /*
  Output
 */
  out.ready  := (next_state === sADDR & (!a_waiting))
  out.finish := (next_state === sADDR & curr_state =/= sADDR)
  memory_data_buffer := Mux(out.finish, memory_data, memory_data_buffer)
  out.data := Mux(curr_state === sREAD1 | curr_state === sREAD2, memory_data, memory_data_buffer)
}


//class AXIMaster extends Module{
//  val io = IO(new AXI4)
//}
//
//class AXISlave extends Module{
//  val io = IO(Flipped(new AXI4))
//  AXI4Parameters.CACHE_BUFFERABLE
//}