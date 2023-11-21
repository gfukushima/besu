package org.hyperledger.besu.ethereum.trie.verkle;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

import java.util.List;

public class VerkleProof {
    //TODO Maybe create a Bytes31 in Tuweni for stems?
    private final List<Bytes> otherStems;
    private final List<Bytes> depthExtensionPresent;
    private final List<Bytes32> commitmentsByPath;
    private final Bytes32 d;
    private final IPAProof ipaProof;

    public VerkleProof(final List<Bytes> otherStems, final List<Bytes> depthExtensionPresent, final List<Bytes32> commitmentsByPath, final Bytes32 d, final IPAProof ipaProof) {
        this.otherStems = otherStems;
        this.depthExtensionPresent = depthExtensionPresent;
        this.commitmentsByPath = commitmentsByPath;
        this.d = d;
        this.ipaProof = ipaProof;
    }

    @Override
    public String toString() {
        return "VerkleProof{" +
                "otherStems=" + otherStems +
                ", depthExtensionPresent=" + depthExtensionPresent +
                ", commitmentsByPath=" + commitmentsByPath +
                ", d=" + d +
                ", ipaProof=" + ipaProof +
                '}';
    }

    public List<Bytes> getOtherStems() {
        return otherStems;
    }

    public List<Bytes> getDepthExtensionPresent() {
        return depthExtensionPresent;
    }

    public List<Bytes32> getCommitmentsByPath() {
        return commitmentsByPath;
    }

    public Bytes32 getD() {
        return d;
    }

    public IPAProof getIpaProof() {
        return ipaProof;
    }
}
