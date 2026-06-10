package chipyard.fpga.zedboard

import chisel3._
import chisel3.experimental.{BaseModule}

import org.chipsalliance.diplomacy.nodes.{HeterogeneousBag}
import freechips.rocketchip.tilelink.{TLBundle}

import sifive.blocks.devices.uart.{UARTPortIO}

import chipyard._
import chipyard.harness._
import chipyard.iobinders._

class WithDDRMem extends HarnessBinder({
  case (th: ZedboardFPGATestHarnessImp, port: AXI4MemPort, chipId: Int) => {
    val bundle = th.zedboardOuter.axi4Client.out.head._1
    bundle <> port.io.bits
    // Address translation: Rocket Chip uses 0x80000000, but Zynq PS DDR reserved for PL starts at 0x10000000
    bundle.aw.bits.addr := port.io.bits.aw.bits.addr - 0x70000000L.U
    bundle.ar.bits.addr := port.io.bits.ar.bits.addr - 0x70000000L.U
  }
})

class WithUART extends HarnessBinder({
  case (th: ZedboardFPGATestHarnessImp, port: UARTPort, chipId: Int) => {
    port.io.rxd := 1.U
  }
})

class WithUARTTSI extends HarnessBinder({
  case (th: ZedboardFPGATestHarnessImp, port: UARTTSIPort, chipId: Int) => {
    // Cross connect: Rocket TX -> Zynq RX, Rocket RX <- Zynq TX
    // uartSink is connected to uartNode, which is a BundleBridgeSource from Zynq overlay
    th.zedboardOuter.uartSink.bundle.rxd := port.io.uart.txd
    port.io.uart.rxd := th.zedboardOuter.uartSink.bundle.txd
  }
})

class WithJTAGTieOff extends HarnessBinder({
  case (th: ZedboardFPGATestHarnessImp, port: JTAGPort, chipId: Int) => {
    port.io.TCK := false.B.asClock
    port.io.TMS := true.B
    port.io.TDI := true.B
    port.io.reset.foreach(_ := true.B)
  }
})
