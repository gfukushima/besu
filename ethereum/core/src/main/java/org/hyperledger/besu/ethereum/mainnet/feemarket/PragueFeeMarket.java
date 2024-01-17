package org.hyperledger.besu.ethereum.mainnet.feemarket;

import org.hyperledger.besu.datatypes.Wei;

import java.util.Optional;

public class PragueFeeMarket extends CancunFeeMarket{
    public PragueFeeMarket(long londonForkBlockNumber, Optional<Wei> baseFeePerGasOverride) {
        super(londonForkBlockNumber, baseFeePerGasOverride);
    }
}
