package chipyard.fpga.zedboard

import org.chipsalliance.cde.config.{Config, Parameters}
import freechips.rocketchip.subsystem.{SystemBusKey}
import freechips.rocketchip.diplomacy.{RegionType, AddressSet}
import chipyard._
import chipyard.harness._

class WithSystemModifications extends Config((site, here, up) => {
  case testchipip.serdes.SerialTLKey => Nil // remove serialized tl port
})

class WithZedboardTweaks extends Config(
  new chipyard.harness.WithAllClocksFromHarnessClockInstantiator ++
  new chipyard.clocking.WithPassthroughClockGenerator ++
  new chipyard.config.WithUniformBusFrequencies(50) ++
  new chipyard.harness.WithHarnessBinderClockFreqMHz(50) ++
  new WithDDRMem ++
  new WithUART ++
  new WithUARTTSI ++
  new chipyard.iobinders.WithUARTTSIPunchthrough ++
  new testchipip.tsi.WithUARTTSIClient(115200) ++
  new WithJTAGTieOff ++
  new WithSystemModifications ++
  new freechips.rocketchip.subsystem.WithExtMemSize(0x10000000L) ++ // 256MB
  new testchipip.soc.WithNoScratchpads
)

class WithFPGAFrequency(fMHz: Double) extends Config(
  new chipyard.config.WithPeripheryBusFrequency(fMHz) ++
  new chipyard.config.WithSystemBusFrequency(fMHz) ++
  new chipyard.config.WithMemoryBusFrequency(fMHz) ++
  new chipyard.config.WithFrontBusFrequency(fMHz) ++
  new chipyard.config.WithControlBusFrequency(fMHz)
)

class RocketZedboardConfig extends Config(
  new WithFPGAFrequency(50) ++
  new WithZedboardTweaks ++
  new freechips.rocketchip.subsystem.WithInclusiveCache(nWays = 2, capacityKB = 32) ++ // Tiny L2 Cache to preserve mbus and save LUTs
  new freechips.rocketchip.rocket.WithNBigCores(1) ++ // Use Big core instead of Huge core
  new chipyard.config.AbstractConfig
)
