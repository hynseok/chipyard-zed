package chipyard.fpga.zedboard

import chisel3._

import freechips.rocketchip.diplomacy.{LazyModule, LazyRawModuleImp, BundleBridgeSource, BundleBridgeSink}
import org.chipsalliance.cde.config.{Parameters}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.diplomacy.{IdRange, TransferSizes}
import freechips.rocketchip.subsystem.{SystemBusKey, ExtMem}
import freechips.rocketchip.prci._
import sifive.fpgashells.shell.xilinx._
import sifive.fpgashells.ip.xilinx.{IBUF, PowerOnResetFPGAOnly}
import sifive.fpgashells.shell._
import sifive.fpgashells.clocks._

import sifive.blocks.devices.uart.{PeripheryUARTKey, UARTPortIO}

import chipyard._
import chipyard.harness._

class ZedboardFPGATestHarness(override implicit val p: Parameters) extends ZedboardShellBasicOverlays {
  def dp = designParameters

  val dutFreqMHz = (dp(SystemBusKey).dtsFrequency.get / (1000 * 1000)).toInt
  val dutClock = ClockSinkNode(freqMHz = dutFreqMHz)
  println(s"Zedboard FPGA Base Clock Freq: ${dutFreqMHz} MHz")
  val dutWrangler = LazyModule(new ResetWrangler)
  val dutGroup = ClockGroup()
  
  require(dp(Zynq7000OverlayKey).size >= 1)
  val zynqOverlay = dp(Zynq7000OverlayKey)(0).place(Zynq7000DesignInput()).asInstanceOf[Zynq7000PlacedOverlay]
  val harnessSysPLL = dp(PLLFactoryKey)()
  harnessSysPLL := zynqOverlay.overlayOutput.ps_clkNode
  dutClock := dutWrangler.node := dutGroup := harnessSysPLL

  val extMem = dp(ExtMem).get
  val axi4Client = AXI4MasterNode(Seq(AXI4MasterPortParameters(Seq(AXI4MasterParameters(
    name = "chip_ddr",
    id = IdRange(0, 1 << extMem.master.idBits)
  )))))

  zynqOverlay.overlayOutput.pl2ps := axi4Client
  
  val uartSink = BundleBridgeSink[UARTPortIO]()
  uartSink := zynqOverlay.overlayOutput.uartNode

  override lazy val module = new ZedboardFPGATestHarnessImp(this)
}

class ZedboardFPGATestHarnessImp(_outer: ZedboardFPGATestHarness) extends LazyRawModuleImp(_outer) with HasHarnessInstantiators {
  override def provideImplicitClockToLazyChildren = true
  val zedboardOuter = _outer

  val reset = IO(Input(Bool())).suggestName("reset")
  _outer.xdc.addPackagePin(reset, "P16")
  _outer.xdc.addIOStandard(reset, "LVCMOS33")

  val resetIBUF = Module(new IBUF)
  resetIBUF.io.I := reset

  val sysclk: Clock = _outer.zynqOverlay.overlayOutput.ps_clkNode.out.head._1.clock

  val powerOnReset: Bool = PowerOnResetFPGAOnly(sysclk)
  _outer.sdc.addAsyncPath(Seq(powerOnReset))

  _outer.pllReset := (resetIBUF.io.O || powerOnReset || _outer.zynqOverlay.overlayOutput.ps_clkNode.out.head._1.reset.asBool)

  val hReset = Wire(Reset())
  hReset := _outer.dutClock.in.head._1.reset

  def referenceClockFreqMHz = _outer.dutFreqMHz
  def referenceClock = _outer.dutClock.in.head._1.clock
  def referenceReset = hReset
  def success = { require(false, "Unused"); false.B }

  childClock := referenceClock
  childReset := referenceReset

  instantiateChipTops()
}
