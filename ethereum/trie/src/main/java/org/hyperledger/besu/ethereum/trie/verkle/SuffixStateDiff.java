package org.hyperledger.besu.ethereum.trie.verkle;

import org.apache.tuweni.bytes.Bytes32;

public class SuffixStateDiff {
    private final byte suffix;
    private final Bytes32 currentValue;
    private final Bytes32 newValue;

    public SuffixStateDiff(final byte suffix, final Bytes32 currentValue, final Bytes32 newValue) {
        this.suffix = suffix;
        this.currentValue = currentValue;
        this.newValue = newValue;
    }

    @Override
    public String toString() {
        return "SuffixStateDiff{" +
                "suffix=" + suffix +
                ", currentValue=" + currentValue +
                ", new Value =" + newValue;
    }

    public byte getSuffix() {
        return suffix;
    }

    public Bytes32 getCurrentValue() {
        return currentValue;
    }

    public Bytes32 getNewValue() {
        return newValue;
    }
}
