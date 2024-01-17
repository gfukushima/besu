package org.hyperledger.besu.ethereum.mainnet.feemarket;

import org.hyperledger.besu.datatypes.Wei;

import java.util.Optional;

public class PragueFeeMarket extends CancunFeeMarket {
  public PragueFeeMarket(
      final long londonForkBlockNumber, final Optional<Wei> baseFeePerGasOverride) {
    super(londonForkBlockNumber, baseFeePerGasOverride);
  }
}
