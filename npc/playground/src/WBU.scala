import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

class WBReg extends Module{
  val io = IO(new Bundle() {
    val prev = Flipped(new MEMUOut)
    val next = new MEMUOut
  })
  val rdyPrev  = io.prev.ready
  val vldPrev  = io.prev.valid
  val dataPrev = io.prev.bits
  val rdyNext  = io.next.ready
  val vldNext  = io.next.valid
  val dataNext = io.next.bits
  // Left
  rdyPrev := rdyNext//RegNext(rdyNext, true.B)//rdyNext
  // Right
  vldNext := RegEnable(init = false.B, next = vldPrev, enable = rdyNext)
  // comp
  val data = Mux(vldPrev, dataPrev, 0.U.asTypeOf((new MEMUOut).bits))
  val reg = RegEnable(init = 0.U.asTypeOf(data), next = data, enable = rdyNext)
  dataNext := reg
}

class WBU extends Module {
  val io = IO(new Bundle {
    val prev = Flipped(new MEMUOut)
    val regfile = Flipped(new RegfileWB)
  })
  private val idb = io.prev.bits.id2wb
  private val exb = io.prev.bits.ex2wb
  private val memb = io.prev.bits.mem2wb
  private val rfb  = io.regfile
  io.prev.ready := true.B
  private val prev_valid = io.prev.valid
  /* interface */
  private val we_en = idb.regfile_we_en
  private val we_addr = idb.regfile_we_addr
  private val wb_sel = idb.wb_sel
  private val result_data = exb.result_data
  private val memory_data = memb.memory_data
  /* wb2regfile interface */
  rfb.en  := we_en & prev_valid
  rfb.addr:= we_addr
  rfb.data:= Mux(wb_sel, memory_data, result_data)
  /* ebreak */
  if(!SparkConfig.ysyxSoC){
    val ebreak = Module(new Ebreak)
    ebreak.io.valid := idb.ebreak//operator.ebreak
  }
  /* feedback */
  BoringUtils.addSource(io.prev.bits.id2wb.intr_exce_ret, "wb_intr_exce_ret")
  BoringUtils.addSource(io.prev.bits.id2wb.fencei, "wb_fencei")
  /*
   Test Interface
   */
  /* test */
  val test_pc = idb.test_pc
  val test_inst = idb.test_inst
  if(SparkConfig.Printf) {
    when(!reset.asBool()){
      printf(p"${test_pc} ${test_inst}\n")
    }
  }
  /* DPIC pc out */
  if(/*!SparkConfig.ysyxSoC*/true){
    val test = Module(new TestPC)
    test.io.pc := test_pc
    test.io.npc := test_inst === "h7b".U
    test.io.is_device := idb.test_device
//    val counter_en = (test_inst =/= 0.U)
//    val (test_a, test_b) = Counter(counter_en, 1024000000)
//    val test_a_old = RegInit(test_a)
//    when(test_a % 2000.U === 0.U && test_a =/= 0.U && test_a_old =/= test_a && test_inst =/= 0.U){
//      test_a_old := test_a
//      printf(p"time   >: ${test_a} ")
//      printf(p"pc:${Hexadecimal(test_pc)} inst:${Hexadecimal(test_inst)}\n")
//    }
//    if (SparkConfig.Printf) {
//      printf(p"time: ${Hexadecimal(test_a)}\n")
//    }
//    dontTouch(test_a)
//    dontTouch(test_b)
  }
}//

object WBU {
  def apply(prev: MEMUOut,
            regfile: RegfileWB, fwu: WB2FW): WBU ={
    val MEM2WBReg = Module(new WBReg)
    MEM2WBReg.io.prev <> prev

    val wbu = Module(new WBU)
    wbu.io.prev <> MEM2WBReg.io.next
    regfile <> wbu.io.regfile

    fwu.dst_addr := MEM2WBReg.io.next.bits.id2wb.regfile_we_addr
    fwu.dst_data := wbu.io.regfile.data

    wbu
  }
}