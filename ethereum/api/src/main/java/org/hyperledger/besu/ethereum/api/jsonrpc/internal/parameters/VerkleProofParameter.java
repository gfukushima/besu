package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.ethereum.trie.verkle.VerkleProof;

import java.util.List;
public class VerkleProofParameter {
    //TODO Maybe create a Bytes31 in Tuweni for stems?

    final List<Bytes> otherStems;
    private final List<Bytes> depthExtensionPresent;
    private final List<Bytes32> commitmentsByPath;
    private final Bytes32 d;
    private final IPAProofParameter ipaProof;

    @JsonCreator
    public VerkleProofParameter(
            @JsonProperty("otherStems")
            final List<Bytes> otherStems,
    @JsonProperty("depthExtensionPresent")
     final List<Bytes> depthExtensionPresent,
    @JsonProperty("commitmentsByPath")
     final List<Bytes32> commitmentsByPath,
    @JsonProperty("d")
     final Bytes32 d,
    @JsonProperty("ipaProof")
     final IPAProofParameter ipaProof
    ) {
        this.otherStems = otherStems;
        this.depthExtensionPresent = depthExtensionPresent;
        this.commitmentsByPath = commitmentsByPath;
        this.d = d;
        this.ipaProof = ipaProof;
    }

    public static VerkleProofParameter fromVerkleProof(final org.hyperledger.besu.ethereum.trie.verkle.VerkleProof verkleProof) {
        return new VerkleProofParameter(
                verkleProof.getOtherStems(),
                verkleProof.getDepthExtensionPresent(),
                verkleProof.getCommitmentsByPath(),
                verkleProof.getD(),
                IPAProofParameter.fromIPAProof(verkleProof.getIpaProof()));
    }

    public static VerkleProof toVerkleProof(final VerkleProofParameter verkleProofParameter) {
        return new VerkleProof(
                verkleProofParameter.getOtherStems(),
                verkleProofParameter.getDepthExtensionPresent(),
                verkleProofParameter.getCommitmentsByPath(),
                verkleProofParameter.getD(),
                IPAProofParameter.toIPAProof(verkleProofParameter.getIpaProof()));
    }

    @JsonGetter
    public List<Bytes> getOtherStems() {
        return otherStems;
    }
    @JsonGetter
    public List<Bytes> getDepthExtensionPresent() {
        return depthExtensionPresent;
    }
    @JsonGetter
    public List<Bytes32> getCommitmentsByPath() {
        return commitmentsByPath;
    }
    @JsonGetter
    public Bytes32 getD() {
        return d;
    }
    @JsonGetter
    public IPAProofParameter getIpaProof() {
        return ipaProof;
    }
}
