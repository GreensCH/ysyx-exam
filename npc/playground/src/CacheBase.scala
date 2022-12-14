import chisel3._

object CacheCfg {
  val byte  = 8
  val hword = 16
  val word  = 32
  val dword = 64
  val paddr_bits = 39 //fixed ,and related to cpu core config

  val ram_depth_bits = 6
  val ram_depth = scala.math.pow(2, ram_depth_bits).toInt//2^6=64
  val ram_width = 128 //
  val ram_mask_scale = 1 // 1-bit 8-byte

  val cache_way = 2 //fixed
  val cache_line_bits = ram_width
  val cache_line_number = (ram_depth/cache_way).toInt // 32
  val cache_line_index_bits =  (Math.log(cache_line_number)/Math.log(2)).toInt //l2g(32) = 5
  val cache_offset_bits = (Math.log(ram_width/byte)/Math.log(2)).toInt // l2g(128/8) = l2g(16) = 4
  val cache_tag_bits = paddr_bits - cache_offset_bits - cache_line_index_bits // 39 - 5 - 4 = 30
  // This design doesn't use cache line valid
  //val cache_tag_valid_set = cache_tag_bits//begin with 0

}

class SRAMIO extends Bundle{
  val addr = Input(UInt(CacheCfg.ram_depth_bits.W))//addr
  val cen = Input(Bool())//sram enable low is active
  val wen = Input(Bool())//sram write enable low is active
  val wmask = Input(UInt(CacheCfg.ram_width.W)) // low is active
  val wdata = Input(UInt(CacheCfg.ram_width.W))
  val rdata = Output(UInt(CacheCfg.ram_width.W))
}

class SRAM extends Module{
  val io = IO(new SRAMIO)

  // arg_1(128) = ram depth, vec_arg1 = total_data(128), vec_arg2 = pre_data_size(1)
  private val data_in = Wire(Vec(CacheCfg.ram_width/CacheCfg.ram_mask_scale, UInt(CacheCfg.ram_mask_scale.W)))
  private val data_out = Wire(Vec(CacheCfg.ram_width/CacheCfg.ram_mask_scale, UInt(CacheCfg.ram_mask_scale.W)))
  private val wmask = Wire(Vec(CacheCfg.ram_width/CacheCfg.ram_mask_scale, Bool()))
  private val ram = SyncReadMem(CacheCfg.ram_depth, Vec(CacheCfg.ram_width/CacheCfg.ram_mask_scale, UInt(CacheCfg.ram_mask_scale.W)))
  wmask := (~io.wmask).asTypeOf(wmask)
  data_in := io.wdata.asTypeOf(data_in)
  io.rdata := data_out.asTypeOf(io.rdata)

  data_out := DontCare
  when(!io.cen){
    when(!io.wen){
      ram.write(io.addr, data_in, wmask)
    }.otherwise{
      data_out := ram.read(io.addr)
    }
  }

}

object SRAM{
  def apply(): SRAM = {
    val ram = Module(new SRAM)
    ram.io.cen := true.B// low is valid, when true mean sram doesn't work, or not
    ram.io.wen := true.B
    ram.io.addr := 0.U(CacheCfg.ram_depth_bits.W)
    ram.io.wdata := 0.U(CacheCfg.ram_width.W)
    ram.io.wmask := "hffffffff_ffffffff_ffffffff_ffffffff".U
    ram
  }

  def write(ram: SRAMIO, addr: UInt, wdata: UInt, rdata: UInt): Unit = {
    ram.cen := false.B
    ram.wen := false.B
    ram.addr := addr
    ram.wdata := wdata
    ram.wmask := 0.U(CacheCfg.ram_width.W)
    rdata := ram.rdata
  }

  def read(ram: SRAMIO, cen: Bool, addr: UInt, rdata: UInt): Unit = {
    ram.cen := cen
    ram.wen := true.B//true is close, false is open
    ram.addr := addr
    ram.wdata := 0.U
    ram.wmask := "hffffffff_ffffffff_ffffffff_ffffffff".U
    rdata := ram.rdata
  }
}





