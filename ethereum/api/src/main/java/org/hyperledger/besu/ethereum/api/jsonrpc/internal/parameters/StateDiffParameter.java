package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.ethereum.trie.verkle.StateDiff;


public class StateDiffParameter {
     final Bytes stem;
     final SuffixStateDiffParameter suffixDiffs;

    @JsonCreator
    public StateDiffParameter(
            @JsonProperty("stem") final Bytes stem,
            @JsonProperty("suffixDiffs") final SuffixStateDiffParameter suffixDiffs) {
        this.stem = stem;
        this.suffixDiffs = suffixDiffs;
    }

    public static StateDiffParameter fromStateDiff(final StateDiff stateDiff) {
        return new StateDiffParameter(
                stateDiff.getStem(),
                SuffixStateDiffParameter.fromSuffixStateDiff(stateDiff.getSuffixDiffs()));
    }

    public static StateDiff toStateDiff(final StateDiffParameter stateDiffParameter) {
        return new StateDiff(
                stateDiffParameter.getStem(),
                SuffixStateDiffParameter.toSuffixStateDiff(stateDiffParameter.getSuffixDiffs()));
    }

    @JsonGetter
    public Bytes getStem() {
        return stem;
    }

    @JsonGetter
    public SuffixStateDiffParameter getSuffixDiffs() {
        return suffixDiffs;
    }
}
