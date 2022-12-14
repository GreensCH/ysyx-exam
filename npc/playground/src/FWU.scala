import chisel3._
import chisel3.util._

class IDFW extends Bundle{
  val fw_src1_data   = Input(UInt(64.W))
  val fw_src2_data   = Input(UInt(64.W))
  val fw_ready    =  Input(Bool())
  val optype      =   new Optype
  val operator    =   new Operator
  val src1_addr   =   Output (UInt(5.W))
  val src2_addr   =   Output (UInt(5.W))
  val src1_data   =   Output (UInt(64.W))
  val src2_data   =   Output (UInt(64.W))
}

class EX2FW extends Bundle{
  val is_load = Output(Bool())
  val dst_addr = Output (UInt(5.W))
  val dst_data = Output (UInt(64.W))
}

class MEM2FW extends Bundle{
  val is_load_1 = Output(Bool())
  val dst_addr_1 = Output(UInt(5.W))
  val dst_data_1 = Output(UInt(64.W))
  val dst_addr_2 = Output(UInt(5.W))
}

class WB2FW extends Bundle{
  val dst_addr = Output (UInt(5.W))
  val dst_data = Output (UInt(64.W))
}

class FWU extends Module{
  val io = IO(new Bundle() {
    val idu  = Flipped(new IDFW)
    val exu  = Flipped(new EX2FW)
    val memu = Flipped(new MEM2FW)
    val wbu  = Flipped(new WB2FW)
  })
  val idb = io.idu
  val exb = io.exu
  val memb = io.memu
  val wbb = io.wbu

  val ex_is_load = exb.is_load
  val mem1_is_load = memb.is_load_1
  val optype   = idb.optype
  val operator = idb.operator
  val id_data1 = idb.src1_data
  val id_addr1 = idb.src1_addr
  val id_data2 = idb.src2_data
  val id_addr2 = idb.src2_addr
  val ex_data  = exb.dst_data
  val ex_addr  = exb.dst_addr
  val mem_data_1 = memb.dst_data_1
  val mem_addr_1 = memb.dst_addr_1
  val mem_addr_2 = memb.dst_addr_2
  val wb_data  = wbb.dst_data
  val wb_addr  = wbb.dst_addr

  val ex_zero_n   = ex_addr  =/= 0.U
  val mem1_zero_n = mem_addr_1 =/= 0.U
  val mem2_zero_n = mem_addr_2 =/= 0.U
  val wb_zero_n   = wb_addr  =/= 0.U
  val zero_n      = ex_zero_n | mem1_zero_n | mem2_zero_n | wb_zero_n

  val eq1_ex    = id_addr1 === ex_addr    & ex_zero_n
  val eq1_mem1  = id_addr1 === mem_addr_1 & mem1_zero_n
  val eq1_mem2  = id_addr1 === mem_addr_2 & mem2_zero_n
  val eq1_wb    = id_addr1 === wb_addr    & wb_zero_n
  val eq2_ex    = id_addr2 === ex_addr    & ex_zero_n
  val eq2_mem1  = id_addr2 === mem_addr_1 & mem1_zero_n
  val eq2_mem2  = id_addr2 === mem_addr_2 & mem2_zero_n
  val eq2_wb    = id_addr2 === wb_addr    & wb_zero_n

  val pre_is_load = ((eq1_ex | eq2_ex) & (ex_is_load)) | mem1_is_load | eq1_mem2 | eq2_mem2

  idb.fw_src1_data := MuxCase(id_data1,
    Array(
      (eq1_ex)   -> ex_data,
      (eq1_mem1) -> mem_data_1,
      (eq1_wb)   -> wb_data
    )
  )

  idb.fw_src2_data := MuxCase(id_data2,
    Array(
      (optype.Itype) -> id_data2,
      (eq2_ex)       -> ex_data,
      (eq2_mem1)     -> mem_data_1,
      (eq2_wb)       -> wb_data
    )
  )

  io.idu.fw_ready := !pre_is_load

}

object FWU{
  def apply(idu: IDFW, exu: EX2FW, memu: MEM2FW, wbu: WB2FW): Unit ={
    val fwu = Module(new FWU)
    fwu.io.idu <> idu
    fwu.io.exu <> exu
    fwu.io.memu <> memu
    fwu.io.wbu <> wbu
  }
}
