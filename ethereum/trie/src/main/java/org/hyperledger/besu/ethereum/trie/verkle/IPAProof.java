package org.hyperledger.besu.ethereum.trie.verkle;

import org.apache.tuweni.bytes.Bytes32;

import java.util.Arrays;
import java.util.List;

public class IPAProof {
    private static final int IPA_PROOF_DEPTH = 8;
    private final List<Bytes32> cl;
    private final List<Bytes32> cr;
    private final Bytes32 finalEvaluation;

    public IPAProof(final List<Bytes32> cl,final  List<Bytes32> cr,final  Bytes32 finalEvaluation) {
        if (cl.size() != IPA_PROOF_DEPTH || cr.size() != IPA_PROOF_DEPTH) {
            throw new IllegalArgumentException("cl and cr must have a length of " + IPA_PROOF_DEPTH);
        }
        this.cl = cl;
        this.cr = cr;
        this.finalEvaluation = finalEvaluation;
    }

    @Override
    public String toString() {
        return "IPAProof{" +
                "cl=" + cl.toString() +
                ", cr=" + cr.toString() +
                ", finalEvaluation=" + finalEvaluation +
                '}';
    }

    public List<Bytes32> getCl() {
        return cl;
    }

    public List<Bytes32> getCr() {
        return cr;
    }

    public Bytes32 getFinalEvaluation() {
        return finalEvaluation;
    }
}
