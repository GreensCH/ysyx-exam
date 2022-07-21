import chisel3._
import chisel3.util._


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
  vldNext := RegEnable(next = vldPrev, enable = rdyNext)
  // comp
  val data = Mux(vldPrev, dataPrev, 0.U.asTypeOf((new MEMUOut).bits))
  val reg = RegEnable(next = data, enable = rdyNext)
  dataNext := reg
}

class WBU extends Module {
  val io = IO(new Bundle {
    val prev = Flipped(new MEMUOut)
    val regfile = Flipped(new RegfileWB)
  })
  val idb = io.prev.bits.id2wb
  val exb = io.prev.bits.ex2wb
  val memb = io.prev.bits.mem2wb
  val rfb  = io.regfile
  io.prev.ready := true.B
  io.prev.valid <> DontCare
  /* interface */
  val we_en = idb.regfile_we_en
  val we_addr = idb.regfile_we_addr
  val wb_sel = idb.wb_sel
  val result_data = exb.result_data
  val memory_data = memb.memory_data
  /* wb2regfile interface */
  rfb.en  := we_en
  rfb.addr:= we_addr
  rfb.data:= Mux(wb_sel, memory_data, result_data)

  
  /*
   Test Interface
   */
  /* test */
  val test_pc = idb.test_pc
  val test_inst = idb.test_inst
  if(SparkConfig.printf) {
    when(!reset.asBool()){
      printf(p"${test_pc} ${test_inst}\n")
    }
  }
  /* DPIC pc out */
  val test = Module(new TestPC)
  test.io.pc := test_pc
  test.io.npc := DontCare
  val counter_en = (test_inst =/= 0.U)
  val (test_a, test_b) = Counter(counter_en, 4096)
  if(SparkConfig.debug) { printf(p"time: ${Hexadecimal(test_a)}\n") }
  dontTouch(test_a)
  dontTouch(test_b)
}

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