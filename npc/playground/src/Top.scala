import chisel3._
//import chisel3.util._
//import chisel3.experimental._


/**
  * Spark CPU: A Single Cycle Processor,
  * CPU powered by RV64IM instruction set 
  * 
  */

class Top extends Module {
  val io = IO(new Bundle {
    val inst_i = Input(UInt(64.W))

  })
  val pc_unit = Module(new PCUnit)
  io.inst_i := DontCare
  pc_unit.io := DontCare
}
