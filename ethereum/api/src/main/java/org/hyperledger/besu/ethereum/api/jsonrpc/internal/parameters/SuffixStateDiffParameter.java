package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.ethereum.trie.verkle.SuffixStateDiff;

public class SuffixStateDiffParameter {

    private final byte suffix;
    private final Bytes32 currentValue;
    private final Bytes32 newValue;
    @JsonCreator

    public SuffixStateDiffParameter(
            @JsonProperty("suffix")
             final byte suffix,
    @JsonProperty("currentValue")
     final Bytes32 currentValue,
    @JsonProperty("newValue")
     final Bytes32 newValue
    ) {
        this.suffix = suffix;
        this.currentValue = currentValue;
        this.newValue = newValue;
    }

    public static SuffixStateDiffParameter fromSuffixStateDiff(final SuffixStateDiff suffixStateDiff) {
        return new SuffixStateDiffParameter(
                suffixStateDiff.getSuffix(),
                suffixStateDiff.getCurrentValue(),
                suffixStateDiff.getNewValue());
    }

    public static SuffixStateDiff toSuffixStateDiff(final SuffixStateDiffParameter suffixStateDiffParameter) {
        return new SuffixStateDiff(
                suffixStateDiffParameter.getSuffix(),
                suffixStateDiffParameter.getCurrentValue(),
                suffixStateDiffParameter.getNewValue());
    }

    @Override
    public String toString() {
        return "SuffixStateDiff{" +
                "suffix=" + suffix +
                ", currentValue=" + currentValue;
    }

    @JsonGetter
    public byte getSuffix() {
        return suffix;
    }

    @JsonGetter
    public Bytes32 getCurrentValue() {
        return currentValue;
    }

    @JsonGetter
    public Bytes32 getNewValue() {
        return newValue;
    }
}
