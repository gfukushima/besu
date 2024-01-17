package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.ethereum.mainnet.feemarket.BaseFeeMarket;

public class PragueTargetingGasLimitCalculator extends CancunTargetingGasLimitCalculator {
  public PragueTargetingGasLimitCalculator(
      final long londonForkBlock, final BaseFeeMarket feeMarket) {
    super(londonForkBlock, feeMarket);
  }
}
