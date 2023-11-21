package org.hyperledger.besu.ethereum.trie.verkle;

import org.apache.tuweni.bytes.Bytes;


public class StateDiff {
     final Bytes stem;
     final SuffixStateDiff suffixDiffs;

    public StateDiff(final Bytes stem, final SuffixStateDiff suffixDiffs) {
        this.stem = stem;
        this.suffixDiffs = suffixDiffs;
    }

    public Bytes getStem() {
        return stem;
    }

    public SuffixStateDiff getSuffixDiffs() {
        return suffixDiffs;
    }
}
