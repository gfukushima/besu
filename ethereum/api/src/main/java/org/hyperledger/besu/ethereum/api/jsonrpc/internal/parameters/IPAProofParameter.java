package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.ethereum.trie.verkle.IPAProof;

import java.util.List;

public class IPAProofParameter {
    static final int IPA_PROOF_DEPTH = 8;
     private final List<Bytes32> cl;
    private final List<Bytes32> cr;
    private final Bytes32 finalEvaluation;
    @JsonCreator
    public IPAProofParameter(
    @JsonProperty("cl")  final List<Bytes32> cl,
    @JsonProperty("cr") final List<Bytes32> cr,
    @JsonProperty("finalEvaluation") final Bytes32 finalEvaluation) {
        if (cl.size() != IPA_PROOF_DEPTH || cr.size() != IPA_PROOF_DEPTH) {
            throw new IllegalArgumentException("cl and cr must have a length of " + IPA_PROOF_DEPTH);
        }
        this.cl = cl;
        this.cr = cr;
        this.finalEvaluation = finalEvaluation;
    }

    public static IPAProofParameter fromIPAProof(final IPAProof ipaProof) {
        return new IPAProofParameter(
                ipaProof.getCl(),
                ipaProof.getCr(),
                ipaProof.getFinalEvaluation());
    }

    public static IPAProof toIPAProof(final IPAProofParameter ipaProofParameter) {
        return new org.hyperledger.besu.ethereum.trie.verkle.IPAProof(
                ipaProofParameter.getCl(),
                ipaProofParameter.getCr(),
                ipaProofParameter.getFinalEvaluation());
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
